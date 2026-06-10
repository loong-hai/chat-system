package com.hailong.chatsystem.service.impl;

import com.hailong.chatsystem.model.dto.*;
import com.hailong.chatsystem.model.entity.FriendGroup;
import com.hailong.chatsystem.model.entity.FriendRelation;
import com.hailong.chatsystem.model.entity.FriendRequest;
import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.model.vo.FriendGroupVO;
import com.hailong.chatsystem.model.vo.FriendRequestVO;
import com.hailong.chatsystem.model.vo.FriendVO;
import com.hailong.chatsystem.repository.FriendGroupRepository;
import com.hailong.chatsystem.repository.FriendRelationRepository;
import com.hailong.chatsystem.repository.FriendRequestRepository;
import com.hailong.chatsystem.repository.UserRepository;
import com.hailong.chatsystem.service.FriendService;
import com.hailong.chatsystem.service.cache.UserSessionService;
import com.hailong.chatsystem.service.cache.UserStatusService;
import com.hailong.chatsystem.service.websocket.WebSocketSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FriendServiceImpl implements FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final FriendGroupRepository friendGroupRepository;
    private final UserRepository userRepository;
    private final WebSocketSessionService webSocketSessionService;
    private final UserStatusService userStatusService;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public FriendServiceImpl(FriendRequestRepository friendRequestRepository,
                             FriendRelationRepository friendRelationRepository,
                             FriendGroupRepository friendGroupRepository,
                             UserRepository userRepository,
                             UserSessionService userSessionService,
                             WebSocketSessionService webSocketSessionService,
                             UserStatusService userStatusService, StringRedisTemplate stringRedisTemplate) {
        this.friendRequestRepository = friendRequestRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.friendGroupRepository = friendGroupRepository;
        this.userRepository = userRepository;
        this.webSocketSessionService = webSocketSessionService;
        this.userStatusService = userStatusService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private User getCurrentUser() {
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
    public void sendFriendRequest(FriendRequestDTO requestDTO) {
        User currentUser = getCurrentUser();
        User receiver = userRepository.findById(requestDTO.getReceiverId())
                .orElseThrow(() -> new RuntimeException("接收用户不存在"));

        if (friendRelationRepository.existsByUserUserIdAndFriendUserIdAndStatus(
                currentUser.getUserId(), receiver.getUserId(), FriendRelation.Status.ACTIVE)) {
            throw new RuntimeException("已经是好友");
        }

        if (friendRequestRepository.existsBySenderUserIdAndReceiverUserIdAndStatus(
                currentUser.getUserId(), receiver.getUserId(), FriendRequest.Status.PENDING)) {
            throw new RuntimeException("已经发送过好友申请");
        }

        FriendRequest friendRequest = new FriendRequest();
        friendRequest.setSender(currentUser);
        friendRequest.setReceiver(receiver);
        friendRequest.setMessage(requestDTO.getMessage());
        friendRequest.setSource(requestDTO.getSource());
        friendRequest.setExpiresAt(LocalDateTime.now().plusDays(7));

        friendRequestRepository.save(friendRequest);
    }

    @Override
    @Transactional
    public void handleFriendRequest(FriendRequestHandleDTO handleDTO) {
        FriendRequest friendRequest = friendRequestRepository.findById(handleDTO.getRequestId())
                .orElseThrow(() -> new RuntimeException("好友申请不存在"));

        User currentUser = getCurrentUser();

        if (!friendRequest.getReceiver().getUserId().equals(currentUser.getUserId())) {
            throw new RuntimeException("无权处理此申请");
        }

        if (!friendRequest.canProcess()) {
            throw new RuntimeException("申请已过期或已处理");
        }

        if (handleDTO.getAccept()) {
            friendRequest.accept();
            createFriendship(friendRequest.getSender(), friendRequest.getReceiver());
        } else {
            friendRequest.reject(handleDTO.getRejectReason());
        }

        friendRequestRepository.save(friendRequest);
    }

    @Override
    public List<FriendRequestVO> getFriendRequests(Integer status, String type) {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();
        String queryType = (type == null) ? "received" : type;

        List<FriendRequest> requests;
        if ("sent".equals(queryType)) {
            if (status == null) {
                requests = friendRequestRepository.findBySenderUserId(currentUserId);
            } else {
                requests = friendRequestRepository.findBySenderUserIdAndStatus(currentUserId, status);
            }
        } else {
            if (status == null) {
                requests = friendRequestRepository.findByReceiverUserId(currentUserId);
            } else {
                requests = friendRequestRepository.findByReceiverUserIdAndStatusOrderByCreatedAtDesc(currentUserId, status);
            }
        }

        return requests.stream()
                .map(request -> convertToFriendRequestVO(request, currentUserId))
                .collect(Collectors.toList());
    }

    @Override
    public List<FriendVO> getFriendList() {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        List<FriendRelation> relations = friendRelationRepository.findByUserUserIdAndStatus(
                currentUserId, FriendRelation.Status.ACTIVE);

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询 Redis，避免 N+1 问题
        return convertToFriendVOBatch(relations, currentUserId);
    }

    @Override
    public FriendVO getFriendDetail(Long friendId) {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        FriendRelation relation = friendRelationRepository
                .findByUserUserIdAndFriendUserId(currentUserId, friendId)
                .orElseThrow(() -> new RuntimeException("好友不存在"));

        return convertToFriendVO(relation, currentUserId);
    }

    @Override
    @Transactional
    public void updateFriend(Long friendId, FriendUpdateDTO updateDTO) {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        FriendRelation relation = friendRelationRepository
                .findByUserUserIdAndFriendUserId(currentUserId, friendId)
                .orElseThrow(() -> new RuntimeException("好友不存在"));

        if (StringUtils.hasText(updateDTO.getRemark())) {
            relation.setRemark(updateDTO.getRemark());
        }

        if (updateDTO.getGroupId() != null) {
            FriendGroup group = friendGroupRepository.findById(updateDTO.getGroupId())
                    .orElseThrow(() -> new RuntimeException("分组不存在"));
            if (!group.getUser().getUserId().equals(currentUserId)) {
                throw new RuntimeException("分组不属于当前用户");
            }
            relation.setGroup(group);
        }

        if (updateDTO.getIsPinned() != null) {
            relation.setIsPinned(updateDTO.getIsPinned());
        }

        if (updateDTO.getIsMuted() != null) {
            relation.setIsMuted(updateDTO.getIsMuted());
        }

        if (updateDTO.getIntimacyLevel() != null) {
            relation.setIntimacyLevel(updateDTO.getIntimacyLevel());
        }

        friendRelationRepository.save(relation);
    }

    @Override
    @Transactional
    public void deleteFriend(Long friendId) {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        FriendRelation relation = friendRelationRepository
                .findByUserUserIdAndFriendUserId(currentUserId, friendId)
                .orElseThrow(() -> new RuntimeException("好友不存在"));

        if (!relation.getUser().getUserId().equals(currentUserId)) {
            log.error("非法删除好友尝试: 当前用户={}, 目标关系用户={}",
                    currentUserId, relation.getUser().getUserId());
            throw new RuntimeException("无权删除此好友");
        }

        relation.setStatus(FriendRelation.Status.DELETED);
        friendRelationRepository.save(relation);
        log.info("用户 {} 删除好友 {}", currentUserId, friendId);
    }

    @Override
    @Transactional
    public void blockFriend(Long friendId) {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        FriendRelation relation = friendRelationRepository
                .findByUserUserIdAndFriendUserId(currentUserId, friendId)
                .orElseThrow(() -> new RuntimeException("好友不存在"));

        relation.setStatus(FriendRelation.Status.BLOCKED);
        friendRelationRepository.save(relation);
    }

    @Override
    @Transactional
    public void unblockFriend(Long friendId) {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        FriendRelation relation = friendRelationRepository
                .findByUserUserIdAndFriendUserId(currentUserId, friendId)
                .orElseThrow(() -> new RuntimeException("好友关系不存在"));

        if (relation.isBlocked()) {
            relation.setStatus(FriendRelation.Status.ACTIVE);
            friendRelationRepository.save(relation);
        }
    }

    @Override
    public List<FriendVO> searchFriends(String keyword) {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        List<FriendRelation> relations = friendRelationRepository
                .findByUserUserIdAndStatus(currentUserId, FriendRelation.Status.ACTIVE);

        return relations.stream()
                .filter(relation -> {
                    User friend = relation.getFriend();
                    boolean matches = friend.getUsername() != null && friend.getUsername().contains(keyword);
                    if (friend.getNickname() != null && friend.getNickname().contains(keyword)) {
                        matches = true;
                    }
                    if (relation.getRemark() != null && relation.getRemark().contains(keyword)) {
                        matches = true;
                    }
                    return matches;
                })
                .map(relation -> convertToFriendVO(relation, currentUserId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FriendGroupVO createFriendGroup(FriendGroupDTO groupDTO) {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        if (friendGroupRepository.existsByUserUserIdAndGroupName(
                currentUserId, groupDTO.getGroupName())) {
            throw new RuntimeException("分组名称已存在");
        }

        FriendGroup group = new FriendGroup();
        group.setUser(currentUser);
        group.setGroupName(groupDTO.getGroupName());
        group.setSortOrder(groupDTO.getSortOrder() != null ? groupDTO.getSortOrder() : 0);
        group.setColor(FriendGroup.Colors.BLUE);
        group.setIcon("group");
        group.setIsVisible(true);

        FriendGroup savedGroup = friendGroupRepository.save(group);
        return convertToFriendGroupVO(savedGroup);
    }

    @Override
    @Transactional
    public void updateFriendGroup(Long groupId, FriendGroupDTO groupDTO) {
        FriendGroup group = friendGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("分组不存在"));

        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        if (!group.getUser().getUserId().equals(currentUserId)) {
            throw new RuntimeException("无权修改此分组");
        }

        if (Boolean.TRUE.equals(group.getIsDefault()) &&
                StringUtils.hasText(groupDTO.getGroupName())) {
            throw new RuntimeException("默认分组不能修改名称");
        }

        if (StringUtils.hasText(groupDTO.getGroupName())) {
            group.setGroupName(groupDTO.getGroupName());
        }

        if (groupDTO.getSortOrder() != null) {
            group.setSortOrder(groupDTO.getSortOrder());
        }

        friendGroupRepository.save(group);
    }

    @Override
    @Transactional
    public void deleteFriendGroup(Long groupId) {
        FriendGroup group = friendGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("分组不存在"));

        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        if (!group.getUser().getUserId().equals(currentUserId)) {
            throw new RuntimeException("无权删除此分组");
        }

        if (!group.canDelete()) {
            throw new RuntimeException("分组不为空或为默认分组，无法删除");
        }

        friendGroupRepository.delete(group);
    }

    @Override
    public List<FriendGroupVO> getFriendGroups() {
        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        List<FriendGroup> groups = friendGroupRepository
                .findByUserUserIdOrderBySortOrderAsc(currentUserId);

        return groups.stream()
                .map(this::convertToFriendGroupVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void moveFriendToGroup(Long relationId, Long groupId) {
        FriendRelation relation = friendRelationRepository.findById(relationId)
                .orElseThrow(() -> new RuntimeException("好友关系不存在"));

        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        if (!relation.getUser().getUserId().equals(currentUserId)) {
            throw new RuntimeException("无权移动此好友");
        }

        FriendGroup group = null;
        if (groupId != null) {
            group = friendGroupRepository.findById(groupId)
                    .orElseThrow(() -> new RuntimeException("分组不存在"));

            if (!group.getUser().getUserId().equals(currentUserId)) {
                throw new RuntimeException("分组不属于当前用户");
            }
        }

        relation.setGroup(group);
        friendRelationRepository.save(relation);
    }

    @Override
    public FriendGroupVO getFriendGroupDetail(Long groupId) {
        FriendGroup group = friendGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("分组不存在"));

        User currentUser = getCurrentUser();
        Long currentUserId = currentUser.getUserId();

        if (!group.getUser().getUserId().equals(currentUserId)) {
            throw new RuntimeException("无权查看此分组");
        }

        return convertToFriendGroupVO(group);
    }

    // ==================== 辅助方法 ====================

    private void createFriendship(User user1, User user2) {
        FriendRelation relation1 = new FriendRelation();
        relation1.setUser(user1);
        relation1.setFriend(user2);
        relation1.setStatus(FriendRelation.Status.ACTIVE);

        FriendGroup defaultGroup1 = friendGroupRepository
                .findByUserUserIdAndGroupName(user1.getUserId(), FriendGroup.DefaultGroups.ALL_FRIENDS)
                .orElseThrow(() -> new RuntimeException("用户没有默认分组"));

        relation1.setGroup(defaultGroup1);
        friendRelationRepository.save(relation1);

        FriendRelation relation2 = new FriendRelation();
        relation2.setUser(user2);
        relation2.setFriend(user1);
        relation2.setStatus(FriendRelation.Status.ACTIVE);

        FriendGroup defaultGroup2 = friendGroupRepository
                .findByUserUserIdAndGroupName(user2.getUserId(), FriendGroup.DefaultGroups.ALL_FRIENDS)
                .orElseThrow(() -> new RuntimeException("用户没有默认分组"));

        relation2.setGroup(defaultGroup2);
        friendRelationRepository.save(relation2);
    }

    /**
     * 转换为FriendVO，不再依赖User实体状态定义
     */
    private FriendVO convertToFriendVO(FriendRelation relation, Long currentUserId) {
        FriendVO vo = new FriendVO();
        User friend = relation.getFriend();

        vo.setRelationId(relation.getRelationId());
        vo.setFriendId(friend.getUserId());
        vo.setUsername(friend.getUsername());
        vo.setNickname(friend.getNickname());
        vo.setRemark(relation.getRemark());
        vo.setDisplayName(relation.getDisplayName());
        vo.setAvatarUrl(friend.getAvatarUrl());
        vo.setSignature(friend.getSignature());

        Long friendId = friend.getUserId();

        // 1️⃣ 物理连接状态（真实技术状态）
        boolean isPhysicallyOnline = webSocketSessionService.isUserOnline(friendId);
        int deviceCount = webSocketSessionService.getUserOnlineDeviceCount(friendId);
        vo.setIsPhysicallyOnline(isPhysicallyOnline);

        // 2️⃣ 业务设置状态（来自Redis，与User实体无关）
        String bizStatus = userStatusService.getUserStatus(friendId);
        if (bizStatus == null || bizStatus.isEmpty()) {
            bizStatus = isPhysicallyOnline ? "online" : "offline";
        }
        vo.setBizStatus(bizStatus);

        // 【修改后】
        // 业务状态：隐身检查（简化：隐身=对所有人显示离线）
        boolean isInvisible = userStatusService.isInvisible(friend.getUserId());

        // 最终可见状态：物理在线 && 未隐身
        boolean isVisibleOnline = isPhysicallyOnline && !isInvisible;

        // 状态编码（使用本地常量，不再依赖 User.OnlineStatus）
        Integer statusCode = calculateOnlineStatusCode(bizStatus, isPhysicallyOnline, isVisibleOnline);
        vo.setOnlineStatus(statusCode);
        vo.setOnlineStatusText(generateStatusText(bizStatus, isPhysicallyOnline, deviceCount, isVisibleOnline));

        vo.setIsPinned(relation.getIsPinned());
        vo.setIsMuted(relation.getIsMuted());
        vo.setIntimacyLevel(relation.getIntimacyLevel());
        vo.setLastInteraction(relation.getLastInteraction());

        if (relation.getGroup() != null) {
            vo.setGroupId(relation.getGroup().getGroupId());
            vo.setGroupName(relation.getGroup().getGroupName());
            vo.setGroupColor(relation.getGroup().getDisplayColor());
        }

        return vo;
    }

    private List<FriendVO> convertToFriendVOBatch(List<FriendRelation> relations, Long currentUserId) {
        // 1. 收集所有好友ID
        List<Long> friendIds = relations.stream()
                .map(r -> r.getFriend().getUserId())
                .distinct()
                .collect(Collectors.toList());

        // 2. 批量查询 Redis（Pipeline 一次往返）
        Map<Long, Boolean> onlineMap = batchQueryOnlineStatus(friendIds);
        Map<Long, String> statusMap = batchQueryBizStatus(friendIds);

        // 3. 批量构建VO（复用查询结果，不再访问Redis）
        return relations.stream()
                .map(relation -> {
                    User friend = relation.getFriend();
                    Long friendId = friend.getUserId();

                    FriendVO vo = new FriendVO();
                    vo.setRelationId(relation.getRelationId());
                    vo.setFriendId(friendId);
                    vo.setUsername(friend.getUsername());
                    vo.setNickname(friend.getNickname());
                    vo.setRemark(relation.getRemark());
                    vo.setDisplayName(relation.getDisplayName());
                    vo.setAvatarUrl(friend.getAvatarUrl());
                    vo.setSignature(friend.getSignature());

                    // 使用批量查询的结果，不再单独查询Redis
                    boolean isPhysicallyOnline = onlineMap.getOrDefault(friendId, false);
                    vo.setIsPhysicallyOnline(isPhysicallyOnline);

                    String bizStatus = statusMap.getOrDefault(friendId,
                            isPhysicallyOnline ? "online" : "offline");
                    vo.setBizStatus(bizStatus);

                    // 计算最终显示状态
                    boolean isInvisible = "invisible".equals(bizStatus);
                    boolean isVisibleOnline = isPhysicallyOnline && !isInvisible;
                    vo.setIsVisibleOnline(isVisibleOnline);

                    // 状态编码和文本
                    vo.setOnlineStatus(calculateOnlineStatusCode(bizStatus, isPhysicallyOnline));
                    vo.setOnlineStatusText(generateStatusText(bizStatus, isPhysicallyOnline,
                            isVisibleOnline ? webSocketSessionService.getUserOnlineDeviceCount(friendId) : 0));

                    vo.setIsPinned(relation.getIsPinned());
                    vo.setIsMuted(relation.getIsMuted());
                    vo.setIntimacyLevel(relation.getIntimacyLevel());
                    vo.setLastInteraction(relation.getLastInteraction());

                    if (relation.getGroup() != null) {
                        vo.setGroupId(relation.getGroup().getGroupId());
                        vo.setGroupName(relation.getGroup().getGroupName());
                        vo.setGroupColor(relation.getGroup().getDisplayColor());
                    }

                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 批量查询在线状态（Pipeline 优化）
     */
    private Map<Long, Boolean> batchQueryOnlineStatus(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Boolean> result = new HashMap<>();

        try {
            // 使用 stringRedisTemplate 执行管道
            List<Object> pipelineResults = stringRedisTemplate.executePipelined(
                    (RedisCallback<Object>) connection -> {
                        for (Long userId : userIds) {
                            String key = "chat:ws:user:sessions:" + userId;
                            connection.keyCommands().exists(key.getBytes());
                        }
                        return null;
                    }
            );

            // 映射结果
            for (int i = 0; i < userIds.size(); i++) {
                Boolean isOnline = (Boolean) pipelineResults.get(i);
                result.put(userIds.get(i), isOnline != null && isOnline);
            }
        } catch (Exception e) {
            log.error("批量查询在线状态失败", e);
            // 降级：全部返回 false，避免影响主流程
            userIds.forEach(id -> result.put(id, false));
        }

        return result;
    }

    /**
     * 批量查询业务状态（Pipeline 优化）
     */
    private Map<Long, String> batchQueryBizStatus(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> result = new HashMap<>();

        try {
            // 使用 stringRedisTemplate 执行管道
            List<Object> pipelineResults = stringRedisTemplate.executePipelined(
                    (RedisCallback<Object>) connection -> {
                        for (Long userId : userIds) {
                            String key = "chat:user:biz-status:" + userId;
                            connection.hashCommands().hGet(key.getBytes(), "status".getBytes());
                        }
                        return null;
                    }
            );

            // 映射结果
            for (int i = 0; i < userIds.size(); i++) {
                Object statusObj = pipelineResults.get(i);
                String statusStr = statusObj != null ? statusObj.toString() : null;
                result.put(userIds.get(i), statusStr);
            }
        } catch (Exception e) {
            log.error("批量查询业务状态失败", e);
        }

        return result;
    }


    /**
     * 计算在线状态编码 - 2参数版本（用于批量查询，假设可见）
     */
    private Integer calculateOnlineStatusCode(String bizStatus, boolean isPhysicallyOnline) {
        if (!isPhysicallyOnline) return 0;
        return switch (bizStatus) {
            case "online" -> 1;
            case "busy" -> 2;
            case "invisible" -> 3;
            default -> 1;
        };
    }

    /**
     * 计算在线状态编码 - 3参数版本（考虑可见性）
     */
    private Integer calculateOnlineStatusCode(String bizStatus, boolean isPhysicallyOnline, boolean isVisibleOnline) {
        if (!isPhysicallyOnline || !isVisibleOnline) return 0;
        return switch (bizStatus) {
            case "online" -> 1;
            case "busy" -> 2;
            case "invisible" -> 3;
            default -> 1;
        };
    }

    /**
     * 生成状态文本 - 3参数版本（用于批量查询）
     */
    private String generateStatusText(String bizStatus, boolean isPhysicallyOnline, int deviceCount) {
        if (!isPhysicallyOnline) return "离线";
        String baseText = switch (bizStatus) {
            case "busy" -> "忙碌";
            case "invisible" -> "隐身";
            case "online" -> "在线";
            default -> "在线";
        };
        return deviceCount > 1 ? baseText + " (多设备)" : baseText;
    }

    /**
     * 生成状态文本 - 4参数版本（考虑可见性）
     */
    private String generateStatusText(String bizStatus, boolean isPhysicallyOnline, int deviceCount, boolean isVisibleOnline) {
        if (!isPhysicallyOnline || !isVisibleOnline) return "离线";
        String baseText = switch (bizStatus) {
            case "busy" -> "忙碌";
            case "invisible" -> "隐身";
            case "online" -> "在线";
            default -> "在线";
        };
        return deviceCount > 1 ? baseText + " (多设备)" : baseText;
    }

    private FriendGroupVO convertToFriendGroupVO(FriendGroup group) {
        FriendGroupVO vo = new FriendGroupVO();
        vo.setGroupId(group.getGroupId());
        vo.setGroupName(group.getGroupName());
        vo.setSortOrder(group.getSortOrder());
        vo.setColor(group.getDisplayColor());
        vo.setDescription(group.getDescription());
        vo.setIcon(group.getIcon());
        vo.setIsDefault(group.getIsDefault());
        vo.setIsVisible(group.getIsVisible());
        vo.setFriendCount(group.getFriendCount());
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }

    private FriendRequestVO convertToFriendRequestVO(FriendRequest request, Long currentUserId) {
        FriendRequestVO vo = new FriendRequestVO();
        vo.setRequestId(request.getRequestId());

        User sender = request.getSender();
        vo.setSenderId(sender.getUserId());
        vo.setSenderUsername(sender.getUsername());
        vo.setSenderNickname(sender.getNickname());
        vo.setSenderAvatarUrl(sender.getAvatarUrl());

        User receiver = request.getReceiver();
        vo.setReceiverId(receiver.getUserId());
        vo.setReceiverUsername(receiver.getUsername());

        vo.setStatus(request.getStatus());
        vo.setStatusText(getRequestStatusText(request.getStatus()));
        vo.setMessage(request.getMessage());
        vo.setSource(request.getSource());
        vo.setRejectReason(request.getRejectReason());
        vo.setCreatedAt(request.getCreatedAt());
        vo.setProcessedAt(request.getProcessedAt());
        vo.setExpiresAt(request.getExpiresAt());
        vo.setRemainingDays(request.getRemainingDays());

        boolean isSender = request.getSender().getUserId().equals(currentUserId);
        vo.setIsSender(isSender);

        return vo;
    }

    private String getOnlineStatusText(Integer status) {
        return switch (status) {
            case 0 -> "离线";
            case 1 -> "在线";
            case 2 -> "忙碌";
            case 3 -> "隐身";
            default -> "未知";
        };
    }

    private String getRequestStatusText(Integer status) {
        return switch (status) {
            case 0 -> "待处理";
            case 1 -> "已同意";
            case 2 -> "已拒绝";
            case 3 -> "已过期";
            case 4 -> "已取消";
            default -> "未知";
        };
    }
}