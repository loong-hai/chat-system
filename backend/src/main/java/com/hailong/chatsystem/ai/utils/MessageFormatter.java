package com.hailong.chatsystem.ai.utils;

import com.hailong.chatsystem.model.document.ChatMessageDocument;
import com.hailong.chatsystem.model.dto.ChatMessageDTO;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI消息格式化工具
 * 将MongoDB文档转换为简洁的AI可读格式
 */
public class MessageFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * 格式化消息列表为AI输入文本
     *
     * @param messages 消息文档列表
     * @param currentUserId 当前用户ID（用于区分"我"和"对象"）
     * @return 格式化后的字符串
     */
    public static String formatForAI(List<ChatMessageDocument> messages, Long currentUserId) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        // 按时间正序排列（最早的在前）
        List<ChatMessageDocument> sorted = messages.stream()
                .sorted((m1, m2) -> m1.getServerTime().compareTo(m2.getServerTime()))
                .limit(50) // 限制50条避免过长
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();

        for (ChatMessageDocument msg : sorted) {
            String line = formatSingleMessage(msg, currentUserId);
            if (line != null) { // 忽略返回null的（如被过滤的系统消息）
                sb.append(line).append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * 单条消息格式化
     * 格式：[我]:内容 - MM-dd HH:mm 或 [对象]:内容 - MM-dd HH:mm
     */
    private static String formatSingleMessage(ChatMessageDocument msg, Long currentUserId) {
        // 确定发送者标识
        String senderLabel = msg.getSenderId().equals(currentUserId) ? "我" : "对象";

        // 获取时间字符串
        String timeStr = msg.getServerTime().format(TIME_FORMATTER);

        // 处理不同类型的消息
        String content = extractContent(msg);

        // 如果内容为空（如被忽略的非文本消息），返回null
        if (content == null || content.isEmpty()) {
            return null;
        }

        return String.format("[%s]:%s - %s", senderLabel, content, timeStr);
    }

    /**
     * 根据消息类型提取内容
     */
    private static String extractContent(ChatMessageDocument msg) {
        String type = msg.getType();

        // 文本消息：直接返回内容
        if ("TEXT".equals(type)) {
            return msg.getContent() != null ? msg.getContent().trim() : "";
        }

        // 图片：结合extra中的描述或返回占位符
        if ("IMAGE".equals(type)) {
            // 尝试从extra获取图片说明
            Map<String, Object> extra = msg.getExtra();
            if (extra != null && extra.containsKey("caption")) {
                return "[图片] " + extra.get("caption");
            }
            // 如果有文件名，显示文件名
            if (msg.getFileName() != null) {
                return "[图片:" + msg.getFileName() + "]";
            }
            return "[图片]";
        }

        // 文件：显示文件名
        if ("FILE".equals(type)) {
            String fileName = msg.getFileName();
            if (fileName != null) {
                return "[文件:" + fileName + "]";
            }
            return "[文件]";
        }

        // 语音：显示时长
        if ("VOICE".equals(type)) {
            Integer duration = msg.getDuration();
            if (duration != null && duration > 0) {
                return String.format("[语音 %d秒]", duration);
            }
            return "[语音]";
        }

        // 视频：显示文件名或占位符
        if ("VIDEO".equals(type)) {
            if (msg.getFileName() != null) {
                return "[视频:" + msg.getFileName() + "]";
            }
            return "[视频]";
        }

        // 系统消息/通知：可选返回null忽略，或简化显示
        if ("SYSTEM".equals(type) || "NOTICE".equals(type)) {
            // 如果是重要的系统通知（如好友申请通过），保留简短信息
            if (msg.getContent() != null && msg.getContent().contains("好友")) {
                return "[系统]" + msg.getContent();
            }
            return null; // 忽略普通系统消息
        }

        // 其他类型（位置、名片等）
        if ("LOCATION".equals(type)) {
            return "[位置分享]";
        }

        if ("CONTACT".equals(type)) {
            return "[名片]";
        }

        // 未知类型：返回内容或占位符
        return msg.getContent() != null ? msg.getContent() : "[未知消息]";
    }
}