<template>
    <div class="chat-window" v-loading="loading" @drop.prevent="handleDrop" @dragover.prevent>
        <!-- 顶部栏 -->
        <div class="chat-header">
            <div class="header-left">
                <span class="friend-name">
                    <template v-if="friendInfo?.displayName && friendInfo.displayName !== friendInfo.nickname">
                        <span class="display-name">{{ friendInfo.displayName }}</span>
                        <span class="nickname">({{ friendInfo.nickname }})</span>
                    </template>
                    <span v-else class="text-title display-name">
                        {{ friendInfo?.nickname || '未知用户' }}
                    </span>
                </span>
                <span v-if="friendInfo?.isOnline" class="online-status">●</span>
            </div>
            <div class="header-right">
                <el-icon class="more-icon">
                    <More />
                </el-icon>
            </div>
        </div>

        <!-- 消息列表区域（虚拟滚动） -->
        <div class="chat-body" ref="bodyRef">
            <DynamicScroller :items="chatStore.currentMessages" :min-item-size="70" key-field="messageId"
                class="message-scroller" ref="scrollerRef" @scroll="handleScroll" @resize="onScrollerResize"
                @update="onScrollerUpdate">
                <template v-slot="{ item, index, active }">
                    <DynamicScrollerItem :item="item" :active="active" :size-dependencies="[item.content]"
                        :data-index="index">
                        <MessageBubble :message="item" />
                    </DynamicScrollerItem>
                </template>

                <!-- 顶部加载更多提示 -->
                <template #before>
                    <div :style="{ height: isLoadingMore ? 'auto' : '0', overflow: 'hidden' }">
                        <div v-if="isLoadingMore" class="loading-tip">
                            <el-icon class="is-loading">
                                <Loading />
                            </el-icon> 加载历史消息...
                        </div>
                    </div>
                </template>
            </DynamicScroller>

            <!-- 新增：回到底部提示按钮 -->
            <div v-show="showScrollToBottomBtn" class="scroll-to-bottom-btn" @click="handleScrollToBottomClick">
                <div class="new-message-count" v-if="unreadMessageCount > 0">
                    {{ unreadMessageCount }} 条新消息
                </div>
                <div class="btn-content" v-else>
                    <el-icon>
                        <ArrowDown />
                    </el-icon>
                    <span>回到底部</span>
                </div>
            </div>
        </div>

        <!-- 底部输入区域 -->
        <div class="chat-footer">
            <div class="toolbar">
                <!-- AI 拟定回复按钮（替换原表情按钮） -->
                <el-tooltip :content="isGeneratingReply ? 'AI 生成中...' : (inputText.trim() ? 'AI 润色/续写' : 'AI 拟定回复')">
                    <el-icon class="tool-icon ai-suggest-icon" :class="{ 'is-loading': isGeneratingReply }"
                        @click="handleSuggestReply">
                        <ChatRound v-if="!isGeneratingReply" />
                        <Loading v-else />
                    </el-icon>
                </el-tooltip>
                <el-tooltip content="发送图片">
                    <el-icon class="tool-icon">
                        <Picture />
                    </el-icon>
                </el-tooltip>
                <el-tooltip content="语音通话">
                    <el-icon class="tool-icon">
                        <Phone />
                    </el-icon>
                </el-tooltip>
                <el-tooltip content="视频通话">
                    <el-icon class="tool-icon">
                        <VideoCamera />
                    </el-icon>
                </el-tooltip>
                <el-tooltip content="发送文件">
                    <el-icon class="tool-icon" @click="triggerFileSelect">
                        <Folder />
                    </el-icon>
                </el-tooltip>

                <!-- 隐藏的文件输入 -->
                <input ref="fileInputRef" type="file" multiple style="display: none" @change="handleFileSelect" />
                <!-- 分隔线 -->
                <div class="divider"></div>
                <el-icon class="tool-icon history-icon">
                    <Clock />
                </el-icon>
            </div>

            <div class="input-area">
                <el-input v-model="inputText" type="textarea" :rows="4" resize="none" placeholder="请输入消息..."
                    @keyup.enter.exact.prevent="sendMessage" @keydown.enter.shift.exact.prevent="inputText += '\n'" />
                <div class="send-btn">
                    <el-button type="primary" :disabled="!inputText.trim() || isSending" @click="sendMessage">
                        发送
                    </el-button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import { More, ChatRound, Picture, Phone, VideoCamera, Clock, Loading, ArrowDown } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { useFriendStore } from '@/stores/friend'
