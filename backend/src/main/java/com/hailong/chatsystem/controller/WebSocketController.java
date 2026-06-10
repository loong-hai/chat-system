// controller/WebSocketController.java - 简洁版
package com.hailong.chatsystem.controller;

import com.hailong.chatsystem.config.RabbitMQConfig;
import com.hailong.chatsystem.model.document.ChatMessageDocument;
import com.hailong.chatsystem.model.dto.ChatMessageDTO;
import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.common.ResponseMessage;
import com.hailong.chatsystem.repository.UserRepository;
import com.hailong.chatsystem.service.cache.UserStatusService;
import com.hailong.chatsystem.service.message.MessageDistributionService;
import com.hailong.chatsystem.service.message.MessagePersistenceService;
import com.hailong.chatsystem.service.websocket.WebSocketMessageService;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import com.hailong.chatsystem.service.cache.UserSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@Tag(name = "WebSocket消息", description = "实时消息推送相关接口")
public class WebSocketController {


    @Autowired
    private MessageDistributionService messageDistributionService;

    @Autowired
    private WebSocketSessionService webSocketSessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessagePersistenceService messagePersistenceService;

    @Autowired
    private UserStatusService userStatusService;

    @Autowired
    private WebSocketMessageService webSocketMessageService;

    /**
     * 批量标记会话已读（HTTP接口）
     * @param conversationId 会话ID
     * @param principal 当前用户
     * @return 统一响应
     */
    @PostMapping("/messages/read/conversation")
    @Operation(summary = "标记会话已读", description = "将当前用户在指定会话中的所有未读消息标记为已读")
    public ResponseMessage<Void> markConversationRead(@RequestParam String conversationId,
                                                      Principal principal) {
        User user = getCurrentUser(principal);
        if (user == null) {
            return ResponseMessage.unauthorized("未登录");
        }
        messagePersistenceService.markConversationAsRead(conversationId, user.getUserId());
        return ResponseMessage.success();
    }

    /**
     * 发送聊天消息 - 直接使用User对象
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessageDTO message, Principal principal) {
        try {
            // 获取发送者
            User sender = getCurrentUser(principal);
            // 添加空值检查
            if (sender == null) {
                log.error("发送消息失败: 无法获取发送者信息");
                return;
            }

            // 设置发送者信息
            message.setSenderId(sender.getUserId());
            message.setSenderName(sender.getNickname() != null ? sender.getNickname() : sender.getUsername());
            message.setSenderAvatar(sender.getAvatarUrl());
            message.setClientTime(LocalDateTime.now());
            message.setStatus(ChatMessageDTO.MessageStatus.SENDING);

            // 使用新的分发服务
            messageDistributionService.distributeChatMessage(message);

            log.info("消息已提交到分发服务: messageId={}, senderId={}",
                    message.getMessageId(), sender.getUserId());

        } catch (Exception e) {
            log.error("发送消息失败", e);
            User sender = getCurrentUser(principal);
            if (sender != null) {
                webSocketMessageService.sendSystemNotification(sender.getUserId(),
                        "消息发送失败: " + e.getMessage());
            }
        }
    }

    private User getCurrentUser(Principal principal) {
        if (principal == null) {
            log.error("获取当前用户失败: principal为null");
            return null;
        }

        try {
            if (principal instanceof Authentication) {
                Authentication auth = (Authentication) principal;
                Object principalObj = auth.getPrincipal();

                // 1. 优先：principal 本身就是 User 对象（HTTP 场景）
                if (principalObj instanceof User) {
                    return (User) principalObj;
                }

                // 2. 其次：从 details 中获取 userId（WebSocket 场景，由 WebSocketAuthInterceptor 存入）
                Object details = auth.getDetails();
                if (details instanceof Map) {
                    Map<?, ?> detailsMap = (Map<?, ?>) details;
                    Object userIdObj = detailsMap.get("userId");
                    if (userIdObj instanceof Long) {
                        return userRepository.findById((Long) userIdObj).orElse(null);
                    } else if (userIdObj instanceof String) {
                        try {
                            Long userId = Long.parseLong((String) userIdObj);
                            return userRepository.findById(userId).orElse(null);
                        } catch (NumberFormatException e) {
                            log.error("无效的userId格式: {}", userIdObj);
                        }
                    }
                }

                // 3. 再尝试：将 auth.getName() 解析为 Long（WebSocket 场景的备用，此时 name 是 userId 字符串）
                String userIdStr = auth.getName();
                if (userIdStr != null && !userIdStr.isEmpty()) {
                    try {
                        Long userId = Long.parseLong(userIdStr);
                        return userRepository.findById(userId).orElse(null);
                    } catch (NumberFormatException e) {
                        log.error("无效的userId格式: {}", userIdStr);
                    }
                }

                // 4. 最后兜底：通过用户名查询（可能是 HTTP 场景但 principalObj 不是 User？理论上不会，但保留）
                String username = auth.getName();
                if (username != null && !username.isEmpty()) {
                    return userRepository.findByUsername(username).orElse(null);
                }
            }
        } catch (Exception e) {
            log.error("获取当前用户异常", e);
        }
        return null;
    }

    /**
     * 心跳检测
     */
    @MessageMapping("/heartbeat")
    public void wsHeartbeat(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        User user = getCurrentUser(principal);
        if (user == null) return;

        String sessionId = headerAccessor.getSessionId();
        if (sessionId == null) return;

        // 更新 WebSocket 会话心跳
        boolean wsHeartbeatSuccess = webSocketSessionService.updateHeartbeat(sessionId);

        // 同时续期业务状态的 TTL（避免 5 分钟兜底时间到期）
        if (wsHeartbeatSuccess) {
            userStatusService.renewStatusTtl(user.getUserId());
            sendWsHeartbeatAck(user.getUserId(), sessionId);
        }
    }

