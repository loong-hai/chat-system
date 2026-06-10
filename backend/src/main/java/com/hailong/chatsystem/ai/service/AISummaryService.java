package com.hailong.chatsystem.ai.service;

import com.hailong.chatsystem.ai.client.AIClient;
import com.hailong.chatsystem.ai.config.AIProperties;
import com.hailong.chatsystem.ai.dto.SummaryCacheDTO;
import com.hailong.chatsystem.model.document.ChatMessageDocument;
import com.hailong.chatsystem.repository.mongo.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.hailong.chatsystem.ai.utils.MessageFormatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AISummaryService {

    @Autowired
    private AIClient aiClient;

    @Autowired
    private AIProperties aiProperties;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String AUTO_SUMMARY_KEY_PREFIX = "ai:summary:auto:";
    private static final String AUTO_SUMMARY_LOCK_PREFIX = "ai:summary:auto:lock:";
    private static final String CHAT_SUMMARY_KEY_PREFIX = "ai:summary:chat:";
    private static final String CHAT_SUMMARY_LOCK_PREFIX = "ai:summary:chat:lock:";
    private static final String LAST_MSG_ID_KEY_PREFIX = "ai:summary:lastmsg:";


    private static final java.time.format.DateTimeFormatter TIME_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");

    // Redis Lua脚本：原子性检查并设置锁（防止重复生成）
    private static final String LOCK_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 0 then " +
                    "   redis.call('setex', KEYS[1], ARGV[1], '1'); " +
                    "   return 1; " +
                    "else " +
                    "   return 0; " +
                    "end;";

    // ==================== 功能1：自动定时总结 ====================

    /**
     * 定时任务调用：为指定用户生成自动总结（已读消息）
     * 由ScheduledTask每10分钟调用一次
     */
    /**
     * 功能1：按用户分组生成自动总结
     */
    /**
     * 格式化单条消息（带"你对XX说"或"XX对你说"的上下文）
     */
    @Async("aiTaskExecutor")
    public void generateAutoSummary(Long userId) {
        String lockKey = AUTO_SUMMARY_LOCK_PREFIX + userId;
        try {
            Boolean locked = redisTemplate.execute(
                    new DefaultRedisScript<>(LOCK_SCRIPT, Boolean.class),
                    Collections.singletonList(lockKey), "60"
            );
            if (!Boolean.TRUE.equals(locked)) return;

            // 1. 查询最近2天所有相关消息（收发都包含）
            LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
            List<ChatMessageDocument> messages = chatMessageRepository
                    .findByUserIdAndTimeRange(userId, twoDaysAgo, LocalDateTime.now());

            String summary;
            if (messages.isEmpty()) {
                summary = "暂无聊天记录";
            } else {
                // 2. 构建 conversationId -> 对方名字 映射
                Map<String, String> convToPeerName = messages.stream()
                        .filter(msg -> !msg.getSenderId().equals(userId))
                        .collect(Collectors.toMap(
                                ChatMessageDocument::getConversationId,
                                ChatMessageDocument::getSenderName,
                                (existing, replacement) -> existing
                        ));

                // 3. 格式化所有消息
                String allContext = messages.stream()
                        .sorted(Comparator.comparing(ChatMessageDocument::getServerTime))
                        .limit(100)
                        .map(msg -> formatMessageWithContext(msg, userId, convToPeerName))
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("\n"));

                if (allContext.isEmpty()) {
                    summary = "暂无需要关注的事项";
                } else {
                    // 4. 调用AI
                    String prompt = "请总结以下聊天记录，提取用户需要注意的事项。\n" +
                            "要求：\n" +
                            "1. 最多3点，每点不超过30字\n" +
                            "2. 使用Markdown格式：标题用###，要点用- 开头\n" +
                            "3. 格式示例：\n" +
                            "### 紧急待办\n" +
                            "- 【项目进度】林俊杰催促上线，当前仅50%，需立即确认阻塞原因\n" +
                            "- 【时间确认】确认最终上线 deadline，倒排计划\n" +
                            "4. 禁止大段描述，只列关键动作和涉及人\n\n" +
                            "聊天记录：\n" + allContext;

                    String systemPrompt = aiProperties.getPrompts().getAutoSummary();
                    summary = aiClient.generate(systemPrompt, prompt);
                    log.info("AI返回的总结内容: {}", summary);
                }
            }

            // 5. 写入Redis缓存（统一写入）
            redisTemplate.opsForValue().set(
                    AUTO_SUMMARY_KEY_PREFIX + userId,
                    summary,
                    20, TimeUnit.MINUTES
            );
            log.info("用户{}事项总结生成完成，基于{}条消息", userId, messages.isEmpty() ? 0 : messages.size());

        } catch (Exception e) {
            log.error("用户{}总结失败", userId, e);
            // 异常时写入错误提示，避免一直等待
            redisTemplate.opsForValue().set(
                    AUTO_SUMMARY_KEY_PREFIX + userId,
                    "AI总结生成失败，请稍后重试",
                    5, TimeUnit.MINUTES
            );
        } finally {
            // 释放锁（无论成功还是失败）
            redisTemplate.delete(lockKey);
        }
    }
    /**
     * 格式化单条消息（带"你对XX说"或"XX对你说"的上下文）
     */
    private String formatMessageWithContext(ChatMessageDocument msg, Long userId, Map<String, String> convToPeerName) {
        boolean isMe = msg.getSenderId().equals(userId);
        String convId = msg.getConversationId();
        String peerName = convToPeerName.getOrDefault(convId, "对方");

        String content = extractContent(msg);
        if (content == null) return null;

        String time = msg.getServerTime().format(TIME_FORMATTER);

        if (isMe) {
            return String.format("[你对%s说]: %s - %s", peerName, content, time);
        } else {
            // 对方发的，更新映射（确保有值）
            String senderName = msg.getSenderName();
            if (senderName != null && !senderName.isEmpty()) {
                convToPeerName.put(convId, senderName);
            }
            return String.format("[%s对你说]: %s - %s", senderName, content, time);
        }
    }

    /**
     * 获取自动总结（API接口调用）
     * 如果缓存不存在，返回"AI正在总结中"（由定时任务稍后生成）
     */
    public String getAutoSummary(Long userId) {
        String cacheKey = AUTO_SUMMARY_KEY_PREFIX + userId;
        String content = redisTemplate.opsForValue().get(cacheKey);

        if (content == null) {
            // 缓存击穿：检查是否有锁（表示正在生成）
            Boolean hasLock = redisTemplate.hasKey(AUTO_SUMMARY_LOCK_PREFIX + userId);
            if (Boolean.TRUE.equals(hasLock)) {
                return "AI正在总结中...";
            }
            // 无锁且无缓存，可能是首次登录或过期，触发异步生成但不等待
            triggerAsyncGeneration(userId);
            return "AI正在总结中...";
        }

        return content;
    }

    private void triggerAsyncGeneration(Long userId) {
        // 异步触发，不阻塞API响应
        new Thread(() -> generateAutoSummary(userId)).start();
    }

    // ==================== 功能2：对话注意事项总结 ====================

    /**
     * 获取或生成对话注意事项（懒加载模式）
     * 由前端打开对话框时调用（或通过消息事件触发）
     */
    public SummaryCacheDTO getOrGenerateChatSummary(Long userId, String conversationId) {
        String cacheKey = CHAT_SUMMARY_KEY_PREFIX + userId + ":" + conversationId;
        String lockKey = CHAT_SUMMARY_LOCK_PREFIX + userId + ":" + conversationId;
        String lastMsgKey = LAST_MSG_ID_KEY_PREFIX + userId + ":" + conversationId;

        try {
            // 1. 查询该会话最新一条消息ID（用于判断是否需要更新）
            List<ChatMessageDocument> recentMessages = chatMessageRepository
                    .findByConversationIdOrderByServerTimeDesc(conversationId,
                            org.springframework.data.domain.PageRequest.of(0, 1));

            if (recentMessages.isEmpty()) {
                return createEmptyCache("暂无聊天记录");
            }

            String latestMsgId = recentMessages.get(0).getId();
            String cachedLastId = redisTemplate.opsForValue().get(lastMsgKey);

            // 2. 检查缓存
            String cachedContent = redisTemplate.opsForValue().get(cacheKey);

            // 3. 如果有缓存且基于最新消息，直接返回
            if (cachedContent != null && latestMsgId.toString().equals(cachedLastId)) {
                SummaryCacheDTO dto = new SummaryCacheDTO();
                dto.setContent(cachedContent);
                dto.setGenerateTime(LocalDateTime.now()); // 可从Redis TTL推断
                dto.setLastMessageId(latestMsgId);
                dto.setConversationId(conversationId);
                dto.setGenerating(false);
                return dto;
            }

            // 4. 无缓存或数据已更新：检查是否正在生成
            Boolean locked = redisTemplate.hasKey(lockKey);
            if (Boolean.TRUE.equals(locked)) {
                return SummaryCacheDTO.generating(); // 返回"生成中"占位
            }

            // 5. 异步生成新总结（不阻塞用户）
            asyncGenerateChatSummary(userId, conversationId, latestMsgId, lockKey, cacheKey, lastMsgKey);

            // 6. 如果有旧缓存，返回旧缓存；否则返回生成中
            if (cachedContent != null) {
                SummaryCacheDTO dto = new SummaryCacheDTO();
                dto.setContent(cachedContent + "\n\n[提示：有新消息，总结更新中...]");
                dto.setLastMessageId(cachedLastId != null ? cachedLastId : "0");
                dto.setGenerating(true);
                return dto;
            } else {
                return SummaryCacheDTO.generating();
            }

        } catch (Exception e) {
            log.error("获取对话总结失败 userId={}, convId={}", userId, conversationId, e);
            return SummaryCacheDTO.generating();
        }
    }

    @Async("aiTaskExecutor")
    public void asyncGenerateChatSummary(Long userId, String conversationId,
                                         String latestMsgId, String lockKey,
                                         String cacheKey, String lastMsgKey) {
        try {
            // 设置锁（5分钟过期，防止死锁）
            redisTemplate.opsForValue().set(lockKey, "1", 5, TimeUnit.MINUTES);

            // 查询最近2天消息
            LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
            List<ChatMessageDocument> messages = chatMessageRepository
                    .findByConversationIdAndServerTimeAfterOrderByServerTimeDesc(
                            conversationId, twoDaysAgo);

            // 空会话处理：写入占位缓存，避免前端一直等待
            if (messages.isEmpty()) {
                String emptyContent = "### 会话总结\n\n该对话暂无聊天记录，无需注意事项。";

                // 写入空总结缓存（30分钟TTL）
                redisTemplate.opsForValue().set(cacheKey, emptyContent, 30, TimeUnit.MINUTES);
                redisTemplate.opsForValue().set(lastMsgKey, latestMsgId, 30, TimeUnit.MINUTES);

                log.info("对话总结生成完成（空会话）: userId={}, convId={}", userId, conversationId);
                return;
            }

            // 正常生成逻辑...
            String content = formatMessagesForAI(messages, userId);
            String keyPoints = aiClient.generate(
                    aiProperties.getPrompts().getKeyPoints(),
                    content
            );

            // 更新缓存
            redisTemplate.opsForValue().set(cacheKey, keyPoints, 30, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(lastMsgKey, latestMsgId.toString(), 30, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("生成对话注意事项失败", e);
            // 【增强】异常时也写入错误提示，避免无限重试
            redisTemplate.opsForValue().set(cacheKey, "AI总结生成失败，请稍后重试", 5, TimeUnit.MINUTES);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    // ==================== 功能3：回复建议 ====================

    /**
     * 生成回复建议（结合功能2的缓存）
     */
    public String generateReplySuggestion(Long userId, String conversationId, String lastMessageContent) {
        // 1. 获取功能2的上下文（注意事项）
        String contextKey = CHAT_SUMMARY_KEY_PREFIX + userId + ":" + conversationId;
        String context = redisTemplate.opsForValue().get(contextKey);

        // 2. 构建提示词
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("最近的聊天注意事项：\n");
        promptBuilder.append(context != null ? context : "无历史注意事项");
        promptBuilder.append("\n\n当前需要回复的消息：\n");
        promptBuilder.append(lastMessageContent);
        promptBuilder.append("\n\n请根据以上上下文，生成一条合适的回复建议：");

        // 3. 调用AI（不缓存，每次实时生成，因为依赖最新消息）
        return aiClient.generate(aiProperties.getPrompts().getReplySuggestion(), promptBuilder.toString());
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化消息为AI输入（替换原有方法）
     * @param messages 消息列表
     * @param userId 当前用户ID（用于区分"我"和"对方"）
     */
    private String formatMessagesForAI(List<ChatMessageDocument> messages, Long userId) {
        return MessageFormatter.formatForAI(messages, userId);
    }

    private SummaryCacheDTO createEmptyCache(String message) {
        SummaryCacheDTO dto = new SummaryCacheDTO();
        dto.setContent(message);
        dto.setGenerating(false);
        return dto;
    }

    /**
     * 从消息中提取内容文本（处理各种消息类型）
     */
    private String extractContent(ChatMessageDocument msg) {
        String type = msg.getType();

        // 文本消息
        if ("TEXT".equals(type)) {
            return msg.getContent() != null ? msg.getContent().trim() : "";
        }

        // 图片
        if ("IMAGE".equals(type)) {
            Map<String, Object> extra = msg.getExtra();
            if (extra != null && extra.containsKey("caption")) {
                return "[图片] " + extra.get("caption");
            }
            if (msg.getFileName() != null) {
                return "[图片:" + msg.getFileName() + "]";
            }
            return "[图片]";
        }

        // 文件
        if ("FILE".equals(type)) {
            return msg.getFileName() != null ? "[文件:" + msg.getFileName() + "]" : "[文件]";
        }

        // 语音
        if ("VOICE".equals(type)) {
            Integer duration = msg.getDuration();
            return duration != null && duration > 0 ? String.format("[语音 %d秒]", duration) : "[语音]";
        }

        // 视频
        if ("VIDEO".equals(type)) {
            return msg.getFileName() != null ? "[视频:" + msg.getFileName() + "]" : "[视频]";
        }

        // 系统消息
        if ("SYSTEM".equals(type) || "NOTICE".equals(type)) {
            if (msg.getContent() != null && msg.getContent().contains("好友")) {
                return "[系统]" + msg.getContent();
            }
            return null; // 忽略普通系统消息
        }

        // 其他
        if ("LOCATION".equals(type)) return "[位置分享]";
        if ("CONTACT".equals(type)) return "[名片]";

        return msg.getContent() != null ? msg.getContent() : "[未知消息]";
    }
}