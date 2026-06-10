package com.hailong.chatsystem.service.cache;

import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserSessionService {

    // 只保留用户信息缓存
    private static final String USER_INFO_CACHE_PREFIX = "chat:user:info:";
    private static final long USER_INFO_EXPIRE_MINUTES = 30;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private WebSocketSessionService webSocketSessionService;

    // ==================== 用户信息缓存（保留） ====================

    public void cacheUserInfo(User user) {
        String key = USER_INFO_CACHE_PREFIX + user.getUserId();
        redisTemplate.opsForValue().set(key, user, USER_INFO_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    public User getCachedUserInfo(Long userId) {
        String key = USER_INFO_CACHE_PREFIX + userId;
        return (User) redisTemplate.opsForValue().get(key);
    }

    public void updateUserInfoCache(User user) {
        cacheUserInfo(user);
    }

    public void clearUserInfoCache(Long userId) {
        String key = USER_INFO_CACHE_PREFIX + userId;
        redisTemplate.delete(key);
    }

    public void batchClearUserInfoCache(Iterable<Long> userIds) {
        for (Long userId : userIds) {
            clearUserInfoCache(userId);
        }
    }

    // ==================== 会话管理（简化，不再存储 HTTP Session） ====================

    /**
     * 创建会话 - 仅生成 ID，不再存储 HTTP Session 到 Redis
     * 在线状态统一由 WebSocket 管理
     */
    public String createSession(Long userId, String deviceId, String ip) {
        String sessionId = java.util.UUID.randomUUID().toString();
        log.info("创建用户会话: userId={}, sessionId={}, deviceId={}, ip={}",
                userId, sessionId, deviceId, ip);
        return sessionId;
    }

    /**
     * 强制下线 - 清理缓存 + 断开 WebSocket，不再清理 HTTP Session
     */
    public void forceLogout(Long userId, String sessionId) {
        log.info("强制用户下线: userId={}", userId);

        // 1. 清除用户信息缓存
        clearUserInfoCache(userId);

        // 2. 断开 WebSocket 连接（权威在线状态）
        webSocketSessionService.disconnectUser(userId);


    }
}