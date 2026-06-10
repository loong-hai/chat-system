// 好友信息VO
export interface FriendVO {
  relationId: number
  friendId: number
  username: string
  nickname: string
  remark?: string
  displayName: string
  avatarUrl?: string
  signature?: string
  onlineStatus: number        // 0=离线, 1=在线, 2=忙碌, 3=隐身
  onlineStatusText: string
  isOnline: boolean          // 保留兼容，但建议优先使用 isVisibleOnline
    // ✅ 新增字段（后端 FriendVO.java 新增）
  /** WebSocket 物理连接状态（真实技术状态） */
  isPhysicallyOnline?: boolean
  
  /** 业务设置状态（来自 Redis：online/busy/invisible/offline） */
  bizStatus?: string
  
  /** 对当前查看者是否可见地在线（计算后的最终展示状态，考虑了隐身） */
  isVisibleOnline?: boolean
  groupId?: number
  groupName?: string
  groupColor?: string
  isPinned: boolean
  isMuted: boolean
  intimacyLevel: number
  lastInteraction?: string
}

// 添加好友分组类型
export interface FriendGroup {
  groupId: number
  groupName: string
  count: number
  isExpanded: boolean
  friends: FriendVO[]
}

// 好友分组VO
export interface FriendGroupVO {
  groupId: number
  groupName: string
  sortOrder: number
  color?: string
  description?: string
  icon?: string
  isDefault: boolean
  isVisible: boolean
  friendCount: number
  createdAt: string
  updatedAt: string
}

// 好友请求VO
export interface FriendRequestVO {
  isSender: boolean
  requestId: number
  senderId: number
  senderUsername: string
  senderNickname: string
  senderAvatarUrl?: string
  receiverId: number
  receiverUsername: string
  status: number
  statusText: string
  message?: string
  source?: string
  rejectReason?: string
  createdAt: string
  processedAt?: string
  expiresAt?: string
  remainingDays: number
}

// 发送好友申请DTO
export interface FriendRequestDTO {
  receiverId: number
  message?: string
  source?: string
}

// 处理好友申请DTO
export interface FriendRequestHandleDTO {
  requestId: number
  accept: boolean
  rejectReason?: string
}

// 更新好友信息DTO
export interface FriendUpdateDTO {
  remark?: string
  groupId?: number
  isPinned?: boolean
  isMuted?: boolean
  intimacyLevel?: number
}

// 好友分组DTO
export interface FriendGroupDTO {
  groupName: string
  sortOrder?: number
}