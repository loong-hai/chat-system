package com.hailong.chatsystem.interceptor;

import com.hailong.chatsystem.security.jwt.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;


    // 匹配两种格式：
    // 1. /user/queue/chat (客户端发送，无userId，Spring自动转换)
    // 2. /user/123/queue/chat (服务器内部使用，包含userId)
    private static final Pattern USER_DESTINATION_PATTERN = Pattern.compile("/user/(?:(\\d+)/)?queue/(.+)");
    private static final String USER_DESTINATION_PREFIX = "/user/";


    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        // 1. 恢复 Principal（关键修复：确保所有命令都能获取到用户身份）
        restorePrincipalIfMissing(accessor);

        // 2. 处理不同命令类型的认证逻辑
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(accessor, message);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return handleSubscribe(accessor, message);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            return handleSend(accessor, message);
        }

        return message;
    }

    /**
     * 核心修复：从 SessionAttributes 恢复 Principal
     * 解决 WebSocket 握手后 Principal 丢失的问题
     */
    private void restorePrincipalIfMissing(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) {
            return; // 已有 Principal，无需恢复
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.debug("SessionAttributes 为空，无法恢复 Principal");
            return;
        }

        Object userIdObj = sessionAttributes.get("userId");
        Object usernameObj = sessionAttributes.get("username");

        if (userIdObj == null) {
            log.debug("SessionAttributes 中缺少 userId，无法恢复 Principal");
            return;
        }

        try {
            Long userId = parseUserId(userIdObj);
            String username = usernameObj != null ? usernameObj.toString() : userId.toString();

            // 创建 Authentication Token
            // Principal name 设为 userId 字符串，确保与 @MessageMapping 中的 Principal 一致
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(),  // Principal name
                            null,               // 凭证（WebSocket 场景无需密码）
                            Collections.emptyList()
                    );

            // 存储额外信息到 details，供后续使用
            Map<String, Object> details = new HashMap<>();
            details.put("userId", userId);
            details.put("username", username);
            details.put("source", "websocket_session");
            authentication.setDetails(details);

            // 设置到 accessor（这是关键步骤）
            accessor.setUser(authentication);

            // 同时设置到 SecurityContext（可选，但推荐保持线程安全）
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("成功从 SessionAttributes 恢复 Principal: userId={}, username={}", userId, username);

        } catch (Exception e) {
            log.error("恢复 Principal 失败: userIdObj={}, error={}", userIdObj, e.getMessage());
        }
    }

    /**
     * 处理 CONNECT 命令（初始连接）
     */
    private Message<?> handleConnect(StompHeaderAccessor accessor, Message<?> message) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            log.error("WebSocket CONNECT 失败: 无法恢复 Principal，拒绝连接");
            return null; // 拒绝连接
        }

        log.info("WebSocket CONNECT 成功: userId={}", principal.getName());
        return message;
    }

    /**
     * 处理 SUBSCRIBE 命令
     */
    private Message<?> handleSubscribe(StompHeaderAccessor accessor, Message<?> message) {
        Principal principal = accessor.getUser();
        String destination = accessor.getDestination();

        if (principal == null || !StringUtils.hasText(destination)) {
            log.warn("拒绝订阅: principal={}, destination={}", principal, destination);
            return null;
        }

        Long userId = getUserIdFromPrincipal(principal);
        if (userId == null) {
            log.warn("拒绝订阅: 无法解析userId, principal={}", principal.getName());
            return null;
        }

        if (!hasSubscribePermission(userId, destination, principal)) {
            log.warn("拒绝订阅: 权限不足, userId={}, destination={}", userId, destination);
            return null;
        }

        log.debug("允许订阅: userId={}, destination={}", userId, destination);
        return message;
    }

    /**
     * 处理 SEND 命令（发送消息）
     */
    private Message<?> handleSend(StompHeaderAccessor accessor, Message<?> message) {
        Principal principal = accessor.getUser();
        String destination = accessor.getDestination();

        if (principal == null) {
            log.warn("拒绝发送消息: Principal 为空, destination={}", destination);
            return null;
        }

        // 可以在这里添加发送频率限制等逻辑

        return message;
    }

    /**
     * 检查订阅权限 - 关键修复
     */
    private boolean hasSubscribePermission(Long userId, String destination, Principal principal) {
        // 1. 用户特定队列 /user/...
        if (destination.startsWith(USER_DESTINATION_PREFIX)) {
            return hasUserDestinationPermission(userId, destination);
        }

        // 2. 公共主题
        if (destination.startsWith("/topic/public/")) {
            return true;
        }

        // 3. 群组主题
        if (destination.startsWith("/topic/group/")) {
            // TODO: 检查群组成员权限
            return true;
        }

        log.warn("未知的订阅目的地: {}", destination);
        return false;
    }


    /**
     * 关键修复：处理用户目的地权限
     * 支持两种格式：
     * - /user/queue/chat (客户端标准订阅，Spring自动映射)
     * - /user/123/queue/chat (显式指定userId)
     */
    private boolean hasUserDestinationPermission(Long currentUserId, String destination) {
        Matcher matcher = USER_DESTINATION_PATTERN.matcher(destination);

        if (!matcher.matches()) {
            log.warn("用户目的地格式错误: {}", destination);
            return false;
        }

        String targetUserIdStr = matcher.group(1); // 可能为null
        String queueName = matcher.group(2);

        // 情况1: /user/queue/chat (无userId，Spring自动处理)
        // 这种情况下允许订阅，因为Spring只会将消息发送给当前会话用户
        if (!StringUtils.hasText(targetUserIdStr)) {
            log.debug("用户 {} 订阅个人队列: /user/queue/{}", currentUserId, queueName);
            return true;
        }

        // 情况2: /user/123/queue/chat (显式指定userId)
        // 必须检查是否订阅的是自己的队列
        try {
            Long targetUserId = Long.parseLong(targetUserIdStr);
            if (!targetUserId.equals(currentUserId)) {
                log.warn("用户 {} 尝试订阅他人队列: {}", currentUserId, destination);
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            log.error("解析目标用户ID失败: {}", targetUserIdStr);
            return false;
        }
    }

    /**
     * 从 Principal 安全获取 userId
     */
    private Long getUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }

        try {
            // 优先从 Principal name 解析（我们设置的是 userId 字符串）
            String name = principal.getName();
            if (StringUtils.hasText(name) && !"anonymousUser".equals(name)) {
                return Long.parseLong(name);
            }
        } catch (NumberFormatException e) {
            log.debug("Principal name 不是数字格式: {}", principal.getName());
        }

        // 回退到 details
        if (principal instanceof Authentication) {
            Object details = ((Authentication) principal).getDetails();
            if (details instanceof Map) {
                Object userId = ((Map<?, ?>) details).get("userId");
                return parseUserId(userId);
            }
        }

        return null;
    }

    /**
     * 安全解析 userId（支持 Long 和 String 类型）
     */
    private Long parseUserId(Object userIdObj) {
        if (userIdObj == null) {
            return null;
        }
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        }
        if (userIdObj instanceof String) {
            return Long.parseLong((String) userIdObj);
        }
        throw new IllegalArgumentException("不支持的 userId 类型: " + userIdObj.getClass());
    }

    /**
     * 提取 Token（备用方法，用于无 SessionAttributes 的场景）
     */
    private String extractToken(StompHeaderAccessor accessor) {
        // 从 Authorization header 获取
        String token = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            return token.substring(7);
        }

        // 从查询参数获取（ handshake 时可能传递）
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object tokenObj = sessionAttributes.get("token");
            if (tokenObj instanceof String) {
                return (String) tokenObj;
            }
        }

        return null;
    }
}