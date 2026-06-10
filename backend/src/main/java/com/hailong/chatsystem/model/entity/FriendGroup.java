package com.hailong.chatsystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 好友分组实体类
 */
@Getter
@Setter
@Entity
@Table(name = "friend_group",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "group_name"}))
public class FriendGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "color", length = 10)
    private String color = "#1890FF";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "icon", length = 255)
    private String icon;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible = true;

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    private Set<FriendRelation> friends = new HashSet<>();

    // === 业务常量定义 ===
    public static class DefaultGroups {
        public static final String ALL_FRIENDS = "所有好友";
        public static final String CLOSE_FRIENDS = "亲密好友";
        public static final String FAMILY = "家人";
        public static final String COLLEAGUES = "同事";
        public static final String CLASSMATES = "同学";
    }

    public static class Colors {
        public static final String BLUE = "#1890FF";
        public static final String RED = "#FF4D4F";
        public static final String GREEN = "#52C41A";
        public static final String ORANGE = "#FA8C16";
        public static final String PURPLE = "#722ED1";
    }

    // === 业务方法 ===

    /**
     * 获取分组中的好友数量
     */
    @Transient
    public int getFriendCount() {
        if (friends == null) {
            return 0;
        }
        // 使用 FriendRelation 的 isValidFriend 方法代替直接比较状态
        return (int) friends.stream()
                .filter(FriendRelation::isValidFriend)
                .count();
    }

    /**
     * 检查是否可以删除
     */
    @Transient
    public boolean canDelete() {
        // 默认分组不能删除
        if (Boolean.TRUE.equals(isDefault)) {
            return false;
        }

        // 分组非空时不能直接删除
        if (friends != null && !friends.isEmpty()) {
            // 使用 FriendRelation 的 isValidFriend 方法
            boolean hasValidFriends = friends.stream()
                    .anyMatch(FriendRelation::isValidFriend);
            return !hasValidFriends;
        }

        return true;
    }

    /**
     * 添加好友到分组
     */
    public void addFriend(FriendRelation friendRelation) {
        if (friends == null) {
            friends = new HashSet<>();
        }
        friends.add(friendRelation);
        friendRelation.setGroup(this);
    }

    /**
     * 从分组移除好友
     */
    public void removeFriend(FriendRelation friendRelation) {
        if (friends != null) {
            friends.remove(friendRelation);
            friendRelation.setGroup(null);
        }
    }



    /**
     * 获取显示颜色
     */
    @Transient
    public String getDisplayColor() {
        return (color != null && !color.trim().isEmpty()) ? color : Colors.BLUE;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}