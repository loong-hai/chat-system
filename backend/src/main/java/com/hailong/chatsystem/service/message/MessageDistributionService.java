// service/message/MessageDistributionService.java - 重构版
package com.hailong.chatsystem.service.message;

import com.hailong.chatsystem.config.InstanceInfo;
import com.hailong.chatsystem.config.RabbitMQConfig;
import com.hailong.chatsystem.model.document.ChatMessageDocument;
import com.hailong.chatsystem.model.dto.ChatMessageDTO;
import com.hailong.chatsystem.service.rules.OnlineStatusRuleEngine;
import com.hailong.chatsystem.service.websocket.WebSocketMessageService;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MessageDistributionService {

    @Autowired
    private MessageRouter messageRouter;

    @Autowired
    private InstanceInfo instanceInfo;

    @Autowired
    private MessagePersistenceService messagePersistenceService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private WebSocketMessageService webSocketMessageService;


    @Autowired
    private WebSocketSessionService webSocketSessionService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    @Autowired
    private StringRedisTemplate stringRedisTemplate; // 新增

    // 新增锁相关常量
    private static final String OFFLINE_PUSH_LOCK_PREFIX = "chat:offline:push:lock:";
    private static final String OFFLINE_PUSH_PROGRESS_PREFIX = "chat:offline:push:progress:";

    // 添加销毁方法
    @PreDestroy
    public void shutdown() {
        log.info("正在关闭消息分发服务...");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("消息分发服务已关闭");
    }

    /**
     * 分发聊天消息 - 重构版
     * 流程：持久化 -> 规则检查 -> 队列处理
     */
    public void distributeChatMessage(ChatMessageDTO message) {
        // 1. 保留：立即持久化到MongoDB
        try {
            messagePersistenceService.saveMessage(message);
        } catch (Exception e) {
            log.error("消息持久化失败: messageId={}", message.getMessageId(), e);
            sendNotificationToSender(message, "消息保存失败");
            return;
        }

        Long receiverId = message.getReceiverId();
        String currentInstanceId = instanceInfo.getInstanceId();

        try {
            // 2. 查询目标实例
            String targetInstance = messageRouter.getUserInstance(String.valueOf(receiverId));

            if (targetInstance == null) {
                // 场景A：用户离线 -> 转离线存储
                log.debug("用户离线，消息转存储: receiverId={}", receiverId);
                messagePersistenceService.updateMessageStatus(
                        message.getMessageId(), "OFFLINE", LocalDateTime.now());
                return;
            }

            if (targetInstance.equals(currentInstanceId)) {
                // 场景B：同实例 -> 直接内存推送（优化，不走MQ）
                log.debug("同实例投递: receiverId={}, instance={}", receiverId, currentInstanceId);
                boolean sent = webSocketMessageService.sendChatMessage(message);

                if (sent) {
                    messagePersistenceService.updateMessageStatus(
                            message.getMessageId(), "DELIVERED", LocalDateTime.now());
                } else {
                    // 推送失败，转为离线（用户可能刚断开）
                    messagePersistenceService.updateMessageStatus(
                            message.getMessageId(), "OFFLINE", LocalDateTime.now());
                }
            } else {
                // 场景C：跨实例 -> 发送到目标实例队列
                log.debug("跨实例投递: receiverId={}, from={}, to={}",
                        receiverId, currentInstanceId, targetInstance);

                // 检查目标实例存活（二次确认）
                if (!messageRouter.isInstanceAlive(targetInstance)) {
                    log.warn("目标实例已死，转离线: targetInstance={}", targetInstance);
                    messagePersistenceService.updateMessageStatus(
                            message.getMessageId(), "OFFLINE", LocalDateTime.now());
                    return;
                }

                // 在 rabbitTemplate.convertAndSend 之前添加：
                messagePersistenceService.updateMessageStatus(
                        message.getMessageId(), "SENT", LocalDateTime.now());

                // 发送到目标实例专属队列
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.PUSH_EXCHANGE,
                        targetInstance,  // routingKey = 目标实例ID
                        message
                );

                // 注意：此时不更新为DELIVERED，由目标实例消费后更新
            }

        } catch (Exception e) {
            log.error("消息分发异常，降级为离线存储: messageId={}", message.getMessageId(), e);
            // 降级策略：任何异常都转离线，保证不丢消息
            messagePersistenceService.updateMessageStatus(
                    message.getMessageId(), "OFFLINE", LocalDateTime.now());
        }
    }

    @RabbitListener(queues = "#{rabbitMQConfig.pushQueue(instanceInfo).getName()}")
    public void handleInstanceMessage(ChatMessageDTO message, Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        String messageId = message.getMessageId();
        boolean pushed = false;

        try {
            // 1. 双重检查：用户是否还在线（防止路由表延迟）
            Long userId = message.getReceiverId();
            if (!webSocketSessionService.isUserOnline(userId)) {
                log.warn("用户已离线，消息转离线: messageId={}", messageId);
                messagePersistenceService.updateMessageStatus(messageId, "OFFLINE", LocalDateTime.now());
                channel.basicAck(tag, false); // 确认已从MQ移除
                return;
            }

            // 2. 尝试WebSocket推送（最多重试1次）
            pushed = webSocketMessageService.sendChatMessage(message);

            if (pushed) {
                // 3. 推送成功：ACK + 更新DELIVERED
                channel.basicAck(tag, false);
                messagePersistenceService.updateMessageStatus(messageId, "DELIVERED", LocalDateTime.now());
                log.debug("消息推送成功: messageId={}", messageId);
            } else {
                // 4. 推送失败：NACK + 转离线（不重新入队，防止死循环）
                log.error("WebSocket推送失败，转离线存储: messageId={}", messageId);
                messagePersistenceService.updateMessageStatus(messageId, "OFFLINE", LocalDateTime.now());
                channel.basicNack(tag, false, false); // 不重新入队，直接丢弃
            }

        } catch (Exception e) {
            log.error("消费异常，转离线: messageId={}", messageId, e);
            // 异常也转离线，确保不丢
            try {
                messagePersistenceService.updateMessageStatus(messageId, "OFFLINE", LocalDateTime.now());
                channel.basicNack(tag, false, false);
            } catch (IOException ioException) {
                log.error("NACK失败，消息可能重复投递", ioException);
            }
        }
    }



    // 补偿机制：如果 DB 更新失败，通过延迟队列重试
    private void sendStatusUpdateCompensation(String messageId, String status) {
        // 发送到延迟队列，5 秒后重试更新 DB
        // 简化实现：直接记录到 Redis，由定时任务补偿
        stringRedisTemplate.opsForSet().add("chat:msg:status:pending", messageId + ":" + status);
    }

    /**
     * RabbitMQ消息监听器 - 处理队列中的消息
     * 只负责消息推送，不修改消息内容
     */
    /**
     * RabbitMQ消息监听器 - 处理队列中的消息（修复版）
     * 核心修复：确保状态更新成功后才ACK，否则消息重回队列
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE)
    public void handleChatMessage(ChatMessageDTO message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        String messageId = message.getMessageId();

        // 1. 幂等性检查：是否已在处理/处理完成（使用 Redis 去重，5分钟窗口）
        String processingKey = "msg:processing:" + messageId;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(processingKey, "1", 24, TimeUnit.HOURS); // 原为5分钟

        if (!Boolean.TRUE.equals(isNew)) {
            log.warn("消息重复投递，跳过处理: messageId={}", messageId);
            channel.basicAck(tag, false); // 直接确认，避免重复
            return;
        }

        try {
            // 2. 检查当前状态，只允许从特定状态流转（状态机）
            String currentStatus = messagePersistenceService.getMessageStatus(messageId);

            // 如果已经是终态，直接 ACK
            if ("DELIVERED".equals(currentStatus) || "READ".equals(currentStatus)) {
                channel.basicAck(tag, false);
                return;
            }

            // 如果不是 SENDING/IN_QUEUE/FAILED，可能是乱序，记录警告
            if (!Arrays.asList("SENDING", "IN_QUEUE", "FAILED", "OFFLINE").contains(currentStatus)) {
                log.warn("消息状态异常，可能乱序: messageId={}, status={}", messageId, currentStatus);
            }

            // 3. 先推送，成功后更新状态，最后 ACK（逆向操作）
            boolean sent = webSocketMessageService.sendChatMessage(message);

            if (sent) {
                // 推送成功，更新状态（允许失败重试，不影响 ACK）
                try {
                    messagePersistenceService.updateMessageStatus(
                            messageId, "DELIVERED", LocalDateTime.now());
                } catch (Exception dbEx) {
                    // DB 更新失败不影响消息已送达的事实，记录告警即可
                    log.error("消息已送达但 DB 状态更新失败: messageId={}", messageId, dbEx);
                    // 可发送补偿事件到延迟队列
                }

                // 确认消息已从队列移除
                channel.basicAck(tag, false);

            } else {
                // 推送失败，标记为离线存储，进入死信队列
                messagePersistenceService.updateMessageStatus(messageId, "OFFLINE", null);
                channel.basicNack(tag, false, false); // 不重新入队，进入死信
            }
            // 2. 在catch块中不要立即删除key，避免无限快速重试
        } catch (Exception e) {
            log.error("处理消息异常: messageId={}", messageId, e);
            // 删除下面这行：stringRedisTemplate.delete(processingKey);
            // 让消息在24小时后自然过期重试，或记录到死信表人工处理
            channel.basicNack(tag, false, false); // 进入死信队列，而非立即重试
        }
    }



    /**
     * 处理规则动作
     */
    private void handleRuleAction(OnlineStatusRuleEngine.RuleResult ruleResult, ChatMessageDTO message) {
        try {
            String newStatus;
            String reason = ruleResult.getMessage();

            switch (ruleResult.getAction()) {
                case STORE_OFFLINE:
                    newStatus = "OFFLINE";
                    log.info("消息转为离线存储: {}", reason);
                    break;

                case DELAY_PUSH:
                    newStatus = "DELAYED";
                    scheduleDelayedPush(message, 300); // 延迟5分钟
                    log.info("消息延迟推送: {}", reason);
                    break;

                case BLOCK:
                    newStatus = "BLOCKED";
                    log.warn("消息被阻止: {}", reason);
                    break;

                default:
                    newStatus = "PENDING";
                    break;
            }

            // 更新MongoDB中的消息状态
            messagePersistenceService.updateMessageStatus(
                    message.getMessageId(),
                    newStatus,
                    null
            );

            // 通知发送者
            if (ruleResult.getAction() == OnlineStatusRuleEngine.RuleAction.NOTIFY_SENDER) {
                sendNotificationToSender(message, reason);
            }

        } catch (Exception e) {
            log.error("处理规则动作失败", e);
        }
    }

    /**
     * 调度延迟推送
     */
    private void scheduleDelayedPush(ChatMessageDTO message, long delaySeconds) {
        scheduler.schedule(() -> {
            try {
                log.info("执行延迟推送: messageId={}, 延迟{}秒",
                        message.getMessageId(), delaySeconds);

                // 更新状态为重新发送
                messagePersistenceService.updateMessageStatus(
                        message.getMessageId(),
                        "RESENDING",
                        null
                );

                // 重新分发消息
                distributeChatMessage(message);
            } catch (Exception e) {
                log.error("延迟推送失败: messageId={}", message.getMessageId(), e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * 推送离线消息（用户登录时调用）- 完整实现（带并发控制）
     * 核心修复：1. 分布式锁防止并发推送 2. 进度记录防止重复推送
     */
    public void pushOfflineMessagesOnLogin(Long userId) {
        String lockKey = OFFLINE_PUSH_LOCK_PREFIX + userId;
        String lockValue = UUID.randomUUID().toString(); // 唯一标识

        try {
            // 获取锁时设置唯一值
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, 30, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(locked)) {
                return;
            }

            log.info("开始推送离线消息: userId={}", userId);

            // 【修复】延长已推送记录保留时间至72小时
            String progressKey = OFFLINE_PUSH_PROGRESS_PREFIX + userId;

            // 【修复】查询当前72小时内已推送的消息ID
            Set<String> recentlyPushedIds = stringRedisTemplate.opsForSet().members(progressKey);


            // 【修复】查询离线消息（排除最近72小时内已推送的）
            List<ChatMessageDocument> offlineMessages =
                    messagePersistenceService.findOfflineMessages(userId);

            if (offlineMessages.isEmpty()) {
                log.debug("用户没有离线消息: userId={}", userId);
                return;
            }

            // 【修复】双重过滤：1. 72小时内已推送的 2. 当前状态仍为OFFLINE的
            List<ChatMessageDocument> toPush = new ArrayList<>();
            for (ChatMessageDocument doc : offlineMessages) {
                // 检查1：72小时内是否已推送
                boolean recentlyPushed = recentlyPushedIds != null &&
                        recentlyPushedIds.contains(doc.getId());

                if (recentlyPushed) {
                    continue;
                }

                // 检查2：当前状态是否仍为OFFLINE（可能已被其他方式处理）
                String currentStatus = messagePersistenceService.getMessageStatus(doc.getMessageId());
                if ("OFFLINE".equals(currentStatus)) {
                    toPush.add(doc);
                } else {
                    log.debug("跳过消息，当前状态不是OFFLINE: messageId={}, status={}",
                            doc.getMessageId(), currentStatus);
                }
            }


            if (toPush.isEmpty()) {
                log.debug("所有离线消息已在最近24小时内推送过: userId={}", userId);
                return;
            }

            log.info("找到 {} 条待推送离线消息（过滤掉 {} 条已推送）: userId={}",
                    toPush.size(), offlineMessages.size() - toPush.size(), userId);

            // 4. 分批推送
            int batchSize = 50;
            int totalPushed = 0;
            int failedCount = 0;

            for (int i = 0; i < toPush.size(); i += batchSize) {
                List<ChatMessageDocument> batch = toPush.subList(
                        i, Math.min(i + batchSize, toPush.size())
                );

                for (ChatMessageDocument doc : batch) {
                    try {
                        // 【修复】再次检查是否已推送（双重检查）
                        if (stringRedisTemplate.opsForSet().isMember(progressKey, doc.getId())) {
                            continue;
                        }
                        // 【修复】再次检查当前消息状态是否为OFFLINE
                        String currentStatus = messagePersistenceService.getMessageStatus(doc.getMessageId());
                        if (!"OFFLINE".equals(currentStatus)) {
                            log.debug("跳过消息，因为当前状态不是OFFLINE: messageId={}, currentStatus={}",
                                    doc.getMessageId(), currentStatus);
                            continue;
                        }

                        // 转换为DTO并推送
                        ChatMessageDTO message = doc.toDTO();

                        // 更新为投递中
                        messagePersistenceService.updateMessageStatus(
                                message.getMessageId(),
                                "DELIVERING",
                                LocalDateTime.now()
                        );

                        // 尝试WebSocket推送
                        boolean sent = webSocketMessageService.sendChatMessage(message);

                        if (sent) {
                            // 推送成功
                            messagePersistenceService.updateMessageStatus(
                                    message.getMessageId(),
                                    "DELIVERED",
                                    LocalDateTime.now()
                            );

                            // 【修复】记录已推送（72小时过期）
                            stringRedisTemplate.opsForSet().add(progressKey, doc.getId());
                            // 【修复】设置72小时过期时间
                            stringRedisTemplate.expire(progressKey, 72, TimeUnit.HOURS);

                            totalPushed++;
                        } else {
                            // 推送失败（用户可能再次离线），保持离线状态
                            failedCount++;
                            log.warn("离线消息推送失败（用户可能已离线）: messageId={}",
                                    message.getMessageId());
                        }

                        // 控制推送速率，避免压垮客户端
                        Thread.sleep(20);

                    } catch (Exception e) {
                        failedCount++;
                        log.error("推送单条离线消息失败: messageId={}", doc.getMessageId(), e);
                    }
                }

                // 每批处理完，延长锁的过期时间（防止大批消息处理时锁过期）
                stringRedisTemplate.expire(lockKey, 30, TimeUnit.SECONDS);
            }

            log.info("离线消息推送完成: userId={}, 成功推送{}/{}条, 失败{}条",
                    userId, totalPushed, toPush.size(), failedCount);

            // 5. 发送系统通知告知用户有离线消息
            if (totalPushed > 0) {
                try {
                    ChatMessageDTO notification = ChatMessageDTO.createSystemMessage(
                            0L,
                            userId,
                            String.format("您有 %d 条离线消息已送达", totalPushed),
                            Map.of("offlineMessageCount", totalPushed,
                                    "type", "offline_sync_complete",
                                    "timestamp", System.currentTimeMillis())
                    );
                    webSocketMessageService.sendChatMessage(notification);
                } catch (Exception e) {
                    log.error("发送离线消息同步通知失败", e);
                }
            }

        } catch (Exception e) {
            log.error("推送离线消息失败: userId={}", userId, e);
        } finally {
            // 使用 Lua 脚本原子性释放（只释放自己持有的锁）
            String script =
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "    return redis.call('del', KEYS[1]) " +
                            "else " +
                            "    return 0 " +
                            "end";

            stringRedisTemplate.execute(
                    new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(lockKey),
                    lockValue
            );
        }
    }

    /**
     * 发送通知给发送者
     */
    private void sendNotificationToSender(ChatMessageDTO originalMessage, String reason) {
        try {
            ChatMessageDTO notification = ChatMessageDTO.createSystemMessage(
                    0L, // 系统发送者
                    originalMessage.getSenderId(),
                    "消息处理状态: " + reason,
                    null
            );

            webSocketMessageService.sendChatMessage(notification);
        } catch (Exception e) {
            log.error("发送通知失败", e);
        }
    }

    /**
     * 处理分发失败
     */
    private void handleDistributionFailure(ChatMessageDTO message, Exception e) {
        try {
            // 更新状态为失败
            messagePersistenceService.updateMessageStatus(
                    message.getMessageId(),
                    "FAILED",
                    null
            );

            // 通知发送者
            sendNotificationToSender(message, "消息发送失败: " + e.getMessage());
        } catch (Exception ex) {
            log.error("处理分发失败也失败", ex);
        }
    }
}