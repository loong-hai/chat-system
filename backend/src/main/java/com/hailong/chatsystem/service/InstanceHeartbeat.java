package com.hailong.chatsystem.service;

import com.hailong.chatsystem.config.InstanceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class InstanceHeartbeat {
    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;
    @Autowired
    private InstanceInfo instanceInfo;

    // 每10秒更新一次心跳，过期时间30秒
    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        String key = "chat:ws:instance:heartbeat:" + instanceInfo.getInstanceId();
        stringRedisTemplate.opsForValue().set(key, "alive", 30, TimeUnit.SECONDS); // 改用 stringRedisTemplate
        log.debug("实例心跳更新: {}", instanceInfo.getInstanceId());
    }
}