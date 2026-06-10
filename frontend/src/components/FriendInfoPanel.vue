<script lang="ts" setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useFriendStore } from '@/stores/friend'
import { ElMessage } from 'element-plus'
import type { FriendVO } from '@/types/friend'
import type { InputInstance } from 'element-plus'
import {
    Edit as ElIconEdit,
    Folder as ElIconFolder,
    Document as ElIconDocument,
    Promotion as ElIconPromotion,
    ArrowRight as ElIconArrowRight,
    Share as ElIconShare  // 需要导入分享图标
} from '@element-plus/icons-vue'

const props = defineProps<{
    friendId: number
}>()

const friendStore = useFriendStore()
const friendInfo = ref<FriendVO | null>(null)
const loading = ref(false)

// 编辑状态
const isEditing = ref(false)
const editText = ref('')
const inputRef = ref<InputInstance>()

// 加载好友信息
async function loadFriendInfo() {
    try {
        friendInfo.value = await friendStore.fetchFriendDetail(props.friendId)
    } catch (error) {
        console.error('获取好友信息失败:', error)
        ElMessage.error('获取好友信息失败')
        friendInfo.value = null
    }
}

onMounted(() => {
    loadFriendInfo()
})

watch(() => props.friendId, (newId) => {
    if (newId) {
        loadFriendInfo()
    }
})

const getStatusType = (status: number | undefined): 'success' | 'danger' | 'warning' | 'info' => {
    switch (status) {
        case 1: return 'success'
        case 2: return 'danger'
        case 3: return 'warning'
        default: return 'info'
    }
}

const emit = defineEmits<{
    (e: 'update', remark: string): void
    (e: 'share'): void
    (e: 'send-message', friendId: number): void  // 添加发消息事件
}>()

// ==================== 1. 提交备注 ====================
const startEditing = () => {
    isEditing.value = true
    editText.value = friendInfo.value?.remark || ''
    nextTick(() => {
        inputRef.value?.focus()
    })
}

const handleSubmit = async () => {
    isEditing.value = false
    const newRemark = editText.value.trim()
    const currentRemark = friendInfo.value?.remark || ''
    
    if (newRemark !== currentRemark && friendInfo.value) {
        loading.value = true
        try {
            await friendStore.updateFriend(friendInfo.value.friendId, {
                remark: newRemark
            })
            // 更新本地显示
            friendInfo.value.remark = newRemark
            friendInfo.value.displayName = newRemark || friendInfo.value.nickname
            ElMessage.success('备注修改成功')
            emit('update', newRemark)
        } catch (error: any) {
            ElMessage.error(error.message || '修改备注失败')
        } finally {
            loading.value = false
        }
    }
}

// ==================== 2. 提交分组 ====================
const currentGroupName = computed(() => {
    if (!friendInfo.value?.groupId) return '未分组'
    const group = friendStore.groups.find(g => g.groupId === friendInfo.value?.groupId)
    return group?.groupName || '未分组'
})

const handleGroupChange = async (groupId?: number) => {
    if (!friendInfo.value || !friendInfo.value.relationId) {
        ElMessage.error('好友信息不完整')
        return
    }
    
    // 如果点击的是当前分组，不做处理
    if (groupId === friendInfo.value.groupId) return
    
    loading.value = true
    try {
        if (groupId) {
            // 移动到指定分组
            await friendStore.moveFriendToGroup(friendInfo.value.relationId, groupId)
        } else {
            // 如果 groupId 为 undefined，视为移出分组（移到未分组）
            // 注意：moveFriendToGroup 可能需要一个特殊值或另一个 API 来移出分组
            // 这里假设传入 0 或特定值表示移出，根据你的后端 API 调整
            await friendStore.moveFriendToGroup(friendInfo.value.relationId, groupId || 0)
        }
        
        // 更新本地数据
        friendInfo.value.groupId = groupId
        const group = friendStore.groups.find(g => g.groupId === groupId)
        friendInfo.value.groupName = group?.groupName
        
        ElMessage.success('分组修改成功')
    } catch (error: any) {
        ElMessage.error(error.message || '修改分组失败')
    } finally {
        loading.value = false
    }
}

