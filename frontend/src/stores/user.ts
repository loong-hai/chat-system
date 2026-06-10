// stores/user.ts - 只保留用户相关功能
import { defineStore } from 'pinia'
import { userApi, type FileUploadResult } from '@/api'
import type { UserVO, UserUpdateDTO, PasswordChangeDTO } from '@/types/user'

export const useUserStore = defineStore('user', {
    state: () => ({
        // 当前用户信息
        currentUser: null as UserVO | null,
        // 加载状态
        loading: false,
        // 在线状态
        onlineStatus: 'offline' as 'online' | 'busy' | 'invisible' | 'offline',
        // 在线用户数量
        onlineUserCount: 0,
        // 错误信息
        error: '',
        // 可选：缓存头像历史，避免重复请求
        avatarHistory: [] as FileUploadResult[]
    }),

    getters: {
        // 用户基本信息
        userInfo: (state) => ({
            userId: state.currentUser?.userId,
            username: state.currentUser?.username,
            nickname: state.currentUser?.nickname,
            avatarUrl: state.currentUser?.avatarUrl,
            signature: state.currentUser?.signature,
            onlineStatus: state.currentUser?.onlineStatus,
            onlineStatusText: state.currentUser?.onlineStatusText,
            isOnline: state.currentUser?.isOnline
        }),

        // 是否在线
        isOnline: (state) => state.onlineStatus === 'online'
    },

    actions: {
        /**
         * 获取当前用户信息
         */
        async fetchCurrentUser() {
            this.loading = true
            this.error = ''

            try {
                const user = await userApi.getCurrentUser()
                this.currentUser = user
                this.onlineStatus = this.mapStatusNumberToString(user.onlineStatus)
                return user
            } catch (error: any) {
                this.error = error.message || '获取用户信息失败'
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * 更新在线状态
         */
        async updateOnlineStatus(status: number) {
            try {
                await userApi.updateOnlineStatus(status)

                // 更新本地状态
                this.onlineStatus = this.mapStatusNumberToString(status)

                // ✅ 关键新增：同步更新 authStore 中的状态，确保 WebSocket 重连时携带最新状态
                const { useAuthStore } = await import('@/stores/auth') // 动态导入避免循环依赖
                const authStore = useAuthStore()
                if (authStore.isAuthenticated) {
                    authStore.userStatus = this.mapStatusNumberToString(status)
                    localStorage.setItem('user_status', authStore.userStatus)
                }

                if (this.currentUser) {
                    this.currentUser.onlineStatus = status
                    this.currentUser.onlineStatusText = this.getOnlineStatusText(status)
                    // ✅ 修正：isOnline 是物理连接状态，只要 WebSocket 连着就是 true
                    // 业务状态（隐身/忙碌）通过 onlineStatus 字段体现
                    this.currentUser.isOnline = true // WebSocket 连着就是物理在线
                }
            } catch (error: any) {
                console.error('更新在线状态失败:', error)
                throw error
            }
        },

        /**
         * 更新用户信息
         */
        async updateUserInfo(data: UserUpdateDTO, avatarFile?: File) {
            this.loading = true
            try {
                let updatedUser: UserVO

                if (avatarFile) {
                    // 有头像文件：使用 Multipart 格式
                    updatedUser = await userApi.updateUserWithAvatar(data, avatarFile)
                } else {
                    // 无头像文件：使用 JSON 格式
                    updatedUser = await userApi.updateUser(data)
                }

                this.currentUser = updatedUser
                return updatedUser
            } catch (error: any) {
                this.error = error.message || '更新失败'
                throw error
            } finally {
                this.loading = false
            }
        },
        /**
     * 单独上传头像（不需要修改其他资料时使用）
     * 后端会自动更新用户头像，并保存历史记录
     */
        async uploadAvatar(file: File) {
            this.loading = true
            try {
                const result = await userApi.uploadAvatar(file)
                // 上传成功后刷新用户信息（获取新的 avatarUrl）
                await this.fetchCurrentUser()
                return result
            } catch (error: any) {
                this.error = error.message || '上传头像失败'
                throw error
            } finally {
                this.loading = false
            }
        },/**
     * 获取头像历史（用于"查看历史头像"功能）
     */
        async fetchAvatarHistory() {
            try {
                this.avatarHistory = await userApi.getAvatarHistory()
                return this.avatarHistory
            } catch (error) {
                console.error('获取头像历史失败:', error)
                return []
            }
        },

        /**
         * 切换到历史头像
         */
        async switchToHistoryAvatar(historyId: number) {
            try {
                await userApi.switchAvatar(historyId)
                // 切换成功后刷新用户信息
                await this.fetchCurrentUser()
            } catch (error) {
                console.error('切换头像失败:', error)
                throw error
            }
        },

        /**
         * 状态码转字符串
         */
        mapStatusNumberToString(status: number): 'online' | 'busy' | 'invisible' | 'offline' {
            switch (status) {
                case 1: return 'online'
                case 2: return 'busy'
                case 3: return 'invisible'
                default: return 'offline'
            }
        },

        /**
         * 获取在线状态文本
         */
        getOnlineStatusText(status: number): string {
            switch (status) {
                case 1: return '在线'
                case 2: return '忙碌'
                case 3: return '隐身'
                default: return '离线'
            }
        },

        /**
         * 获取在线用户概况
         */
        async fetchOnlineUserCount() {
            try {
                const count = await userApi.getOnlineUserCount()
                this.onlineUserCount = count
                return count
            } catch (error) {
                console.error('获取在线用户概况失败:', error)
                throw error
            }
        },

        /**
         * 清空用户数据
         */
        clearUserData() {
            this.currentUser = null
            this.onlineStatus = 'offline'
            this.onlineUserCount = 0
            this.loading = false
            this.error = ''
        }
    }
})