import MessageBubble from './MessageBubble.vue'
import type { Conversation } from '@/types/message'
import { wsService } from '@/services/websocketService'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import { messageApi } from '@/api'

import { ElMessage } from 'element-plus'
import { Folder } from '@element-plus/icons-vue'
import { aiApi } from '@/api/ai'  // 导入 AI API

// ====== 日志工具 ======
const logger = {
    info: (msg: string, ...args: any[]) => console.log(`[ChatWindow] ${msg}`, ...args),
    warn: (msg: string, ...args: any[]) => console.warn(`[ChatWindow] ${msg}`, ...args),
    error: (msg: string, ...args: any[]) => console.error(`[ChatWindow] ${msg}`, ...args),
    scroll: (msg: string, ...args: any[]) => console.log(`[ChatWindow][Scroll] ${msg}`, ...args)
}

const fileInputRef = ref<HTMLInputElement>()
const isUploading = ref(false)

// 触发文件选择
const triggerFileSelect = () => {
    fileInputRef.value?.click()
}

// 处理文件选择
const handleFileSelect = async (event: Event) => {
    const files = (event.target as HTMLInputElement).files
    if (!files || files.length === 0) return
    await processFiles(Array.from(files))
    fileInputRef.value!.value = ''
}

// 处理拖拽文件
const handleDrop = async (event: DragEvent) => {
    const files = event.dataTransfer?.files
    if (!files || files.length === 0) return
    await processFiles(Array.from(files))
}

// 统一处理文件上传
const processFiles = async (files: File[]) => {
    if (!chatStore.currentConversation) {
        ElMessage.warning('请先选择一个会话')
        return
    }

    isUploading.value = true
    try {
        for (const file of files) {
            await wsService.sendMediaMessage(
                file,
                props.friendId,
                'USER'
            )
        }
        ElMessage.success(`已发送 ${files.length} 个文件`)
    } catch (error) {
        ElMessage.error('文件发送失败')
    } finally {
        isUploading.value = false
    }
}

interface Props {
    friendId: number
}

import { onMounted, onUnmounted } from 'vue'

onMounted(() => {
    logger.info('组件挂载')
    if (chatStore.currentConversation) {
        chatStore.clearUnread(chatStore.currentConversation.id)
    }
})

onUnmounted(() => {
    logger.info('组件卸载')
    chatStore.isChatPanelActive = false
    if (scrollStateDebounceTimer) {
        clearTimeout(scrollStateDebounceTimer)
    }
})

const props = defineProps<Props>()

// Store
const chatStore = useChatStore()
const userStore = useUserStore()
const friendStore = useFriendStore()

// Refs
const inputText = ref('')
const scrollerRef = ref<any>()
const bodyRef = ref<HTMLDivElement>()
const isSending = ref(false)
const isLoadingMore = ref(false)
const hasMoreHistory = ref(true)
const initialScrollDone = ref(false)
const isGeneratingReply = ref(false)  // AI 生成状态

const scrollRetryCount = ref(0)
const MAX_SCROLL_RETRY = 10

const SCROLL_NEAR_BOTTOM_THRESHOLD = 300
const SCROLL_STATE_DEBOUNCE = 1500

const isNearBottom = ref(true)
const isUserReadingHistory = ref(false)
const showScrollToBottomBtn = ref(false)
const unreadMessageCount = ref(0)
let scrollStateDebounceTimer: ReturnType<typeof setTimeout> | null = null

