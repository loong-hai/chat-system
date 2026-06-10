package com.hailong.chatsystem.service.message;

import com.hailong.chatsystem.model.dto.ChatMessageDTO;
import com.hailong.chatsystem.model.document.ChatMessageDocument;
import com.hailong.chatsystem.repository.mongo.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class MessagePersistenceService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<ChatMessageDocument> findUnreadMessages(Long receiverId) {
        return chatMessageRepository.findUnreadMessages(receiverId);
    }



    /**
     * 查询离线消息
     */
    public List<ChatMessageDocument> findOfflineMessages(Long receiverId) {
        try {
            List<ChatMessageDocument> result = chatMessageRepository.findOfflineMessages(receiverId);
            log.debug("查询用户[{}]离线消息，结果数：{}", receiverId, result.size());
            return result;
        } catch (Exception e) {
            log.error("查询离线消息失败: receiverId={}", receiverId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 安全保存消息（带重试机制）
     */
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public ChatMessageDocument saveMessage(ChatMessageDTO message) {
        try {
            // 生成唯一的消息ID（如果前端没有提供）
            if (message.getMessageId() == null || message.getMessageId().isEmpty()) {
                message.setMessageId(UUID.randomUUID().toString());
            }

            // 确保有服务器时间
            if (message.getServerTime() == null) {
                message.setServerTime(LocalDateTime.now());
            }

            // 确保有状态
            if (message.getStatus() == null) {
                message.setStatus(ChatMessageDTO.MessageStatus.SENDING);
            }

            // 检查是否已存在（幂等性处理）
            if (message.getMessageId() != null) {
                boolean exists = chatMessageRepository.existsByMessageId(message.getMessageId());
                if (exists) {
                    log.warn("消息已存在，跳过保存: messageId={}", message.getMessageId());
                    return chatMessageRepository.findByMessageId(message.getMessageId())
                            .orElseThrow(() -> new RuntimeException("消息已存在但查询失败"));
                }
            }

            // 转换为文档
            ChatMessageDocument document = ChatMessageDocument.fromDTO(message);
            if (document.getIsDeleted() == null) {
                document.setIsDeleted(false);
            }


            // 保存到MongoDB
            ChatMessageDocument savedDocument = chatMessageRepository.save(document);

            // 添加额外的日志信息到extra字段（可选）
            if (savedDocument.getExtra() == null) {
                Map<String, Object> extra = new HashMap<>();
                extra.put("persistedAt", LocalDateTime.now().toString());
                extra.put("persistAttempts", 1);
                savedDocument.setExtra(extra);
                savedDocument = chatMessageRepository.save(savedDocument);
            }

            log.info("消息持久化成功: messageId={}, docId={}, status={}",
                    savedDocument.getMessageId(), savedDocument.getId(), savedDocument.getStatus());

            return savedDocument;

        } catch (Exception e) {
            log.error("消息持久化失败: messageId={}, content={}",
                    message.getMessageId(), message.getContent(), e);

            // 记录持久化失败的详细信息
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("persistError", e.getMessage());
            errorInfo.put("errorTime", LocalDateTime.now().toString());
            message.setExtra(errorInfo);

            throw new RuntimeException("消息持久化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将指定会话中当前用户的所有未读消息标记为已读
     * @param conversationId 会话ID
     * @param userId 当前用户ID
     */
    public void markConversationAsRead(String conversationId, Long userId) {
        // 构建查询条件
        Query query = new Query(Criteria.where("conversationId").is(conversationId)
                .and("receiverId").is(userId)
                .and("status").is("DELIVERED"));
        // 更新内容
        Update update = new Update().set("status", "READ")
                .set("readAt", LocalDateTime.now());
        // 执行批量更新
        UpdateResult result = mongoTemplate.updateMulti(query, update, ChatMessageDocument.class);
        log.info("标记会话已读完成: conversationId={}, userId={}, 更新{}条消息",
                conversationId, userId, result.getModifiedCount());
    }

    /**
     * 更新消息状态（带状态机校验的原子更新）
     * 1. 使用 MongoTemplate 的 updateFirst 实现原子更新（CAS机制）
     * 2. 增加状态机校验，防止非法状态流转（如 DELIVERED -> DELIVERED 覆盖 READ）
     * 3. 使用乐观锁（版本号/状态条件）防止并发覆盖
     */
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public void updateMessageStatus(String messageId, String newStatus, LocalDateTime timestamp) {
        try {
            // ===== 1. 状态机定义（允许的状态流转）=====
            Map<String, List<String>> validTransitions = new HashMap<>();
            validTransitions.put("SENDING", Arrays.asList("SENT", "FAILED", "OFFLINE"));
            validTransitions.put("SENT", Arrays.asList("DELIVERED", "FAILED", "OFFLINE"));
            validTransitions.put("DELIVERED", Arrays.asList("READ")); // 关键：DELIVERED 只能转为 READ
            validTransitions.put("FAILED", Arrays.asList("RESENDING", "OFFLINE"));
            validTransitions.put("OFFLINE", Arrays.asList("DELIVERING", "SENT"));
            validTransitions.put("DELIVERING", Arrays.asList("DELIVERED", "FAILED"));
            validTransitions.put("READ", Collections.emptyList()); // 终态，不可再变

            // ===== 2. 查询当前状态（用于日志和前置校验）=====
            String currentStatus = getMessageStatus(messageId);

            // 如果已经是目标状态，直接返回（幂等性）
            if (newStatus.equals(currentStatus)) {
                log.debug("消息状态无需更新（已是目标状态）: messageId={}, status={}", messageId, newStatus);
                return;
            }

            // 状态机校验：检查是否允许从 currentStatus -> newStatus
            if (currentStatus != null) {
                List<String> allowedNextStates = validTransitions.getOrDefault(
                        currentStatus,
                        Collections.emptyList()
                );

                if (!allowedNextStates.contains(newStatus)) {
                    log.warn("非法状态流转被阻止: messageId={}, 当前状态={}, 尝试更新为={}",
                            messageId, currentStatus, newStatus);
                    return; // 直接返回，不执行更新
                }
            }

            // ===== 3. 构建原子更新条件（Query）=====
            Query query = new Query(Criteria.where("messageId").is(messageId));

            // 【关键】乐观锁：如果是更新为 READ，确保当前状态是 DELIVERED（防止覆盖）
            // 这解决了并发场景下：线程A(DELIVERED->READ) 与 线程B(DELIVERED->DELIVERED) 的竞态
            if ("READ".equals(newStatus)) {
                query.addCriteria(Criteria.where("status").is("DELIVERED"));
            }
            // 如果是 DELIVERED，确保不是已经是 READ（防止回退）
            else if ("DELIVERED".equals(newStatus)) {
                query.addCriteria(Criteria.where("status").ne("READ"));
            }

            // ===== 4. 构建更新内容（Update）=====
            Update update = new Update();
            update.set("status", newStatus);
            update.set("updatedAt", LocalDateTime.now());

            // 根据状态设置对应的时间戳字段
            LocalDateTime actualTime = (timestamp != null) ? timestamp : LocalDateTime.now();

            switch (newStatus) {
                case "DELIVERED":
                    update.set("deliveredAt", actualTime);
                    break;
                case "READ":
                    update.set("readAt", actualTime);
                    break;
                case "FAILED":
                    // 使用 $push 追加失败历史（数组）
                    Map<String, Object> failureRecord = new HashMap<>();
                    failureRecord.put("time", LocalDateTime.now());
                    failureRecord.put("reason", "更新失败");
                    update.push("failureHistory", failureRecord);
                    break;
            }

            // 追加状态变更历史（审计日志）
            Map<String, String> statusChange = new HashMap<>();
            statusChange.put("from", currentStatus != null ? currentStatus : "null");
            statusChange.put("to", newStatus);
            statusChange.put("time", LocalDateTime.now().toString());
            update.push("statusHistory", statusChange);

            // ===== 5. 执行原子更新 =====
            // updateFirst: 只更新第一条匹配的记录
            // ChatMessageDocument.class: 指定集合对应的实体类
            UpdateResult result = mongoTemplate.updateFirst(
                    query,
                    update,
                    ChatMessageDocument.class
            );

            // ===== 6. 处理更新结果 =====
            if (result.getMatchedCount() == 0) {
                // 未匹配到文档，可能是：
                // 1. 消息不存在
                // 2. 乐观锁冲突（状态已被其他线程修改）

                String actualCurrentStatus = getMessageStatus(messageId);

                if (actualCurrentStatus == null) {
                    throw new RuntimeException("消息不存在: " + messageId);
                } else if (actualCurrentStatus.equals(newStatus)) {
                    // 已经是目标状态，视为成功（幂等）
                    log.debug("消息状态已被其他线程更新为{}: messageId={}", newStatus, messageId);
                    return;
                } else {
                    // 乐观锁冲突：当前状态与预期不符
                    log.warn("乐观锁冲突: messageId={}, 期望前置状态≠实际当前状态={}, 尝试更新为={}",
                            messageId, actualCurrentStatus, newStatus);
                    throw new RuntimeException("状态已被修改，请重试: current=" + actualCurrentStatus + ", expected=" + newStatus);
                }
            }

            if (result.getModifiedCount() == 0 && result.getMatchedCount() > 0) {
                // 匹配到了但 ModifiedCount=0，通常是因为设置的数据与现有数据相同
                log.debug("消息状态未改变（可能已是相同值）: messageId={}", messageId);
            }

            log.info("✅ 消息状态更新成功: messageId={}, {} -> {}",
                    messageId, currentStatus, newStatus);

        } catch (Exception e) {
            log.error("❌ 更新消息状态失败: messageId={}, status={}, error={}",
                    messageId, newStatus, e.getMessage());
            throw new RuntimeException("更新消息状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取消息的当前状态（辅助方法）
     */
    public String getMessageStatus(String messageId) {
        try {
            return chatMessageRepository.findByMessageId(messageId)
                    .map(ChatMessageDocument::getStatus)
                    .orElse(null);
        } catch (Exception e) {
            log.error("获取消息状态失败: messageId={}", messageId, e);
            return null;
        }
    }

    /**
     * 批量更新消息状态（用于离线消息推送等场景）
     */
    @Transactional
    public void batchUpdateStatus(String conversationId, String oldStatus, String newStatus) {
        try {
            // TODO: 实现基于conversationId和状态的批量更新
            log.info("批量更新消息状态: conversationId={}, {} -> {}",
                    conversationId, oldStatus, newStatus);
        } catch (Exception e) {
            log.error("批量更新消息状态失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 标记消息为已读（支持批量）
     */
    @Transactional
    public void markAsRead(String messageId) {
        updateMessageStatus(messageId, "READ", LocalDateTime.now());
    }


    /**
     * 消息是否已持久化
     */
    public boolean isMessagePersisted(String messageId) {
        return chatMessageRepository.existsByMessageId(messageId);
    }

    /**
     * 修复丢失的消息（用于数据恢复）
     */
    @Transactional
    public ChatMessageDocument repairMessage(ChatMessageDTO message) {
        log.warn("尝试修复丢失的消息: messageId={}", message.getMessageId());

        try {
            // 检查消息是否真的丢失
            if (isMessagePersisted(message.getMessageId())) {
                log.info("消息已存在，无需修复: messageId={}", message.getMessageId());
                return chatMessageRepository.findByMessageId(message.getMessageId()).orElse(null);
            }

            // 标记为修复的消息
            if (message.getExtra() == null) {
                message.setExtra(new HashMap<>());
            }
            message.getExtra().put("repaired", true);
            message.getExtra().put("repairedAt", LocalDateTime.now().toString());

            // 重新保存
            return saveMessage(message);

        } catch (Exception e) {
            log.error("修复消息失败: messageId={}", message.getMessageId(), e);
            return null;
        }
    }
}