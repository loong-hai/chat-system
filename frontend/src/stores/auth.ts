import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { LoginDTO, TokenDTO } from '@/types/user'
import { wsService } from '@/services/websocketService'

export const useAuthStore = defineStore('auth', {
    state: () => ({
        isAuthenticated: false,
        tokenData: null as TokenDTO | null,
        loading: false,
        error: '',
        // ✅ 新增：持久化用户选择的在线状态，用于 WebSocket 重连和刷新恢复
        userStatus: null as 'online' | 'busy' | 'invisible' | 'offline' | null
    }),

    getters: {
        isLoggedIn: (state) => state.isAuthenticated,
        currentUserId: (state) => state.tokenData?.userId,
        accessToken: (state) => state.tokenData?.accessToken,
        // ✅ 新增：获取当前在线状态，默认 online
        currentUserStatus: (state) => state.userStatus || 'online'
    },

    actions: {
        /**
         * 用户登录
         * 修改点：保存登录时选择的 status，用于 WebSocket 连接
         */
        async login(loginData: LoginDTO) {
            this.loading = true
            this.error = ''

            try {
                const tokenData = await authApi.login(loginData)

                this.isAuthenticated = true
                this.tokenData = tokenData
                // ✅ 关键：保存用户选择的登录状态（来自 LoginDTO）
                this.userStatus = loginData.status
                
                // ✅ 传递 status 到持久化方法
                this.saveToLocalStorage(tokenData, loginData.status)

                // ✅ WebSocket 连接时传入用户选择的状态
                wsService.connect(tokenData.accessToken, loginData.status)

                return tokenData

            } catch (error: any) {
                this.error = error.message || '登录失败'
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * 保存Token到本地存储 - 按用户隔离
         */
        saveToLocalStorage(tokenData: TokenDTO, status?: string) {
            const userPrefix = `user_${tokenData.userId}_`
            
            localStorage.setItem(`${userPrefix}access_token`, tokenData.accessToken)
            localStorage.setItem(`${userPrefix}session_id`, tokenData.sessionId)
            localStorage.setItem(`${userPrefix}user_id`, String(tokenData.userId))
            localStorage.setItem(`${userPrefix}username`, tokenData.username)
            localStorage.setItem(`${userPrefix}nickname`, tokenData.nickname)
            
            // 同时保存当前登录用户ID，用于识别当前是谁
            localStorage.setItem('current_user_id', String(tokenData.userId))

            if (tokenData.avatarUrl) {
                localStorage.setItem(`${userPrefix}avatar_url`, tokenData.avatarUrl)
            }

            if (status) {
                localStorage.setItem(`${userPrefix}user_status`, status)
            }

            const expireTime = Date.now() + tokenData.expiresIn
            localStorage.setItem(`${userPrefix}expire_time`, String(expireTime))
        },

      /**
         * 检查本地是否有有效的登录状态 - 需要传入 userId 或从 current_user_id 获取
         */
        checkLocalAuth(userId?: number): boolean {
            const currentId = userId || Number(localStorage.getItem('current_user_id'))
            if (!currentId) return false
            
            const userPrefix = `user_${currentId}_`
            const token = localStorage.getItem(`${userPrefix}access_token`)

            if (!token) {
                this.isAuthenticated = false
                this.tokenData = null
                this.userStatus = null
                return false
            }

            const expireTime = localStorage.getItem(`${userPrefix}expire_time`)
            if (expireTime && Date.now() > Number(expireTime)) {
                this.clearAuth(currentId)
                return false
            }

            const savedStatus = localStorage.getItem(`${userPrefix}user_status`) as any
            this.userStatus = savedStatus || 'online'
            this.isAuthenticated = true
            
            // 恢复 tokenData...
            this.tokenData = {
                accessToken: token,
                tokenType: 'Bearer',
                expiresIn: Number(localStorage.getItem(`${userPrefix}expire_time`)) - Date.now(),
                userId: currentId,
                username: localStorage.getItem(`${userPrefix}username`) || '',
                nickname: localStorage.getItem(`${userPrefix}nickname`) || '',
                avatarUrl: localStorage.getItem(`${userPrefix}avatar_url`) || '',
                sessionId: localStorage.getItem(`${userPrefix}session_id`) || '',
                deviceId: ''
            }

            return true
        },

        /**
         * 退出登录
         */
        async logout() {
            try {
                await authApi.logout()
            } catch (error) {
                console.error('退出登录失败:', error)
            } finally {
                this.clearAuth()
            }
        },

        /**
         * 清除所有认证信息 - 可以指定用户ID，否则清除当前用户
         */
        clearAuth(userId?: number) {
            const targetId = userId || this.tokenData?.userId || Number(localStorage.getItem('current_user_id'))
            
            if (targetId) {
                const userPrefix = `user_${targetId}_`
                localStorage.removeItem(`${userPrefix}access_token`)
                localStorage.removeItem(`${userPrefix}refresh_token`)
                localStorage.removeItem(`${userPrefix}session_id`)
                localStorage.removeItem(`${userPrefix}user_id`)
                localStorage.removeItem(`${userPrefix}username`)
                localStorage.removeItem(`${userPrefix}nickname`)
                localStorage.removeItem(`${userPrefix}avatar_url`)
                localStorage.removeItem(`${userPrefix}expire_time`)
                localStorage.removeItem(`${userPrefix}user_status`)
            }
            
            // 如果是当前用户，同时清除 store 状态
            if (!userId || userId === this.tokenData?.userId) {
                this.isAuthenticated = false
                this.tokenData = null
                this.userStatus = null
                this.error = ''
                localStorage.removeItem('current_user_id')
            }
        }
    }
})