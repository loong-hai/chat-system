package com.hailong.chatsystem.controller;

import com.hailong.chatsystem.model.dto.*;
import com.hailong.chatsystem.common.ResponseMessage;
import com.hailong.chatsystem.model.entity.FriendRelation;
import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.model.vo.UserVO;
import com.hailong.chatsystem.repository.UserRepository;
import com.hailong.chatsystem.service.UserService;
import com.hailong.chatsystem.service.cache.UserSessionService;
import com.hailong.chatsystem.service.cache.UserStatusService;
import com.hailong.chatsystem.service.storage.StorageService;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "用户信息管理相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private StorageService storageService;  // 新增注入

    @Autowired
    private UserStatusService userStatusService;  // 新增注入

    @Autowired
    private UserRepository  userRepository;
    @Autowired
    private WebSocketSessionService webSocketSessionService;


    /**
     * 获取当前用户在线状态（基于 WebSocket）
     */
    @GetMapping("/me/online-status")
    @Operation(summary = "获取在线状态", description = "基于 WebSocket 连接状态")
    public ResponseMessage<Map<String, Object>> getOnlineStatus() {
        Long userId = getCurrentUserId();

        // 从 WebSocket 服务获取
        boolean isOnline = webSocketSessionService.isUserOnline(userId);
        int deviceCount = webSocketSessionService.getUserOnlineDeviceCount(userId);

        // 从 UserStatusService 获取业务状态（busy/invisible）
        String bizStatus = userStatusService.getUserStatus(userId);

        Map<String, Object> status = new HashMap<>();
        status.put("isOnline", isOnline);           // WebSocket 物理连接
        status.put("deviceCount", deviceCount);     // 多端数量
        status.put("bizStatus", bizStatus);         // 业务设置（online/busy/invisible）
        status.put("userId", userId);
        status.put("lastLoginTime", userService.getCurrentUser().getLastLoginTime());
        return ResponseMessage.success(status);
    }

    @GetMapping("/online/count")
    @Operation(summary = "获取在线用户概况", description = "基于 WebSocket 实时连接数")
    public ResponseMessage<Map<String, Object>> getOnlineUserStatus() {
        // 改为 WebSocket 统计
        Long count = webSocketSessionService.getOnlineUserCount();

        Map<String, Object> result = new HashMap<>();
        result.put("onlineCount", count);
        result.put("status", count > 0 ? "online" : "quiet");
        result.put("activity", count > 100 ? "high" : count > 10 ? "medium" : "low");
        result.put("message", count > 0 ? "有用户在线" : "当前较安静");

        return ResponseMessage.success(result);
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    public ResponseMessage<UserVO> getCurrentUser() {
        UserVO userVO = userService.getCurrentUser();
        return ResponseMessage.success(userVO);
    }

    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "更新用户信息(JSON)")
    public ResponseMessage<UserVO> updateUserJson(@Valid @RequestBody UserUpdateDTO updateDTO) {
        UserVO userVO = userService.updateUser(updateDTO);
        return ResponseMessage.success(userVO);
    }

    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "更新用户信息(含文件)")
    public ResponseMessage<UserVO> updateUserMultipart(
            @Valid @ModelAttribute UserUpdateDTO updateDTO,
            @RequestPart(value = "avatarFile", required = false) MultipartFile avatarFile) {

        Long userId = getCurrentUserId();

        if (avatarFile != null && !avatarFile.isEmpty()) {
            var result = storageService.uploadAvatar(userId, avatarFile);
            updateDTO.setAvatarUrl(result.getFileUrl());
        }

        UserVO userVO = userService.updateUser(updateDTO);
        return ResponseMessage.success(userVO);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("用户未登录");
        }

        // Principal 就是 User 实体（来自 UserDetailsServiceImpl.loadUserByUsername）
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getUserId();  // 直接取主键，绝对可靠
        }

        // 防御性编程：万一Principal是字符串（理论上不会，但保险起见）
        throw new RuntimeException("无法获取用户ID，Principal类型异常: " + principal.getClass());
    }

    @PutMapping("/me/password")
    @Operation(summary = "修改密码", description = "修改当前登录用户的密码")
    public ResponseMessage<Void> changePassword(@Valid @RequestBody PasswordChangeDTO passwordChangeDTO) {
        userService.changePassword(passwordChangeDTO);
        return ResponseMessage.success();
    }

    /**
     * 更新用户业务状态（在线/忙碌/隐身）
     * 只写入Redis，不操作数据库
     */
    @PutMapping("/me/status")
    @Operation(summary = "更新在线状态", description = "设置 online/busy/invisible")
    public ResponseMessage<Void> updateOnlineStatus(@RequestParam Integer status) {
        Long userId = getCurrentUserId();

        // 转换为字符串状态
        String statusStr = convertStatusCodeToString(status); // 你的现有方法

        // 【关键】写入Redis（UserStatusService），这是唯一的状态存储
        userStatusService.setUserStatus(userId, statusStr);

        log.info("用户更新业务状态: userId={}, status={}", userId, statusStr);
        return ResponseMessage.success("状态更新成功");
    }


    private String convertStatusCodeToString(Integer status) {
        return switch (status) {
            case 1 -> "online";
            case 2 -> "busy";
            case 3 -> "invisible";
            default -> "offline";
        };
    }

    /**
     * 设置免打扰模式
     */
    @PostMapping("/me/dnd")
    @Operation(summary = "设置免打扰", description = "设置免打扰时段")
    public ResponseMessage<Void> setDoNotDisturb(
            @RequestParam boolean enabled,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        User user = getCurrentUserEntity();
        userStatusService.setDoNotDisturb(user.getUserId(), enabled, startTime, endTime);
        return ResponseMessage.success();
    }

    /**
     * 获取当前登录用户实体（新增辅助方法）
     */
    private User getCurrentUserEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("用户未登录");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "获取用户信息", description = "根据用户ID获取用户信息")
    public ResponseMessage<UserVO> getUserById(@PathVariable Long userId) {
        UserVO userVO = userService.getUserById(userId);
        return ResponseMessage.success(userVO);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索用户", description = "根据关键词搜索用户")
    public ResponseMessage<List<UserVO>> searchUsers(@RequestParam String keyword) {
        List<UserVO> users = userService.searchUsers(keyword);
        return ResponseMessage.success(users);
    }

    @GetMapping
    @Operation(summary = "查询用户列表", description = "分页查询用户列表")
    public ResponseMessage<Page<UserVO>> queryUsers(@Valid UserQueryDTO queryDTO) {
        Page<UserVO> page = userService.queryUsers(queryDTO);
        return ResponseMessage.success(page);
    }

    @PostMapping("/{userId}/freeze")
    @Operation(summary = "冻结用户", description = "冻结指定用户账号")
    public ResponseMessage<Void> freezeUser(@PathVariable Long userId) {
        userService.freezeUser(userId);
        return ResponseMessage.success();
    }

    @PostMapping("/{userId}/unfreeze")
    @Operation(summary = "解冻用户", description = "解冻指定用户账号")
    public ResponseMessage<Void> unfreezeUser(@PathVariable Long userId) {
        userService.unfreezeUser(userId);
        return ResponseMessage.success();
    }

    @PostMapping("/{userId}/deregister")
    @Operation(summary = "注销用户", description = "注销指定用户账号")
    public ResponseMessage<Void> deregisterUser(@PathVariable Long userId) {
        userService.deregisterUser(userId);
        return ResponseMessage.success();
    }
}