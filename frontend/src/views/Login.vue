<template>
    <!-- 主页面容器：包含背景和居中卡片 -->
    <div class="login-page">
        <!-- 登录卡片容器 -->
        <div class="login-card-container">
            <!-- 磨砂玻璃效果卡片 -->
            <div class="login-card">
                <!-- 头像区域 -->
                <div class="avatar-section">
                    <el-avatar :size="80" class="login-avatar">
                        {{ defaultAvatarText }}
                    </el-avatar>
                    <h2 class="welcome-text">欢迎登录</h2>
                    <p class="welcome-subtext">请登录您的账户</p>
                </div>

                <!-- 登录表单 -->
                <el-form ref="loginFormRef" :model="formState" :rules="formRules" label-width="0" class="login-form"
                    @submit.prevent="handleLogin">
                    <!-- 用户名输入框 -->
                    <el-form-item prop="identifier">
                        <el-input v-model="formState.identifier" :prefix-icon="User" placeholder="请输入用户名/邮箱/手机号"
                            size="large" :disabled="loading" clearable class="form-input" />
                    </el-form-item>

                    <!-- 密码输入框 -->
                    <el-form-item prop="password">
                        <el-input v-model="formState.password" :prefix-icon="Lock" type="password" placeholder="请输入密码"
                            size="large" show-password :disabled="loading" clearable class="form-input" />
                    </el-form-item>

                    <!-- 登录状态选择 -->
                    <el-form-item prop="status">
                        <el-select v-model="formState.status" placeholder="选择登录状态" size="large" :disabled="loading"
                            class="status-select">
                            <el-option v-for="status in loginStatusOptions" :key="status.value" :label="status.label"
                                :value="status.value">
                                <div class="status-option">
                                    <el-icon :size="14" :color="status.color">
                                        <CircleCheck v-if="status.value === 'online'" />
                                        <Clock v-else-if="status.value === 'busy'" />
                                        <VideoPause v-else-if="status.value === 'invisible'" />
                                        <Hide v-else />
                                    </el-icon>
                                    <span style="margin-left: 8px">{{ status.label }}</span>
                                </div>
                            </el-option>
                        </el-select>
                    </el-form-item>

                    <!-- 链接和按钮区域 -->
                    <div class="form-footer">
                        <div class="form-links">
                            <el-link type="primary" :underline="false" :disabled="loading" @click="handleForgotPassword"
                                class="link-item">
                                忘记密码？
                            </el-link>
                            <el-link type="primary" :underline="false" :disabled="loading" @click="handleRegister"
                                class="link-item">
                                立即注册
                            </el-link>
                        </div>

                        <!-- 登录按钮 -->
                        <el-button type="primary" size="large" :loading="loading" native-type="submit" class="login-btn"
                            :icon="loading ? '' : Right">
                            {{ loading ? '登录中...' : '登 录' }}
                        </el-button>
                    </div>
                </el-form>
            </div>
        </div>

        <!-- 加载对话框 -->
        <el-dialog v-model="showLoadingDialog" :show-close="false" :close-on-click-modal="false"
            :close-on-press-escape="false" width="300px" center class="loading-dialog">
            <div class="loading-content">
                <el-icon class="is-loading loading-icon" :size="40">
                    <Loading />
                </el-icon>
                <p class="loading-text">正在登录，请稍候...</p>
                <p class="loading-subtext">{{ getStatusText(formState.status) }}</p>
            </div>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Loading, Right, CircleCheck, Clock, VideoPause, Hide } from '@element-plus/icons-vue'
import type { LoginDTO } from '@/types/user'
import { useAuthStore, useFriendStore, useUserStore } from '@/stores'

import { wsService } from '@/services/websocketService'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const friendStore = useFriendStore()


// 使用 Store
const authStore = useAuthStore()
const userStore = useUserStore()

// 路由实例
const router = useRouter()

// 类型定义
interface LoginFormState {
    identifier: string
    password: string
    status: 'online' | 'busy' | 'invisible' | 'offline'
}

