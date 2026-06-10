// /api/user.ts
import request from '@/utils/request'
import type { PageResponse } from '@/types/api'
import type {
  UserVO,
  UserUpdateDTO,
  PasswordChangeDTO,
  UserQueryDTO
} from '@/types/user'

// 新增：文件上传结果类型（简单定义，不增加文件）
export interface FileUploadResult {
  fileUrl: string
  thumbnailUrl?: string
  fileKey: string
  isRapidUpload?: boolean
}

export const userApi = {
  // 获取当前用户
  getCurrentUser(): Promise<UserVO> {
    return request.get('/users/me')
  },

  /**
   * 更新用户信息（JSON格式，不带头像文件）
   * 对应后端：PUT /users/me (application/json)
   */
  updateUser(data: UserUpdateDTO): Promise<UserVO> {
    return request.put('/users/me', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },
  /**
 * 更新用户信息（含头像文件）
 * 对应后端：PUT /users/me (multipart/form-data)
 * 注意：avatarFile 字段名必须与后端 @RequestPart("avatarFile") 一致
 */
  updateUserWithAvatar(data: UserUpdateDTO, avatarFile?: File): Promise<UserVO> {
    const formData = new FormData()

    // 添加文本字段（只添加有值的，避免覆盖后端原有数据）
    Object.entries(data).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        formData.append(key, String(value))
      }
    })

    // 添加文件（关键：字段名必须是 avatarFile）
    if (avatarFile) {
      formData.append('avatarFile', avatarFile)
    }

    return request.put('/users/me', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
  /**
 * 直接上传头像（返回URL后你可自行决定如何使用）
 * 对应后端：POST /storage/avatar
 * 后端会自动更新用户表的 avatar_url 字段
 */
  uploadAvatar(file: File): Promise<FileUploadResult> {
    const formData = new FormData()
    formData.append('file', file)
    return request.upload('/storage/avatar', formData)
  },

  /**
   * 获取头像历史记录（如需支持"换回旧头像"功能）
   * 对应后端：GET /storage/avatar/history
   */
  getAvatarHistory(): Promise<FileUploadResult[]> {
    return request.get('/storage/avatar/history')
  },

  /**
   * 切换到历史头像
   * 对应后端：PUT /storage/avatar/switch/{historyId}
   */
  switchAvatar(historyId: number): Promise<FileUploadResult> {
    return request.put(`/storage/avatar/switch/${historyId}`)
  },

  // 修改密码
  changePassword(data: PasswordChangeDTO): Promise<void> {
    return request.put('/users/me/password', data)
  },

  // 更新在线状态
  updateOnlineStatus(status: number): Promise<void> {
    return request.put('/users/me/status', null, {
      params: { status }
    })
  },

  // 获取在线状态
  getOnlineStatus(): Promise<any> {
    return request.get('/users/me/online-status')
  },

  // 获取在线用户概况
  getOnlineUserCount(): Promise<any> {
    return request.get('/users/online/count')
  },

  // 根据ID获取用户
  getUserById(userId: number): Promise<UserVO> {
    return request.get(`/users/${userId}`)
  },

  // 搜索用户
  searchUsers(keyword: string): Promise<UserVO[]> {
    return request.get('/users/search', { keyword })
  },

  // 查询用户列表
  queryUsers(params: UserQueryDTO): Promise<PageResponse<UserVO>> {
    return request.get('/users', {
      ...params,
      page: params.page || 1,
      size: params.size || 20
    })
  },

  // 冻结用户
  freezeUser(userId: number): Promise<void> {
    return request.post(`/users/${userId}/freeze`)
  },

  // 解冻用户
  unfreezeUser(userId: number): Promise<void> {
    return request.post(`/users/${userId}/unfreeze`)
  },

  // 注销用户
  deregisterUser(userId: number): Promise<void> {
    return request.post(`/users/${userId}/deregister`)
  }
}