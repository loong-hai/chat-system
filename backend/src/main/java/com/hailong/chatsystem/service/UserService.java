package com.hailong.chatsystem.service;

import com.hailong.chatsystem.model.dto.*;
import com.hailong.chatsystem.model.vo.UserVO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface UserService {

    /**
     * 更新用户头像（内部调用，不对外暴露Controller接口）
     * @param userId 用户ID
     * @param avatarUrl S3头像地址
     */
    void updateUserAvatar(Long userId, String avatarUrl);

    // 用户注册
    UserVO register(RegisterDTO registerDTO);

    // 用户登录
    TokenDTO login(LoginDTO loginDTO, String ip);

    // 获取当前用户信息
    UserVO getCurrentUser();

    // 更新用户信息
    UserVO updateUser(UserUpdateDTO updateDTO);

    // 修改密码
    void changePassword(PasswordChangeDTO passwordChangeDTO);

    // 退出登录
    void logout();

    // 刷新token
    TokenDTO refreshToken(String refreshToken);

    // 根据ID获取用户信息
    UserVO getUserById(Long userId);

    // 根据用户名获取用户信息
    UserVO getUserByUsername(String username);

    // 查询用户列表
    Page<UserVO> queryUsers(UserQueryDTO queryDTO);

    Map<String, Object> getCurrentUserOnlineStatus();

    // 搜索用户
    List<UserVO> searchUsers(String keyword);

    // 冻结用户
    void freezeUser(Long userId);

    // 解冻用户
    void unfreezeUser(Long userId);

    // 注销用户
    void deregisterUser(Long userId);
}