const currentUserId = computed(() => userStore.userInfo?.userId || 0)

const friendInfo = computed(() => {
    return friendStore.friends.find(f => f.friendId === props.friendId)
})

const displayName = computed(() => {
    return friendInfo.value?.displayName ||
        friendInfo.value?.nickname ||
        friendInfo.value?.username ||
        '未知用户'
})

const loading = computed(() => chatStore.loading)

watch(() => props.friendId, (newId, oldId) => {
    logger.info(`friendId 变化: ${oldId} -> ${newId}`)
    if (newId && newId !== chatStore.currentConversation?.targetId) {
        initChat(newId)
    }
}, { immediate: true })

watch(() => scrollerRef.value, (newVal) => {
    logger.scroll('scrollerRef 已绑定:', !!newVal)
}, { immediate: true })

async function initChat(friendId: number) {
    logger.info('开始初始化聊天:', friendId)
    initialScrollDone.value = false
    scrollRetryCount.value = 0
    isNearBottom.value = true
    isUserReadingHistory.value = false
    showScrollToBottomBtn.value = false
    unreadMessageCount.value = 0

    if (scrollStateDebounceTimer) {
        clearTimeout(scrollStateDebounceTimer)
    }

    const myId = currentUserId.value
    const min = Math.min(myId, friendId)
    const max = Math.max(myId, friendId)
    const conversationId = `user_${min}_${max}`

    const conv: Conversation = {
        id: conversationId,
        type: 'private',
        targetId: friendId,
        targetName: displayName.value,
        targetAvatar: friendInfo.value?.avatarUrl,
        unreadCount: 0,
        updatedAt: new Date()
    }

    await chatStore.selectConversation(conv)
    hasMoreHistory.value = true

    // 延迟执行滚动，确保虚拟滚动器已渲染
    setTimeout(() => {
        scrollToBottom(true) // 强制滚动到底部（最新消息）
    }, 150)
    logger.info('会话已选择，消息数量:', chatStore.currentMessages.length)

    setTimeout(() => {
        tryScrollToBottomWithRetry()
    }, 100)
}

function tryScrollToBottomWithRetry() {
    if (scrollRetryCount.value >= MAX_SCROLL_RETRY) {
        logger.warn('滚动到底部重试次数已达上限，放弃')
        return
    }

    scrollRetryCount.value++
    logger.scroll(`第 ${scrollRetryCount.value} 次尝试滚动到底部`)

    if (!scrollerRef.value) {
        logger.warn('scrollerRef 未绑定，延迟重试')
        setTimeout(tryScrollToBottomWithRetry, 100)
        return
    }

    const lastIndex = chatStore.currentMessages.length - 1
    if (lastIndex < 0) {
        logger.info('消息列表为空，无需滚动')
        initialScrollDone.value = true
        return
    }

    if (typeof scrollerRef.value.scrollToItem === 'function') {
        try {
            scrollerRef.value.scrollToItem(lastIndex, 'end')
            logger.scroll('scrollToItem 调用成功，索引:', lastIndex)

            setTimeout(() => {
                const scrollEl = getScrollElement()
                if (scrollEl) {
                    const isAtBottom = checkIfAtBottom(scrollEl)
                    logger.scroll('scrollToItem 后验证底部状态:', isAtBottom)

                    if (!isAtBottom) {
                        logger.scroll('scrollToItem 未生效，尝试备用方案')
                        fallbackScrollToBottom()
                    } else {
                        initialScrollDone.value = true
                        isNearBottom.value = true
                        isUserReadingHistory.value = false
                        logger.info('初始化滚动成功')
                    }
                }
            }, 50)

            return
        } catch (e) {
            logger.warn('scrollToItem 失败:', e)
        }
    } else {
        logger.warn('scrollToItem 方法不可用')
    }

    fallbackScrollToBottom()
}

