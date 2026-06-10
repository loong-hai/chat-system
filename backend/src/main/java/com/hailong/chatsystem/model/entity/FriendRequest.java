package com.hailong.chatsystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "friend_request",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sender_id", "receiver_id"}),
        indexes = {
                @Index(name = "idx_receiver_status", columnList = "receiver_id, status, created_at DESC"),
                @Index(name = "idx_sender_status", columnList = "sender_id, status"),
                @Index(name = "idx_created_at", columnList = "created_at")
        })
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    /**
     * 申请发送者
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /**
     * 申请接收者
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    /**
     * 申请状态
     */
    @Column(name = "status", nullable = false)
    private Integer status = Status.PENDING;

    /**
     * 申请理由/备注
     */
    @Column(name = "message", length = 200)
    private String message;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 处理时间（同意/拒绝的时间）
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * 过期时间（可选，默认7天）
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * 来源（如：扫一扫、群聊、手机号搜索等）
     */
    @Column(name = "source", length = 50)
    private String source;

    /**
     * 拒绝理由（可选）
     */
    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    // === 业务常量 ===
    public static class Status {
        public static final Integer PENDING = 0;    // 待处理
        public static final Integer ACCEPTED = 1;   // 已同意
        public static final Integer REJECTED = 2;   // 已拒绝
        public static final Integer EXPIRED = 3;    // 已过期
        public static final Integer CANCELLED = 4;  // 已取消
    }

    public static class Source {
        public static final String SEARCH = "search";        // 搜索添加
        public static final String QR_CODE = "qr_code";      // 扫一扫
        public static final String GROUP = "group";          // 群聊
        public static final String CONTACT = "contact";      // 手机通讯录
        public static final String RECOMMEND = "recommend";  // 好友推荐
    }

    // === 业务方法 ===
    @Transient
    public boolean isPending() {
        return Status.PENDING.equals(status);
    }

    @Transient
    public boolean isAccepted() {
        return Status.ACCEPTED.equals(status);
    }

    @Transient
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 检查是否可以处理（未过期且未处理）
     */
    @Transient
    public boolean canProcess() {
        return isPending() && !isExpired();
    }

    /**
     * 同意申请
     */
    public void accept() {
        this.status = Status.ACCEPTED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 拒绝申请
     */
    public void reject(String reason) {
        this.status = Status.REJECTED;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = reason;
    }

    /**
     * 取消申请
     */
    public void cancel() {
        if (isPending()) {
            this.status = Status.CANCELLED;
            this.processedAt = LocalDateTime.now();
        }
    }

    /**
     * 获取请求的有效期（天）
     */
    @Transient
    public long getRemainingDays() {
        if (expiresAt == null) {
            return 7; // 默认7天
        }
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).toDays();
    }
}