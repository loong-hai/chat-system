<template>
    <!-- 主页面容器：渐变背景和居中布局 -->
    <div class="register-page">
        <!-- 注册卡片容器 -->
        <div class="register-card-container">
            <!-- 磨砂玻璃效果卡片 -->
            <div class="register-card">
                <!-- 标题和返回链接 -->
                <div class="header-section">
                    <h2 class="register-title">用户注册</h2>
                    <el-link type="primary" :underline="false" @click="goToLogin" class="back-link">
                        <el-icon>
                            <ArrowLeft />
                        </el-icon> 已有账户？
                    </el-link>
                </div>

                <!-- 注册表单 -->
                <el-form ref="registerFormRef" :model="formState" :rules="formRules" label-width="0"
                    class="register-form" @submit.prevent="handleRegister">
                    <!-- 用户名输入框（新增） -->
                    <el-form-item prop="username">
                        <el-input v-model="formState.username" :prefix-icon="User" placeholder="请输入用户名（必填）" size="large"
                            :disabled="loading" clearable maxlength="20" show-word-limit class="form-input"
                            @blur="checkUsernameUnique" />
                    </el-form-item>

                    <!-- 昵称输入框 -->
                    <el-form-item prop="nickname">
                        <el-input v-model="formState.nickname" :prefix-icon="User" placeholder="请输入昵称（必填）" size="large"
                            :disabled="loading" clearable maxlength="20" show-word-limit class="form-input"
                            @blur="checkNicknameUnique" />
                    </el-form-item>

                    <!-- 密码输入框 -->
                    <el-form-item prop="password">
                        <el-input v-model="formState.password" :prefix-icon="Lock" type="password"
                            placeholder="请输入密码（必填）" size="large" show-password :disabled="loading" clearable
                            class="form-input" />
                    </el-form-item>

                    <!-- 确认密码输入框 -->
                    <el-form-item prop="confirmPassword">
                        <el-input v-model="formState.confirmPassword" :prefix-icon="Lock" type="password"
                            placeholder="请再次输入密码（必填）" size="large" show-password :disabled="loading" clearable
                            class="form-input" />
                    </el-form-item>

                    <!-- 邮箱输入框 -->
                    <el-form-item prop="email">
                        <el-input v-model="formState.email" :prefix-icon="Message" placeholder="请输入邮箱（选填）" size="large"
                            :disabled="loading" clearable class="form-input" @blur="checkEmailUnique" />
                    </el-form-item>

                    <!-- 手机号输入框 -->
                    <el-form-item prop="phone">
                        <el-input v-model="formState.phone" :prefix-icon="Phone" placeholder="请输入手机号（选填）" size="large"
                            :disabled="loading" clearable maxlength="11" class="form-input" @blur="checkPhoneUnique" />
                    </el-form-item>

                    <!-- 协议同意 -->
                    <el-form-item prop="agreed">
                        <el-checkbox v-model="formState.agreed" :disabled="loading">
                            我已阅读并同意
                            <el-link type="primary" :underline="false" @click="showUserAgreement">
                                《用户协议》
                            </el-link>
                            和
                            <el-link type="primary" :underline="false" @click="showPrivacyPolicy">
                                《隐私政策》
                            </el-link>
                        </el-checkbox>
                    </el-form-item>

                    <!-- 注册按钮 -->
                    <el-button type="primary" size="large" :loading="loading" native-type="submit" class="register-btn"
                        :icon="loading ? '' : UserFilled">
                        {{ loading ? '注册中...' : '立即注册' }}
                    </el-button>

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
                <p class="loading-text">正在注册，请稍候...</p>
                <p class="loading-subtext">系统正在处理您的注册信息</p>
            </div>
        </el-dialog>

        <!-- 用户协议对话框 -->
        <el-dialog v-model="showAgreementDialog" title="用户协议" width="600px" center>
            <div class="agreement-content">
                <p>这里是用户协议的内容...</p>
                <!-- 实际项目中这里应该填充完整的用户协议内容 -->
            </div>
            <template #footer>
                <el-button type="primary" @click="showAgreementDialog = false">
                    我已阅读
                </el-button>
            </template>
        </el-dialog>

        <!-- 隐私政策对话框 -->
        <el-dialog v-model="showPrivacyDialog" title="隐私政策" width="600px" center>
            <div class="privacy-content">
                <p>这里是隐私政策的内容...</p>
                <!-- 实际项目中这里应该填充完整的隐私政策内容 -->
            </div>
            <template #footer>
                <el-button type="primary" @click="showPrivacyDialog = false">
                    我已阅读
                </el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Loading, UserFilled, Message, Phone, ArrowLeft } from '@element-plus/icons-vue'
