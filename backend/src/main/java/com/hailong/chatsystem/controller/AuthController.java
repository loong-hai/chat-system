package com.hailong.chatsystem.controller;
import com.hailong.chatsystem.ai.service.AISummaryService;
import com.hailong.chatsystem.model.dto.LoginDTO;
import com.hailong.chatsystem.model.dto.RegisterDTO;
import com.hailong.chatsystem.model.dto.TokenDTO;
import com.hailong.chatsystem.common.ResponseMessage;
import com.hailong.chatsystem.model.vo.UserVO;
import com.hailong.chatsystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "用户注册、登录、注销等认证相关接口")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private AISummaryService aiSummaryService;

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "新用户注册接口")
    public ResponseMessage<TokenDTO> register(@Valid @RequestBody RegisterDTO registerDTO,
                                              HttpServletRequest request) {
        // 先注册用户
        UserVO userVO = userService.register(registerDTO);

        // 自动登录
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setIdentifier(registerDTO.getUsername());
        loginDTO.setPassword(registerDTO.getPassword());
        loginDTO.setStatus("online");

        String ip = getClientIp(request);
        TokenDTO tokenDTO = userService.login(loginDTO, ip); // 这会创建会话

        return ResponseMessage.success("注册成功", tokenDTO);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录接口，支持用户名/邮箱/手机号登录")
    public ResponseMessage<TokenDTO> login(@Valid @RequestBody LoginDTO loginDTO,
                                           HttpServletRequest request) {
        String ip = getClientIp(request);

        TokenDTO tokenDTO = userService.login(loginDTO, ip);

        // 上层调用：用户上线立即触发AI总结
         triggerAISummaryAsync(tokenDTO.getUserId());

        return ResponseMessage.success(tokenDTO);
    }

    /**
     * 异步触发AI总结（避免阻塞登录响应）
     */
    private void triggerAISummaryAsync(Long userId) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3000); // 延迟3秒，确保登录事务已提交
                aiSummaryService.generateAutoSummary(userId);
                log.debug("用户上线触发AI总结完成: userId={}", userId);
            } catch (Exception e) {
                log.error("用户上线触发AI总结失败: userId={}", userId, e);
            }
        });
    }

    @PostMapping("/logout")
    @Operation(summary = "用户退出", description = "用户退出登录接口")
    public ResponseMessage<Void> logout() {
        userService.logout();
        return ResponseMessage.success();
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "刷新访问令牌")
    public ResponseMessage<TokenDTO> refreshToken(@RequestParam String refreshToken) {
        TokenDTO tokenDTO = userService.refreshToken(refreshToken);
        return ResponseMessage.success(tokenDTO);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}