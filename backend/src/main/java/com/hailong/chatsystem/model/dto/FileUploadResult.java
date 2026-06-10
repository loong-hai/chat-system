package com.hailong.chatsystem.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件上传结果DTO
 * 统一封装服务器中转和预签名两种模式的结果
 */
@Data
@Builder
public class FileUploadResult {

    /**
     * 是否秒传成功（秒传时无需实际上传文件）
     */
    private Boolean isRapidUpload;

    /**
     * 文件访问URL（如果是服务器中转，直接返回；如果是预签名，返回基础URL）
     */
    private String fileUrl;

    /**
     * 缩略图URL（如果有）
     */
    private String thumbnailUrl;

    /**
     * 文件唯一标识（S3 Key）
     */
    private String fileKey;

    /**
     * 文件Hash（用于秒传校验）
     */
    private String fileHash;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 预签名上传URL（仅预签名模式返回，前端需要PUT到此URL）
     * 服务器中转时此字段为null
     */
    private String presignedUrl;

    /**
     * 预签名URL过期时间（秒）
     */
    private Integer expiresIn;

    /**
     * 上传任务ID（用于大文件分片上传的追踪，当前版本预留）
     */
    private String uploadId;

    /**
     * 提示信息（如"秒传成功"、"请使用预签名URL上传"等）
     */
    private String message;
}