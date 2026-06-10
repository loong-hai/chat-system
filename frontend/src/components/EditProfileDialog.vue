<!-- components/EditProfileDialog.vue -->
<template>
    <!-- 模板部分完全不变 -->
    <el-dialog v-model="visible" title="编辑个人资料" width="500px" :close-on-click-modal="false" destroy-on-close
        class="edit-profile-dialog">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" label-position="right">
            <!-- 头像上传 -->
            <el-form-item label="头像" class="avatar-form-item">
                <div class="avatar-upload-wrapper">
                    <el-upload class="avatar-uploader" action="#" :auto-upload="false" :show-file-list="false"
                        :on-change="handleAvatarChange" accept=".jpg,.jpeg,.png,.gif,.webp">
                        <div class="avatar-box">
                            <el-avatar  :src="previewAvatar" class="avatar-xxl upload-avatar">
                                {{ form.nickname?.charAt(0) || '?' }}
                            </el-avatar>
                            <div class="avatar-edit-text">修改</div>
                        </div>
                    </el-upload>
                    <div class="avatar-tip">支持 JPG、PNG、GIF、WEBP，最大 5MB</div>
                </div>
            </el-form-item>

            <!-- 昵称 -->
            <el-form-item label="昵称" prop="nickname">
                <el-input v-model="form.nickname" placeholder="请输入昵称" maxlength="50" show-word-limit />
            </el-form-item>

            <!-- 个性签名 -->
            <el-form-item label="个性签名" prop="signature">
                <el-input v-model="form.signature" type="textarea" :rows="3" placeholder="请输入个性签名" maxlength="200"
                    show-word-limit resize="none" />
            </el-form-item>

            <!-- 性别 -->
            <el-form-item label="性别" prop="gender">
                <el-radio-group v-model="form.gender">
                    <el-radio label="0">保密</el-radio>
                    <el-radio label="1">男</el-radio>
                    <el-radio label="2">女</el-radio>
                </el-radio-group>
            </el-form-item>

            <!-- 生日 -->
            <el-form-item label="生日" prop="birthday">
                <el-date-picker v-model="form.birthday" type="date" placeholder="选择生日" format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>

            <!-- 邮箱 -->
            <el-form-item label="邮箱" prop="email">
                <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>

            <!-- 手机号 -->
            <el-form-item label="手机号" prop="phone">
                <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
        </el-form>

        <template #footer>
            <div class="dialog-footer">
                <el-button @click="handleCancel" :disabled="loading">取消</el-button>
                <el-button type="primary" @click="handleSubmit" :loading="loading">
                    保存修改
                </el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script lang="ts" setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
const props = defineProps<{
    modelValue: boolean
}>()

const emit = defineEmits<{
    (e: 'update:modelValue', value: boolean): void
    (e: 'success'): void
}>()

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const avatarFile = ref<File | null>(null)
// ✅ 修正：独立定义 avatarPreview，不放在 form 里
const avatarPreview = ref<string>('')

// 表单数据（纯后端接口字段）
const form = ref({
    nickname: '',
    signature: '',
    gender: '0',
    birthday: '',
    email: '',
    phone: ''
})

// 预览头像计算属性（优先显示本地预览，其次显示原头像）
const previewAvatar = computed(() => {
    // 如果有本地文件预览（通过 FileReader 生成的 base64）
    if (avatarPreview.value) {
        return avatarPreview.value
    }
    // 否则显示当前用户的原头像
    return userStore.currentUser?.avatarUrl || ''
})

// 同步父组件的 v-model
const visible = computed({
    get: () => props.modelValue,
    set: (val) => emit('update:modelValue', val)
})

// 监听打开弹窗，初始化数据
watch(() => props.modelValue, (val) => {
    if (val && userStore.currentUser) {
        const user = userStore.currentUser
        form.value = {
            nickname: user.nickname || '',
            signature: user.signature || '',
            gender: String(user.gender || '0'),
            birthday: user.birthday || '',
            email: user.email || '',
            phone: user.phone || ''
        }
        // ✅ 修正：重置临时状态
        avatarFile.value = null
        avatarPreview.value = ''
    }
})

