package com.hailong.chatsystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 文件上传事务补偿表 - 防止S3文件孤儿
 * 用于记录预签名上传或服务器中转的待确认上传任务
 */
@Getter
@Setter
@Entity
@Table(name = "file_upload_transaction",
        indexes = {
                @Index(name = "idx_status_created", columnList = "status, created_at"),
                @Index(name = "idx_file_key", columnList = "fileKey")
        })
public class FileUploadTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "source_type", length = 20)
    private String sourceType; // AVATAR, CHAT, etc.

    @Column(name = "status", nullable = false, length = 20)
    private String status = Status.PENDING;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    public static class Status {
        public static final String PENDING = "PENDING";      // 待确认（已生成预签名URL或已上传到S3）
        public static final String COMPLETED = "COMPLETED";  // 已确认完成
        public static final String FAILED = "FAILED";        // 确认失败（可重试）
        public static final String ORPHAN = "ORPHAN";        // 孤儿文件（需清理）
    }
}