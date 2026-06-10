package com.hailong.chatsystem.service.message;

import com.hailong.chatsystem.config.RabbitMQConfig;
import com.hailong.chatsystem.model.dto.ChatMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 轻量级消息路由表
 * 职责：维护 userId -> instanceId 的映射关系
 */
@Slf4j
@Service
public class MessageRouter {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Redis Key 前缀
    private static final String USER_ROUTE_PREFIX = "chat:route:user:";  // user:route:{userId} -> instanceId
    private static final String INSTANCE_USERS_PREFIX = "chat:route:instance:users:"; // instance:users:{instanceId} -> Set<userId>
    private static final String INSTANCE_ALIVE_PREFIX = "chat:route:instance:alive:"; // instance:alive:{instanceId} -> "1"

    // 路由信息 TTL（秒）：5分钟，需配合心跳续期
    private static final long ROUTE_TTL_SECONDS = 300;
    // 实例心跳 TTL（秒）：30秒
    private static final long INSTANCE_TTL_SECONDS = 30;

    /**
     * 用户上线：注册路由（WebSocket连接时调用）
     */
    public void registerUser(Long userId, String instanceId) {
        String userKey = USER_ROUTE_PREFIX + userId;
        String instanceUsersKey = INSTANCE_USERS_PREFIX + instanceId;
        String instanceAliveKey = INSTANCE_ALIVE_PREFIX + instanceId;

        try {
            // 1. 写入用户路由（Hash结构，便于扩展字段）
            redisTemplate.opsForHash().put(userKey, "instanceId", instanceId);
            redisTemplate.opsForHash().put(userKey, "timestamp", String.valueOf(System.currentTimeMillis()));
            redisTemplate.expire(userKey, ROUTE_TTL_SECONDS, TimeUnit.SECONDS);

            // 2. 反向索引：实例 -> 用户集合（用于实例下线时清理）
            redisTemplate.opsForSet().add(instanceUsersKey, userId.toString());
            redisTemplate.expire(instanceUsersKey, ROUTE_TTL_SECONDS, TimeUnit.SECONDS);

            // 3. 确保实例存活标记存在（如果不存在，创建它）
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(instanceAliveKey))) {
                redisTemplate.opsForValue().set(instanceAliveKey, "1", INSTANCE_TTL_SECONDS, TimeUnit.SECONDS);
            }

            log.debug("路由注册成功: userId={}, instanceId={}", userId, instanceId);
        } catch (Exception e) {
            log.error("路由注册失败: userId={}, instanceId={}", userId, instanceId, e);
            // 不影响主流程，降级为本地处理
        }
    }
    /**
     * 广播模式（降级）：当Redis故障或路由查询失败时，广播到所有存活实例
     * 注意：可能导致重复投递，依赖客户端幂等（messageId去重）
     */
    public void broadcastToAllInstances(ChatMessageDTO message, RabbitTemplate rabbitTemplate) {
        Set<String> instances = getAllAliveInstances();
        if (instances.isEmpty()) {
            // 无存活实例，全部转离线
            return;
        }

        for (String instanceId : instances) {
            rabbitTemplate.convertAndSend(RabbitMQConfig.PUSH_EXCHANGE, instanceId, message);
        }
        log.warn("广播模式投递消息: messageId={}, instances={}",
                message.getMessageId(), instances.size());
    }


    /**
     * 用户下线：注销路由（WebSocket断开时调用）
     */
    public void unregisterUser(String userId, String instanceId) {
        String userKey = USER_ROUTE_PREFIX + userId;
        String instanceUsersKey = INSTANCE_USERS_PREFIX + instanceId;

        try {
            // 验证当前路由是否属于本实例（防止误删其他实例的路由，处理快速重连场景）
            String currentInstance = getUserInstance(userId);
            if (instanceId.equals(currentInstance)) {
                redisTemplate.delete(userKey);
                log.debug("路由注销成功: userId={}, instanceId={}", userId, instanceId);
            }

            // 从反向索引中移除（无论是否匹配都移除，清理脏数据）
            redisTemplate.opsForSet().remove(instanceUsersKey, userId.toString());
        } catch (Exception e) {
            log.error("路由注销失败: userId={}, instanceId={}", userId, instanceId, e);
        }
    }

    /**
     * 查询用户所在实例（核心方法）
     * 如果返回null表示用户离线或路由失效
     */
    public String getUserInstance(String userId) {
        String userKey = USER_ROUTE_PREFIX + userId;
        try {
            Object instanceId = redisTemplate.opsForHash().get(userKey, "instanceId");
            if (instanceId == null) {
                return null;
            }

            // 二次确认：检查目标实例是否存活（防止实例崩溃后路由残留）
            String targetInstance = instanceId.toString();
            if (!isInstanceAlive(targetInstance)) {
                // 实例已死，清理过期路由
                log.warn("发现孤儿路由，清理: userId={}, deadInstance={}", userId, targetInstance);
                redisTemplate.delete(userKey);
                return null;
            }

            return targetInstance;
        } catch (Exception e) {
            log.error("查询路由失败: userId={}", userId, e);
            return null; // 降级为离线处理
        }
    }

    /**
     * 续期路由TTL（心跳时调用，防止过期）
     */
    public void renewRoute(Long userId, String instanceId) {
        String userKey = USER_ROUTE_PREFIX + userId;
        String instanceUsersKey = INSTANCE_USERS_PREFIX + instanceId;

        try {
            // 只有当路由存在且属于本实例时才续期
            Object currentInstance = redisTemplate.opsForHash().get(userKey, "instanceId");
            if (instanceId.equals(currentInstance)) {
                redisTemplate.expire(userKey, ROUTE_TTL_SECONDS, TimeUnit.SECONDS);
                redisTemplate.expire(instanceUsersKey, ROUTE_TTL_SECONDS, TimeUnit.SECONDS);

                // 更新心跳时间戳
                redisTemplate.opsForHash().put(userKey, "timestamp", String.valueOf(System.currentTimeMillis()));
            }
        } catch (Exception e) {
            log.debug("路由续期失败（可忽略）: userId={}", userId);
        }
    }

    /**
     * 检查实例是否存活（根据心跳键）
     */
    public boolean isInstanceAlive(String instanceId) {
        String aliveKey = INSTANCE_ALIVE_PREFIX + instanceId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(aliveKey));
    }

    /**
     * 获取本实例的所有用户（用于优雅下线时转移消息）
     */
    public Set<Long> getInstanceUsers(String instanceId) {
        String instanceUsersKey = INSTANCE_USERS_PREFIX + instanceId;
        try {
            Set<String> userIds = redisTemplate.opsForSet().members(instanceUsersKey);
            if (userIds == null) return Collections.emptySet();

            return userIds.stream()
                    .map(Long::parseLong)
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            log.error("获取实例用户失败: instanceId={}", instanceId, e);
            return Collections.emptySet();
        }
    }

    /**
     * 广播场景：获取所有存活实例ID（降级策略使用）
     */
    public Set<String> getAllAliveInstances() {
        try {
            Set<String> keys = redisTemplate.keys(INSTANCE_ALIVE_PREFIX + "*");
            if (keys == null) return Collections.emptySet();

            return keys.stream()
                    .map(key -> key.substring(INSTANCE_ALIVE_PREFIX.length()))
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            log.error("获取存活实例失败", e);
            return Collections.emptySet();
        }
    }
}