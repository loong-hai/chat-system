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
@Table(name = "friend_relation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "friend_id"}),
        indexes = {
                @Index(name = "idx_user_group", columnList = "user_id, group_id"),
                @Index(name = "idx_last_interaction", columnList = "user_id, last_interaction DESC")
        })
public class FriendRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_id")
    private Long relationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    /**
     * 好友关系状态（现在只有有效状态）
     * 1: 正常好友
     * 2: 已拉黑（单向拉黑）
     * 3: 已删除（软删除）
     */
    @Column(name = "status", nullable = false)
    private Integer status = Status.ACTIVE;

    @Column(name = "remark", length = 50)
    private String remark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private FriendGroup group;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_interaction")
    private LocalDateTime lastInteraction;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Column(name = "is_muted", nullable = false)
    private Boolean isMuted = false;

    @Column(name = "intimacy_level", nullable = false)
    private Integer intimacyLevel = 0;

    // === 业务常量 ===
    public static class Status {
        public static final Integer ACTIVE = 1;     // 正常好友
        public static final Integer BLOCKED = 2;    // 已拉黑
        public static final Integer DELETED = 3;    // 已删除
    }

    public static class IntimacyLevel {
        public static final Integer NORMAL = 0;
        public static final Integer CLOSE = 1;
        public static final Integer STARRED = 2;
    }

    // === 业务方法 ===
    @Transient
    public boolean isValidFriend() {
        return Status.ACTIVE.equals(this.status);
    }

    @Transient
    public boolean isBlocked() {
        return Status.BLOCKED.equals(this.status);
    }

    @Transient
    public boolean isDeleted() {
        return Status.DELETED.equals(this.status);
    }

    @Transient
    public String getDisplayName() {
        if (remark != null && !remark.trim().isEmpty()) {
            return remark;
        }
        return (friend != null && friend.getNickname() != null) ? friend.getNickname() : "";
    }

    public void updateInteraction() {
        this.lastInteraction = LocalDateTime.now();
    }

    /**
     * 获取关系来源（如果有）
     */
    @Transient
    public String getSource() {
        // 可以通过关联的 FriendRequest 获取来源
        // 这里只是一个示例
        return "好友申请";
    }
}