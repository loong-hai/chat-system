<template>
    <div class="friend-panel-wrapper">
        <el-splitter class="friendListAndChatArea">
            <el-splitter-panel custom-class="friendListPanel" class="friendList" size="30%" min="20%" max="45%">

                <!-- 第一层：始终置顶的搜索框 -->
                <div class="sticky-search-header">
                    <div class="search-container">
                        <div class="input-wrapper">
                            <el-icon class="search-icon">
                                <Search />
                            </el-icon>
                            <el-input v-model="searchKeyword" class="search-input" placeholder="搜索"
                                @keyup.enter="handleSearch" />
                        </div>
                        <el-button class="add-button" :icon="Plus" @click="handleAdd" />
                    </div>
                </div>

                <!-- 第二层 & 第三层 & 第四层的容器 -->
                <div class="scrollable-content-area">
                    <!-- 第二层：申请按钮 -->
                    <div class="application-buttons">
                        <!-- 好友申请按钮带角标 -->
                        <div class="switch-button" @click="showFriendRequests">
                            <div class="button-content">
                                <span class="button-text">好友申请</span>
                                <el-badge :value="pendingRequestsCount" :hidden="pendingRequestsCount === 0" :max="99"
                                    type="danger" class="request-badge" />
                            </div>
                            <el-icon class="arrow-icon">
                                <Right />
                            </el-icon>
                        </div>
                        <div class="switch-button" @click="switchPanel('group')">
                            <span class="button-text">群聊申请</span>
                            <el-icon class="arrow-icon">
                                <Right />
                            </el-icon>
                        </div>
                    </div>

                    <!-- 第三层：滚动到顶后固定的列表切换 -->
                    <div class="sticky-list-toggle">
                        <div class="toggle-switch">
                            <div class="toggle-button" :class="{ 'active': activeTab === 'friends' }"
                                @click="switchTab('friends')">
                                好友列表
                            </div>
                            <div class="toggle-button" :class="{ 'active': activeTab === 'groups' }"
                                @click="switchTab('groups')">
                                群列表
                            </div>
                        </div>
                    </div>

                    <!-- 在 list-content-panel 中替换现有内容 -->
                    <el-scrollbar class="list-content-panel" ref="scrollContainer">
                        <!-- 加载状态 -->
                        <div v-if="loading" class="loading-state">
                            <el-icon class="loading-icon">
                                <Loading />
                            </el-icon>
                            加载好友列表中...
                        </div>

                        <!-- 好友列表 -->
                        <div v-else-if="activeTab === 'friends'" class="friend-groups-container">
                            <div v-for="(group, _groupIndex) in friendGroups" :key="group.groupId"
                                class="friend-group-wrapper">
                                <!-- 分组标题 -->
                                <div class="group-header">
                                    <div class="group-title" @click="toggleGroup(group.groupId)"
                                        :class="{ expanded: isGroupExpanded(group.groupId) }">
                                        <el-icon class="expand-icon">
                                            <ArrowRight />
                                        </el-icon>
                                        <span class="group-name">{{ group.groupName }}</span>
                                        <span class="group-count">{{ group.onlineCount }}/{{ group.count }}</span>
                                    </div>
                                </div>

                                <!-- 好友列表 -->
                                <ElCollapseTransition>
                                    <div v-show="isGroupExpanded(group.groupId)" class="friend-items">

                                        <div v-for="friend in group.friends" :key="friend.relationId">
                                            <UserCardPopover :user-id="friend.friendId" type="friend" placement="right"
                                                trigger="hover" @send-message="handleSendMessageFromCard">

                                                <!-- 整个好友项作为触发区域 -->
                                                <div class="friend-item" @click="handleFriendClick(friend)"
                                                    @dblclick="handleFriendDoubleClick(friend)">

                                                    <!-- 头像区域：移除 @click.stop，防止阻止事件冒泡导致双击失效 -->
                                                    <div class="friend-avatar-wrapper">
                                                        <el-avatar
                                                            :src="friend.avatarUrl || '/default-avatar.png'"
                                                            class="avatar-md friend-avatar">
                                                            {{ getAvatarText(friend) }}
                                                        </el-avatar>
                                                        <div class="status-indicator" :class="getStatusClass(friend)" />
                                                    </div>

                                                    <!-- 好友信息（保持不变） -->
                                                    <div class="friend-info">
                                                        <div class="name-line">
                                                            <span class="display-name">{{ getDisplayName(friend)
                                                                }}</span>
                                                            <span v-if="shouldShowOriginalName(friend)"
                                                                class="original-name">
                                                                ({{ friend.nickname || friend.username }})
                                                            </span>
                                                        </div>
                                                        <div class="status-line">
                                                            <span class="online-status-text">[{{ friend.onlineStatusText
                                                                || '离线' }}]</span>
                                                            <span class="signature">{{ friend.signature || '暂无签名'
                                                                }}</span>
                                                        </div>
                                                    </div>
                                                </div>
                                            </UserCardPopover>
                                        </div>

                                        <!-- 空状态 -->
                                        <div v-if="group.friends.length === 0" class="empty-group">
                                            该分组暂无好友
                                        </div>
                                    </div>
                                </ElCollapseTransition>
                            </div>
                        </div>

                        <!-- 群列表（占位） -->
                        <div v-else class="group-list-content">
                            <el-empty description="群聊功能开发中" :image-size="100" />
                        </div>
                    </el-scrollbar>
                </div>

            </el-splitter-panel>

            <el-splitter-panel class="chatArea" size="70%">
                <!-- 好友申请列表面板 -->
                <div v-if="panelType === 'requests'" style="height: 100%;">
                    <FriendRequestPanel @close="panelType = 'info'" />
                </div>

                <!-- 原有好友信息面板 -->
                <div v-else-if="currentFriendId && panelType === 'info'" style="height: 100%;">
                    <FriendInfoPanel :friend-id="currentFriendId" @send-message="handleSendMessage"
                        @share="console.log('分享成功')" />
                </div>

                <!-- 原有聊天窗口 -->
                <div v-else-if="currentFriendId && panelType === 'chat'" style="height: 100%;">
                    <ChatWindow :friend-id="currentFriendId" />
                </div>

                <!-- 空状态 -->
                <div v-else class="empty-chat">
                    <el-empty description="单击好友查看信息，双击好友开始聊天" :image-size="200" />
                </div>
            </el-splitter-panel>
        </el-splitter>
    </div>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import {
    Search,
    Plus,
    Right,
    ArrowRight,
    Loading
} from '@element-plus/icons-vue'
import { ElCollapseTransition } from 'element-plus'
import { useFriendStore } from '@/stores/friend'
import type { FriendVO } from '@/types/friend'
import FriendRequestPanel from '@/components/FriendRequestPanel.vue'
// 导入组件
import FriendInfoPanel from '@/components/FriendInfoPanel.vue'
import ChatWindow from '@/components/ChatWindow.vue'
import { useAuthStore } from '@/stores/auth'
import { wsService } from '@/services/websocketService'

