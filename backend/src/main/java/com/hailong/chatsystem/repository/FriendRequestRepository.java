package com.hailong.chatsystem.repository;

import com.hailong.chatsystem.model.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    // 查询发送的申请
    List<FriendRequest> findBySenderUserId(Long senderId);
    List<FriendRequest> findBySenderUserIdAndStatus(Long senderId, Integer status);

    // 查询收到的申请
    List<FriendRequest> findByReceiverUserId(Long receiverId);
    List<FriendRequest> findByReceiverUserIdAndStatus(Long receiverId, Integer status);

    // 查找特定的好友请求 - 修正方法名
    Optional<FriendRequest> findBySenderUserIdAndReceiverUserId(Long senderId, Long receiverId);

    // 查找待处理的好友请求 - 修正方法名
    List<FriendRequest> findByReceiverUserIdAndStatusOrderByCreatedAtDesc(Long receiverId, Integer status);



    // 更新请求状态
    @Modifying
    @Transactional
    @Query("UPDATE FriendRequest fr SET fr.status = :status, fr.processedAt = :processedAt WHERE fr.requestId = :requestId")
    void updateStatus(@Param("requestId") Long requestId,
                      @Param("status") Integer status,
                      @Param("processedAt") LocalDateTime processedAt);

    // 检查是否存在待处理的请求 - 修正方法名
    boolean existsBySenderUserIdAndReceiverUserIdAndStatus(Long senderId, Long receiverId, Integer status);

    // 查找过期的请求
    @Query("SELECT fr FROM FriendRequest fr WHERE fr.status = 0 AND fr.expiresAt < :now")
    List<FriendRequest> findExpiredRequests(@Param("now") LocalDateTime now);

    // 批量更新过期请求状态
    @Modifying
    @Transactional
    @Query("UPDATE FriendRequest fr SET fr.status = 3, fr.processedAt = :now WHERE fr.requestId IN :requestIds")
    void markAsExpired(@Param("requestIds") List<Long> requestIds,
                       @Param("now") LocalDateTime now);
}