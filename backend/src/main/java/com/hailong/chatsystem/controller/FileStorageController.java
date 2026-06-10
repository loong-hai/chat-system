package com.hailong.chatsystem.controller;

import com.hailong.chatsystem.common.ResponseMessage;
import com.hailong.chatsystem.model.dto.FileUploadResult;
import com.hailong.chatsystem.model.dto.PresignedUploadRequest;
import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.repository.UserRepository;
import com.hailong.chatsystem.service.UserService;
import com.hailong.chatsystem.service.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件存储Controller
 * 提供头像上传、聊天文件上传、预签名上传等接口
 */
@Slf4j
@RestController
@RequestMapping("/storage")
@Tag(name = "文件存储", description = "用户头像、聊天文件上传下载管理")
public class FileStorageController {

    @Autowired
    private StorageService storageService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    /**
     * 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("用户未登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getUserId();
        } else if (principal instanceof String) {
            // 如果 principal 是用户名，通过 repository 查询 userId
            String username = (String) principal;
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用户不存在"))
                    .getUserId();
        } else {
            throw new RuntimeException("无法获取用户ID");
        }
    }

    /**
     * 上传头像（服务器中转模式）
     * 支持jpg/png/gif/webp，最大5MB，自动压缩生成缩略图
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传头像")
    public ResponseMessage<FileUploadResult> uploadAvatar(
            @RequestParam("file") MultipartFile file) {

        Long userId = getCurrentUserId();
        FileUploadResult result = storageService.uploadAvatar(userId, file);

        // 关键：同步更新User表的avatar_url字段
        userService.updateUserAvatar(userId, result.getFileUrl());

        return ResponseMessage.success(
                Boolean.TRUE.equals(result.getIsRapidUpload()) ? "秒传成功" : "上传成功",
                result
        );
    }

    /**
     * 获取头像上传URL（预签名模式，理论上头像不用，但提供备用）
     */
    @PostMapping("/avatar/presigned")
    @Operation(summary = "获取头像预签名URL", description = "大文件头像使用，一般不建议")
    public ResponseMessage<FileUploadResult> getAvatarPresignedUrl(
            @Valid @RequestBody PresignedUploadRequest request) {

        Long userId = getCurrentUserId();
        request.setFileType("avatar");

        FileUploadResult result = storageService.getPresignedUploadUrl(userId, request);
        return ResponseMessage.success(result);
    }

    /**
     * 上传聊天文件（服务器中转，适合<100MB）
     */
    @PostMapping(value = "/chat/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传聊天文件", description = "上传图片/视频/文件到聊天，支持秒传，限制100MB以内")
    public ResponseMessage<FileUploadResult> uploadChatFile(
            @Parameter(description = "文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "会话ID", required = false)
            @RequestParam(value = "conversationId", required = false) String conversationId) {

        Long userId = getCurrentUserId();
        log.info("用户上传聊天文件: userId={}, filename={}, size={}MB",
                userId, file.getOriginalFilename(), file.getSize() / 1024 / 1024);

        FileUploadResult result = storageService.uploadChatFile(userId, file, true);
        return ResponseMessage.success(result);
    }

    /**
     * 申请大文件预签名上传URL（>=100MB使用）
     */
    @PostMapping("/presigned-url")
    @Operation(summary = "申请预签名上传URL", description = "大文件上传第一步，获取临时直传URL，支持秒传检查")
    public ResponseMessage<FileUploadResult> getPresignedUrl(
            @Valid @RequestBody PresignedUploadRequest request) {

        Long userId = getCurrentUserId();
        log.info("用户申请预签名URL: userId={}, filename={}, size={}",
                userId, request.getFileName(), request.getFileSize());

        FileUploadResult result = storageService.getPresignedUploadUrl(userId, request);

        if (Boolean.TRUE.equals(result.getIsRapidUpload())) {
            return ResponseMessage.success("秒传成功，无需上传", result);
        }
        return ResponseMessage.success("请使用presignedUrl上传文件", result);
    }

    /**
     * 确认预签名上传完成（大文件上传第二步）
     */
    @PostMapping("/confirm-upload")
    @Operation(summary = "确认上传完成", description = "大文件直传S3成功后，通知服务器记录元数据")
    public ResponseMessage<FileUploadResult> confirmUpload(
            @Parameter(description = "文件Key", required = true)
            @RequestParam String fileKey,
            @Parameter(description = "文件Hash(SHA256)", required = true)
            @RequestParam String fileHash) {

        Long userId = getCurrentUserId();
        log.info("用户确认上传完成: userId={}, fileKey={}", userId, fileKey);

        FileUploadResult result = storageService.confirmUpload(userId, fileKey, fileHash);
        return ResponseMessage.success("确认成功", result);
    }

    /**
     * 获取头像历史记录
     */
    @GetMapping("/avatar/history")
    @Operation(summary = "获取头像历史", description = "查询用户历史使用过的头像列表")
    public ResponseMessage<List<FileUploadResult>> getAvatarHistory() {
        Long userId = getCurrentUserId();
        List<FileUploadResult> history = storageService.getAvatarHistory(userId);
        return ResponseMessage.success(history);
    }

    /**
     * 切换历史头像为当前头像
     */
    @PutMapping("/avatar/switch/{historyId}")
    @Operation(summary = "切换头像", description = "将历史头像设为当前使用")
    public ResponseMessage<FileUploadResult> switchAvatar(
            @Parameter(description = "历史记录ID", required = true)
            @PathVariable Long historyId) {

        Long userId = getCurrentUserId();
        FileUploadResult result = storageService.switchAvatar(userId, historyId);
        return ResponseMessage.success("切换成功", result);
    }

    /**
     * 删除文件（头像或聊天文件）
     */
    @DeleteMapping("/file")
    @Operation(summary = "删除文件", description = "删除指定Key的文件（仅可删除自己的）")
    public ResponseMessage<Void> deleteFile(
            @Parameter(description = "文件Key", required = true)
            @RequestParam String fileKey) {

        Long userId = getCurrentUserId();
        boolean success = storageService.deleteFile(userId, fileKey);

        if (success) {
            return ResponseMessage.success("删除成功");
        } else {
            return ResponseMessage.error("删除失败或无权限");
        }
    }

    /**
     * 获取文件访问URL（用于私有文件临时访问，公开文件直接返回CDN链接）
     */
    @GetMapping("/file/url")
    @Operation(summary = "获取文件访问链接", description = "获取带签名的临时访问URL（私有文件）或直接URL（公开文件）")
    public ResponseMessage<String> getFileUrl(
            @Parameter(description = "文件Key", required = true)
            @RequestParam String fileKey,
            @Parameter(description = "是否下载（强制签名）", required = false)
            @RequestParam(defaultValue = "false") boolean forDownload,
            @Parameter(description = "过期时间(分钟)", required = false)
            @RequestParam(defaultValue = "60") int expireMinutes) {

        Long userId = getCurrentUserId();
        // 简单校验：只能获取自己目录下的文件
        if (!fileKey.startsWith("users/" + userId + "/")) {
            return ResponseMessage.forbidden("无权访问此文件");
        }

        String url = storageService.getFileUrl(fileKey, forDownload, expireMinutes);
        return ResponseMessage.success(url);
    }
}