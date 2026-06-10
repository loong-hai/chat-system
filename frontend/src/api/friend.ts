// /api/friend.ts
import request from '@/utils/request'
import type { 
  FriendVO, 
  FriendGroupVO, 
  FriendRequestVO,
  FriendRequestDTO,
  FriendRequestHandleDTO,
  FriendUpdateDTO,
  FriendGroupDTO 
} from '@/types/friend'

export const friendApi = {
  // 发送好友申请
  sendFriendRequest(data: FriendRequestDTO): Promise<void> {
    return request.post('/friends/requests', data)
  },
  
  // 处理好友申请
  handleFriendRequest(requestId: number, data: FriendRequestHandleDTO): Promise<void> {
    return request.put(`/friends/requests/${requestId}`, data)
  },
  
  // 获取好友申请列表
  getFriendRequests(params?: { status?: number; type?: string }): Promise<FriendRequestVO[]> {
    return request.get('/friends/requests', params)
  },
  
  // 获取好友列表
  getFriendList(): Promise<FriendVO[]> {
    return request.get('/friends')
  },
  
  // 获取好友详情
  getFriendDetail(friendId: number): Promise<FriendVO> {
    return request.get(`/friends/${friendId}`)
  },
  
  // 更新好友信息
  updateFriend(friendId: number, data: FriendUpdateDTO): Promise<void> {
    return request.put(`/friends/${friendId}`, data)
  },
  
  // 删除好友
  deleteFriend(friendId: number): Promise<void> {
    return request.delete(`/friends/${friendId}`)
  },
  
  // 拉黑好友
  blockFriend(friendId: number): Promise<void> {
    return request.post(`/friends/${friendId}/block`)
  },
  
  // 取消拉黑
  unblockFriend(friendId: number): Promise<void> {
    return request.post(`/friends/${friendId}/unblock`)
  },
  
  // 搜索好友
  searchFriends(keyword: string): Promise<FriendVO[]> {
    return request.get('/friends/search', { keyword })
  },
  
  // 创建好友分组
  createFriendGroup(data: FriendGroupDTO): Promise<FriendGroupVO> {
    return request.post('/friends/groups', data)
  },
  
  // 更新好友分组
  updateFriendGroup(groupId: number, data: FriendGroupDTO): Promise<void> {
    return request.put(`/friends/groups/${groupId}`, data)
  },
  
  // 删除好友分组
  deleteFriendGroup(groupId: number): Promise<void> {
    return request.delete(`/friends/groups/${groupId}`)
  },
  
  // 获取好友分组列表
  getFriendGroups(): Promise<FriendGroupVO[]> {
    return request.get('/friends/groups')
  },
  
  // 获取分组详情
  getFriendGroupDetail(groupId: number): Promise<FriendGroupVO> {
    return request.get(`/friends/groups/${groupId}`)
  },
  
  // 移动好友到分组
  moveFriendToGroup(relationId: number, groupId: number): Promise<void> {
    return request.put(`/friends/${relationId}/move/${groupId}`)
  }
}