function getScrollElement(): HTMLElement | null {
    if (scrollerRef.value) {
        const el = scrollerRef.value.$el || scrollerRef.value
        if (el && el.scrollHeight !== undefined) {
            return el
        }
    }

    if (bodyRef.value) {
        return bodyRef.value.querySelector('.vue-recycle-scroller') as HTMLElement || bodyRef.value
    }

    return null
}

function checkIfAtBottom(element: HTMLElement): boolean {
    const scrollBottom = element.scrollHeight - element.scrollTop - element.clientHeight
    return scrollBottom < SCROLL_NEAR_BOTTOM_THRESHOLD
}

function fallbackScrollToBottom() {
    const el = getScrollElement()
    if (!el) {
        logger.warn('未找到滚动元素，延迟重试')
        setTimeout(tryScrollToBottomWithRetry, 100)
        return
    }

    logger.scroll('使用备用方案滚动, scrollHeight:', el.scrollHeight)
    el.scrollTop = el.scrollHeight * 2

    setTimeout(() => {
        const isAtBottom = checkIfAtBottom(el)
        logger.scroll('备用方案后验证底部状态:', isAtBottom)

        if (isAtBottom) {
            initialScrollDone.value = true
            isNearBottom.value = true
            isUserReadingHistory.value = false
            logger.info('初始化滚动成功（备用方案）')
        } else {
            if (scrollRetryCount.value < MAX_SCROLL_RETRY) {
                setTimeout(tryScrollToBottomWithRetry, 100)
            }
        }
    }, 50)
}

async function sendMessage() {
    if (!inputText.value.trim() || !chatStore.currentConversation) return

    isSending.value = true
    logger.info('发送消息:', inputText.value.trim())

    try {
        await chatStore.sendTextMessage(
            inputText.value.trim(),
            props.friendId,
            'USER'
        )
        inputText.value = ''

        await nextTick()
        logger.scroll('消息发送后，准备滚动到底部')
        scrollToBottom(true)

        isNearBottom.value = true
        isUserReadingHistory.value = false
        showScrollToBottomBtn.value = false
        unreadMessageCount.value = 0
    } finally {
        isSending.value = false
    }
}

const hasMarkedRead = ref(false)

// 监听 friendId 变化，重置标记
watch(() => props.friendId, () => {
    hasMarkedRead.value = false
})

// 监听会话变化，标记已读
watch(() => chatStore.currentConversation, async (newConv) => {
    if (newConv && newConv.targetId === props.friendId && !hasMarkedRead.value) {
        try {
            await messageApi.markConversationRead(newConv.id)
            console.log('标记会话已读成功', newConv.id)
            hasMarkedRead.value = true
        } catch (error) {
            console.error('标记会话已读失败', error)
        }
    }
}, { immediate: true })

// ====== AI 拟定/润色回复功能（修正版）======
async function handleSuggestReply() {
    if (isGeneratingReply.value) return
    if (!chatStore.currentConversation) {
        ElMessage.warning('请先选择一个会话')
        return
    }

    isGeneratingReply.value = true
    try {
        logger.info('正在请求 AI 拟定回复...')

        // 直接取编辑区当前内容（可能是草稿让AI润色，也可能是空让AI基于数据库生成）
        const currentDraft = '这是我拟定的回复,如果为空则由你根据上下文进行拟定: ' + inputText.value || ''

        const reply = await aiApi.suggestReply(
            chatStore.currentConversation.id,
            currentDraft  // 传当前编辑区内容，后端自己查数据库
        )

        // 将AI返回的内容写入编辑区（覆盖或追加，根据产品需求，这里选择覆盖）
        inputText.value = reply

        ElMessage.success(currentDraft ? '已润色/续写回复' : '已生成回复建议')
    } catch (error: any) {
        logger.error('获取 AI 拟定回复失败:', error)
        ElMessage.error(error.message || '生成回复建议失败，请重试')
    } finally {
        isGeneratingReply.value = false
    }
}