// ==================== 3. 分享功能 ====================
const handleShare = async () => {
    if (!friendInfo.value) return
    
    const info = friendInfo.value
    const shareText = `姓名：${info.nickname}\nID：${info.friendId}\n个性签名：${info.signature || '暂无签名'}\n在线状态：${info.onlineStatusText || '离线'}`
    
    try {
        await navigator.clipboard.writeText(shareText)
        ElMessage.success('已复制用户信息，发给好友吧')
        emit('share')
    } catch (err) {
        // 降级方案
        const textarea = document.createElement('textarea')
        textarea.value = shareText
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
        ElMessage.success('已复制用户信息，发给好友吧')
        emit('share')
    }
}

// ==================== 4. 发消息 ====================
const handleSendMessage = () => {
    if (friendInfo.value) {
        emit('send-message', friendInfo.value.friendId)
    }
}

// 个人空间跳转（保持原样）
const goToPersonalSpace = () => {
    console.log('跳转到个人空间')
}
</script>

<!-- FriendInfoPanel.vue -->
<template>
    <div class="FriendInfoPanelRoot">
        <div class="FriendInfoPanelBox">

            <!-- 第一栏 -->
            <div class="avatar-box">
                <div class="left-section">
                    <el-avatar shape="circle" class="avatar-xxl" :src=friendInfo?.avatarUrl />
                </div>

                <!-- 右侧信息区域 -->
                <div class="right-section">
                    <div class="row-1"><el-text class="text-heading friendNickName" size="large">{{ friendInfo?.nickname }}</el-text>
                    </div>
                    <div class="row-2"><el-text class=" text-heading ">ID: {{ friendInfo?.friendId }}</el-text></div>
                    <div class="row-3"> <!-- 或标签形式 -->
                        <el-tag :type="getStatusType(friendInfo?.onlineStatus)" size="small" effect="dark">
                            {{ friendInfo?.onlineStatusText }}
                        </el-tag>
                    </div>
                </div>
            </div>


            <!-- 第二栏 -->
            <div class="friend-info">

                <div class="friend-info-row-1">
                    <div class="remark-container">
                        <!-- 图标（一直显示） -->
                        <el-icon-edit class="icon" />
                        <!-- 备注区域 -->
                        <div class="remark-content">
                            <!-- 显示模式 -->
                            <span v-if="!isEditing" class="remark-text" :class="{ 'empty-remark': !friendInfo?.remark }"
                                @click="startEditing">
                                {{ friendInfo?.remark || '添加好友备注' }}
                            </span>

                            <!-- 编辑模式 -->
                            <el-input v-else ref="inputRef" v-model="editText" size="small" placeholder="请输入备注"
                                @keyup.enter="handleSubmit" @blur="handleSubmit" @keyup.esc="isEditing = false" />
                        </div>
                    </div>
                </div>
                <div class="friend-info-row-2">
                    <!-- 第二行：分组 -->
                    <div class="action-row">
                        <el-icon-folder class="icon" />
                        <div class="row-content">
                            <el-dropdown trigger="click" placement="bottom" @command="handleGroupChange"
                                :key="friendInfo?.groupId">
                                <span class="group-text">
                                    {{ currentGroupName }}
                                    <el-icon-arrow-down class="dropdown-arrow" />
                                </span>

                                <template #dropdown>
                                    <el-dropdown-menu>
                                        <el-dropdown-item v-for="group in friendStore.groups" :key="group.groupId"
                                            :command="group.groupId"
                                            :class="{ 'active': group.groupId == friendInfo?.groupId }">
                                            {{ group.groupName }}
                                        </el-dropdown-item>
                                    </el-dropdown-menu>
                                </template>
                            </el-dropdown>
                        </div>
                    </div>
                </div>


                <div class="friend-info-row-3">
                    <!-- 第三行：签名 -->
                    <div class="action-row">
                        <el-icon-document class="icon" />
                        <el-text :truncated="true" :line-clamp="2">
                            {{ friendInfo?.signature || '暂无签名' }}
                        </el-text>
                    </div>

                </div>

            </div>

            <!-- 第三栏 -->
            <div class="actions">
                <!-- 上部：个人空间 -->
                <div class="personal-space" @click="goToPersonalSpace">
                    <div class="space-left">
                        <el-icon-promotion class="icon" />
                        <span class="text">个人空间点进去可以看到他的动态</span>
                    </div>
                    <el-icon-arrow-right class="icon" />
                </div>

                <!-- 下部：三个按钮 -->
                <div class="button-group">
                    <el-button size="large">推荐</el-button>
                    <el-button size="large">音视频通话</el-button>
                    <el-button type="primary" @click="handleSendMessage" size="large">发消息</el-button>
                </div>
            </div>
        </div>
    </div>