import type { RegisterDTO } from '@/types/user'

// 路由实例
const router = useRouter()

// 响应式数据
const registerFormRef = ref<FormInstance>()
const loading = ref(false)
const showLoadingDialog = ref(false)
const showAgreementDialog = ref(false)
const showPrivacyDialog = ref(false)

// 表单数据类型定义
interface RegisterFormState {
    username: string
    nickname: string
    password: string
    confirmPassword: string
    email: string
    phone: string
    agreed: boolean
}

// 表单数据
const formState = reactive<RegisterFormState>({
    username: '',
    nickname: '',
    password: '',
    confirmPassword: '',
    email: '',
    phone: '',
    agreed: false
})

// 表单验证规则
const formRules: FormRules<RegisterFormState> = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, max: 20, message: '用户名长度应为3-20个字符', trigger: 'blur' },
        {
            pattern: /^[a-zA-Z0-9_\u4e00-\u9fa5]+$/,
            message: '用户名只能包含中文、英文、数字和下划线',
            trigger: 'blur'
        }
    ],
    nickname: [
        { required: true, message: '请输入昵称', trigger: 'blur' },
        { min: 1, max: 50, message: '昵称长度应为1-50个字符', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, max: 20, message: '密码长度应为6-20个字符', trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, message: '请再次输入密码', trigger: 'blur' },
        {
            validator: (_rule, value, callback) => {
                if (value !== formState.password) {
                    callback(new Error('两次输入的密码不一致'))
                } else {
                    callback()
                }
            },
            trigger: 'blur'
        }
    ],
    email: [
        {
            pattern: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
            message: '请输入有效的邮箱地址',
            trigger: 'blur'
        }
    ],
    phone: [
        {
            pattern: /^1[3-9]\d{9}$/,
            message: '请输入有效的手机号码',
            trigger: 'blur'
        }
    ],
    agreed: [
        {
            validator: (_rule, value, callback) => {
                if (!value) {
                    callback(new Error('请同意用户协议和隐私政策'))
                } else {
                    callback()
                }
            },
            trigger: 'change'
        }
    ]
}

/**
 * 模拟延迟函数
 */
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

/**
 * 检查用户名唯一性
 */
const checkUsernameUnique = async () => {
    if (!formState.username || formState.username.length < 3) return
    // 这里可以调用后端接口检查用户名唯一性
}

/**
 * 检查昵称唯一性
 */
const checkNicknameUnique = async () => {
    if (!formState.nickname || formState.nickname.length < 1) return
    // 这里可以调用后端接口检查昵称唯一性
}

/**
 * 检查邮箱唯一性
 */
const checkEmailUnique = async () => {
    if (!formState.email) return
    // 这里可以调用后端接口检查邮箱唯一性
}

/**
 * 检查手机号唯一性
 */
const checkPhoneUnique = async () => {
    if (!formState.phone) return
    // 这里可以调用后端接口检查手机号唯一性
}

/**
 * 处理注册提交
 */
