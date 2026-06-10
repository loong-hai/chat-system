package com.hailong.chatsystem.ai.controller;

import com.hailong.chatsystem.ai.dto.SummaryCacheDTO;
import com.hailong.chatsystem.ai.service.AISummaryService;
import com.hailong.chatsystem.common.ResponseMessage;
import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI助手", description = "智能总结与回复建议")
public class AIController {

    @Autowired
    private AISummaryService aiSummaryService;

    @Autowired
    private UserRepository userRepository;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("未登录");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .map(User::getUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    /**
     * 功能1 API：获取自动总结（最近1-2天已读消息）
     * GET /ai/summary/auto
     */
    @GetMapping("/summary/auto")
    @Operation(summary = "获取自动总结", description = "获取AI对最近1-2天已读消息的总结，如未生成返回'AI正在总结中'")
    public ResponseMessage<String> getAutoSummary() {
        Long userId = getCurrentUserId();
        String summary = aiSummaryService.getAutoSummary(userId);

        return ResponseMessage.success("操作成功",summary);
    }

    /**
     * 功能2 API：获取对话注意事项
     * GET /ai/summary/chat/{conversationId}
     */
    @GetMapping("/summary/chat/{conversationId}")
    @Operation(summary = "获取对话注意事项")
    public ResponseMessage<SummaryCacheDTO> getChatSummary(@PathVariable String conversationId) {
        Long userId = getCurrentUserId();
        SummaryCacheDTO dto = aiSummaryService.getOrGenerateChatSummary(userId, conversationId);

        if (dto.isGenerating()) {
            // 使用 error 方法或自定义 ResponseMessage 来返回202状态
            return ResponseMessage.error(202, "生成中", dto);
        }
        return ResponseMessage.success(dto);
    }

    /**
     * 功能3 API：获取回复建议
     * POST /ai/suggest-reply
     */
    @PostMapping("/suggest-reply")
    @Operation(summary = "获取回复建议", description = "基于对话上下文和注意事项，AI拟定回复")
    public ResponseMessage<String> getReplySuggestion(
            @RequestParam String conversationId,
            @RequestParam String lastMessage) {
        Long userId = getCurrentUserId();
        String suggestion = aiSummaryService.generateReplySuggestion(userId, conversationId, lastMessage);
        return ResponseMessage.success("操作成功",suggestion);
    }
}