function scrollToBottom(force = false) {
    if (!force && isUserReadingHistory.value) return

    nextTick(() => {
        if (!scrollerRef.value) return

        const lastIndex = chatStore.currentMessages.length - 1
        if (lastIndex < 0) return

        // 使用 scrollToItem 滚动到最后一个元素（最新消息）
        if (typeof scrollerRef.value.scrollToItem === 'function') {
            try {
                scrollerRef.value.scrollToItem(lastIndex, 'end')
                isNearBottom.value = true
                unreadMessageCount.value = 0
                showScrollToBottomBtn.value = false
            } catch (e) {
                console.warn('scrollToItem 失败:', e)
                // 备用方案
                manualScrollToBottom()
            }
        }
    })
}

function manualScrollToBottom() {
    const el = getScrollElement()
    if (el) {
        const oldScrollTop = el.scrollTop
        el.scrollTop = el.scrollHeight
        logger.scroll(`手动滚动: ${oldScrollTop} -> ${el.scrollTop}`)

        unreadMessageCount.value = 0
        showScrollToBottomBtn.value = false
        isNearBottom.value = true
        isUserReadingHistory.value = false
    } else {
        logger.warn('手动滚动失败：未找到元素')
    }
}

function handleScrollToBottomClick() {
    logger.scroll('用户点击回到底部按钮')
    scrollToBottom(true)
    isUserReadingHistory.value = false
}

watch(() => chatStore.currentMessages.length, async (newVal, oldVal) => {
    if (newVal > oldVal && initialScrollDone.value) {
        const newMessagesCount = newVal - oldVal

        // 检查是否是加载历史消息（数组头部插入）
        // 如果是加载更多，不需要自动滚动
        if (isLoadingMore.value) return

        if (isUserReadingHistory.value) {
            unreadMessageCount.value += newMessagesCount
            showScrollToBottomBtn.value = true
        } else {
            // 新消息到达且用户在底部，自动滚动
            await nextTick()
            scrollToBottom(true)
        }
    }
})

function onScrollerResize() {
    logger.scroll('虚拟滚动器 resize')
    if (!initialScrollDone.value && scrollRetryCount.value < MAX_SCROLL_RETRY) {
        setTimeout(tryScrollToBottomWithRetry, 50)
    }
}

function onScrollerUpdate(startIndex: number, endIndex: number) {
    // 仅在调试时输出，避免过多日志
}

let scrollTimeout: ReturnType<typeof setTimeout> | null = null
let previousScrollHeight = 0

function handleScroll(event: Event) {
    const target = event.target as HTMLDivElement
    if (!target || !initialScrollDone.value) {
        return
    }

    const scrollBottom = target.scrollHeight - target.scrollTop - target.clientHeight
    const nearBottom = scrollBottom < SCROLL_NEAR_BOTTOM_THRESHOLD

    const wasNearBottom = isNearBottom.value
    isNearBottom.value = nearBottom

    if (wasNearBottom !== nearBottom) {
        logger.scroll(`实时底部状态变化: ${wasNearBottom} -> ${nearBottom}, 距离底部: ${scrollBottom.toFixed(0)}px`)
    }

    if (scrollStateDebounceTimer) {
        clearTimeout(scrollStateDebounceTimer)
    }

    if (!nearBottom) {
        scrollStateDebounceTimer = setTimeout(() => {
            if (!isNearBottom.value) {
                isUserReadingHistory.value = true
                logger.scroll(`防抖判定：用户正在看历史（持续 ${SCROLL_STATE_DEBOUNCE}ms 不在底部）`)
            }
        }, SCROLL_STATE_DEBOUNCE)
    } else {
        if (isUserReadingHistory.value) {
            isUserReadingHistory.value = false
            unreadMessageCount.value = 0
            showScrollToBottomBtn.value = false
            logger.scroll('用户回到底部，重置看历史状态')
        }
    }

    if (target.scrollTop < 50 && !isLoadingMore.value && hasMoreHistory.value) {
        if (scrollTimeout) return

        scrollTimeout = setTimeout(() => {
            loadMoreHistory(target)
        }, 200)
    }
}