interface LoginStatusOption {
    value: 'online' | 'busy' | 'invisible' | 'offline'
    label: string
    color: string
}

// 响应式数据
const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const showLoadingDialog = ref(false)

// 表单数据
const formState = reactive<LoginFormState>({
    identifier: '',
    password: '',
    status: 'online'
})

// 登录状态选项 - 根据后端定义的status调整
const loginStatusOptions: LoginStatusOption[] = [
    { value: 'online', label: '在线', color: '#67C23A' },
    { value: 'busy', label: '忙碌', color: '#F56C6C' },
    { value: 'invisible', label: '隐身', color: '#909399' },
    { value: 'offline', label: '离线', color: '#909399' }
]

// 头像相关
const defaultAvatarText = computed(() =>
    formState.identifier.slice(0, 2).toUpperCase() || 'User'
)

// 表单验证规则
const formRules: FormRules<LoginFormState> = {
    identifier: [
        { required: true, message: '请输入用户名/邮箱/手机号', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
    ],
    status: [
        { required: true, message: '请选择登录状态', trigger: 'change' }
    ]
}

// 根据状态获取文本
const getStatusText = (status: string): string => {
    const map: Record<string, string> = {
        online: '您将以在线状态登录',
        busy: '您将以忙碌状态登录',
        invisible: '您将以隐身状态登录',
        offline: '您将以离线状态登录'
    }
    return map[status] || ''
}

// 登录处理
const handleLogin = async () => {
    // 表单验证
    const valid = await loginFormRef.value?.validate().catch(() => false)
    if (!valid) return
    // 登录前先清理之前可能残留的连接和数据
    wsService.disconnect()

    // ✅ 手动重置 chatStore 状态（Setup Store 没有 $reset）
    chatStore.$patch((state) => {
        state.conversations = []
        state.currentConversation = null
        state.currentMessages = []
        state.unreadTotal = 0
        state.loading = false
        state.isChatPanelActive = false
    })

    // ✅ 手动重置 friendStore 状态
    friendStore.$patch((state) => {
        state.friends = []
        state.groups = []
        state.requests = []
        state.loading = { friends: false, groups: false, requests: false }
        state.error = null
    })

    // 开始登录流程
    loading.value = true
    showLoadingDialog.value = true

    try {
        // 构建登录数据
        const loginData: LoginDTO = {
            identifier: formState.identifier.trim(),
            password: formState.password,
            status: formState.status
        }

        // 注意：这里只需要传 loginData，不需要传 clientIp
        // 后端的 AuthController 会从 HttpServletRequest 中获取 IP
        const tokenData = await authStore.login(loginData)

        // 2. 登录成功后，获取用户信息
        await userStore.fetchCurrentUser()

        await userStore.updateOnlineStatus(
            formState.status === 'online' ? 1 :
                formState.status === 'busy' ? 2 :
                    formState.status === 'invisible' ? 3 : 0
        )

        // === 新增：初始化 WebSocket 和聊天 ===
        try {
            // 连接 WebSocket（用于实时收发消息）
            await wsService.connect(tokenData.accessToken)

            // 加载本地存储的会话列表（IndexedDB）
            const chatStore = useChatStore()
            await chatStore.initConversations()

            // 触发离线消息同步（向后端请求未读消息）
            wsService.syncOfflineMessages()
        } catch (wsError) {
            // WebSocket 连接失败不影响登录流程，只打印日志
            console.warn('WebSocket 连接失败，将在重连机制中自动恢复:', wsError)
        }
        // === 新增代码结束 ===

        // 登录成功
        ElNotification.success({
            title: '登录成功',
            message: `欢迎回来，${tokenData.nickname}！`,
            duration: 3000
        })

        router.replace({ path: '/index' }).catch(err => {
            console.error('导航失败:', err)
            // 如果导航失败，强制刷新
            window.location.href = '/index'
        })

    } catch (error: any) {
        // 错误处理
        if (error.message) {
            ElMessage.error(error.message)
        } else {
            ElMessage.error('登录失败，请稍后重试')
        }
    } finally {
        loading.value = false
        showLoadingDialog.value = false
    }
}
// 忘记密码
const handleForgotPassword = () => {
    if (loading.value) return
    ElMessage.info('跳转到密码找回页面')
}

// 注册
const handleRegister = () => {
    if (loading.value) return
    router.push('/signup')
}
</script>

<style scoped>
/* 主页面样式 - 渐变背景和全屏布局 */
.login-page {
    width: 100%;
    min-height: 100vh;
    display: flex;
    justify-content: center;
    align-items: center;
    background-image: linear-gradient(135deg, #3ab5b0 0%, #3d99be 31%, #56317a 100%);
    font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
    padding: 20px;
    box-sizing: border-box;
}

/* 卡片容器 - 居中布局 */
.login-card-container {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    max-width: 450px;
}

/* 登录卡片 - 磨砂玻璃效果 */
.login-card {
    width: 100%;
    background: rgba(255, 255, 255, 0.85);
    /* 半透明白色背景 */
    backdrop-filter: blur(12px);
    /* 磨砂玻璃模糊效果 */
    -webkit-backdrop-filter: blur(12px);
    /* Safari支持 */
    border-radius: 16px;
    /* 圆角 */
    padding: 40px 32px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    border: 1px solid rgba(255, 255, 255, 0.2);
}

/* 头像区域样式 */
.avatar-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 32px;
}

.login-avatar {
    margin-bottom: 16px;
    background: linear-gradient(135deg, #409EFF 0%, #3375b9 100%);
    color: white;
    font-weight: bold;
}

.welcome-text {
    color: #409EFF;
    font-weight: 500;
    margin: 0 0 4px 0;
    font-size: 24px;
}

.welcome-subtext {
    color: #606266;
    margin: 0;
    font-size: 14px;
}

/* 表单样式 */
.login-form {
    width: 100%;
}

/* 输入框样式 */
.form-input {
    width: 100%;
}

/* 修复输入框选中时的样式问题 */
:deep(.el-input__wrapper) {
    transition: all 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
    border-color: #409EFF;
}

:deep(.el-input__wrapper.is-focus) {
    border-color: #409EFF;
    box-shadow: 0 0 0 1px #409EFF;
}

/* 登录状态选择器样式 */
.status-select {
    width: 100%;
}

.status-option {
    display: flex;
    align-items: center;
}

/* 底部区域样式 */
.form-footer {
    margin-top: 20px;
}

.form-links {
    display: flex;
    justify-content: space-between;
    margin-bottom: 24px;
}

.link-item {
    font-size: 14px;
}

.link-item:disabled {
    color: #c0c4cc;
    cursor: not-allowed;
}

/* 登录按钮样式 */
.login-btn {
    width: 100%;
    font-size: 16px;
    height: 44px;
}

/* 加载对话框样式 */
.loading-dialog :deep(.el-dialog) {
    border-radius: 12px;
}

.loading-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 24px 0;
}

.loading-icon {
    color: #409EFF;
    margin-bottom: 16px;
}

.loading-text {
    color: #303133;
    font-size: 16px;
    margin: 0 0 8px 0;
}

.loading-subtext {
    color: #606266;
    font-size: 14px;
    margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
    .login-card {
        padding: 32px 24px;
        max-width: 90%;
    }

    .login-card-container {
        max-width: 95%;
    }

    .welcome-text {
        font-size: 22px;
    }
}

@media (max-width: 480px) {
    .login-card {
        padding: 24px 20px;
    }

    .login-avatar {
        width: 70px;
        height: 70px;
        line-height: 70px;
        font-size: 20px;
    }

    .welcome-text {
        font-size: 20px;
    }

    .form-links {
        flex-direction: column;
        gap: 8px;
        align-items: center;
    }
}
</style>