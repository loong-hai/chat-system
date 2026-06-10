package com.hailong.chatsystem.service.impl;

import com.hailong.chatsystem.config.storage.S3StorageProperties;
import com.hailong.chatsystem.model.dto.*;
import com.hailong.chatsystem.model.entity.FriendGroup;
import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.model.vo.UserVO;
import com.hailong.chatsystem.repository.FriendGroupRepository;
import com.hailong.chatsystem.repository.UserRepository;
import com.hailong.chatsystem.security.jwt.JwtTokenProvider;
import com.hailong.chatsystem.service.UserService;
import com.hailong.chatsystem.service.cache.UserSessionService;
import com.hailong.chatsystem.service.cache.UserStatusService;
import com.hailong.chatsystem.service.message.MessageDistributionService;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import com.hailong.chatsystem.utils.BeanCopyUtils;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    private final UserSessionService userSessionService; // 新增
    private final FriendGroupRepository friendGroupRepository;
    private  final MessageDistributionService messageDistributionService;
    private final WebSocketSessionService webSocketSessionService;

    private final UserStatusService userStatusService;
    private final S3StorageProperties s3Properties;


    /**
     * 动态生成默认头像的完整 URL
     * 优先使用 CDN 域名（如果配置），否则使用 S3 endpoint
     */
    private String buildDefaultAvatarUrl() {
        // 构建路径部分：bucket + 默认头像路径
        String path = String.format("%s/%s",
                s3Properties.getDefaultBucket(),
                s3Properties.getDefaultAvatarPath()
        );

        // 如果配置了 CDN 域名，使用 CDN 域名拼接
        if (s3Properties.getCdnDomain() != null && !s3Properties.getCdnDomain().isEmpty()) {
            // 确保 CDN 域名不以斜杠结尾，路径不以斜杠开头，避免双斜杠
            String cdn = s3Properties.getCdnDomain().replaceAll("/$", "");
            return cdn + "/" + path;
        } else {
            // 否则使用原来的 endpoint 拼接
            String endpoint = s3Properties.getEndpoint().replaceAll("/$", "");
            return endpoint + "/" + path;
        }
    }

    private static final ScheduledExecutorService offlinePushExecutor =
            Executors.newScheduledThreadPool(4);


    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtTokenProvider jwtTokenProvider,
                           UserDetailsService userDetailsService,
                           UserSessionService userSessionService,
                           FriendGroupRepository friendGroupRepository,
                           @Lazy MessageDistributionService messageDistributionService,
                           WebSocketSessionService webSocketSessionService,
                           UserStatusService userStatusService,
                           S3StorageProperties s3Properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.userSessionService = userSessionService;
        this.friendGroupRepository = friendGroupRepository;
        this.messageDistributionService = messageDistributionService;
        this.webSocketSessionService = webSocketSessionService;
        this.userStatusService = userStatusService;
        this.s3Properties = s3Properties;
    }

    @Override
    @Transactional
    public void updateUserAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        // 清除缓存，确保下次查询拿到新头像
        userSessionService.clearUserInfoCache(userId);
        log.info("用户头像已更新: userId={}, url={}", userId, avatarUrl);
    }

    // 修改getCurrentUser方法，直接使用SecurityContextHolder
    private User getCurrentUserEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("用户未登录");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Override
    @Transactional
    public UserVO register(RegisterDTO registerDTO) {
        // 验证用户名是否已存在
        if (userRepository.existsByUsername(registerDTO.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        // 验证邮箱是否已存在
        if (StringUtils.hasText(registerDTO.getEmail()) &&
                userRepository.existsByEmail(registerDTO.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 验证手机号是否已存在
        if (StringUtils.hasText(registerDTO.getPhone()) &&
                userRepository.existsByPhone(registerDTO.getPhone())) {
            throw new RuntimeException("手机号已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setNickname(registerDTO.getNickname());
        user.setSignature(registerDTO.getSignature());
        user.setAvatarUrl(buildDefaultAvatarUrl());

        // 保存用户
        User savedUser = userRepository.save(user);

        // 创建默认好友分组 - 新增
        createDefaultFriendGroups(savedUser);
        log.info("已为用户创建默认分组");

        // 转换VO并返回
        return convertToVO(savedUser);
    }

    private String getSafeAvatarUrl(String storedUrl) {
        // 如果用户头像URL失效（返回404），降级使用默认头像
        if (storedUrl == null || storedUrl.isEmpty()) {
            return buildDefaultAvatarUrl();
        }
        return storedUrl;
    }

    /**
     * 为用户创建默认好友分组
     */
    private void createDefaultFriendGroups(User user) {
        try {
            log.info("开始为用户 {} 创建默认好友分组，用户ID: {}", user.getUsername(), user.getUserId());

            List<DefaultGroup> defaultGroups = Arrays.asList(
                    new DefaultGroup(FriendGroup.DefaultGroups.ALL_FRIENDS, 0, true, FriendGroup.Colors.BLUE),
                    new DefaultGroup(FriendGroup.DefaultGroups.CLOSE_FRIENDS, 1, false, FriendGroup.Colors.RED),
                    new DefaultGroup(FriendGroup.DefaultGroups.FAMILY, 2, false, FriendGroup.Colors.GREEN),
                    new DefaultGroup(FriendGroup.DefaultGroups.COLLEAGUES, 3, false, FriendGroup.Colors.ORANGE),
                    new DefaultGroup(FriendGroup.DefaultGroups.CLASSMATES, 4, false, FriendGroup.Colors.PURPLE)
            );

            for (DefaultGroup defaultGroup : defaultGroups) {
                FriendGroup group = new FriendGroup();
                group.setUser(user);
                group.setGroupName(defaultGroup.name);
                group.setSortOrder(defaultGroup.sortOrder);
                group.setIsDefault(defaultGroup.isDefault);
                group.setColor(defaultGroup.color);
                group.setIcon(getDefaultIcon(defaultGroup.name));
                group.setIsVisible(true);

                friendGroupRepository.save(group);
            }

            log.info("为用户 {} 创建了默认好友分组完成", user.getUsername());
        } catch (Exception e) {
            log.error("创建默认好友分组失败，用户ID: {}, 错误: {}", user.getUserId(), e.getMessage(), e);
            // 关键修复：抛出运行时异常，让事务回滚，避免用户处于不一致状态
            throw new RuntimeException("创建默认好友分组失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据分组名获取默认图标
     */
    private String getDefaultIcon(String groupName) {
        return switch (groupName) {
            case FriendGroup.DefaultGroups.ALL_FRIENDS -> "all_inclusive";
            case FriendGroup.DefaultGroups.CLOSE_FRIENDS -> "favorite";
            case FriendGroup.DefaultGroups.FAMILY -> "family_restroom";
            case FriendGroup.DefaultGroups.COLLEAGUES -> "business_center";
            case FriendGroup.DefaultGroups.CLASSMATES -> "school";
            default -> "group";
        };
    }

    /**
     * 默认分组配置内部类
     */
    private static class DefaultGroup {
        String name;
        int sortOrder;
        boolean isDefault;
        String color;

        DefaultGroup(String name, int sortOrder, boolean isDefault, String color) {
            this.name = name;
            this.sortOrder = sortOrder;
            this.isDefault = isDefault;
            this.color = color;
        }
    }



    @Override
    @Transactional
    public TokenDTO login(LoginDTO loginDTO, String ip) {
        // 1. 查找用户
        User user = userRepository.findByIdentifier(loginDTO.getIdentifier())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 2. 检查用户状态
        if (user.isFrozen()) {
            throw new RuntimeException("账号已被冻结");
        }
        if (user.isDeregistered()) {
            throw new RuntimeException("账号已注销");
        }

        // 3. 验证密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 4. Spring Security 认证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        loginDTO.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 5. 【修改】只更新 last_login_time，不再设置 online_status
        userRepository.updateLoginInfoMinimal(user.getUserId(), LocalDateTime.now(), ip);

        // 6. 生成 JWT Token
        String token = jwtTokenProvider.generateToken(user);
        long expiresIn = jwtTokenProvider.getRemainingExpiration(token);

        // 7. 【修复】定义 deviceId（生成逻辑）
        String deviceId = ip + "_" + System.currentTimeMillis();

        // 8. 创建 HTTP 会话（保留 session 但不再标记"在线状态"）
        String sessionId = userSessionService.createSession(
                user.getUserId(),
                deviceId,
                ip
        );

        // 正确构造 TokenDTO 变量
        TokenDTO tokenDTO = new TokenDTO(
                token,
                expiresIn,
                user.getUserId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl()
        );
        tokenDTO.setSessionId(sessionId);
        tokenDTO.setDeviceId(deviceId);

        log.info("用户登录成功: userId={}, username={}, sessionId={}",
                user.getUserId(), user.getUsername(), sessionId);

        // 10. 触发离线推送
        scheduleOfflinePush(user.getUserId());

        return tokenDTO;
    }

    // 辅助方法，使用延迟执行确保事务已提交
    private void scheduleOfflinePush(Long userId) {
        offlinePushExecutor.schedule(() -> {
            try {
                messageDistributionService.pushOfflineMessagesOnLogin(userId);
            } catch (Exception e) {
                log.error("触发离线消息推送失败", e);
            }
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * 应用关闭时优雅关闭线程池
     */
    @PreDestroy
    public void destroy() {
        log.info("正在关闭UserService线程池...");
        if (offlinePushExecutor != null && !offlinePushExecutor.isShutdown()) {
            offlinePushExecutor.shutdown();
            try {
                if (!offlinePushExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    offlinePushExecutor.shutdownNow();
                    if (!offlinePushExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                        log.error("线程池未能正常关闭");
                    }
                }
            } catch (InterruptedException e) {
                offlinePushExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("UserService线程池已关闭");
    }



    @Override
    public UserVO getCurrentUser() {
        User user = getCurrentUserEntity();
        return convertToVO(user);
    }

    @Override
    @Transactional
    public UserVO updateUser(UserUpdateDTO updateDTO) {
        User user = getCurrentUserEntity();

        // 更新昵称
        if (StringUtils.hasText(updateDTO.getNickname())) {
            user.setNickname(updateDTO.getNickname());
        }

        // 更新头像
        if (StringUtils.hasText(updateDTO.getAvatarUrl())) {
            user.setAvatarUrl(updateDTO.getAvatarUrl());
        }

        // 更新性别
        if (StringUtils.hasText(updateDTO.getGender())) {
            user.setGender(Integer.parseInt(updateDTO.getGender()));
        }

        // 更新生日
        if (updateDTO.getBirthday() != null) {
            user.setBirthday(updateDTO.getBirthday());
        }

        // 更新个性签名
        if (updateDTO.getSignature() != null) {
            user.setSignature(updateDTO.getSignature());
        }

        // 更新邮箱（需要验证）
        if (StringUtils.hasText(updateDTO.getEmail()) &&
                !updateDTO.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(updateDTO.getEmail())) {
                throw new RuntimeException("邮箱已被注册");
            }
            user.setEmail(updateDTO.getEmail());
            user.setIsVerified(false);
        }

        // 更新手机号（需要验证）
        if (StringUtils.hasText(updateDTO.getPhone()) &&
                !updateDTO.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(updateDTO.getPhone())) {
                throw new RuntimeException("手机号已被注册");
            }
            user.setPhone(updateDTO.getPhone());
            user.setIsVerified(false);
        }

        User updatedUser = userRepository.save(user);

        // 更新缓存
        userSessionService.updateUserInfoCache(updatedUser);

        return convertToVO(updatedUser);
    }

    @Override
    @Transactional
    public void changePassword(PasswordChangeDTO passwordChangeDTO) {
        User user = getCurrentUserEntity();

        // 验证旧密码
        if (!passwordEncoder.matches(passwordChangeDTO.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        // 验证新密码和确认密码
        if (!passwordChangeDTO.getNewPassword().equals(passwordChangeDTO.getConfirmPassword())) {
            throw new RuntimeException("新密码和确认密码不一致");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(passwordChangeDTO.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                // 从SecurityContext获取sessionId（需要扩展存储）
                // 这里简化处理：踢出用户所有会话
                userSessionService.forceLogout(user.getUserId(), null);
            }
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    public Map<String, Object> getCurrentUserOnlineStatus() {
        User user = getCurrentUserEntity();
        Map<String, Object> status = new HashMap<>();

        // 改为 WebSocket 状态
        boolean isOnline = webSocketSessionService.isUserOnline(user.getUserId());
        int deviceCount = webSocketSessionService.getUserOnlineDeviceCount(user.getUserId());

        status.put("isOnline", isOnline);
        status.put("deviceCount", deviceCount);
        status.put("lastLoginTime", user.getLastLoginTime());  // 保留最后登录时间
        status.put("userId", user.getUserId());

        return status;
    }

    @Override
    public TokenDTO refreshToken(String refreshToken) {
        // 验证refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("刷新令牌无效或已过期");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // 生成新的access token
        String newToken = jwtTokenProvider.generateToken(userDetails);
        long expiresIn = jwtTokenProvider.getRemainingExpiration(newToken);

        // 获取用户信息
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        return new TokenDTO(
                newToken,
                expiresIn,
                user.getUserId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl()
        );
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        return convertToVO(user);
    }

    @Override
    public UserVO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        return convertToVO(user);
    }

    @Override
    public Page<UserVO> queryUsers(UserQueryDTO queryDTO) {
        // 验证必须提供查询条件
        if (!StringUtils.hasText(queryDTO.getKeyword())) {
            throw new RuntimeException("必须提供查询关键词");
        }

        // 限制查询数量
        if (queryDTO.getSize() > 50) {
            queryDTO.setSize(50);  // 每页最多50条
        }

        // 修改查询逻辑，只允许通过关键词搜索
        Specification<User> spec = new Specification<User>() {
            @Override
            public Predicate toPredicate(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();

                String keyword = queryDTO.getKeyword();
                if (StringUtils.hasText(keyword)) {
                    String likeKeyword = "%" + keyword + "%";
                    predicates.add(cb.or(
                            cb.like(root.get("username"), likeKeyword),
                            cb.like(root.get("nickname"), likeKeyword),
                            cb.like(root.get("email"), likeKeyword)
                    ));
                }

                // 只能查询正常状态的用户
                predicates.add(cb.equal(root.get("userStatus"), User.UserStatus.NORMAL));

                return cb.and(predicates.toArray(new Predicate[0]));
            }
        };

        Pageable pageable = PageRequest.of(
                queryDTO.getPage() - 1,
                queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "registerTime")
        );

        Page<User> userPage = userRepository.findAll(spec, pageable);
        return userPage.map(this::convertToVO);
    }


    @Override
    public List<UserVO> searchUsers(String keyword) {
        // 使用Specification进行搜索
        Specification<User> spec = new Specification<User>() {
            @Override
            public Predicate toPredicate(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();

                if (StringUtils.hasText(keyword)) {
                    String likeKeyword = "%" + keyword + "%";
                    predicates.add(cb.or(
                            cb.like(root.get("username"), likeKeyword),
                            cb.like(root.get("nickname"), likeKeyword),
                            cb.like(root.get("email"), likeKeyword),
                            cb.like(root.get("phone"), likeKeyword)
                    ));
                }

                // 只查询正常状态的用户
                predicates.add(cb.equal(root.get("userStatus"), User.UserStatus.NORMAL));

                return cb.and(predicates.toArray(new Predicate[0]));
            }
        };

        List<User> users = userRepository.findAll(spec);
        return users.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }


    @Override
    @Transactional
    public void freezeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setUserStatus(User.UserStatus.FROZEN);
        // 【移除】user.setOnlineStatus(User.OnlineStatus.OFFLINE); // 不再需要
        userRepository.save(user);

        // 强制下线（清理 WebSocket + HTTP 会话）
        userSessionService.forceLogout(userId, null);
        webSocketSessionService.disconnectUser(userId);
        userSessionService.clearUserInfoCache(userId);

        log.info("用户已冻结: userId={}", userId);
    }

    @Override
    @Transactional
    public void unfreezeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setUserStatus(User.UserStatus.NORMAL);
        // 【移除】user.setOnlineStatus(User.OnlineStatus.OFFLINE); // 不再需要
        userRepository.save(user);

        userSessionService.clearUserInfoCache(userId);
        log.info("用户已解冻: userId={}", userId);
    }

    @Override
    @Transactional
    public void deregisterUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setUserStatus(User.UserStatus.DEREGISTERED);
        user.setDeregisterTime(LocalDateTime.now());
        userRepository.save(user);

        // 清除所有缓存
        userSessionService.forceLogout(userId, null);
        userSessionService.clearUserInfoCache(userId);
    }

    // 辅助方法
    private UserVO convertToVO(User user) {
        UserVO vo = BeanCopyUtils.copyProperties(user, UserVO.class);
        vo.setAvatarUrl(getSafeAvatarUrl(user.getAvatarUrl()));
        vo.setUserStatusText(getUserStatusText(user.getUserStatus()));
        vo.setGenderText(getGenderText(user.getGender()));

        Long userId = user.getUserId();

        // 实时获取物理连接状态（WebSocket）
        boolean isPhysicallyOnline = webSocketSessionService.isUserOnline(userId);
        vo.setIsOnline(isPhysicallyOnline);

        // 实时获取业务状态（Redis：online/busy/invisible）
        String bizStatus = userStatusService.getUserStatus(userId);

        // 如果Redis没有状态，根据物理连接推断（兼容旧数据）
        if (bizStatus == null || bizStatus.isEmpty()) {
            bizStatus = isPhysicallyOnline ? "online" : "offline";
        }

        // 转换为前端需要的格式
        Integer statusCode = convertStatusToCode(bizStatus, isPhysicallyOnline);
        vo.setOnlineStatus(statusCode);
        vo.setOnlineStatusText(convertStatusToText(bizStatus, isPhysicallyOnline));

        return vo;
    }


    /**
     * 业务状态转数字码（兼容前端）
     */
    private Integer convertStatusToCode(String bizStatus, boolean isOnline) {
        if (!isOnline) return 0; // 离线
        return switch (bizStatus) {
            case "online" -> 1;
            case "busy" -> 2;
            case "invisible" -> 3;
            default -> 1;
        };
    }

    /**
     * 生成显示文本
     */
    private String convertStatusToText(String bizStatus, boolean isOnline) {
        if (!isOnline) return "离线";
        return switch (bizStatus) {
            case "online" -> "在线";
            case "busy" -> "忙碌";
            case "invisible" -> "隐身";
            default -> "在线";
        };
    }

    private String getUserStatusText(Integer status) {
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "冻结";
            case 3 -> "注销";
            default -> "未知";
        };
    }

    private String getGenderText(Integer gender) {
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }
}