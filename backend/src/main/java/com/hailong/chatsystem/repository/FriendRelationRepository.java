package com.hailong.chatsystem.repository;

import com.hailong.chatsystem.model.entity.FriendRelation;
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
public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {

    // 查找用户的好友关系
    Optional<FriendRelation> findByUserUserIdAndFriendUserId(Long userId, Long friendId);

    // 查找用户的所有好友关系
    List<FriendRelation> findByUserUserIdAndStatus(Long userId, Integer status);

    // 检查是否为好友 - 修正方法名
    boolean existsByUserUserIdAndFriendUserIdAndStatus(Long userId, Long friendId, Integer status);

    // 更新好友备注
    @Modifying
    @Transactional
    @Query("UPDATE FriendRelation fr SET fr.remark = :remark WHERE fr.relationId = :relationId")
    void updateRemark(@Param("relationId") Long relationId,
                      @Param("remark") String remark);

    // 更新好友分组
    @Modifying
    @Transactional
    @Query("UPDATE FriendRelation fr SET fr.group.groupId = :groupId WHERE fr.relationId = :relationId")
    void updateGroup(@Param("relationId") Long relationId,
                     @Param("groupId") Long groupId);

    // 更新最后交互时间
    @Modifying
    @Transactional
    @Query("UPDATE FriendRelation fr SET fr.lastInteraction = :lastInteraction WHERE fr.relationId = :relationId")
    void updateLastInteraction(@Param("relationId") Long relationId,
                               @Param("lastInteraction") LocalDateTime lastInteraction);

    // 更新好友状态
    @Modifying
    @Transactional
    @Query("UPDATE FriendRelation fr SET fr.status = :status WHERE fr.relationId = :relationId")
    void updateStatus(@Param("relationId") Long relationId,
                      @Param("status") Integer status);

    // 查找用户最近交互的好友
    @Query("SELECT fr FROM FriendRelation fr WHERE fr.user.userId = :userId AND fr.status = 1 ORDER BY fr.lastInteraction DESC LIMIT :limit")
    List<FriendRelation> findRecentFriends(@Param("userId") Long userId,
                                           @Param("limit") int limit);
}