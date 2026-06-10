package com.hailong.chatsystem.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Getter
public class InstanceInfo {

    private String instanceId;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Redis Key 前缀
    private static final String INSTANCE_ALIVE_PREFIX = "chat:route:instance:alive:";
    private static final long INSTANCE_TTL_SECONDS = 30;

    @PostConstruct
    public void init() {
        // 保持原有逻辑，确保与RabbitMQ队列名一致！
        String envInstanceId = System.getenv("INSTANCE_ID");
        if (envInstanceId != null && !envInstanceId.isEmpty()) {
            this.instanceId = envInstanceId;
        } else {
            try {
                String hostname = InetAddress.getLocalHost().getHostName();
                // 注意：这里必须生成与之前一致的ID格式
                this.instanceId = hostname + "-" + UUID.randomUUID().toString().replace("-", "");
            } catch (UnknownHostException e) {
                this.instanceId = "unknown-" + UUID.randomUUID().toString().replace("-", "");
            }
        }

        // 新增：立即注册心跳
        heartbeat();
        log.info("实例初始化完成: instanceId={}", instanceId);
    }

    @PreDestroy
    public void destroy() {
        log.info("实例正在下线: instanceId={}", instanceId);
        // 清理存活标记
        redisTemplate.delete(INSTANCE_ALIVE_PREFIX + instanceId);
    }

    /**
     * 定时心跳：每10秒续期一次
     */
    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        try {
            String aliveKey = INSTANCE_ALIVE_PREFIX + instanceId;
            redisTemplate.opsForValue().set(aliveKey,
                    String.valueOf(System.currentTimeMillis()),
                    INSTANCE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("实例心跳失败", e);
        }
    }
}