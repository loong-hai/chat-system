// listener/WebSocketEventListener.java - 简洁版
package com.hailong.chatsystem.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.repository.UserRepository;
import com.hailong.chatsystem.service.cache.UserStatusService;
import com.hailong.chatsystem.service.message.MessageDistributionService;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WebSocketEventListener {

    @Autowired
    private WebSocketSessionService webSocketSessionService;

    @Autowired
    private UserStatusService userStatusService;

    // 延迟任务调度器（用于10秒后清理状态）
//    private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(2);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 注入消息分发服务
    @Autowired
    private MessageDistributionService messageDistributionService;

    // 用于异步执行离线推送的线程池（避免阻塞 WebSocket 连接建立）
    private final ScheduledExecutorService pushScheduler = Executors.newScheduledThreadPool(2);

    // 常量定义
    private static final String DISCONNECT_DELAY_KEY_PREFIX = "chat:ws:disconnect:delay:";
    private static final long DISCONNECT_DELAY_SECONDS = 2;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Long userId = getUserIdFromSession(headerAccessor);

        if (userId == null) {
            log.error("WebSocket连接事件：无法获取userId，sessionId={}", sessionId);
            return;
        }

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        String clientStatus = "online";
        if (sessionAttributes != null && sessionAttributes.get("clientStatus") instanceof String) {
            clientStatus = (String) sessionAttributes.get("clientStatus");
        }

        String currentStatus = userStatusService.getUserStatus(userId);

        if (currentStatus == null || currentStatus.isEmpty() || currentStatus.equals("offline")) {
            userStatusService.setUserStatus(userId, clientStatus, sessionId);
            log.info("WebSocket连接初始化业务状态: userId={}, sessionId={}, status={}",
                    userId, sessionId, clientStatus);
        } else {
            String oldSessionId = userStatusService.getStatusSessionId(userId);
            userStatusService.setUserStatus(userId, currentStatus, sessionId);
            log.info("WebSocket连接恢复业务状态: userId={}, newSessionId={}, oldSessionId={}, status={}",
                    userId, sessionId, oldSessionId, currentStatus);
        }

        webSocketSessionService.registerSession(sessionId, userId, headerAccessor);

        // WebSocket 连接成功后，延迟触发离线消息推送
        // 延迟 2 秒：1) 确保连接完全建立 2) 避免阻塞当前线程 3) 与登录时的延迟保持一致
        pushScheduler.schedule(() -> {
            try {
                log.info("WebSocket连接成功，检查并推送离线消息: userId={}", userId);
                messageDistributionService.pushOfflineMessagesOnLogin(userId);
            } catch (Exception e) {
                log.error("WebSocket连接后推送离线消息失败: userId={}", userId, e);
            }
        }, 2, TimeUnit.SECONDS);
    }


    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) throws JsonProcessingException {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Long userId = getUserIdFromSession(headerAccessor);

        if (userId == null) return;

        webSocketSessionService.removeSession(sessionId);

        // 使用Redis过期键替代内存延迟
        String delayKey = DISCONNECT_DELAY_KEY_PREFIX + userId + ":" + sessionId;

        // 存储临时数据（JSON格式）
        Map<String, String> data = new HashMap<>();
        data.put("userId", userId.toString());
        data.put("sessionId", sessionId);
        data.put("disconnectTime", String.valueOf(System.currentTimeMillis()));

        stringRedisTemplate.opsForValue().set(delayKey,
                new ObjectMapper().writeValueAsString(data),
                DISCONNECT_DELAY_SECONDS,
                TimeUnit.SECONDS);

        log.debug("设置离线延迟清理: userId={}, sessionId={}", userId, sessionId);
    }

    // 如果不启用Redis过期监听，改用定期扫描（更简单）
    @Scheduled(fixedRate = 5000) // 每5秒检查
    public void scanDelayedDisconnect() {
        String pattern = DISCONNECT_DELAY_KEY_PREFIX + "*";
        Set<String> keys = stringRedisTemplate.keys(pattern);

        if (keys == null || keys.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (String key : keys) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) continue; // 已过期或被处理

            try {
                Map<?, ?> data = new ObjectMapper().readValue(json, Map.class);
                long disconnectTime = Long.parseLong((String) data.get("disconnectTime"));

                // 检查是否已满足延迟时间（预留1秒误差）
                if (now - disconnectTime >= (DISCONNECT_DELAY_SECONDS - 1) * 1000) {
                    Long userId = Long.parseLong((String) data.get("userId"));
                    String sessionId = (String) data.get("sessionId");

                    // 删除key（原子操作）
                    Boolean deleted = stringRedisTemplate.delete(key);
                    if (Boolean.TRUE.equals(deleted)) {
                        // 执行清理逻辑
                        String currentSessionId = userStatusService.getStatusSessionId(userId);
                        if (sessionId.equals(currentSessionId) &&
                                !webSocketSessionService.isUserOnline(userId)) {
                            userStatusService.clearUserStatus(userId, sessionId);
                            log.info("用户彻底离线（扫描触发）: userId={}", userId);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("处理延迟离线任务失败: key={}", key, e);
                stringRedisTemplate.delete(key); // 清理异常数据
            }
        }
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headerAccessor.getDestination();
        Long userId = getUserIdFromSession(headerAccessor);

        if (userId != null && destination != null) {
            log.info("用户订阅频道: userId={}, destination={}, sessionId={}",
                    userId, destination, headerAccessor.getSessionId());
        }
    }

    /**
     * 【统一方法】从SessionAttributes安全获取userId
     * WebSocketAuthInterceptor中明确存储在sessionAttributes中
     */
    /**
     * 【统一方法】从SessionAttributes安全获取userId
     * 现在也要兼容从 Principal 获取的情况
     */
    private Long getUserIdFromSession(StompHeaderAccessor accessor) {
        // 优先从 Principal 获取（如果已恢复）
        Principal principal = accessor.getUser();
        if (principal != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException e) {
                // 如果 name 不是 userId，从 details 获取
                if (principal instanceof Authentication) {
                    Object details = ((Authentication) principal).getDetails();
                    if (details instanceof Map) {
                        Object userId = ((Map<?, ?>) details).get("userId");
                        if (userId instanceof Long) return (Long) userId;
                        if (userId instanceof String) return Long.parseLong((String) userId);
                    }
                }
            }
        }

        // 回退到 SessionAttributes（兼容初始连接阶段）
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object userIdObj = sessionAttributes.get("userId");
            if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            } else if (userIdObj instanceof String) {
                try {
                    return Long.parseLong((String) userIdObj);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return null;
    }
}