async function loadMoreHistory(target: HTMLDivElement) {
    isLoadingMore.value = true
    previousScrollHeight = target.scrollHeight
    logger.info('加载更多历史消息')

    const hasMore = await chatStore.loadMoreMessages()

    if (hasMore) {
        await nextTick()
        const newScrollHeight = target.scrollHeight
        const diff = newScrollHeight - previousScrollHeight
        target.scrollTop = diff
        logger.scroll('加载更多完成，调整滚动位置:', diff)
    } else {
        hasMoreHistory.value = false
        logger.info('没有更多历史消息')
    }

    isLoadingMore.value = false
    scrollTimeout = null
}
</script>

<style scoped lang="scss">
.chat-header {
    .header-left {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;

        .friend-name {
            display: flex;
            align-items: center;
            flex-wrap: nowrap;
            white-space: nowrap;
            overflow: hidden;

            .display-name {
                font-weight: 600;
                color: #303133;
                flex-shrink: 0;
            }

            .nickname {
                font-size: 12px;
                color: #909399;
                font-weight: 400;
                margin-left: 4px;
                flex-shrink: 1;
                white-space: nowrap;
            }
        }
    }
}

.chat-window {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
    position: relative;
}

.chat-header {
    flex-shrink: 0;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    box-sizing: border-box;

    .header-left {
        display: flex;
        align-items: center;
        gap: 8px;
    }

    .online-status {
        color: #67c23a;
        font-size: 12px;
    }

    .more-icon {
        font-size: 20px;
        color: #909399;
        cursor: pointer;

        &:hover {
            color: #409eff;
        }
    }
}

.chat-body {
    flex: 1;
    overflow: hidden;
    position: relative;
}

.message-scroller {
    height: 100%;
    overflow-y: auto;
    padding: 10px 0;
}

.scroll-to-bottom-btn {
    position: absolute;
    right: 20px;
    bottom: 20px;
    background-color: #409eff;
    color: white;
    padding: 8px 16px;
    border-radius: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    transition: all 0.3s ease;
    z-index: 10;
    user-select: none;

    &:hover {
        background-color: #66b1ff;
        transform: translateY(-2px);
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
    }

    &:active {
        transform: translateY(0);
    }

    .new-message-count {
        font-weight: 600;
    }

    .btn-content {
        display: flex;
        align-items: center;
        gap: 4px;
    }

    animation: slideUp 0.3s ease;
}

@keyframes slideUp {
    from {
        opacity: 0;
        transform: translateY(20px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.loading-tip {
    text-align: center;
    padding: 10px;
    color: #909399;
    font-size: 13px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
}

.chat-footer {
    flex-shrink: 0;
    background-color: #fff;
    border-top: 1px solid #e4e7ed;
    padding: 12px 16px;

    .toolbar {
        display: flex;
        align-items: center;
        gap: 16px;
        margin-bottom: 10px;
        padding: 0 4px;

        .tool-icon {
            font-size: 22px;
            color: #606266;
            cursor: pointer;
            transition: color 0.2s;

            &:hover {
                color: #409eff;
            }
        }

        .ai-suggest-icon {
            position: relative;

            &.is-loading {
                color: #409eff;
                cursor: not-allowed;
            }

            &:hover {
                color: #409eff;
            }
        }

        .divider {
            width: 1px;
            height: 20px;
            background-color: #dcdfe6;
            margin: 0 4px;
        }

        .history-icon {
            margin-left: auto;
        }
    }

    .input-area {
        display: flex;
        gap: 12px;
        align-items: flex-end;

        :deep(.el-textarea__inner) {
            background-color: transparent;
            border: none;
            outline: none;
        }

        :deep(.el-textarea__inner.is-focus) {
            box-shadow: none;
        }

        :deep(.el-textarea__inner:hover) {
            box-shadow: none;
        }

        :deep(.el-textarea__inner) {
            background-color: transparent;
            box-shadow: none;
            border: none;
        }

        .send-btn {
            flex-shrink: 0;
            padding-bottom: 2px;
        }
    }
}
</style>