import UserCardPopover from '@/components/UserCardPopover.vue'

// 计算待处理申请数（使用 store 中的 getter）
const pendingRequestsCount = computed(() => friendStore.pendingRequestsCount)


// 处理发送消息
const handleSendMessageFromCard = (friendId: number) => {
  handleSendMessage(friendId)  // 现在会触发 switch-to-chat
}

// 状态变量
const friendStore = useFriendStore()
const authStore = useAuthStore()
const scrollContainer = ref<HTMLElement>()
const searchKeyword = ref('')
const activeTab = ref<'friends' | 'groups'>('friends')
const loading = ref(false)
const currentFriendId = ref<number | null>(null)
const panelType = ref<'info' | 'chat' | 'requests'>('info')  // 添加 'requests'
const lastClickTime = ref<number>(0)
const CLICK_DOUBLE_THRESHOLD = 300 // 双击时间阈值（毫秒）

const expandedGroups = ref<Record<string, boolean>>({})

// 计算属性
const friendGroups = computed(() => {
    return friendStore.displayGroups.map(group => ({
        ...group,
        onlineCount: group.friends.filter(friend => {
            // 优先使用 isVisibleOnline（后端重构后的字段，考虑了隐身状态）
            // 如果不存在则回退到 isOnline（兼容旧数据）
            const isActuallyOnline = friend.isVisibleOnline !== undefined
                ? friend.isVisibleOnline
                : friend.isOnline
            return isActuallyOnline
        }).length
    }))
})

// 显示好友申请列表
const showFriendRequests = () => {
    currentFriendId.value = null  // 清除当前选中的好友
    panelType.value = 'requests'  // 切换到申请列表面板
    // 可选：触发加载数据（FriendRequestPanel 组件内部会自己加载）
}


// 方法
const isGroupExpanded = (groupId: number | 'ungrouped'): boolean => {
    const key = getGroupKey(groupId)
    return expandedGroups.value[key] || false
}

const toggleGroup = async (groupId: number | 'ungrouped') => {
    const key = getGroupKey(groupId)
    expandedGroups.value[key] = !expandedGroups.value[key]

    await nextTick()

    if (expandedGroups.value[key] && scrollContainer.value) {
        const groupElement = document.querySelector(`[data-group-id="${key}"]`)
        if (groupElement) {
            groupElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
        }
    }
}

