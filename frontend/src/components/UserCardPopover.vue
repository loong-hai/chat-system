<!-- components/UserCardPopover.vue -->
<template>
    <el-popover :placement="placement" :width="280" trigger="hover" :show-after="500" popper-class="user-card-popover">
        <!-- 触发器：默认插槽 -->
        <template #reference>
            <slot />
        </template>

        <!-- 弹窗内容 -->
        <div class="user-card-content">
            <!-- 头部：头像和基本信息 -->
            <div class="card-header">
                <el-avatar :src="userInfo?.avatarUrl" class="avatar-lg card-avatar">
                    {{ userInfo?.nickname?.charAt(0) || '?' }}
                </el-avatar>
                <div class="header-info">
                    <div class="nickname">{{ userInfo?.nickname || userInfo?.username }}</div>
                    <div class="user-id">ID: {{ userInfo?.userId || userInfo?.userId }}</div>
                    <el-tag :type="getStatusType(userInfo?.onlineStatus)" size="small" effect="dark" class="status-tag">
                        {{ userInfo?.onlineStatusText || '离线' }}
                    </el-tag>
                </div>
            </div>

            <!-- 签名 -->
            <div class="signature-section" v-if="userInfo?.signature">
                <el-text class="signature-text" :line-clamp="2">
                    <el-icon>
                        <Document />
                    </el-icon>
                    {{ userInfo.signature }}
                </el-text>
            </div>

            <!-- 好友额外信息 -->
            <div v-if="type === 'friend'" class="friend-extra">
                <div class="extra-item" v-if="userInfo?.remark">
                    <span class="label">备注：</span>
                    <span class="value">{{ userInfo.remark }}</span>
                </div>
                <div class="extra-item" v-if="userInfo?.groupName">
                    <span class="label">分组：</span>
                    <span class="value">{{ userInfo.groupName }}</span>
                </div>
            </div>

            <!-- 操作按钮 -->
            <div class="action-buttons">
                <!-- 自己：编辑资料 -->
                <template v-if="type === 'self'">
                    <el-button type="primary" size="small" @click="handleEditProfile">
                        <el-icon>
                            <Edit />
                        </el-icon>
                        编辑资料
                    </el-button>
                </template>

                <!-- 好友：发送消息 -->
                <template v-if="type === 'friend'">
                    <el-button type="primary" size="small" @click="handleSendMessage">
                        <el-icon>
                            <ChatDotRound />
                        </el-icon>
                        发送消息
                    </el-button>
                </template>
            </div>
        </div>
    </el-popover>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { useFriendStore } from '@/stores/friend'
import { Edit, ChatDotRound, Document } from '@element-plus/icons-vue'

const props = defineProps<{
    userId: number
    type: 'self' | 'friend'
    placement?: string
}>()

const emit = defineEmits<{
    (e: 'edit-profile'): void
    (e: 'send-message', userId: number): void
}>()

const userStore = useUserStore()
const friendStore = useFriendStore()

// 统一数据结构，避免联合类型问题
interface CardUserInfo {
    userId: number
    username: string
    nickname: string
    avatarUrl?: string
    signature?: string
    onlineStatus?: number
    onlineStatusText?: string
    remark?: string
    groupName?: string
}

// 获取用户信息（转换为统一格式）
const userInfo = computed<CardUserInfo | undefined>(() => {
    if (props.type === 'self') {
        const user = userStore.currentUser
        if (!user) return undefined
        return {
            userId: user.userId,
            username: user.username,
            nickname: user.nickname,
            avatarUrl: user.avatarUrl,
            signature: user.signature,
            onlineStatus: user.onlineStatus,
            onlineStatusText: user.onlineStatusText,
            remark: undefined,
            groupName: undefined
        }
    } else {
        const friend = friendStore.friends.find(f => f.friendId === props.userId)
        if (!friend) return undefined
        return {
            userId: friend.friendId, // 映射 friendId 为 userId
            username: friend.username,
            nickname: friend.nickname,
            avatarUrl: friend.avatarUrl,
            signature: friend.signature,
            onlineStatus: friend.onlineStatus,
            onlineStatusText: friend.onlineStatusText,
            remark: friend.remark,
            groupName: friend.groupName
        }
    }
})

// 状态类型映射
const getStatusType = (status: number | undefined): 'success' | 'danger' | 'warning' | 'info' => {
    switch (status) {
        case 1: return 'success'
        case 2: return 'danger'
        case 3: return 'warning'
        default: return 'info'
    }
}

const handleEditProfile = () => {
    emit('edit-profile')
}

const handleSendMessage = () => {
    emit('send-message', props.userId)
}
</script>

<style scoped>
.user-card-content {
    padding: 4px;
}

.card-header {
    display: flex;
    gap: 12px;
    margin-bottom: 12px;
}

.card-avatar {
    flex-shrink: 0;
    border: 2px solid #f0f0f0;
}

.header-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 4px;
}

.nickname {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.user-id {
    font-size: 12px;
    color: #909399;
}

.status-tag {
    width: fit-content;
    margin-top: 2px;
}

.signature-section {
    margin: 12px 0;
    padding: 8px;
    background: #f5f7fa;
    border-radius: 6px;
}

.signature-text {
    font-size: 13px;
    color: #606266;
    display: flex;
    align-items: flex-start;
    gap: 4px;
}

.signature-text .el-icon {
    margin-top: 2px;
    flex-shrink: 0;
}

.friend-extra {
    margin: 12px 0;
    padding-top: 12px;
    border-top: 1px solid #ebeef5;
}

.extra-item {
    display: flex;
    align-items: center;
    margin-bottom: 6px;
    font-size: 13px;
}

.extra-item .label {
    color: #909399;
    margin-right: 4px;
}

.extra-item .value {
    color: #606266;
    font-weight: 500;
}

.action-buttons {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid #ebeef5;
    display: flex;
    justify-content: center;
}

.action-buttons .el-button {
    width: 100%;
}
</style>