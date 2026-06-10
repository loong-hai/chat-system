package com.hailong.chatsystem.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class AIAsyncConfig {

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4,                              // 核心线程数
                8,                                          // 最大线程数
                60L,                                        // 空闲线程存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),     // 队列容量
                new ThreadPoolExecutor.CallerRunsPolicy()   // 拒绝策略：由调用线程执行
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}