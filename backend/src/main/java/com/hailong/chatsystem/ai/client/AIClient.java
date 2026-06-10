package com.hailong.chatsystem.ai.client;

import com.hailong.chatsystem.ai.config.AIProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AIClient {

    @Autowired
    private AIProperties aiProperties;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        String baseUrl = aiProperties.getBaseUrl();

        // 修复：确保 baseUrl 包含 /v1 路径（兼容 DeepSeek/OpenAI）
        if (baseUrl != null && !baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl + "/v1";
        }

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 通用文本生成方法
     * @param systemPrompt 系统提示词（角色设定）
     * @param userContent 用户输入内容（待处理的聊天记录）
     * @return AI生成的文本
     */
    public String generate(String systemPrompt, String userContent) {
        long startTime = System.currentTimeMillis();
        log.info(">>> AI请求开始: systemPrompt长度={}, userContent长度={}",
                systemPrompt.length(), userContent.length());

        try {

            Map<String, Object> requestBody = Map.of(
                    "model", aiProperties.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userContent)
                    ),
                    "max_tokens", aiProperties.getMaxTokens(),
                    "temperature", aiProperties.getTemperature(),
                    "reasoning_effort", "none"
            );

            log.debug("请求体: {}", objectMapper.writeValueAsString(requestBody));

            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), resp -> {
                        log.error("AI API调用失败: {}", resp.statusCode());
                        return Mono.error(new RuntimeException("AI服务异常"));
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                    .block();

            long duration = System.currentTimeMillis() - startTime;
            log.info("<<< AI请求完成，耗时: {} ms", duration);
            log.debug("原始响应: {}", response);

            // 解析OpenAI格式响应
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").get(0).path("message").path("content");

            if (content.isMissingNode()) {
                log.error("AI响应格式异常: {}", response);
                return "AI响应解析失败";
            }

            return content.asText().trim();

        } catch (Exception e) {
            log.error("AI调用异常", e);
            return "AI服务暂时不可用";
        }
    }

    /**
     * 简化版：仅传入用户内容，使用默认系统提示（用于测试或通用场景）
     */
    public String generateSimple(String userContent) {
        return generate("你是一个 helpful 的助手。", userContent);
    }
}