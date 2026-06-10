package com.hailong.chatsystem.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserStatusService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Redis Key 前缀
    private static final String USER_BIZ_STATUS_PREFIX = "chat:user:biz-status:";  // 业务状态（online/busy/invisible）
    private static final String USER_DND_PREFIX = "chat:user:dnd:";  // 免打扰设置
    private static final String USER_INTIMACY_PREFIX = "chat:user:intimacy:";  // 亲密度设置（用于忙碌时判断）


    /**
     * HTTP 场景：设置用户业务状态（不带 sessionId，表示全局状态）
     * 保持原有方法签名，UserController 无需修改
     */
    public void setUserStatus(Long userId, String status) {
        setUserStatus(userId, status, null);
    }


    /**
     * WebSocket 场景：设置用户业务状态（带 sessionId，绑定到特定连接）
     * 新增方法，供 WebSocketEventListener 使用
     */
    public void setUserStatus(Long userId, String status, String sessionId) {
        String key = USER_BIZ_STATUS_PREFIX + userId;
        Map<String, String> statusInfo = new HashMap<>(); // 注意：Map 的 value 类型改为 String
        statusInfo.put("status", status);
        statusInfo.put("updateTime", String.valueOf(System.currentTimeMillis()));

        if (sessionId != null) {
            statusInfo.put("sessionId", sessionId);
        }

        stringRedisTemplate.opsForHash().putAll(key, statusInfo);
        stringRedisTemplate.expire(key, 300, TimeUnit.SECONDS);

        log.info("用户设置业务状态: userId={}, status={}, sessionId={}",
                userId, status, sessionId);
    }

    /**
     * 获取用户业务状态
     * 默认返回 offline，且如果 Key 不存在返回 null 给上游判断
     */
    public String getUserStatus(Long userId) {
        String key = USER_BIZ_STATUS_PREFIX + userId;

        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return "offline";
        }

        Object status = stringRedisTemplate.opsForHash().get(key, "status");
        if (status == null) {
            return "offline";
        }
        return status.toString();
    }

    /**
     * 检查用户是否设置为忙碌
     */
    public boolean isBusy(Long userId) {
        return "busy".equals(getUserStatus(userId));
    }

    /**
     * 检查用户是否设置为隐身
     */
    public boolean isInvisible(Long userId) {
        return "invisible".equals(getUserStatus(userId));
    }

    /**
     * 设置免打扰（Do Not Disturb）
     */
    public void setDoNotDisturb(Long userId, boolean enabled, String startTime, String endTime) {
        String key = USER_DND_PREFIX + userId;
        Map<String, String> dndInfo = new HashMap<>();
        dndInfo.put("enabled", String.valueOf(enabled));
        if (enabled) {
            dndInfo.put("startTime", startTime != null ? startTime : "23:00");
            dndInfo.put("endTime", endTime != null ? endTime : "07:00");
        }
        dndInfo.put("updateTime", String.valueOf(System.currentTimeMillis()));

        stringRedisTemplate.opsForHash().putAll(key, dndInfo);
        stringRedisTemplate.expire(key, 30, TimeUnit.DAYS);
    }

    /**
     * 检查当前是否在免打扰时段
     */
    public boolean isInDoNotDisturbPeriod(Long userId) {
        String key = USER_DND_PREFIX + userId;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return false;
        }

        String enabledStr = (String) entries.get("enabled");
        boolean enabled = Boolean.parseBoolean(enabledStr);
        if (!enabled) {
            return false;
        }

        String startStr = (String) entries.getOrDefault("startTime", "23:00");
        String endStr = (String) entries.getOrDefault("endTime", "07:00");

        return isTimeInRange(LocalTime.now(),
                LocalTime.parse(startStr),
                LocalTime.parse(endStr));
    }


    /**
     * 获取或设置亲密度（用于忙碌时判断是否推送）
     */
    public int getIntimacy(Long userId, Long friendId) {
        String key = USER_INTIMACY_PREFIX + userId + ":" + friendId;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 清理用户状态（WebSocket 场景）
     * @param sessionId 如果传入 null，表示 HTTP 场景强制清理；传入具体值则只清理匹配的状态
     */
    public void clearUserStatus(Long userId, String sessionId) {
        String key = USER_BIZ_STATUS_PREFIX + userId;

        if (sessionId == null) {
            stringRedisTemplate.delete(key);
            log.info("用户业务状态已清理(HTTP模式): userId={}", userId);
            return;
        }

        Object currentSessionId = stringRedisTemplate.opsForHash().get(key, "sessionId");

        if (sessionId.equals(currentSessionId)) {
            stringRedisTemplate.delete(key);
            log.info("用户业务状态已清理(WebSocket模式): userId={}, sessionId={}",
                    userId, sessionId);
        } else {
            log.warn("跳过清理，sessionId 不匹配: userId={}, expected={}, actual={}",
                    userId, sessionId, currentSessionId);
        }
    }

    /**
     * 获取当前状态的 sessionId（用于断线重连判断）
     */
    public String getStatusSessionId(Long userId) {
        String key = USER_BIZ_STATUS_PREFIX + userId;
        Object sessionId = stringRedisTemplate.opsForHash().get(key, "sessionId");
        return sessionId != null ? sessionId.toString() : null;
    }

    /**
     * 检查当前时间是否在免打扰时段（支持跨天，如23:00-07:00）
     */
    private boolean isTimeInRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            // 不跨天，如 09:00-18:00
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            // 跨天，如 23:00-07:00
            return !now.isBefore(start) || !now.isAfter(end);
        }
    }
    /**
     * ✅ 新增：续期 TTL（WebSocket 心跳时调用，可选优化）
     * 如果业务状态需要长期保持（如 busy 状态），WebSocket 心跳时应续期
     */
    public void renewStatusTtl(Long userId) {
        String key = USER_BIZ_STATUS_PREFIX + userId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            stringRedisTemplate.expire(key, 300, TimeUnit.SECONDS);
        }
    }

}