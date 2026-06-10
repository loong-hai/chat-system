// service/websocket/WebSocketMessageService.java
package com.hailong.chatsystem.service.websocket;

import com.hailong.chatsystem.model.dto.ChatMessageDTO;
import com.hailong.chatsystem.service.message.MessagePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebSocketMessageService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    @Autowired
    private MessagePersistenceService messagePersistenceService;

    /**
     * 发送消息给指定用户
     */
    public boolean sendToUser(Long userId, String destination, Object payload) {
        String messageId = (payload instanceof ChatMessageDTO) ?
                ((ChatMessageDTO) payload).getMessageId() : null;

        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    destination,
                    payload
            );

            log.debug("消息已提交给Spring处理: userId={}, destination=/user/{}{}",
                    userId, userId, destination);
            return true;

        } catch (Exception e) {
            log.error("消息发送失败: userId={}, error={}", userId, e.getMessage());
            if (messageId != null) markMessageOffline(messageId, "发送失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 标记消息为离线状态
     */
    private void markMessageOffline(String messageId, String reason) {
        try {
            if (messagePersistenceService != null && messageId != null) {
                messagePersistenceService.updateMessageStatus(
                        messageId,
                        "OFFLINE",
                        null
                );
                log.info("[消息发送] 消息已转离线存储: messageId={}, reason={}",
                        messageId, reason);
            }
        } catch (Exception e) {
            log.error("[消息发送] 标记离线状态失败: messageId={}, error={}",
                    messageId, e.getMessage());
        }
    }
    /**
     * 发送聊天消息
     */
    public boolean sendChatMessage(ChatMessageDTO message) {
        if (message == null || message.getReceiverId() == null) {
            log.warn("无效的消息参数");
            return false;
        }

        // 构建消息目的地
        String destination = "/queue/chat";

        // 发送消息
        boolean sent = sendToUser(message.getReceiverId(), destination, message);

        if (sent) {
            log.debug("WebSocket消息推送成功: messageId={}, receiverId={}",
                    message.getMessageId(), message.getReceiverId());
        } else {
            log.debug("WebSocket消息推送失败: 用户可能离线, messageId={}, receiverId={}",
                    message.getMessageId(), message.getReceiverId());
        }
        return sent;
    }

    /**
     * 广播消息给所有在线用户
     */
    public void broadcast(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }

    /**
     * 发送系统通知
     */
    public boolean sendSystemNotification(Long userId, String content) {
        ChatMessageDTO notification = new ChatMessageDTO();
        notification.setType(ChatMessageDTO.MessageType.SYSTEM);
        notification.setContent(content);
        notification.setSenderId(0L); // 系统发送者
        notification.setReceiverId(userId);
        notification.setReceiverType(ChatMessageDTO.ReceiverType.USER);
        notification.setMessageId(java.util.UUID.randomUUID().toString());

        return sendChatMessage(notification);
    }
}