const handleRegister = async () => {
    // 表单验证
    const valid = await registerFormRef.value?.validate().catch(() => false)
    if (!valid) return

    // 开始注册流程
    loading.value = true
    showLoadingDialog.value = true

    try {
        // 构建注册数据
        const registerData: RegisterDTO = {
            username: formState.username.trim(),
            password: formState.password,
            nickname: formState.nickname.trim(),
            email: formState.email.trim() || undefined,
            phone: formState.phone.trim() || undefined,
            signature: ''
        }

        // 调用我们封装的注册API
        const tokenData = await authApi.register(registerData)

        // 注册成功
        await delay(500)
        ElNotification.success({
            title: '注册成功',
            message: `欢迎加入，${tokenData.nickname}！`,
            duration: 3000
        })

        // 保存登录信息
        localStorage.setItem('access_token', tokenData.accessToken)
        localStorage.setItem('session_id', tokenData.sessionId)
        localStorage.setItem('user_id', String(tokenData.userId))
        localStorage.setItem('username', tokenData.username)
        localStorage.setItem('nickname', tokenData.nickname)

        if (tokenData.avatarUrl) {
            localStorage.setItem('avatar_url', tokenData.avatarUrl)
        }

        // 延迟跳转到首页
        await delay(1500)
        router.push('/')

    } catch (error: any) {
        // 错误处理
        if (error.message) {
            // 来自业务逻辑抛出的错误
            ElMessage.error(error.message)
        } else {
            ElMessage.error('注册失败，请稍后重试')
        }
    } finally {
        loading.value = false
        showLoadingDialog.value = false
    }
}

/**
 * 跳转到登录页
 */
const goToLogin = () => {
    router.push('/')
}

/**
 * 显示用户协议
 */
const showUserAgreement = () => {
    showAgreementDialog.value = true
}

/**
 * 显示隐私政策
 */
const showPrivacyPolicy = () => {
    showPrivacyDialog.value = true
}
</script>

<style scoped>
/* 主页面样式 - 渐变背景和全屏布局 */
.register-page {
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
.register-card-container {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    max-width: 450px;
}

/* 注册卡片 - 磨砂玻璃效果 */
.register-card {
    width: 100%;
    background: rgba(255, 255, 255, 0.85);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border-radius: 16px;
    padding: 40px 32px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    border: 1px solid rgba(255, 255, 255, 0.2);
}

/* 头部区域样式 */
.header-section {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 32px;
}

.register-title {
    color: #409EFF;
    font-weight: 500;
    margin: 0;
    font-size: 24px;
}

.back-link {
    font-size: 14px;
}

/* 表单样式 */
.register-form {
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

/* 协议复选框样式 */
:deep(.el-checkbox) {
    align-items: flex-start;
}

:deep(.el-checkbox__label) {
    font-size: 14px;
    white-space: normal;
    line-height: 1.5;
}

/* 注册按钮样式 */
.register-btn {
    width: 100%;
    font-size: 16px;
    height: 44px;
    margin-top: 10px;
}

/* 登录链接样式 */
.login-link {
    text-align: center;
    margin-top: 20px;
    font-size: 14px;
    color: #606266;
}

.login-link span {
    margin-right: 6px;
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

/* 协议内容样式 */
.agreement-content,
.privacy-content {
    max-height: 400px;
    overflow-y: auto;
    line-height: 1.6;
    padding: 10px 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
    .register-card {
        padding: 32px 24px;
        max-width: 90%;
    }

    .register-card-container {
        max-width: 95%;
    }

    .register-title {
        font-size: 22px;
    }

    .header-section {
        flex-direction: column;
        align-items: flex-start;
        gap: 10px;
    }

    .back-link {
        align-self: flex-start;
    }
}

@media (max-width: 480px) {
    .register-card {
        padding: 24px 20px;
    }

    .register-title {
        font-size: 20px;
    }

    :deep(.el-checkbox__label) {
        font-size: 13px;
    }
}

/* 关键修复：直接作用于包裹整个复选框的容器，强制其内部所有行内元素垂直居中 */
:deep(.el-form-item__content .el-checkbox) {
    display: inline-flex;
    /* 使用flex布局，便于对齐 */
    align-items: center;
    /* 垂直居中对齐所有子项 */
}

/* 修复标签文本的基线对齐，确保文字和链接在同一水平线 */
:deep(.el-checkbox__label) {
    display: inline-flex;
    align-items: center;
    vertical-align: middle;
    /* 覆盖默认的baseline */
}

/* 特别处理链接元素，确保它们和旁边的普通文本完美对齐 */
:deep(.el-checkbox__label .el-link) {
    display: inline-flex;
    align-items: center;
    vertical-align: middle;
    /* 这是最关键的一行！ */
}
</style>