// 表单验证规则
const rules: FormRules = {
    nickname: [
        { required: true, message: '请输入昵称', trigger: 'blur' },
        { min: 1, max: 50, message: '昵称长度1-50个字符', trigger: 'blur' }
    ],
    email: [
        { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
    ],
    phone: [
        { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
    ]
}

// 处理头像选择
const handleAvatarChange = (file: UploadFile) => {
    const rawFile = file.raw
    if (!rawFile) return

    // 文件类型检查
    const acceptTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
    if (!acceptTypes.includes(rawFile.type)) {
        ElMessage.error('只支持 JPG、PNG、GIF、WEBP 格式的图片')
        return
    }

    // 文件大小检查（5MB = 5 * 1024 * 1024 bytes）
    const maxSize = 5 * 1024 * 1024
    if (rawFile.size > maxSize) {
        ElMessage.error('图片大小不能超过 5MB')
        return
    }

    // ✅ 修正：更新独立的 avatarPreview ref
    const reader = new FileReader()
    reader.onload = (e) => {
        avatarPreview.value = e.target?.result as string
    }
    reader.readAsDataURL(rawFile)

    avatarFile.value = rawFile
    ElMessage.success('头像已选择，点击保存后上传')
}

// 取消
const handleCancel = () => {
    visible.value = false
    avatarFile.value = null
    avatarPreview.value = ''  // ✅ 修正：清空预览
    formRef.value?.resetFields()
}

// 提交
const handleSubmit = async () => {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    loading.value = true

    try {
        // 通过 Store 层更新，自动处理是否有头像文件
        await userStore.updateUserInfo(
            { ...form.value },
            avatarFile.value || undefined
        )

        ElMessage.success('资料更新成功')
        emit('success')
        visible.value = false

        // 清空临时状态
        avatarFile.value = null
        avatarPreview.value = ''  // ✅ 修正：清空预览
    } catch (error: any) {
        ElMessage.error(error.message || '更新失败，请重试')
    } finally {
        loading.value = false
    }
}
</script>

<style scoped>
/* 极简扁平风格 - 蓝白配色 */

/* 对话框基础 */
:deep(.edit-profile-dialog) {
    border-radius: 4px;
}

:deep(.edit-profile-dialog .el-dialog__header) {
    background-color: #1890ff;
    margin: 0;
    padding: 16px 24px;
    border-bottom: 1px solid #e8e8e8;
}

:deep(.edit-profile-dialog .el-dialog__title) {
    color: #fff;
    font-size: 16px;
    font-weight: 500;
    line-height: 1;
}

:deep(.edit-profile-dialog .el-dialog__headerbtn) {
    top: 50%;
    transform: translateY(-50%);
    right: 16px;
}

:deep(.edit-profile-dialog .el-dialog__headerbtn .el-dialog__close) {
    color: rgba(255, 255, 255, 0.8);
    font-size: 16px;
}

:deep(.edit-profile-dialog .el-dialog__headerbtn:hover .el-dialog__close) {
    color: #fff;
}

:deep(.edit-profile-dialog .el-dialog__body) {
    padding: 24px;
    color: #262626;
}

:deep(.edit-profile-dialog .el-dialog__footer) {
    padding: 12px 24px;
    border-top: 1px solid #f0f0f0;
    background-color: #fafafa;
}

/* 表单基础 */
:deep(.el-form-item) {
    margin-bottom: 20px;
}

:deep(.el-form-item__label) {
    color: #595959;
    font-weight: normal;
    padding-right: 12px;
    height: 32px;
    line-height: 32px;
}

/* 输入框样式 - 标准边框 */
:deep(.el-input__wrapper) {
    border-radius: 4px;
    box-shadow: 0 0 0 1px #d9d9d9 inset;
    padding: 0 12px;
    background-color: #fff;
}

:deep(.el-input__wrapper:hover) {
    box-shadow: 0 0 0 1px #1890ff inset;
}

:deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px #1890ff inset;
    border-color: #1890ff;
}

:deep(.el-input__inner) {
    height: 32px;
    color: #262626;
    font-size: 14px;
}

:deep(.el-input__inner::placeholder) {
    color: #bfbfbf;
}

:deep(.el-input__count-inner) {
    background: transparent;
    color: #8c8c8c;
    font-size: 12px;
}

/* 文本域 */
:deep(.el-textarea__inner) {
    border-radius: 4px;
    border: 1px solid #d9d9d9;
    padding: 8px 12px;
    color: #262626;
    font-size: 14px;
    line-height: 1.5;
    background-color: #fff;
}

:deep(.el-textarea__inner:hover) {
    border-color: #1890ff;
}

:deep(.el-textarea__inner:focus) {
    border-color: #1890ff;
    outline: none;
}

:deep(.el-textarea__inner::placeholder) {
    color: #bfbfbf;
}

:deep(.el-textarea .el-input__count) {
    color: #8c8c8c;
    font-size: 12px;
    background: transparent;
}

/* 日期选择器 */
:deep(.el-date-editor.el-input__wrapper) {
    box-shadow: 0 0 0 1px #d9d9d9 inset;
}

:deep(.el-date-editor.el-input__wrapper:hover) {
    box-shadow: 0 0 0 1px #1890ff inset;
}

:deep(.el-date-editor.el-input__wrapper.is-active) {
    box-shadow: 0 0 0 1px #1890ff inset;
}

/* 单选框 */
:deep(.el-radio__input.is-checked .el-radio__inner) {
    border-color: #1890ff;
    background-color: #1890ff;
}

:deep(.el-radio__input.is-checked + .el-radio__label) {
    color: #1890ff;
}

:deep(.el-radio__inner:hover) {
    border-color: #1890ff;
}

:deep(.el-radio__label) {
    padding-left: 6px;
    color: #262626;
}

/* 头像上传区域 */
.avatar-form-item {
    margin-bottom: 24px;
}

.avatar-upload-wrapper {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.avatar-box {
    position: relative;
    display: inline-block;
    cursor: pointer;
    border: 1px dashed #d9d9d9;
    border-radius: 4px;
    padding: 4px;
    transition: all 0.2s;
}

.avatar-box:hover {
    border-color: #1890ff;
}

.upload-avatar {
    display: block;
    border-radius: 4px;
    background-color: #f5f5f5;
    color: #8c8c8c;
    font-size: 32px;
}

.avatar-edit-text {
    position: absolute;
    bottom: 4px;
    left: 4px;
    right: 4px;
    height: 28px;
    line-height: 28px;
    text-align: center;
    background-color: rgba(0, 0, 0, 0.45);
    color: #fff;
    font-size: 12px;
    border-bottom-left-radius: 4px;
    border-bottom-right-radius: 4px;
}

.avatar-tip {
    font-size: 12px;
    color: #8c8c8c;
    line-height: 1.5;
}

/* 底部按钮 */
.dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
}

:deep(.el-button) {
    border-radius: 4px;
    padding: 0 16px;
    height: 32px;
    font-size: 14px;
    transition: all 0.2s;
}

:deep(.el-button--default) {
    border: 1px solid #d9d9d9;
    color: #595959;
    background-color: #fff;
}

:deep(.el-button--default:hover:not(:disabled)) {
    color: #1890ff;
    border-color: #1890ff;
    background-color: #fff;
}

:deep(.el-button--primary) {
    background-color: #1890ff;
    border-color: #1890ff;
    color: #fff;
}

:deep(.el-button--primary:hover:not(:disabled)) {
    background-color: #40a9ff;
    border-color: #40a9ff;
    color: #fff;
}

:deep(.el-button.is-disabled) {
    color: #bfbfbf;
    background-color: #f5f5f5;
    border-color: #d9d9d9;
    cursor: not-allowed;
}

/* 表单验证错误 */
:deep(.el-form-item.is-error .el-input__wrapper) {
    box-shadow: 0 0 0 1px #ff4d4f inset;
}

:deep(.el-form-item.is-error .el-textarea__inner) {
    border-color: #ff4d4f;
}

:deep(.el-form-item__error) {
    color: #ff4d4f;
    font-size: 12px;
    padding-top: 4px;
}
</style>