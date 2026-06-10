package com.hailong.chatsystem.service;

import com.hailong.chatsystem.model.dto.*;
import com.hailong.chatsystem.model.vo.FriendGroupVO;
import com.hailong.chatsystem.model.vo.FriendRequestVO;
import com.hailong.chatsystem.model.vo.FriendVO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface FriendService {

    // 发送好友申请
    void sendFriendRequest(FriendRequestDTO requestDTO);

    // 处理好友申请
    void handleFriendRequest(FriendRequestHandleDTO handleDTO);

    // 获取好友申请列表
    // 修改方法签名，添加type参数
    List<FriendRequestVO> getFriendRequests(Integer status, String type);

    // 获取好友列表
    List<FriendVO> getFriendList();

    // 获取好友详情
    FriendVO getFriendDetail(Long friendId);

    // 更新好友信息
    void updateFriend(Long friendId, FriendUpdateDTO updateDTO);

    // 删除好友（软删除）
    void deleteFriend(Long friendId);

    // 拉黑好友
    void blockFriend(Long friendId);

    // 取消拉黑
    void unblockFriend(Long friendId);

    // 搜索好友
    List<FriendVO> searchFriends(String keyword);

    // 创建好友分组
    FriendGroupVO createFriendGroup(FriendGroupDTO groupDTO);

    // 更新好友分组
    void updateFriendGroup(Long groupId, FriendGroupDTO groupDTO);

    // 删除好友分组
    void deleteFriendGroup(Long groupId);

    // 获取好友分组列表
    List<FriendGroupVO> getFriendGroups();

    // 移动好友到其他分组
    void moveFriendToGroup(Long relationId, Long groupId);

    // 获取好友分组详情
    FriendGroupVO getFriendGroupDetail(Long groupId);
}