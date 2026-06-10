package com.hailong.chatsystem.repository;

import com.hailong.chatsystem.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // 通过用户名查找
    Optional<User> findByUsername(String username);

    // 通过邮箱查找
    Optional<User> findByEmail(String email);

    // 通过手机号查找
    Optional<User> findByPhone(String phone);

    // 通过用户名或邮箱或手机号查找
    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier OR u.phone = :identifier")
    Optional<User> findByIdentifier(@Param("identifier") String identifier);

    /**
     * 更新登录信息
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastLoginTime = :loginTime, u.lastLoginIp = :ip WHERE u.userId = :userId")
    void updateLoginInfoMinimal(@Param("userId") Long userId,
                                @Param("loginTime") LocalDateTime loginTime,
                                @Param("ip") String ip);

    // 检查用户名是否存在
    boolean existsByUsername(String username);

    // 检查邮箱是否存在
    boolean existsByEmail(String email);

    // 检查手机号是否存在
    boolean existsByPhone(String phone);


    // 根据状态查找用户
    List<User> findByUserStatus(Integer userStatus);

    // 批量更新用户状态
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.userStatus = :status, u.deregisterTime = :deregisterTime WHERE u.userId IN :userIds")
    void updateUserStatus(@Param("userIds") List<Long> userIds,
                          @Param("status") Integer status,
                          @Param("deregisterTime") LocalDateTime deregisterTime);
}