</template>



<style scoped>
/* 最后一栏样式 */

/* 上部：个人空间 - 模仿上面行的样式 */
.personal-space {
    padding-top: 20px;
    display: flex;
    align-items: center;
    margin-bottom: 35px;
    /* 与下部的间距 */
    cursor: pointer;
}

.space-left {
    display: flex;
    align-items: center;
    flex: 1;
    /* 占据除箭头外的所有空间 */
}

.text {
    color: #333;
    font-size: 14px;
}

.arrow {
    color: #999;
    font-size: 16px;
    flex-shrink: 0;
}

/* 下部：三个按钮 */
.button-group {
    display: flex;
    justify-content: center;
    gap: 12px;
}

/* 中间栏的右侧文字样式 */
.remark-text,
:deep(.el-text),
.group-text {
    font-size: 16px;
    --el-text-color-primary: #171717;
    --el-text-color-regular: #171717;
    --el-text-color-secondary: #171717;
}

:deep(.el-text) {
    text-align: right;
}

/* 个性签名 */
.action-row {
    display: flex;
    align-items: flex-start;
    /* 图标与文本顶部对齐 */
}

.text-content {
    flex: 1;
    text-align: left;
    /* 使用 el-text 的属性控制文本显示 */
}

/* 分组栏样式 */
.action-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    /* 让左右两端对齐 */
}

.row-content {
    width: 17%;
    text-align: right;
}


:deep(.el-dropdown-menu__item.active) {
    color: #91c8ff !important;
    background-color: #ecf5ff !important;
}

/* 备注栏样式 */
.remark-container {
    display: flex;
    align-items: center;
    justify-content: space-between;
    /* 让左右两端对齐 */
    width: 100%;
}

.remark-icon {
    color: #999;
    font-size: 16px;
    /* 图标靠左，不需要额外样式，因为已经在最左边 */
}

.remark-content {
    /* 让内容区域靠右 */
    text-align: right;
}

/* 文本模式下，靠右显示 */
.remark-text {
    cursor: pointer;
    color: #333;
    display: inline-block;
    text-align: right;
}

.remark-text.empty-remark {
    color: #999;
    font-style: italic;
}

/* 输入框模式下，让输入框的文本靠右 */
:deep(.el-input) {
    width: 100%;
}

:deep(.el-input__inner) {
    text-align: right;
}

/* 第二栏布局样式 */
.friend-info {
    padding-top: 20px;
}

.friend-info-row-1,
.friend-info-row-2,
.friend-info-row-3 {
    flex: 1;
    padding-top: 20px;
}

.icon {
    width: 25px;
    height: 25px;
}

/* 分割下框线 */
.avatar-box,
.friend-info {
    position: relative;
}



.avatar-box::after,
.friend-info::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: 0;
    width: 100%;
    height: 1px;
    background-color: #e0e0e0;
    /* 颜色自己调整 */
    transform: scaleY(0.5);
    transform-origin: 0 0;
}

/* 好友名和好友ID */
:deep(.friendNickName) {
color: var(--text-primary);
}

:deep(.friendId) {
    font-size: 14px;
color: var(--text-primary);
}

.avatar-box {
    display: flex;
}

/* 头像 */
.left-section {
    flex: 2.5;
}

/* 头像旁空间 */
.right-section {
    flex: 7.5;
    display: flex;
    flex-direction: column;
}

/* 昵称 id 装填 */
.row-1,
.row-2,
.row-3 {
    flex: 1;
    /* 每行均分高度 */
}

.FriendInfoPanelRoot {
    height: 100%;
    width: 100%;
    display: flex;
    justify-content: center;
    align-items: center;
}

.FriendInfoPanelBox {
    height: 80%;
    width: 65%;
    display: grid;
    grid-template-rows: 2fr 4fr 4fr;
}
</style>