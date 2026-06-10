package com.hailong.chatsystem.ai.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SummaryCacheDTO {
    private String content;             // 总结内容
    private LocalDateTime generateTime; // 生成时间
    private String conversationId;      // 所属会话（功能2使用）
    private String lastMessageId;       // 基于的最后一条消息ID（用于增量判断）
    private boolean isGenerating;       // 是否正在生成中（防击穿标记）

    // 工厂方法：创建"生成中"的占位缓存
    public static SummaryCacheDTO generating() {
        SummaryCacheDTO dto = new SummaryCacheDTO();
        dto.setGenerating(true);
        dto.setContent("AI正在总结中...");
        dto.setGenerateTime(LocalDateTime.now());
        return dto;
    }
}