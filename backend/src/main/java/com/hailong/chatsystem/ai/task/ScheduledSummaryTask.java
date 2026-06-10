package com.hailong.chatsystem.ai.task;

import com.hailong.chatsystem.ai.service.AISummaryService;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class ScheduledSummaryTask {

    @Autowired
    private WebSocketSessionService webSocketSessionService;

    @Autowired
    private AISummaryService aiSummaryService;

    // 专用线程池，避免阻塞其他定时任务
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * 每10分钟执行一次：为所有在线用户生成自动总结
     */
    // todo 测试1分钟一次
    @Scheduled(fixedDelay = 1 * 60 * 1000) // 10分钟
    public void autoSummarizeForOnlineUsers() {
        try {
            // 获取当前在线用户（利用已有的WebSocket服务）
            Set<Long> onlineUsers = webSocketSessionService.getOnlineUsers();

            log.info("开始自动总结任务，当前在线用户数：{}", onlineUsers.size());

            for (Long userId : onlineUsers) {
                // 异步执行每个用户的总结，避免阻塞定时线程
                executor.submit(() -> {
                    try {
                        aiSummaryService.generateAutoSummary(userId);
                    } catch (Exception e) {
                        log.error("定时总结任务失败 userId={}", userId, e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("自动总结定时任务异常", e);
        }
    }
    /**
     * 清理任务：每小时清理过期的锁
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredLocks() {
        // 这里可以添加清理逻辑，但Redis TTL会自动处理
        log.debug("清理过期锁任务执行");
    }
}