// service/websocket/WebSocketSessionService.java - 最终版
package com.hailong.chatsystem.service.websocket;

import com.hailong.chatsystem.config.InstanceInfo;
import com.hailong.chatsystem.service.message.MessageRouter;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class WebSocketSessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    @Autowired private InstanceInfo instanceInfo;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MessageRouter messageRouter;

    // Redis键前缀
    private static final String WS_SESSION_PREFIX = "chat:ws:session:";
    private static final String WS_USER_SESSIONS_PREFIX = "chat:ws:user:sessions:";


    /**
     * 构造器，移除 UserSessionService 参数
     */
    @Autowired
    public WebSocketSessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 强制移除指定会话（不检查权限，用于清理无效会话）
     */
    public void forceRemoveSession(String sessionId) {
        String sessionKey = WS_SESSION_PREFIX + sessionId;
        Map<Object, Object> sessionInfo = stringRedisTemplate.opsForHash().entries(sessionKey); // 改用 stringRedisTemplate

        if (!sessionInfo.isEmpty()) {
            Long userId = getLongValue(sessionInfo.get("userId"));

            if (userId != null) {
                String userSessionsKey = WS_USER_SESSIONS_PREFIX + userId;
                stringRedisTemplate.opsForSet().remove(userSessionsKey, sessionId); // 改用 stringRedisTemplate
            }

            stringRedisTemplate.delete(sessionKey); // 改用 stringRedisTemplate
            log.info("强制移除会话: sessionId={}, userId={}", sessionId, userId);
        }
    }

    /**
     * 注册WebSocket会话
     */
    public void registerSession(String sessionId, Long userId, StompHeaderAccessor accessor) {
        String sessionKey = WS_SESSION_PREFIX + sessionId;
        String userSessionsKey = WS_USER_SESSIONS_PREFIX + userId;
        String instanceUserKey = "chat:ws:instance:" + instanceInfo.getInstanceId() + ":user:" + userId;
        String userInstancesKey = "chat:ws:user:instances:" + userId;

        String luaScript =
                "redis.call('hset', KEYS[1], 'sessionId', ARGV[1]); " +
                        "redis.call('hset', KEYS[1], 'userId', ARGV[2]); " +
                        "redis.call('hset', KEYS[1], 'connectTime', ARGV[3]); " +
                        "redis.call('hset', KEYS[1], 'instanceId', ARGV[4]); " +
                        "redis.call('expire', KEYS[1], 300); " +
                        "redis.call('sadd', KEYS[2], ARGV[1]); " +
                        "redis.call('expire', KEYS[2], 300); " +
                        "redis.call('sadd', KEYS[3], ARGV[1]); " +
                        "redis.call('expire', KEYS[3], 604800); " + // 7天
                        "redis.call('sadd', KEYS[4], ARGV[4]); " +
                        "return 1;";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Long.class);

        // 使用 stringRedisTemplate 执行，参数类型自动适配
        stringRedisTemplate.execute(script,
                Arrays.asList(sessionKey, userSessionsKey, instanceUserKey, userInstancesKey),
                sessionId,
                userId.toString(),
                String.valueOf(System.currentTimeMillis()),
                instanceInfo.getInstanceId());

        log.info("WebSocket会话注册（原子操作）: userId={}, sessionId={}", userId, sessionId);

        messageRouter.registerUser(userId, instanceInfo.getInstanceId());

        log.info("WebSocket会话注册完成（含路由）: userId={}, instanceId={}",
                userId, instanceInfo.getInstanceId());

    }

    /**
     * 移除WebSocket会话
     */
    public void removeSession(String sessionId) {
        String sessionKey = WS_SESSION_PREFIX + sessionId;

        String luaScript =
                "local sessionInfo = redis.call('hgetall', KEYS[1]); " +
                        "if #sessionInfo == 0 then return nil; end; " +
                        "local userId = nil; " +
                        "for i = 1, #sessionInfo, 2 do " +
                        "  if sessionInfo[i] == 'userId' then userId = sessionInfo[i+1]; break; end; " +
                        "end; " +
                        "if userId then " +
                        "  redis.call('srem', KEYS[2] .. userId, ARGV[1]); " +
                        "  local instanceId = nil; " +
                        "  for i = 1, #sessionInfo, 2 do " +
                        "    if sessionInfo[i] == 'instanceId' then instanceId = sessionInfo[i+1]; break; end; " +
                        "  end; " +
                        "  if instanceId then " +
                        "    redis.call('srem', 'chat:ws:instance:' .. instanceId .. ':user:' .. userId, ARGV[1]); " +
                        "    local count = redis.call('scard', 'chat:ws:instance:' .. instanceId .. ':user:' .. userId); " +
                        "    if count == 0 then " +
                        "      redis.call('srem', 'chat:ws:user:instances:' .. userId, instanceId); " +
                        "      redis.call('del', 'chat:ws:instance:' .. instanceId .. ':user:' .. userId); " +
                        "    end; " +
                        "  end; " +
                        "end; " +
                        "redis.call('del', KEYS[1]); " +
                        "return userId;";

        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(String.class);

        String userId = stringRedisTemplate.execute(script,
                Arrays.asList(sessionKey, WS_USER_SESSIONS_PREFIX),
                sessionId);

        if (userId != null) {
            messageRouter.unregisterUser(userId, instanceInfo.getInstanceId());
        }

        if (userId != null) {
            log.info("WebSocket会话移除（原子操作）: sessionId={}, userId={}", sessionId, userId);
        } else {
            log.info("WebSocket会话移除（已不存在）: sessionId={}", sessionId);
        }
    }

    //关闭实例时清理路由表
    @PreDestroy
    public void destroy() {
        log.info("实例关闭，清理路由表: {}", instanceInfo.getInstanceId());

        // 1. 删除该实例的所有用户会话集合
        String pattern = "chat:ws:instance:" + instanceInfo.getInstanceId() + ":user:*";
        Set<String> keys = stringRedisTemplate.keys(pattern); // 改用 stringRedisTemplate
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                String[] parts = key.split(":");
                if (parts.length >= 5) {
                    try {
                        Long userId = Long.parseLong(parts[4]);
                        String userInstancesKey = "chat:ws:user:instances:" + userId;
                        stringRedisTemplate.opsForSet().remove(userInstancesKey, instanceInfo.getInstanceId()); // 改用 stringRedisTemplate
                    } catch (NumberFormatException e) {
                        log.warn("解析 userId 失败: {}", key);
                    }
                }
                stringRedisTemplate.delete(key); // 改用 stringRedisTemplate
            }
        }

        // 2. 删除实例心跳
        String heartbeatKey = "chat:ws:instance:heartbeat:" + instanceInfo.getInstanceId();
        stringRedisTemplate.delete(heartbeatKey); // 改用 stringRedisTemplate

        // 3. 清理消息处理标记
        try {
            Set<String> pendingKeys = stringRedisTemplate.keys("msg:processing:*"); // 改用 stringRedisTemplate
            if (pendingKeys != null) {
                for (String key : pendingKeys) {
                    stringRedisTemplate.delete(key); // 改用 stringRedisTemplate
                }
            }
        } catch (Exception e) {
            log.error("清理消息处理标记失败", e);
        }

        log.info("实例路由清理完成");
    }


    /**
     * 更新心跳
     */
    public boolean updateHeartbeat(String sessionId) {
        String sessionKey = WS_SESSION_PREFIX + sessionId;
        if (stringRedisTemplate.hasKey(sessionKey)) { // 改用 stringRedisTemplate
            stringRedisTemplate.opsForHash().put(sessionKey, "lastHeartbeat", String.valueOf(System.currentTimeMillis())); // 存入字符串
            stringRedisTemplate.expire(sessionKey, 300, TimeUnit.SECONDS);

            // 新增：续期路由表TTL
            Object userId = stringRedisTemplate.opsForHash().get(sessionKey, "userId");
            if (userId != null) {
                messageRouter.renewRoute(Long.parseLong(userId.toString()), instanceInfo.getInstanceId());
            }

            return true;
        }
        return false;
    }

    /**
     * 获取用户的所有WebSocket会话
     */
    public Set<String> getUserSessions(Long userId) {
        String userSessionsKey = WS_USER_SESSIONS_PREFIX + userId;
        return stringRedisTemplate.opsForSet().members(userSessionsKey);
    }

    /**
     * 强制断开用户的所有WebSocket连接
     */
    public void disconnectUser(Long userId) {
        Set<String> sessionIds = getUserSessions(userId);
        for (String sessionId : sessionIds) {
            removeSession(sessionId);
        }
        log.info("强制断开用户所有WebSocket连接: userId={}, sessions={}", userId, sessionIds.size());
    }

    /**
     * 检查会话是否有效
     */
    public boolean isSessionValid(String sessionId) {
        String sessionKey = WS_SESSION_PREFIX + sessionId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(sessionKey)); // 改用 stringRedisTemplate
    }

    /**
     * 获取会话信息
     */
    public Map<String, Object> getSessionInfo(String sessionId) {
        String sessionKey = WS_SESSION_PREFIX + sessionId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(sessionKey))) { // 改用 stringRedisTemplate
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(sessionKey); // 改用 stringRedisTemplate
            Map<String, Object> sessionInfo = new HashMap<>();
            entries.forEach((k, v) -> sessionInfo.put(k.toString(), v));
            return sessionInfo;
        }
        return null;
    }


    /**
     * 安全获取Long类型值
     */
    private Long getLongValue(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof String) {
            try { return Long.parseLong((String) obj); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }


    /**
     * 检查用户是否在线（验证会话有效性 + 实例心跳）
     */
    public boolean isUserOnline(Long userId) {
        String userSessionsKey = WS_USER_SESSIONS_PREFIX + userId;
        Set<String> sessionIds = getUserSessions(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return false;
        }

        for (String sessionId : sessionIds) {
            String sessionKey = WS_SESSION_PREFIX + sessionId;
            // 改用 stringRedisTemplate 检查会话是否存在
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(sessionKey))) {
                String instanceId = getInstanceIdFromSession(sessionId);
                if (instanceId != null && isInstanceAlive(instanceId)) {
                    return true;
                } else {
                    // 实例已死，清理无效会话（惰性清理）
                    log.warn("发现孤儿会话（实例已宕机）: sessionId={}, instanceId={}",
                            sessionId, instanceId);
                    stringRedisTemplate.opsForSet().remove(userSessionsKey, sessionId);
                    stringRedisTemplate.delete(sessionKey);
                }
            }
        }
        return false;
    }

    // 辅助方法：从会话获取实例ID
    private String getInstanceIdFromSession(String sessionId) {
        String sessionKey = WS_SESSION_PREFIX + sessionId;
        Object instanceId = stringRedisTemplate.opsForHash().get(sessionKey, "instanceId");
        return instanceId != null ? instanceId.toString() : null;
    }

    // 辅助方法：检查实例心跳
    private boolean isInstanceAlive(String instanceId) {
        String heartbeatKey = "chat:ws:instance:heartbeat:" + instanceId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(heartbeatKey));
    }

    /**
     * 获取在线用户数量
     */
    public Long getOnlineUserCount() {
        // 扫描所有WebSocket用户会话键
        Set<String> keys = redisTemplate.keys(WS_USER_SESSIONS_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }

        long count = 0;
        for (String key : keys) {
            String userIdStr = key.substring(WS_USER_SESSIONS_PREFIX.length());
            try {
                Long userId = Long.parseLong(userIdStr);
                if (isUserOnline(userId)) {
                    count++;
                }
            } catch (NumberFormatException e) {
                // 忽略非法key
            }
        }
        return count;
    }

    /**
     * 获取所有在线用户ID
     */
    public Set<Long> getOnlineUsers() {
        Set<String> keys = redisTemplate.keys(WS_USER_SESSIONS_PREFIX + "*");
        Set<Long> onlineUsers = new HashSet<>();

        if (keys == null) return onlineUsers;

        for (String key : keys) {
            String userIdStr = key.substring(WS_USER_SESSIONS_PREFIX.length());
            try {
                Long userId = Long.parseLong(userIdStr);
                if (isUserOnline(userId)) {
                    onlineUsers.add(userId);
                }
            } catch (NumberFormatException ignored) {}
        }
        return onlineUsers;
    }

    /**
     * 获取用户在线设备数
     */
    public int getUserOnlineDeviceCount(Long userId) {
        Set<String> sessions = getUserSessions(userId);
        if (sessions == null) return 0;

        int count = 0;
        for (String sessionId : sessions) {
            if (isSessionValid(sessionId)) {
                count++;
            }
        }
        return count;
    }

}