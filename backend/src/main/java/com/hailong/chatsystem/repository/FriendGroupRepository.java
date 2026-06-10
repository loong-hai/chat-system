package com.hailong.chatsystem.repository;

import com.hailong.chatsystem.model.entity.FriendGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendGroupRepository extends JpaRepository<FriendGroup, Long> {

    // 查找用户的所有分组 - 修正方法名
    List<FriendGroup> findByUserUserIdOrderBySortOrderAsc(Long userId);

    // 查找用户的默认分组 - 修正方法名
    Optional<FriendGroup> findByUserUserIdAndIsDefaultTrue(Long userId);

    // 查找用户的分组（通过名称）- 修正方法名
    Optional<FriendGroup> findByUserUserIdAndGroupName(Long userId, String groupName);

    // 检查分组是否存在 - 修正方法名
    boolean existsByUserUserIdAndGroupName(Long userId, String groupName);



    // 更新分组排序
    @Modifying
    @Transactional
    @Query("UPDATE FriendGroup fg SET fg.sortOrder = :sortOrder WHERE fg.groupId = :groupId")
    void updateSortOrder(@Param("groupId") Long groupId,
                         @Param("sortOrder") Integer sortOrder);

    // 批量更新排序
    @Modifying
    @Transactional
    @Query("UPDATE FriendGroup fg SET fg.sortOrder = fg.sortOrder + 1 WHERE fg.user.userId = :userId AND fg.sortOrder >= :sortOrder")
    void incrementSortOrders(@Param("userId") Long userId,
                             @Param("sortOrder") Integer sortOrder);
}