const getGroupKey = (groupId: number | 'ungrouped'): string => {
    return typeof groupId === 'number' ? `group_${groupId}` : 'ungrouped'
}

// 好友点击处理
const handleFriendClick = async (friend: FriendVO) => {
  const now = Date.now()

  if (now - lastClickTime.value < CLICK_DOUBLE_THRESHOLD) {
    // 双击 - 跳转到消息栏并打开聊天
    emit('switch-to-chat', friend.friendId)  // 修改这行
  } else {
    // 单击 - 保持原有逻辑，显示好友信息
    panelType.value = 'info'
    currentFriendId.value = friend.friendId
  }

  lastClickTime.value = now
}

// 好友双击处理（单独方法，如果需要单独绑定）
const handleFriendDoubleClick = (_friend: FriendVO) => {
    // 双击逻辑已经在 handleFriendClick 中处理
    // 这里可以留空或用于其他目的
}

// 发送消息处理
const handleSendMessage = (friendId: number) => {
  emit('switch-to-chat', friendId)
  
  // 确保 WebSocket 已连接
  if (!wsService.isConnected && authStore.tokenData?.accessToken) {
    wsService.connect(authStore.tokenData.accessToken)
  }
}

// 事件发射器
const emit = defineEmits<{
    (e: 'switch', panelType: 'friend' | 'group'): void
    (e: 'toggle', tabType: 'friends' | 'groups'): void
    (e: 'switch-to-chat', friendId: number): void  // 新增
}>()

// 生命周期
onMounted(async () => {
    loading.value = true
    try {
        await friendStore.initFriendData()
    } catch (error) {
        console.error('加载好友数据失败:', error)
    } finally {
        loading.value = false
    }
})

// 其他方法
const handleSearch = () => {
    if (searchKeyword.value.trim()) {
        friendStore.searchFriends(searchKeyword.value)
    }
}

const handleAdd = () => {
    console.log('点击添加按钮')
}

const switchPanel = (type: 'friend' | 'group') => {
    emit('switch', type)
}

const switchTab = (tab: 'friends' | 'groups') => {
    if (activeTab.value === tab) return
    activeTab.value = tab
    emit('toggle', tab)
}

const getAvatarText = (friend: FriendVO) => {
    return friend.nickname?.charAt(0) || friend.username?.charAt(0) || '?'
}

const getDisplayName = (friend: FriendVO) => {
    return friend.remark || friend.nickname || friend.username
}

const shouldShowOriginalName = (friend: FriendVO): boolean => {
    return !!(friend.remark && friend.remark !== friend.nickname)
}

const getStatusClass = (friend: FriendVO) => {
    return friendStore.getFriendStatusClass(friend)
}
</script>

<style scoped>
/* 申请按钮内容布局 */
.button-content {
    display: flex;
    align-items: center;
    gap: 8px;
}

/* 好友申请角标样式 */
.request-badge :deep(.el-badge__content) {
    position: relative;
    top: 0;
    right: 0;
    transform: translateY(0);
    font-size: 11px;
    height: 16px;
    line-height: 16px;
    padding: 0 5px;
    border-radius: 8px;
}

/* 好友信息界面 */
.empty-chat {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #f0f7ff 0%, #e3f2fd 100%);
}

.chatArea {
    background: #f5f5f5;
    overflow: hidden;
}


/* ==================== 主布局容器 ==================== */
.friendListAndChatArea {
    width: 100%;
    height: 100%;
}

.friendList {
    height: 100%;
    width: 100%;
    display: flex;
    flex-direction: column;
    background: #ffffff;
}

/* ==================== 多层粘性滚动布局 ==================== */
.sticky-search-header {
    position: sticky;
    top: 0;
    z-index: 10;
    background: #ffffff;
    padding: 16px 16px 12px;
    flex-shrink: 0;
}

.search-container {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
}

.input-wrapper {
    flex: 1;
    position: relative;
    height: 32px;
}

.search-icon {
    position: absolute;
    left: 10px;
    top: 50%;
    transform: translateY(-50%);
    z-index: 2;
    color: #909399;
    font-size: 14px;
}

.search-input :deep(.el-input__wrapper) {
    width: 100%;
    height: 32px;
    padding-left: 32px;
    background: #f5f7fa;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
}

.search-input :deep(.el-input__wrapper:hover) {
    border-color: #c0c4cc;
}

.search-input :deep(.el-input__wrapper.is-focus) {
    border-color: #409eff;
    box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.2);
}

.add-button {
    width: 32px;
    height: 32px;
    padding: 0;
    border-radius: 4px;
    background: #f5f7fa;
    border: 1px solid #e4e7ed;
    color: #606266;
}

