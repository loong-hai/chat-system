<!-- components/ConversationList.vue -->
<template>
    <div class="conversation-list-root">
        <!-- 顶部栏 -->
        <div class="list-header">
            <div class="header-title">
                <span class="title-text">消息</span>
                <el-badge v-if="chatStore.unreadTotal > 0" :value="chatStore.unreadTotal" class="unread-badge" />
            </div>
            <div class="header-actions">
                <el-icon class="action-icon" @click="handleAdd">
                    <Plus />
                </el-icon>
                <el-icon class="action-icon" @click="handleMore">
                    <MoreFilled />
                </el-icon>
            </div>
        </div>

        <!-- 搜索框 -->
        <div class="search-section">
            <div class="search-input-wrapper">
                <el-icon class="search-icon">
                    <Search />
                </el-icon>
                <el-input v-model="searchKeyword" placeholder="搜索会话" class="search-input" clearable />
            </div>
        </div>

        <!-- 会话列表 -->
        <div class="conversation-content">
            <el-scrollbar v-if="filteredConversations.length > 0">
                <div v-for="conv in filteredConversations" :key="conv.id" class="conversation-item"
                    :class="{ 'is-selected': currentId === conv.id }" @click="handleSelect(conv)">
                    <!-- 头像区域 -->
                    <div class="avatar-wrapper">
                        <el-avatar class="avatar-md" :src="conv.targetAvatar || '/default-avatar.png'" />
                        <div v-if="conv.unreadCount > 0" class="unread-dot">
                            {{ conv.unreadCount > 99 ? '99+' : conv.unreadCount }}
                        </div>
                    </div>

                    <!-- 内容区域 -->
                    <div class="content-wrapper">
                        <div class="content-row">
                            <!-- 修改这里：使用双显示逻辑 -->
                            <div class="target-name" :class="{ 'is-pinned': conv.isPinned }">
                                <template v-if="conv.hasRemark && conv.displayName !== conv.nickname">
                                    <span class="text-title display-name">{{ conv.displayName }}</span>
                                    <span class="nickname">({{ conv.nickname }})</span>
                                </template>
                                <span v-else class="display-name">
                                    {{ conv.nickname || '未知用户' }}
                                </span>
                            </div>
                            <span class="msg-time">{{ formatTime(conv.updatedAt) }}</span>
                        </div>
                        <div class="content-row">
                            <span class="last-message" :class="{ 'is-muted': conv.isMuted }">
                                {{ formatLastMessage(conv.lastMessage) }}
                            </span>
                            <el-icon v-if="conv.isMuted" class="muted-icon">
                                <Mute />
                            </el-icon>
                        </div>
                    </div>
                </div>
            </el-scrollbar>

            <div v-else class="empty-state">
                <el-empty description="暂无会话" :image-size="120" />
            </div>
        </div>

    </div>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue'
import { Search, Plus, MoreFilled, Mute } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import type { Conversation } from '@/types/message'
import { useFriendStore } from '@/stores/friend'
const chatStore = useChatStore()
const searchKeyword = ref('')
const currentId = ref('')
const friendStore = useFriendStore()


// 修改：补充好友信息
const enrichedConversations = computed(() => {
    return chatStore.sortedConversations.map(conv => {
        if (conv.type === 'private') {
            const friend = friendStore.friends.find(f => f.friendId === conv.targetId)
            if (friend) {
                // 统一在这里处理显示名称逻辑
                const hasRemark = !!friend.displayName && friend.displayName !== friend.nickname
                const targetName = hasRemark
                    ? `${friend.displayName} (${friend.nickname})`
                    : (friend.nickname || '未知用户')

                return {
                    ...conv,
                    targetName, // 覆盖为带备注的格式
                    displayName: friend.displayName,
                    nickname: friend.nickname,
                    hasRemark,
                    targetAvatar: friend.avatarUrl || conv.targetAvatar
                }
            }
        }
        return {
            ...conv,
            displayName: conv.targetName,
            nickname: conv.targetName,
            hasRemark: false
        }
    })
})


// 过滤会话
const filteredConversations = computed(() => {
    const list = enrichedConversations.value
    if (!searchKeyword.value.trim()) return list

    const keyword = searchKeyword.value.toLowerCase()
    return list.filter(conv =>
        conv.targetName.toLowerCase().includes(keyword) ||
        (conv.lastMessage?.content || '').toLowerCase().includes(keyword)
    )
})


