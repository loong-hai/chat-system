// model/document/ChatMessageDocument.java - 保持完整
package com.hailong.chatsystem.model.document;

import com.hailong.chatsystem.model.dto.ChatMessageDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MongoDB聊天消息文档 - 完整版
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
@CompoundIndexes({
        @CompoundIndex(name = "idx_conversation_time", def = "{'conversationId': 1, 'serverTime': -1}"),
        @CompoundIndex(name = "idx_sender_receiver_time", def = "{'senderId': 1, 'receiverId': 1, 'serverTime': -1}"),
        @CompoundIndex(name = "idx_status_time", def = "{'status': 1, 'serverTime': 1}")
})
public class ChatMessageDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("msg_id")
    private String messageId;

    @Field("type")
    private String type;

    @Field("content")
    private String content;

    @Field("thumbnail")
    private String thumbnail;

    @Field("file_name")
    private String fileName;

    @Field("file_size")
    private Long fileSize;

    @Field("duration")
    private Integer duration;

    @Field("file_ext")
    private String fileExt;

    @Field("extra")
    private Map<String, Object> extra;

    @Indexed
    @Field("sender_id")
    private Long senderId;

    @Field("sender_name")
    private String senderName;

    @Field("sender_avatar")
    private String senderAvatar;

    @Indexed
    @Field("receiver_id")
    private Long receiverId;

    @Field("receiver_name")
    private String receiverName;

    @Field("receiver_type")
    private String receiverType;

    @Field("status")
    private String status;

    @Field("is_deleted")
    private Boolean isDeleted = false;

    @Field("is_recalled")
    private Boolean isRecalled = false;

    @Field("recall_reason")
    private String recallReason;

    @Indexed
    @Field("client_time")
    private LocalDateTime clientTime;

    @Indexed
    @Field("server_time")
    private LocalDateTime serverTime;

    @Field("delivered_at")
    private LocalDateTime deliveredAt;

    @Field("read_at")
    private LocalDateTime readAt;

    @Indexed
    @Field("conversation_id")
    private String conversationId;

    @Field("sequence")
    private Long sequence;

    @Field("client_id")
    private String clientId;

    @Field("device_type")
    private String deviceType;

    /**
     * 从DTO转换为Document - 完整转换
     */
    public static ChatMessageDocument fromDTO(ChatMessageDTO dto) {
        ChatMessageDocument doc = new ChatMessageDocument();
        doc.setMessageId(dto.getMessageId());
        doc.setType(dto.getType().name());
        doc.setContent(dto.getContent());
        doc.setThumbnail(dto.getThumbnail());
        doc.setFileName(dto.getFileName());
        doc.setFileSize(dto.getFileSize());
        doc.setDuration(dto.getDuration());
        doc.setFileExt(dto.getFileExt());
        doc.setExtra(dto.getExtra());

        doc.setSenderId(dto.getSenderId());
        doc.setSenderName(dto.getSenderName());
        doc.setSenderAvatar(dto.getSenderAvatar());
        doc.setReceiverId(dto.getReceiverId());
        doc.setReceiverName(dto.getReceiverName());
        doc.setReceiverType(dto.getReceiverType().name());

        doc.setStatus(dto.getStatus() != null ? dto.getStatus().name() : "SENDING");
        doc.setIsDeleted(dto.getIsDeleted());
        doc.setIsRecalled(dto.getIsRecalled());
        doc.setRecallReason(dto.getRecallReason());

        doc.setClientTime(dto.getClientTime());
        doc.setServerTime(dto.getServerTime() != null ? dto.getServerTime() : LocalDateTime.now());
        doc.setDeliveredAt(dto.getDeliveredAt());
        doc.setReadAt(dto.getReadAt());

        doc.setConversationId(dto.getConversationId() != null ?
                dto.getConversationId() : dto.generateConversationId());
        doc.setSequence(dto.getSequence());

        doc.setClientId(dto.getClientId());
        doc.setDeviceType(dto.getDeviceType());

        doc.setIsDeleted(false);
        return doc;
    }

    /**
     * 转换为DTO - 完整转换
     */
    public ChatMessageDTO toDTO() {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(this.id);
        dto.setMessageId(this.messageId);

        if (this.type != null) {
            try {
                dto.setType(ChatMessageDTO.MessageType.valueOf(this.type));
            } catch (IllegalArgumentException e) {
                dto.setType(ChatMessageDTO.MessageType.TEXT);
            }
        }

        dto.setContent(this.content);
        dto.setThumbnail(this.thumbnail);
        dto.setFileName(this.fileName);
        dto.setFileSize(this.fileSize);
        dto.setDuration(this.duration);
        dto.setFileExt(this.fileExt);
        dto.setExtra(this.extra);

        dto.setSenderId(this.senderId);
        dto.setSenderName(this.senderName);
        dto.setSenderAvatar(this.senderAvatar);
        dto.setReceiverId(this.receiverId);
        dto.setReceiverName(this.receiverName);

        if (this.receiverType != null) {
            try {
                dto.setReceiverType(ChatMessageDTO.ReceiverType.valueOf(this.receiverType));
            } catch (IllegalArgumentException e) {
                dto.setReceiverType(ChatMessageDTO.ReceiverType.USER);
            }
        }

        if (this.status != null) {
            try {
                dto.setStatus(ChatMessageDTO.MessageStatus.valueOf(this.status));
            } catch (IllegalArgumentException e) {
                dto.setStatus(ChatMessageDTO.MessageStatus.SENDING);
            }
        }

        dto.setIsDeleted(this.isDeleted);
        dto.setIsRecalled(this.isRecalled);
        dto.setRecallReason(this.recallReason);

        dto.setClientTime(this.clientTime);
        dto.setServerTime(this.serverTime);
        dto.setDeliveredAt(this.deliveredAt);
        dto.setReadAt(this.readAt);

        dto.setConversationId(this.conversationId);
        dto.setSequence(this.sequence);

        dto.setClientId(this.clientId);
        dto.setDeviceType(this.deviceType);

        return dto;
    }
}