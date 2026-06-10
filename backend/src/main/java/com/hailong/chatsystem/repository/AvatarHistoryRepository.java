package com.hailong.chatsystem.repository;

import com.hailong.chatsystem.model.entity.AvatarHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvatarHistoryRepository extends JpaRepository<AvatarHistory, Long> {

    /**
     * 查询用户当前头像
     */
    Optional<AvatarHistory> findByUserIdAndIsCurrentTrue(Long userId);

    /**
     * 查询用户头像历史（按时间倒序）
     */
    List<AvatarHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 查询用户所有当前有效的头像历史
     */
    List<AvatarHistory> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    /**
     * 取消用户所有头像的当前状态（用于设置新头像时）
     */
    @Modifying
    @Transactional
    @Query("UPDATE AvatarHistory ah SET ah.isCurrent = false WHERE ah.userId = :userId")
    void clearCurrentAvatar(@Param("userId") Long userId);

    /**
     * 根据S3 Key查找记录（用于删除时清理）
     */
    Optional<AvatarHistory> findByS3Key(String s3Key);
}