    // 新增：发送心跳确认消息（可选）
    private void sendWsHeartbeatAck(Long userId, String sessionId) {
        try {
            // 如果需要，可以发送确认消息给客户端
            Map<String, Object> ack = new HashMap<>();
            ack.put("type", "heartbeat_ack");
            ack.put("timestamp", System.currentTimeMillis());
            ack.put("sessionId", sessionId);

            // 通过WebSocketMessageService发送
            webSocketMessageService.sendToUser(userId, "/queue/heartbeat", ack);
        } catch (Exception e) {
            log.debug("发送心跳确认失败", e);
        }
    }

    /**
     * 用户订阅自己的消息队列
     */
    @SubscribeMapping("/user/queue/chat")
    public void subscribeUserQueue(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String userId = principal.getName(); // 现在应该是 "30"
        log.info("用户订阅消息队列: userId={}, sessionId={}",
                userId, headerAccessor.getSessionId());
        // 发送欢迎消息测试
        sendWelcomeMessage(Long.parseLong(userId));
    }

    /**
     * 发送欢迎消息
     */
    private void sendWelcomeMessage(Long userId) {
        try {
            ChatMessageDTO welcomeMessage = new ChatMessageDTO();
            welcomeMessage.setType(ChatMessageDTO.MessageType.SYSTEM);
            welcomeMessage.setContent("欢迎使用聊天系统！您已成功连接到服务器。");
            welcomeMessage.setSenderId(0L); // 系统消息
            welcomeMessage.setReceiverId(userId);
            welcomeMessage.setReceiverType(ChatMessageDTO.ReceiverType.USER);
            welcomeMessage.setMessageId(UUID.randomUUID().toString());
            welcomeMessage.setServerTime(LocalDateTime.now());

            webSocketMessageService.sendChatMessage(welcomeMessage);
            log.info("欢迎消息已发送: userId={}", userId);
        } catch (Exception e) {
            log.error("发送欢迎消息失败", e);
        }
    }

    /**
     * 发送错误消息给用户
     */
    private void sendErrorMessageToUser(Long userId, String errorMessage) {
        try {
            ChatMessageDTO errorMsg = new ChatMessageDTO();
            errorMsg.setType(ChatMessageDTO.MessageType.SYSTEM);
            errorMsg.setContent(errorMessage);
            errorMsg.setSenderId(0L); // 系统消息
            errorMsg.setReceiverId(userId);
            errorMsg.setReceiverType(ChatMessageDTO.ReceiverType.USER);
            errorMsg.setMessageId(UUID.randomUUID().toString());
            errorMsg.setServerTime(LocalDateTime.now());

            webSocketMessageService.sendChatMessage(errorMsg);
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 获取用户未读消息数量（HTTP接口）- 完整实现
     */
    @GetMapping("/messages/unread/count")
    @Operation(summary = "获取未读消息数量", description = "获取当前用户的未读消息数量")
    public ResponseMessage<Map<String, Object>> getUnreadCount(Principal principal) {
        try {
            User user = getCurrentUser(principal);
            if (user == null) {
                return ResponseMessage.error("用户未登录");
            }

            // 从MongoDB查询未读消息数量
            List<ChatMessageDocument> unreadMessages =
                    messagePersistenceService.findUnreadMessages(user.getUserId());

            // 按会话分组统计
            Map<String, Long> countByConversation = unreadMessages.stream()
                    .collect(Collectors.groupingBy(
                            ChatMessageDocument::getConversationId,
                            Collectors.counting()
                    ));

            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", unreadMessages.size());
            result.put("conversationCount", countByConversation.size());
            result.put("details", countByConversation);
            result.put("lastUpdate", LocalDateTime.now());
            result.put("userId", user.getUserId());

            return ResponseMessage.success(result);
        } catch (Exception e) {
            log.error("获取未读消息数量失败", e);
            return ResponseMessage.error("获取未读消息数量失败: " + e.getMessage());
        }
    }
}