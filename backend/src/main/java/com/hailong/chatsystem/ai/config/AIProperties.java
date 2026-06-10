package com.hailong.chatsystem.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AIProperties {
    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1/chat/completions"; // 支持自定义/OpenAI/Claude等
    private String model = "gpt-3.5-turbo";
    private int timeoutSeconds = 30;
    private int maxTokens = 1000;
    private double temperature = 0.7;

    // 提示词配置
    private Prompts prompts = new Prompts();

    @Data
    public static class Prompts {
        private String autoSummary = "请总结以下聊天记录的主要话题和关键信息（1-2天内的消息），用中文返回：";
        private String keyPoints = "请分析以下聊天记录，提取需要注意的事项、待办事项或重要信息，用中文返回：";
        private String replySuggestion = "基于以上聊天上下文，请帮用户拟定一条友好、得体的回复建议，用中文返回：";
    }
}