.add-button:hover {
    background: #409eff;
    border-color: #409eff;
    color: #ffffff;
}

/* 滚动容器 */
.scrollable-content-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    background: #ffffff;
    min-height: 0;
}

/* 申请按钮区域 */
.application-buttons {
    flex-shrink: 0;
}

.switch-button {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    cursor: pointer;
}

.switch-button:hover {
    background: #f5f5f5;
}

.button-text {
    font-size: 14px;
    color: #333333;
}

.arrow-icon {
    color: #999999;
    font-size: 12px;
}

/* 列表切换 */
.sticky-list-toggle {
    position: sticky;
    top: 60px;
    z-index: 9;
    background: #ffffff;
    padding: 12px 16px;
    flex-shrink: 0;
}

.toggle-switch {
    display: flex;
    width: 100%;
    height: 36px;
    background: #f5f7fa;
    border-radius: 6px;
    padding: 2px;
}

.toggle-button {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    color: #606266;
    cursor: pointer;
    border-radius: 4px;
}

.toggle-button.active {
    background: #ffffff;
    color: #333333;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.toggle-button:hover:not(.active) {
    color: #409eff;
}

/* 内容区域 */
.list-content-panel {
    flex: 1;
    min-height: 0;
}

/* 加载状态 */
.loading-state {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
    color: #999999;
    font-size: 14px;
}

.loading-icon {
    margin-right: 8px;
    animation: rotate 2s linear infinite;
}

@keyframes rotate {
    from {
        transform: rotate(0deg);
    }

    to {
        transform: rotate(360deg);
    }
}

/* 好友分组容器 */
.friend-groups-container {
    padding: 0;
}

.friend-group-wrapper {
    margin: 0;
}

/* 分组标题 */
.group-header {
    background: #ffffff;
    cursor: pointer;
    border-bottom: 1px solid #f0f0f0;
}

.group-title {
    display: flex;
    align-items: center;
    padding: 10px 16px;
    font-size: 14px;
    color: #333333;
    font-weight: 500;
}

.group-title:hover {
    background: #f5f7fa;
}

.expand-icon {
    margin-right: 8px;
    font-size: 12px;
    color: #666666;
    transition: transform 0.3s;
}

.group-title.expanded .expand-icon {
    transform: rotate(90deg);
}

.group-name {
    flex: 1;
    text-align: left;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.group-count {
    font-size: 12px;
    color: #999999;
    margin-left: 8px;
}

/* 好友项容器 */
.friend-items {
    background: #ffffff;
}

.friend-item {
    display: flex;
    align-items: center;
    padding: 10px 16px;
    cursor: pointer;
    border-bottom: 1px solid #f8f8f8;
}

.friend-item:hover {
    background: #f5f5f5;
}

.friend-item:last-child {
    border-bottom: none;
}

/* 头像区域 */
.friend-avatar-wrapper {
    position: relative;
    margin-right: 12px;
    flex-shrink: 0;
}

.friend-avatar {
    background: #f5f7fa;
}

.status-indicator {
    position: absolute;
    bottom: 2px;
    right: 2px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    border: 2px solid #ffffff;
}

.status-indicator.status-online {
    background: #52c41a;
}

.status-indicator.status-busy {
    background: #fa8c16;
}

.status-indicator.status-offline {
    background: #bfbfbf;
}

/* 好友信息区域 */
.friend-info {
    flex: 1;
    min-width: 0;
    overflow: hidden;
}

.name-line {
    display: flex;
    align-items: center;
    margin-bottom: 4px;
}

.display-name {
    font-size: 14px;
    font-weight: 500;
    color: #333333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.original-name {
    font-size: 12px;
    color: #999999;
    margin-left: 6px;
}

.status-line {
    display: flex;
    align-items: center;
    font-size: 12px;
    color: #666666;
}

.online-status-text {
    color: #1890ff;
    margin-right: 8px;
}

.signature {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    opacity: 0.8;
}

/* 空状态 */
.empty-group {
    padding: 20px;
    text-align: center;
    color: #999999;
    font-size: 12px;
    background: #fafafa;
    border-top: 1px solid #f0f0f0;
}

/* 群列表内容 */
.group-list-content {
    padding: 40px 20px;
    height: 100%;
}

/* 右侧聊天区域 */
.chatArea {
    height: 100%;
    width: 100%;
}

.friend-panel-wrapper {
    height: 100%;
    width: 100%;
    display: flex;          /* 确保 flex 布局 */
    flex-direction: column; /* 垂直排列（如果内部需要） */
}

::v-deep(.el-splitter-panel) {
    scrollbar-width: none;
}

::v-deep(.el-splitter-bar__dragger) {
    z-index: 12;
}
</style>