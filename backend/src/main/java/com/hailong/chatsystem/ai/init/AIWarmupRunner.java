package com.hailong.chatsystem.ai.init;

import com.hailong.chatsystem.ai.client.AIClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AIWarmupRunner implements ApplicationRunner {

    @Autowired
    private AIClient aiClient;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 异步预热，不阻塞主流程
        new Thread(() -> {
            try {
                log.info("开始预热 AI 模型（将模型加载到显存）...");
                // 发送一个极简的请求，触发模型加载
                aiClient.generateSimple("ping");
                log.info("AI 模型预热完成，显存已占用，后续请求将无延迟。");
            } catch (Exception e) {
                log.warn("AI 模型预热失败（不影响主功能）: {}", e.getMessage());
            }
        }).start();
    }
}