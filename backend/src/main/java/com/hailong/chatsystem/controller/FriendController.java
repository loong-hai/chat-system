package com.hailong.chatsystem.controller;

import com.hailong.chatsystem.common.ResponseMessage;
import com.hailong.chatsystem.model.dto.*;
import com.hailong.chatsystem.model.vo.FriendGroupVO;
import com.hailong.chatsystem.model.vo.FriendRequestVO;
import com.hailong.chatsystem.model.vo.FriendVO;
import com.hailong.chatsystem.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friends")
@Tag(name = "好友管理", description = "好友相关接口")
public class FriendController {

    @Autowired
    private FriendService friendService;

    @PostMapping("/requests")
    @Operation(summary = "发送好友申请", description = "向指定用户发送好友申请")
    public ResponseMessage<Void> sendFriendRequest(@Valid @RequestBody FriendRequestDTO requestDTO) {
        friendService.sendFriendRequest(requestDTO);
        return ResponseMessage.success("好友申请发送成功");
    }

    @PutMapping("/requests/{requestId}")
    @Operation(summary = "处理好友申请", description = "同意或拒绝好友申请")
    public ResponseMessage<Void> handleFriendRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody FriendRequestHandleDTO handleDTO) {
        handleDTO.setRequestId(requestId);
        friendService.handleFriendRequest(handleDTO);

        String message = handleDTO.getAccept() ? "好友申请已同意" : "好友申请已拒绝";
        return ResponseMessage.success(message);
    }

    @GetMapping("/requests")
    @Operation(summary = "获取好友申请列表", description = "获取收到或发送的好友申请列表")
    public ResponseMessage<List<FriendRequestVO>> getFriendRequests(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "received") String type) {
        List<FriendRequestVO> requests = friendService.getFriendRequests(status, type);
        return ResponseMessage.success(requests);
    }

    @GetMapping
    @Operation(summary = "获取好友列表", description = "获取当前用户的好友列表")
    public ResponseMessage<List<FriendVO>> getFriendList() {
        List<FriendVO> friends = friendService.getFriendList();
        return ResponseMessage.success(friends);
    }

    @GetMapping("/{friendId}")
    @Operation(summary = "获取好友详情", description = "获取指定好友的详细信息")
    public ResponseMessage<FriendVO> getFriendDetail(@PathVariable Long friendId) {
        FriendVO friend = friendService.getFriendDetail(friendId);
        return ResponseMessage.success(friend);
    }

    @PutMapping("/{friendId}")
    @Operation(summary = "更新好友信息", description = "更新好友备注、分组等信息")
    public ResponseMessage<Void> updateFriend(
            @PathVariable Long friendId,
            @Valid @RequestBody FriendUpdateDTO updateDTO) {
        friendService.updateFriend(friendId, updateDTO);
        return ResponseMessage.success("好友信息更新成功");
    }

    @DeleteMapping("/{friendId}")
    @Operation(summary = "删除好友", description = "删除指定好友")
    public ResponseMessage<Void> deleteFriend(@PathVariable Long friendId) {
        friendService.deleteFriend(friendId);
        return ResponseMessage.success("好友删除成功");
    }

    @PostMapping("/{friendId}/block")
    @Operation(summary = "拉黑好友", description = "将好友加入黑名单")
    public ResponseMessage<Void> blockFriend(@PathVariable Long friendId) {
        friendService.blockFriend(friendId);
        return ResponseMessage.success("好友已拉黑");
    }

    @PostMapping("/{friendId}/unblock")
    @Operation(summary = "取消拉黑", description = "将好友从黑名单移除")
    public ResponseMessage<Void> unblockFriend(@PathVariable Long friendId) {
        friendService.unblockFriend(friendId);
        return ResponseMessage.success("好友已取消拉黑");
    }

    @GetMapping("/search")
    @Operation(summary = "搜索好友", description = "根据关键词搜索好友")
    public ResponseMessage<List<FriendVO>> searchFriends(@RequestParam String keyword) {
        List<FriendVO> friends = friendService.searchFriends(keyword);
        return ResponseMessage.success(friends);
    }

    @PostMapping("/groups")
    @Operation(summary = "创建好友分组", description = "创建新的好友分组")
    public ResponseMessage<FriendGroupVO> createFriendGroup(@Valid @RequestBody FriendGroupDTO groupDTO) {
        FriendGroupVO group = friendService.createFriendGroup(groupDTO);
        return ResponseMessage.success("分组创建成功", group);
    }

    @PutMapping("/groups/{groupId}")
    @Operation(summary = "更新好友分组", description = "更新好友分组信息")
    public ResponseMessage<Void> updateFriendGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody FriendGroupDTO groupDTO) {
        friendService.updateFriendGroup(groupId, groupDTO);
        return ResponseMessage.success("分组更新成功");
    }

    @DeleteMapping("/groups/{groupId}")
    @Operation(summary = "删除好友分组", description = "删除指定的好友分组")
    public ResponseMessage<Void> deleteFriendGroup(@PathVariable Long groupId) {
        friendService.deleteFriendGroup(groupId);
        return ResponseMessage.success("分组删除成功");
    }

    @GetMapping("/groups")
    @Operation(summary = "获取好友分组列表", description = "获取当前用户的所有好友分组")
    public ResponseMessage<List<FriendGroupVO>> getFriendGroups() {
        List<FriendGroupVO> groups = friendService.getFriendGroups();
        return ResponseMessage.success(groups);
    }

    @GetMapping("/groups/{groupId}")
    @Operation(summary = "获取分组详情", description = "获取指定分组的详细信息")
    public ResponseMessage<FriendGroupVO> getFriendGroupDetail(@PathVariable Long groupId) {
        FriendGroupVO group = friendService.getFriendGroupDetail(groupId);
        return ResponseMessage.success(group);
    }

    @PutMapping("/{relationId}/move/{groupId}")
    @Operation(summary = "移动好友到分组", description = "将好友移动到指定分组")
    public ResponseMessage<Void> moveFriendToGroup(
            @PathVariable Long relationId,
            @PathVariable Long groupId) {
        friendService.moveFriendToGroup(relationId, groupId);
        return ResponseMessage.success("好友移动成功");
    }
}