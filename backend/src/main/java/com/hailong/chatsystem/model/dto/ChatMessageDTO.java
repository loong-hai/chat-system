package com.hailong.chatsystem.model.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 聊天消息传输对象 - 支持多媒体消息
 * 设计原则：
 * 1. 前端发送时填充基础字段
 * 2. 服务端处理时补充状态和时间字段
 * 3. 支持文本、图片、音频、视频、文件等多种类型
 */
@Data
public class ChatMessageDTO {
    // ====== 核心标识 ======
    private String id;                 // MongoDB ObjectId（服务端生成）
    private String messageId;          // 业务ID（前端生成UUID，用于去重）

    // ====== 消息内容 ======
    private MessageType type;          // 消息类型
    private String content;            // 消息内容（文本内容或文件URL）
    private String thumbnail;          // 缩略图URL（用于图片/视频预览）
    private String fileName;           // 文件名（文件消息）
    private Long fileSize;             // 文件大小（字节）
    private String fileExt;            // 文件扩展名
    private Integer duration;          // 音频/视频时长（秒）
    private Map<String, Object> extra; // 扩展信息（JSON格式）

    // ====== 发送与接收 ======
    private Long senderId;             // 发送者ID
    private String senderName;         // 发送者名称（冗余存储）
    private String senderAvatar;       // 发送者头像
    private Long receiverId;           // 接收者ID（用户ID或群ID）
    private String receiverName;       // 接收者名称
    private ReceiverType receiverType; // 接收类型：USER, GROUP

    // ====== 消息状态 ======
    private MessageStatus status;      // 消息状态
    private Boolean isDeleted;         // 是否已删除（软删除）
    private Boolean isRecalled;        // 是否已撤回
    private String recallReason;       // 撤回原因

    // ====== 时间轴 ======
    private LocalDateTime clientTime;  // 客户端发送时间
    private LocalDateTime serverTime;  // 服务器接收时间
    private LocalDateTime deliveredAt; // 送达时间
    private LocalDateTime readAt;      // 已读时间

    // ====== 会话管理 ======
    private String conversationId;     // 会话ID
    private Long sequence;             // 会话内消息序号

    // ====== 客户端信息 ======
    private String clientId;           // 客户端ID（用于多端同步）
    private String deviceType;         // 设备类型：WEB, IOS, ANDROID

    // ====== 枚举定义 ======
    public enum MessageType {
        TEXT,          // 文本消息
        IMAGE,         // 图片消息
        VOICE,         // 语音消息
        VIDEO,         // 视频消息
        FILE,          // 文件消息
        LOCATION,      // 位置消息
        CONTACT,       // 联系人名片
        RED_ENVELOPE,  // 红包
        SYSTEM,        // 系统消息
        NOTICE,        // 通知
        TIP            // 提示消息
    }

    public enum ReceiverType {
        USER,          // 私聊
        GROUP          // 群聊
    }

    public enum MessageStatus {
        SENDING,       // 发送中（前端状态）
        SENT,          // 已发送（到达服务器）
        DELIVERED,     // 已送达（对方在线且已推送到设备）
        READ,          // 已读
        FAILED,        // 发送失败
        RECALLED       // 已撤回
    }

    // ====== 辅助方法 ======

    /**
     * 生成会话ID
     * 规则：私聊 = user_{minId}_{maxId}，群聊 = group_{groupId}
     */
    public String generateConversationId() {
        if (receiverType == ReceiverType.USER) {
            long min = Math.min(senderId, receiverId);
            long max = Math.max(senderId, receiverId);
            return String.format("user_%d_%d", min, max);
        } else {
            return String.format("group_%d", receiverId);
        }
    }

    /**
     * 验证消息基本格式
     */
    public boolean isValid() {
        if (senderId == null || receiverId == null || receiverType == null || type == null) {
            return false;
        }

        // 文本消息必须有内容
        if (type == MessageType.TEXT && (content == null || content.trim().isEmpty())) {
            return false;
        }

        // 文件消息必须有文件信息
        if ((type == MessageType.IMAGE || type == MessageType.VOICE ||
                type == MessageType.VIDEO || type == MessageType.FILE) &&
                (content == null || content.trim().isEmpty())) {
            return false;
        }

        return true;
    }

    /**
     * 创建系统消息
     */
    public static ChatMessageDTO createSystemMessage(Long senderId, Long receiverId,
                                                     String content, Map<String, Object> extra) {
        ChatMessageDTO message = new ChatMessageDTO();
        message.setType(MessageType.SYSTEM);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setReceiverType(ReceiverType.USER);
        message.setContent(content);
        message.setExtra(extra);
        message.setStatus(MessageStatus.SENT);
        message.setServerTime(LocalDateTime.now());
        return message;
    }

    /**
     * 转换为Map用于Redis存储
     */
    public Map<String, String> toRedisMap() {
        Map<String, String> map = new HashMap<>();
        map.put("id", id != null ? id : "");
        map.put("messageId", messageId);
        map.put("type", type.name());
        map.put("content", content != null ? content : "");
        map.put("senderId", senderId.toString());
        map.put("receiverId", receiverId.toString());
        map.put("receiverType", receiverType.name());
        map.put("status", status.name());
        map.put("serverTime", serverTime != null ? serverTime.toString() : LocalDateTime.now().toString());
        map.put("conversationId", conversationId != null ? conversationId : generateConversationId());
        map.put("sequence", sequence != null ? sequence.toString() : "0");
        return map;
    }

    /**
     * 从Redis Map恢复对象
     */
    public static ChatMessageDTO fromRedisMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;

        ChatMessageDTO message = new ChatMessageDTO();
        message.setId(map.getOrDefault("id", null));
        message.setMessageId(map.get("messageId"));
        message.setType(MessageType.valueOf(map.get("type")));
        message.setContent(map.get("content"));
        message.setSenderId(Long.parseLong(map.get("senderId")));
        message.setReceiverId(Long.parseLong(map.get("receiverId")));
        message.setReceiverType(ReceiverType.valueOf(map.get("receiverType")));
        message.setStatus(MessageStatus.valueOf(map.get("status")));
        message.setServerTime(LocalDateTime.parse(map.get("serverTime")));
        message.setConversationId(map.get("conversationId"));
        message.setSequence(Long.parseLong(map.getOrDefault("sequence", "0")));

        return message;
    }
}