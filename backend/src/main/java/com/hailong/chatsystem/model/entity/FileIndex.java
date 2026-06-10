package com.hailong.chatsystem.model.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 全局文件索引表 - 用于秒传去重系统
 * 记录文件Hash到S3 Key的映射，实现"文件级去重"
 *
 * 扩展建议：
 * 1. 可添加ref_count字段记录引用计数，实现垃圾回收
 * 2. 可添加file_size字段用于存储配额统计
 * 3. 可添加is_public字段区分公开/私有文件
 */
@Getter
@Setter
@Entity
@Table(name = "file_index",
        indexes = {
                @Index(name = "idx_file_hash", columnList = "fileHash", unique = true),
                @Index(name = "idx_s3_key", columnList = "s3_key"),  // 改为 s3_key
                @Index(name = "idx_created_at", columnList = "created_at")  // 改为 created_at
        })
public class FileIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文件SHA-256哈希值 (唯一标识文件内容)
     * 用于秒传校验：用户上传前计算Hash，查询此表是否存在
     */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    /**
     * S3存储Key (完整路径，如 users/100/chat/abc/def123.jpg)
     * 多个用户引用相同文件时，此Key复用
     */
    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    /**
     * 原始文件名 (仅用于展示，不参与存储路径)
     */
    @Column(name = "original_name", length = 255)
    private String originalName;

    /**
     * 文件大小(字节)
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * MIME类型
     */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /**
     * 引用计数 (当前有多少用户在使用此文件)
     * 用于后续垃圾回收：当refCount=0时可安全删除S3对象
     */
    @Column(name = "ref_count")
    private Integer refCount = 1;

    /**
     * 是否为公开文件 (聊天图片通常公开，头像看业务需求)
     * true: 通过URL直接访问
     * false: 需要预签名URL访问
     */
    @Column(name = "is_public")
    private Boolean isPublic = true;

    /**
     * 上传来源 (用于追踪：AVATAR/CHAT/OTHER)
     */
    @Column(name = "source_type", length = 20)
    private String sourceType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 增加引用计数
     */
    public void incrementRef() {
        this.refCount = (this.refCount == null) ? 1 : this.refCount + 1;
    }

    /**
     * 减少引用计数
     */
    public void decrementRef() {
        if (this.refCount != null && this.refCount > 0) {
            this.refCount--;
        }
    }
}