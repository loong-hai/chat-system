package com.hailong.chatsystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

@Getter
@Setter
@ToString
@Entity
@Table(name = "user",
        indexes = {
                @Index(name = "idx_username", columnList = "username"),
                @Index(name = "idx_email", columnList = "email"),
                @Index(name = "idx_phone", columnList = "phone"),
                @Index(name = "idx_status", columnList = "user_status"),
                @Index(name = "idx_register_time", columnList = "register_time")
        })
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "email", length = 100, unique = true)
    private String email;

    @Column(name = "phone", length = 20, unique = true)
    private String phone;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "gender")
    private Integer gender = Gender.UNKNOWN;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "signature", length = 200)
    private String signature;

    @Column(name = "user_status", nullable = false)
    private Integer userStatus = UserStatus.NORMAL;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;

    @CreationTimestamp
    @Column(name = "register_time", nullable = false, updatable = false)
    private LocalDateTime registerTime;

    @Column(name = "deregister_time")
    private LocalDateTime deregisterTime;

    /**
     * 【已废弃】最后活动时间不再维护，使用 last_login_time 或 WebSocket 心跳
     */
    @Deprecated
    @Column(name = "last_activity_time")
    private LocalDateTime lastActivityTime;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "verification_code", length = 100)
    private String verificationCode;

    @Column(name = "verification_expire")
    private LocalDateTime verificationExpire;

    // Spring Security 相关字段
    @Column(name = "account_non_expired")
    private Boolean accountNonExpired = true;

    @Column(name = "account_non_locked")
    private Boolean accountNonLocked = true;

    @Column(name = "credentials_non_expired")
    private Boolean credentialsNonExpired = true;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 枚举类定义
    public static class Gender {
        public static final Integer UNKNOWN = 0;
        public static final Integer MALE = 1;
        public static final Integer FEMALE = 2;
    }

    public static class UserStatus {
        public static final Integer NORMAL = 1;     // 正常
        public static final Integer FROZEN = 2;     // 冻结
        public static final Integer DEREGISTERED = 3; // 注销
    }

    // Spring Security UserDetails 接口实现
    @Override
    @Transient
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    @Transient
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    @Transient
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    @Transient
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    @Transient
    public boolean isEnabled() {
        return enabled && userStatus.equals(UserStatus.NORMAL);
    }

    // 业务方法
    @Transient
    public String getDisplayName() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname;
        }
        return username;
    }

    @Transient
    public boolean isFrozen() {
        return userStatus.equals(UserStatus.FROZEN);
    }

    @Transient
    public boolean isDeregistered() {
        return userStatus.equals(UserStatus.DEREGISTERED);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (registerTime == null) {
            registerTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}