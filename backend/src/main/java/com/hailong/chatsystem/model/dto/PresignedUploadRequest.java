package com.hailong.chatsystem.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预签名上传请求DTO
 * 前端准备上传大文件前，先调用此接口获取临时上传URL
 */
@Data
public class PresignedUploadRequest {

    /**
     * 文件名（用于获取扩展名和Content-Type）
     */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /**
     * 文件大小（字节，用于服务器配额检查）
     */
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    /**
     * 文件Hash（客户端预先计算的SHA256，用于秒传检查）
     * 如果提供，服务器先检查是否已存在，存在则直接返回秒传结果，不生成预签名URL
     */
    private String fileHash;

    /**
     * 文件类型（avatar/chat/other，决定存储路径和权限）
     */
    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    /**
     * MIME类型（可选，用于S3 Content-Type）
     */
    private String contentType;

    /**
     * 聊天会话ID（如果是聊天文件，用于后续权限控制）
     * 可选，当前版本预留
     */
    private String conversationId;
}