// 删除第二个 onMounted，只保留这一个
onMounted(async () => {
    if (friendStore.friends.length === 0) {
        await friendStore.initFriendData()
    }
    await chatStore.initConversations()

    // 只同步当前选中的 ID，不要重复添加会话
    if (chatStore.currentConversation) {
        currentId.value = chatStore.currentConversation.id
    } else if (filteredConversations.value.length > 0 && !currentId.value) {
        handleSelect(filteredConversations.value[0]!)
    }
})
// 初始化加载本地数据
// onMounted(async () => {
//     await chatStore.initConversations()
//     // 如果有会话，默认选中第一个
//     if (chatStore.sortedConversations.length > 0 && !currentId.value) {
//         handleSelect(chatStore.sortedConversations[0]!)
//     }
// })

const handleSelect = (conv: Conversation) => {
    currentId.value = conv.id
    chatStore.selectConversation(conv)
    emit('select', conv)
}

const formatLastMessage = (msg?: Conversation['lastMessage']) => {
    if (!msg) return '暂无消息'

    switch (msg.type) {
        case 'IMAGE': return '[图片]'
        case 'VOICE': return '[语音]'
        case 'VIDEO': return '[视频]'
        case 'FILE': return `[文件] ${msg.fileName || ''}`
        case 'LOCATION': return '[位置]'
        default:
            // 截断长文本
            const text = msg.content || ''
            return text.length > 20 ? text.substring(0, 20) + '...' : text
    }
}

const formatTime = (date: Date | string) => {
    const d = new Date(date)
    const now = new Date()
    const isToday = d.toDateString() === now.toDateString()

    if (isToday) {
        return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    }

    const yesterday = new Date(now)
    yesterday.setDate(yesterday.getDate() - 1)
    if (d.toDateString() === yesterday.toDateString()) {
        return '昨天'
    }

    return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

const emit = defineEmits<{
    (e: 'select', conv: Conversation): void
}>()

const handleAdd = () => {
    console.log('添加会话')
}

const handleMore = () => {
    console.log('更多操作')
}
</script>

<style scoped>
.conversation-list-root {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #ffffff;
    border-right: 1px solid #e4e7ed;
}

.list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px;
    border-bottom: 1px solid #f0f0f0;
}

.target-name {
    /* font-size: 14px; */
    font-weight: 500;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 60%;
    display: flex;
    align-items: baseline;
    flex-wrap: nowrap;
}

.target-name .display-name {
    /* font-size: 14px; */
    font-weight: 500;
    color: #303133;
    line-height: 1;
}

.target-name .nickname {
    font-size: 11px;
    color: #909399;
    font-weight: 400;
    margin-left: 4px;
    line-height: 1;
    flex-shrink: 0;
}

.target-name.is-pinned .display-name {
    font-weight: 600;
}


.header-title {
    display: flex;
    align-items: center;
    gap: 8px;
}

.title-text {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
}

.unread-badge :deep(.el-badge__content) {
    background-color: #f56c6c;
    border: none;
    font-size: 11px;
    height: 18px;
    line-height: 18px;
    padding: 0 6px;
}

.header-actions {
    display: flex;
    gap: 16px;
}

.action-icon {
    font-size: 20px;
    color: #606266;
    cursor: pointer;
    padding: 4px;
    border-radius: 4px;
    transition: all 0.3s;
}

.action-icon:hover {
    background: #f5f7fa;
    color: #409eff;
}

.search-section {
    padding: 12px 16px;
    border-bottom: 1px solid #f0f0f0;
}

.search-input-wrapper {
    position: relative;
    display: flex;
    align-items: center;
}

.search-icon {
    position: absolute;
    left: 12px;
    color: #909399;
    z-index: 1;
}

.search-input :deep(.el-input__wrapper) {
    padding-left: 32px;
    background: #f5f7fa;
    box-shadow: none;
}

.conversation-content {
    flex: 1;
    overflow: hidden;
    background: #ffffff;
}

.conversation-item {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    cursor: pointer;
    transition: all 0.2s;
    position: relative;
}

.conversation-item:hover {
    background: #f5f7fa;
}

.conversation-item.is-selected {
    background: #ecf5ff;
}

.avatar-wrapper {
    position: relative;
    margin-right: 12px;
    flex-shrink: 0;
}

.unread-dot {
    position: absolute;
    top: -4px;
    right: -4px;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    background: #f56c6c;
    color: #fff;
    font-size: 11px;
    border-radius: 9px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px solid #fff;
    box-sizing: border-box;
}

.content-wrapper {
    flex: 1;
    min-width: 0;
    overflow: hidden;
}

.content-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
}

.content-row:last-child {
    margin-bottom: 0;
}

.target-name {
    font-size: 14px;
    font-weight: 500;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 60%;
}

.target-name.is-pinned {
    font-weight: 600;
}

.msg-time {
    font-size: 12px;
    color: #909399;
    flex-shrink: 0;
}

.last-message {
    font-size: 13px;
    color: #606266;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 85%;
}

.last-message.is-muted {
    color: #c0c4cc;
}

.muted-icon {
    font-size: 12px;
    color: #c0c4cc;
}

.empty-state {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
}
</style>