// 用户信息VO
export interface UserVO {
  userId: number
  username: string
  nickname: string
  email?: string
  phone?: string
  avatarUrl?: string
  gender: number
  genderText: string
  birthday?: string
  signature?: string
  userStatus: number
  userStatusText: string
  onlineStatus: number
  onlineStatusText: string
  lastLoginTime?: string
  registerTime: string
  // lastActivityTime?: string  // ❌ 后端已废弃，移除
    /**
   * ✅ 注意：现在代表 WebSocket 物理连接状态（true=在线，false=离线）
   * 业务状态（online/busy/invisible）请使用 onlineStatus 字段（1=在线, 2=忙碌, 3=隐身）
   */
  isOnline: boolean
}

// 登录DTO
export interface LoginDTO {
  identifier: string
  password: string
  status: 'online' | 'busy' | 'invisible' | 'offline'
}

// 注册DTO
export interface RegisterDTO {
  username: string
  password: string
  email?: string
  phone?: string
  nickname?: string
  signature?: string
}

// 更新用户信息DTO
export interface UserUpdateDTO {
  nickname?: string
  avatarUrl?: string
  gender?: string
  birthday?: string
  signature?: string
  email?: string
  phone?: string
}

// 修改密码DTO
export interface PasswordChangeDTO {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

// 查询用户DTO
export interface UserQueryDTO {
  keyword: string
  userStatus?: number
  onlineStatus?: number
  registerDateStart?: string
  registerDateEnd?: string
  page?: number
  size?: number
}

// Token信息DTO
export interface TokenDTO {
  accessToken: string
  tokenType: string
  expiresIn: number
  userId: number
  username: string
  nickname: string
  avatarUrl?: string
  sessionId: string
  deviceId: string
}