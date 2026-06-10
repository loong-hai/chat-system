<template>
    <div class="chatBox">
        <!-- AI 总结浮窗：所有状态通过 Markdown 显示，零弹窗干扰 -->
        <FloatingCard ref="floatingCardRef" v-if="showFloatingCard" title="AI 总结" :initial-position="{ x: 1226, y: 50 }"
            :initial-size="{ width: 292, height: 600 }" :initial-collapsed="false" :closable="true" :collapsible="true"
            @close="handleCloseCard" @toggle="handleToggle" :markdown="getSummaryMarkdown()">
            <!-- 插槽仅作为终极后备，理论上不会显示 -->
            <div class="status-wrapper">
                <el-icon>
                    <Document />
                </el-icon>
                <span>准备就绪</span>
            </div>
        </FloatingCard>

        <div class="chatRoot" ref="chatRootRef" style="position: fixed; margin: 0;">
            <el-container class="containerBox">
                <el-header class="chatHeader" @mousedown="startDragChat" style="cursor: grab;">
                    <div class="header-left">
                        <el-image style="width: 30px; margin-left: 0px; height: 30px" :src="logo" />
                        <UserCardPopover :user-id="userStore.currentUser?.userId || 0" type="self" placement="bottom"
                            @edit-profile="handleEditProfile">
                            <div class="user-info-wrapper"
                                style="display: flex; align-items: center; gap: 15px; cursor: pointer;">
                                <el-avatar class="avatar-md" :src="userStore.currentUser?.avatarUrl">user</el-avatar>
                                <el-text class="nickname" size="large">{{ userStore.currentUser?.nickname }}</el-text>
                                <el-text class="personalizedSignature" size="small">{{ userStore.currentUser?.signature
                                }}</el-text>
                            </div>
                        </UserCardPopover>
                    </div>
                    <div class="header-right">
                        <el-tooltip content="退出登录" placement="bottom">
                            <el-button type="primary" circle size="small" class="close-btn" color="#00ccffe3"
                                @click="showLogoutDialog = true">
                                <el-icon>
                                    <SwitchButton />
                                </el-icon>
                            </el-button>
                        </el-tooltip>
                    </div>
                </el-header>
                <el-main class="chatMain" v-show="userStore.currentUser">
                    <div class="Navigation">
                        <el-menu :default-active="activeIndex" class="sidebar-menu vertical-layout"
                            @select="handleSelect">
                            <div class="menu-section">
                                <el-menu-item index="friend" class="nav-icon">
                                    <div class="icon-container">
                                        <el-badge is-dot :value="pendingRequestsCount"
                                            :hidden="pendingRequestsCount === 0" :max="99" type="danger"
                                            class="nav-badge">
                                            <el-icon :size="iconSize">
                                                <UserFilled />
                                            </el-icon>
                                        </el-badge>
                                    </div>
                                </el-menu-item>
                                <el-menu-item index="chat" class="nav-icon">
                                    <div class="icon-container">
                                        <el-badge is-dot :value="unreadTotal" :hidden="unreadTotal === 0" :max="99"
                                            type="danger" class="nav-badge">
                                            <el-icon :size="iconSize">
                                                <ChatDotRound />
                                            </el-icon>
                                        </el-badge>
                                    </div>
                                </el-menu-item>
                                <el-menu-item index="search" class="nav-icon">
                                    <el-icon :size="iconSize">
                                        <Search />
                                    </el-icon>
                                </el-menu-item>
                                <!-- 预备，后期添加社交圈功能 -->
                                <!-- <el-menu-item index="moments" class="nav-icon">
                                    <el-icon :size="iconSize">
                                        <Promotion />
                                    </el-icon>
                                </el-menu-item> -->
                            </div>

                            <!-- 底部菜单：设置按钮改为状态切换下拉菜单，并添加自定义类 -->
                            <div class="menu-section bottom-section">
                                <el-dropdown
                                    trigger="click"
                                    @command="handleStatusChange"
                                    popper-class="status-dropdown-menu"
                                    class="status-dropdown-trigger"
                                >
                                    <div class="nav-icon" style="cursor: pointer;">
                                        <el-icon :size="iconSize">
                                            <Setting />
                                        </el-icon>
                                    </div>
                                    <template #dropdown>
                                        <el-dropdown-menu>
                                            <el-dropdown-item :command="{ status: 1, label: '在线' }">
                                                <el-icon><CircleCheck /></el-icon>
                                                <span>在线</span>
                                            </el-dropdown-item>
                                            <el-dropdown-item :command="{ status: 2, label: '忙碌' }">
                                                <el-icon><Clock /></el-icon>
                                                <span>忙碌</span>
                                            </el-dropdown-item>
                                            <el-dropdown-item :command="{ status: 3, label: '隐身' }">
                                                <el-icon><Hide /></el-icon>
                                                <span>隐身</span>
                                            </el-dropdown-item>
                                            <el-dropdown-item :command="{ status: 0, label: '离线' }">
                                                <el-icon><Mute /></el-icon>
                                                <span>离线</span>
                                            </el-dropdown-item>
                                        </el-dropdown-menu>
                                    </template>
                                </el-dropdown>
                            </div>
                        </el-menu>
                    </div>
                    <Transition name="fade" :key="activeIndex">
                        <component :is="activeComponent" @switch-to-chat="handleSwitchToChat" />
                    </Transition>
                </el-main>
            </el-container>
        </div>
        <EditProfileDialog v-model="showEditDialog" @success="handleEditSuccess" />
        <el-dialog v-model="showLogoutDialog" title="退出登录" width="400px" :close-on-click-modal="false" destroy-on-close>
            <div style="text-align: center; padding: 10px 0;">
                <el-icon class="avatar-lg" color="#f56c6c">
                    <WarningFilled />
                </el-icon>
                <p style="margin-top: 12px; font-size: 16px;">确定要退出登录吗？</p>
                <el-checkbox v-model="clearLocalData" style="margin-top: 16px;">清空本地所有信息（聊天记录、缓存等）</el-checkbox>
            </div>
            <template #footer>
                <el-button @click="showLogoutDialog = false">取消</el-button>
                <el-button type="danger" :loading="logoutLoading" @click="handleLogout">确定退出</el-button>
            </template>
        </el-dialog>

        <!-- 左下角悬浮重置按钮 -->
        <div class="reset-btn-wrapper" @click="handleResetPositions">
            <el-tooltip content="重置窗口位置" placement="right">
                <div class="reset-btn" :class="{ 'is-spinning': isResetting }">
                    <el-icon :size="20">
                        <Refresh />
                    </el-icon>
                </div>
            </el-tooltip>
        </div>
    </div>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, watch, nextTick, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
    UserFilled,
    ChatDotRound,
    Search,
    Setting,
    CircleCheck,
    Promotion,
    Clock,
    Hide,
    Mute,
    WarningFilled,
    SwitchButton,
    Document,
    Refresh,
} from '@element-plus/icons-vue'

// Store 导入
import { useAuthStore } from '@/stores'
import { useUserStore } from '@/stores'
import { useChatStore } from '@/stores/chat'
import { useFriendStore } from '@/stores/friend'

// API & 数据库
import { aiApi } from '@/api/ai'
import { chatDB } from '@/db/chatDatabase'
import { wsService } from '@/services/websocketService'

// 类型导入
import type { Conversation } from '@/types/message'
import type { SummaryCacheDTO } from '@/types/ai'

// 组件导入
import logo from '@/assets/Logo.png'
import friendListAndChatArea from '@/components/FriendListAndChatArea.vue'
import chatComponent from '@/components/ChatComponent.vue'
import searchComponent from '@/components/SearchPanel.vue'
import FriendCirclePanel from '@/components/FriendCirclePanel.vue'
import UserCardPopover from '@/components/UserCardPopover.vue'
import EditProfileDialog from '@/components/EditProfileDialog.vue'
import FloatingCard from '@/components/FloatingCard.vue'

// ==================== 类型定义 ====================
type MenuIndex = keyof typeof componentMap

// AI 总结状态机
type SummaryState = 'IDLE' | 'LOADING' | 'GENERATING' | 'SUCCESS' | 'ERROR'

// ==================== Store 初始化 ====================
const router = useRouter()
const authStore = useAuthStore()
const userStore = useUserStore()
const chatStore = useChatStore()
const friendStore = useFriendStore()

// ==================== 组件映射 ====================
const componentMap = {
    friend: friendListAndChatArea,
    chat: chatComponent,
    search: searchComponent,
    moments: FriendCirclePanel,
}

// ==================== 响应式数据 ====================
const activeIndex = ref<MenuIndex>('friend')
const iconSize = 24

// ==================== 浮窗状态管理 ====================
// 状态切换方法
const handleStatusChange = async (command: { status: number; label: string }) => {
  try {
    await userStore.updateOnlineStatus(command.status)
    ElMessage.success(`状态已切换为 ${command.label}`)
  } catch (error) {
    ElMessage.error('切换状态失败')
  }
}
const showFloatingCard = ref(true)

// 统一的状态结构，支持错误字段和后端字段映射
const chatSummary = ref<SummaryCacheDTO & { error?: string } | null>(null)
const autoSummary = ref<string>('')
const autoSummaryError = ref<string>('')

const loadingChat = ref(false)
const loadingAuto = ref(false)

// 新增：状态机和轮询计数器
const summaryState = ref<SummaryState>('IDLE')
const pollingAttempts = ref(0)

// 定时器管理
let autoSummaryTimer: ReturnType<typeof setInterval> | null = null
let chatPollingTimer: ReturnType<typeof setTimeout> | null = null

// 登出相关
const showLogoutDialog = ref(false)
const clearLocalData = ref(false)
const logoutLoading = ref(false)

// 编辑资料相关
const showEditDialog = ref(false)

/* 新增：拖动相关状态 */
const chatRootRef = ref<HTMLElement | null>(null)
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0, left: 0, top: 0 })

/* 新增：开始拖动 */
const startDragChat = (e: MouseEvent) => {
    // 排除点击按钮的情况
    if ((e.target as HTMLElement).closest('.close-btn') ||
        (e.target as HTMLElement).closest('button') ||
        (e.target as HTMLElement).closest('.el-button')) {
        return
    }

    if (!chatRootRef.value) return

    isDragging.value = true
    dragStart.value = {
        x: e.clientX,
        y: e.clientY,
        left: chatRootRef.value.offsetLeft,
        top: chatRootRef.value.offsetTop
    }

    document.addEventListener('mousemove', onDragChat)
    document.addEventListener('mouseup', stopDragChat)

    // 改变光标
    if (chatRootRef.value) {
        chatRootRef.value.style.cursor = 'grabbing'
    }
    // 阻止文本选择
    e.preventDefault()
}

/* 新增：拖动中 */
const onDragChat = (e: MouseEvent) => {
    if (!isDragging.value || !chatRootRef.value) return

    const dx = e.clientX - dragStart.value.x
    const dy = e.clientY - dragStart.value.y

    let newLeft = dragStart.value.left + dx
    let newTop = dragStart.value.top + dy

    // 边界约束：至少保留 100px 在屏幕内，防止拖丢
    const minVisible = 100
    const winWidth = window.innerWidth
    const winHeight = window.innerHeight
    const elWidth = chatRootRef.value.offsetWidth
    const elHeight = chatRootRef.value.offsetHeight

    newLeft = Math.max(-(elWidth - minVisible), Math.min(newLeft, winWidth - minVisible))
    newTop = Math.max(-(elHeight - minVisible), Math.min(newTop, winHeight - minVisible))

    chatRootRef.value.style.left = newLeft + 'px'
    chatRootRef.value.style.top = newTop + 'px'
}

/* 新增：结束拖动 */
const stopDragChat = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onDragChat)
    document.removeEventListener('mouseup', stopDragChat)

    if (chatRootRef.value) {
        chatRootRef.value.style.cursor = ''
    }
}

// ==================== 计算属性 ====================
const unreadTotal = computed(() => chatStore.unreadTotal)
const pendingRequestsCount = computed(() => friendStore.pendingRequestsCount)
const activeComponent = computed(() => componentMap[activeIndex.value])

// ==================== 核心方法：Markdown 内容生成 ====================
const getSummaryMarkdown = (): string | undefined => {
    const now = new Date().toLocaleTimeString()

    // Chat 模式
    if (activeIndex.value === 'chat' && chatStore.currentConversation) {
        // 关键修复：检查后端的 generating 字段（小写）或 isGenerating（驼峰）
        const isGenerating = chatSummary.value?.isGenerating ?? (chatSummary.value as any)?.generating ?? false

        // 1. 首次加载中
        if (summaryState.value === 'IDLE' || summaryState.value === 'LOADING') {
            return `### ⏳ 正在连接 AI 服务...\n\n首次加载可能需要几秒钟，请稍候\n\n*${now}*`
        }

        // 2. 生成中状态（后台还在生成）
        if (summaryState.value === 'GENERATING' || isGenerating) {
            const hasContent = chatSummary.value?.content && chatSummary.value.content.length > 10
            const attemptText = pollingAttempts.value > 0 ? ` · 第 ${pollingAttempts.value} 次检查` : ''

            return `### 🔄 ${hasContent ? 'AI 正在完善总结...' : 'AI 分析中...'}\n\n${hasContent ? '已获取初步结果，正在获取完整分析...' : '正在实时分析对话内容，生成注意事项和回复建议...'}\n\n> ⏱️ **自动刷新中**${attemptText} · ${now}\n\n${chatSummary.value?.error ? `> ⚠️ ${chatSummary.value.error}` : ''}`
        }

        // 3. 错误状态（且不在生成中）
        if (summaryState.value === 'ERROR') {
            return `### ❌ 获取失败\n\n${chatSummary.value?.error || '无法获取对话总结'}\n\n---\n\n> 💡 **解决方案**：\n> 1. 检查网络连接\n> 2. 尝试切换会话后返回\n> 3. 联系管理员检查 AI 服务状态\n\n*${now}*`
        }

        // 4. 正常内容（SUCCESS 状态且有内容）
        if (summaryState.value === 'SUCCESS' && chatSummary.value?.content) {
            return chatSummary.value.content
        }

        // 5. 空数据
        return `### 📝 准备就绪\n\n开始聊天后，AI 将自动分析对话内容并生成总结\n\n*${now}*`
    }

    // 全局模式（非 chat 标签）
    if (activeIndex.value !== 'chat') {
        // 1. 加载中
        if (loadingAuto.value) {
            return `### ⏳ 获取全局总结...\n\n正在获取最近1-2天的消息总结...\n\n*${now}*`
        }

        // 2. 错误状态
        if (autoSummaryError.value) {
            return `### ⚠️ 获取失败\n\n${autoSummaryError.value}\n\n---\n\n> 💡 系统将在下次自动刷新时重试（每10分钟）\n\n*${now}*`
        }

        // 3. 正常内容
        if (autoSummary.value) {
            return autoSummary.value
        }

        // 4. 空数据
        return `### 📝 暂无全局总结\n\n暂无最近1-2天的消息总结数据\n\n*${now}*`
    }
    return undefined
}

// ==================== AI 总结数据获取（修复版）====================

const fetchAutoSummary = async () => {
    loadingAuto.value = true
    autoSummaryError.value = ''
    try {
        const result = await aiApi.getAutoSummary()
        autoSummary.value = result
        console.log('[AI总结-全局] 获取成功:', result.substring(0, 50) + '...')
    } catch (error: any) {
        console.error('[AI总结-全局] 获取失败:', error)
        autoSummary.value = ''
        autoSummaryError.value = error?.message || error?.response?.data?.message || '网络异常，无法获取总结'
    } finally {
        loadingAuto.value = false
    }
}

/**
 * 智能轮询获取对话总结
 * 修复：正确处理后端 generating 字段（小写）
 */
const fetchChatSummary = async (conversationId: string) => {
    // 清理旧定时器，避免重复
    if (chatPollingTimer) {
        clearTimeout(chatPollingTimer)
        chatPollingTimer = null
    }

    const isFirstLoad = summaryState.value === 'IDLE'

    // 首次加载显示 Loading，轮询中不显示（避免闪烁）
    if (isFirstLoad) {
        summaryState.value = 'LOADING'
        loadingChat.value = true
    }

    try {
        console.log(`[AI总结-对话] 查询中... 会话ID: ${conversationId}, 状态: ${summaryState.value}`)
        const res = await aiApi.getChatSummary(conversationId)

        console.log('[AI总结-对话] 收到响应:', {
            generating: (res as any).generating,
            isGenerating: res.isGenerating,
            hasContent: !!res.content,
            contentPreview: res.content?.substring(0, 50)
        })

        // 关键修复：后端返回 generating（小写），前端类型是 isGenerating（驼峰）
        // 需要映射字段名，并确保响应式更新
        const normalizedRes: SummaryCacheDTO & { error?: string } = {
            ...res,
            // 如果后端返回 generating，映射到 isGenerating
            isGenerating: res.isGenerating ?? (res as any).generating ?? false,
            error: undefined
        }

        // 关键修复：强制触发响应式更新，使用新对象替换
        chatSummary.value = normalizedRes

        // 重置轮询计数（成功响应）
        pollingAttempts.value = 0

        // 关键修复：使用映射后的字段判断
        if (normalizedRes.isGenerating) {
            // 后台还在生成中，继续轮询
            summaryState.value = 'GENERATING'
            scheduleNextPoll(conversationId)
        } else {
            // 生成完成
            console.log('[AI总结-对话] 生成完成！内容长度:', normalizedRes.content?.length)
            summaryState.value = 'SUCCESS'
        }

    } catch (error: any) {
        console.error('[AI总结-对话] 请求异常:', error)

        // 处理202 Accepted（生成中）
        const is202Generating =
            error?.response?.status === 202 ||
            error?.code === 202 ||
            error?.status === 202

        if (is202Generating) {
            console.log('[AI总结-对话] 收到202 Accepted，继续轮询...')
            summaryState.value = 'GENERATING'

            chatSummary.value = {
                ...chatSummary.value,
                isGenerating: true,
                content: chatSummary.value?.content || 'AI正在分析对话内容，请稍候...',
                error: undefined
            } as any

            scheduleNextPoll(conversationId)
            return
        }

        // 生成中遇到网络错误，继续重试
        if (summaryState.value === 'GENERATING' || summaryState.value === 'LOADING') {
            pollingAttempts.value++

            if (pollingAttempts.value < 10) {
                const retryDelay = 2000
                console.log(`[AI总结-对话] 网络错误，${retryDelay}ms后重试... (${pollingAttempts.value}/10)`)

                chatPollingTimer = setTimeout(() => {
                    fetchChatSummary(conversationId)
                }, retryDelay)
                return
            } else {
                // 慢速轮询
                chatSummary.value = {
                    ...chatSummary.value,
                    isGenerating: true,
                    content: chatSummary.value?.content || '正在努力生成中，请稍候...',
                    error: `网络波动，正在尝试恢复... (${pollingAttempts.value}次)`
                } as any
                scheduleNextPoll(conversationId, 5000)
                return
            }
        }

        // 真正的业务错误
        summaryState.value = 'ERROR'
        chatSummary.value = {
            content: '',
            isGenerating: false,
            error: error?.message || error?.response?.data?.message || '获取对话总结失败，请稍后重试'
        } as any

    } finally {
        if (summaryState.value !== 'GENERATING') {
            loadingChat.value = false
        }
    }
}

// 辅助函数：调度下一次轮询
const scheduleNextPoll = (conversationId: string, fixedDelay?: number) => {
    // 指数退避：1s → 2s → 4s → 5s（封顶）
    const delay = fixedDelay || Math.min(1000 * Math.pow(2, Math.min(pollingAttempts.value, 2)), 5000)
    pollingAttempts.value++

    console.log(`[AI总结-对话] 下次轮询: ${delay}ms后 (第${pollingAttempts.value}次)`)

    chatPollingTimer = setTimeout(() => {
        fetchChatSummary(conversationId)
    }, delay)
}

// ==================== 全局轮询管理（修复版）====================

// 启动自动总结定时器（10分钟一次）
const startAutoSummaryPolling = () => {
    if (autoSummaryTimer) return

    console.log('[AI总结-全局] 启动定时轮询')

    // 立即执行一次
    if (!autoSummary.value && !loadingAuto.value) {
        fetchAutoSummary()
    }

    // 每10分钟执行一次
    autoSummaryTimer = setInterval(() => {
        console.log('[AI总结-全局] 定时触发刷新')
        fetchAutoSummary()
    }, 1 * 60 * 500) // 30秒
}

// 停止自动总结定时器
const stopAutoSummaryPolling = () => {
    if (autoSummaryTimer) {
        console.log('[AI总结-全局] 停止定时轮询')
        clearInterval(autoSummaryTimer)
        autoSummaryTimer = null
    }
}

// 关闭卡片时清理所有轮询
const handleCloseCard = () => {
    showFloatingCard.value = false
    if (chatPollingTimer) {
        clearTimeout(chatPollingTimer)
        chatPollingTimer = null
    }
}

const handleToggle = (collapsed: boolean) => {
    console.log('卡片折叠状态:', collapsed)
}

// ==================== 其他原有方法（保持不变）====================

const handleSelect = (index: MenuIndex) => {
    activeIndex.value = index
    chatStore.isChatPanelActive = index === 'chat'
    if (index === 'chat') {
        chatStore.updateUnreadTotal()
    }
    console.log(`切换到：${index}`)
}

const handleSwitchToChat = async (friendId: number) => {
    activeIndex.value = 'chat'
    chatStore.isChatPanelActive = true
    await nextTick()

    if (friendStore.friends.length === 0) {
        await friendStore.initFriendData()
    }

    const friend = friendStore.friends.find(f => f.friendId === friendId)
    if (!friend || !userStore.userInfo) return

    const myId = userStore.userInfo!.userId
    const min = Math.min(myId as number, friendId)
    const max = Math.max(myId as number, friendId)
    const conversationId = `user_${min}_${max}`

    let conversation = chatStore.conversations.find(c => c.id === conversationId)

    if (!conversation) {
        conversation = {
            id: conversationId,
            type: 'private',
            targetId: friendId,
            targetName: friend.nickname || friend.username,
            targetAvatar: friend.avatarUrl,
            unreadCount: 0,
            updatedAt: new Date()
        } as Conversation
        chatStore.conversations.unshift(conversation)
    }

    await chatStore.selectConversation(conversation)
}

const handleLogout = async () => {
    logoutLoading.value = true
    try {
        const currentUserId = authStore.tokenData?.userId

        if (clearLocalData.value && currentUserId) {
            await chatDB.delete()
            authStore.clearAuth(currentUserId)
        } else {
            await authStore.logout()
        }

        wsService.disconnect()

        router.push('/')
        ElMessage.success('已退出登录')
    } finally {
        logoutLoading.value = false
    }
}

const handleEditProfile = () => {
    showEditDialog.value = true
}

const handleEditSuccess = () => {
    showEditDialog.value = false
}

// 添加 FloatingCard 组件引用
const floatingCardRef = ref<InstanceType<typeof FloatingCard> | null>(null)
const isResetting = ref(false)

// 重置位置方法
const handleResetPositions = async () => {
    if (isResetting.value) return
    isResetting.value = true

    // 1. 重置 chatRoot 到屏幕居中
    if (chatRootRef.value) {
        const winWidth = window.innerWidth
        const winHeight = window.innerHeight
        const rect = chatRootRef.value.getBoundingClientRect()

        const centerLeft = (winWidth - rect.width) / 2
        const centerTop = (winHeight - rect.height) / 2

        // 添加平滑过渡效果（先开启 transition，移动后再关闭）
        chatRootRef.value.style.transition = 'all 0.3s ease'
        chatRootRef.value.style.left = `${centerLeft}px`
        chatRootRef.value.style.top = `${centerTop}px`

        // 重置拖动起点
        dragStart.value = {
            x: 0,
            y: 0,
            left: centerLeft,
            top: centerTop
        }

        // 动画结束后移除 transition，避免影响后续拖拽
        setTimeout(() => {
            if (chatRootRef.value) {
                chatRootRef.value.style.transition = ''
            }
        }, 300)
    }

    // 2. 重置 FloatingCard 位置
    if (floatingCardRef.value) {
        floatingCardRef.value.resetPosition()
    }

    // 旋转动画持续时间
    setTimeout(() => {
        isResetting.value = false
    }, 500)

    ElMessage.success('窗口位置已重置')
}

// ==================== 监听器（修复版）====================

watch(activeIndex, (newVal, oldVal) => {
    console.log(`[Tab切换] ${oldVal} -> ${newVal}`)

    if (newVal === 'chat') {
        // 停止全局轮询
        stopAutoSummaryPolling()

        // 清理旧的对话轮询
        if (chatPollingTimer) {
            clearTimeout(chatPollingTimer)
            chatPollingTimer = null
        }

        // 重置状态
        summaryState.value = 'IDLE'
        pollingAttempts.value = 0

        // 启动对话总结轮询
        if (chatStore.currentConversation) {
            fetchChatSummary(chatStore.currentConversation.id)
        }
    } else {
        // 非 chat 标签，启动全局轮询
        startAutoSummaryPolling()
    }
})

watch(
    () => chatStore.currentConversation,
    (newConv, oldConv) => {
        if (activeIndex.value === 'chat' && newConv) {
            // 切换会话时清理旧轮询
            if (chatPollingTimer) {
                clearTimeout(chatPollingTimer)
                chatPollingTimer = null
            }

            // 关键：切换会话时重置所有状态
            if (oldConv?.id !== newConv.id) {
                console.log(`[会话切换] ${oldConv?.id} -> ${newConv.id}`)
                summaryState.value = 'IDLE'
                pollingAttempts.value = 0
                chatSummary.value = null
            }

            fetchChatSummary(newConv.id)
        }
    },
    { deep: true }
)

// ==================== 生命周期钩子 ====================
onMounted(() => {
    /* 新增：计算初始居中位置 */
    nextTick(() => {
        if (chatRootRef.value) {
            const rect = chatRootRef.value.getBoundingClientRect()
            const winWidth = window.innerWidth
            const winHeight = window.innerHeight

            // 初始居中位置
            const initialLeft = (winWidth - rect.width) / 2
            const initialTop = (winHeight - rect.height) / 2

            chatRootRef.value.style.left = initialLeft + 'px'
            chatRootRef.value.style.top = initialTop + 'px'
        }
    })

    chatStore.isChatPanelActive = activeIndex.value === 'chat'

    if (activeIndex.value === 'chat' && chatStore.currentConversation) {
        fetchChatSummary(chatStore.currentConversation.id)
    } else if (activeIndex.value !== 'chat') {
        startAutoSummaryPolling()
    }
})

onUnmounted(() => {
    stopAutoSummaryPolling()
    if (chatPollingTimer) {
        clearTimeout(chatPollingTimer)
    }
    /* 新增：清理拖动事件监听 */
    document.removeEventListener('mousemove', onDragChat)
    document.removeEventListener('mouseup', stopDragChat)
})

</script>

<style scoped>
/* 所有原有样式保持不变，新增少量工具类 */
.icon-container {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
}

.nav-badge :deep(.el-badge__content) {
    top: 15px;
}

/* 修改 chatHeader 确保左右分布 */
.chatHeader {
    height: var(--header-height);
    /* 60px (或 56px 在 1080p 下) */
    width: 100%;
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    flex-wrap: nowrap;
    padding: 0 var(--space-lg);
    /* 16px */
    box-sizing: border-box;
    flex-shrink: 0;
    /* 关键：禁止压缩 */
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    /* 可选：分隔线 */
    /* 新增：拖动时的光标反馈 */
    user-select: none;
    /* 防止拖动时选中文本 */
}

/* 新增：拖动时的光标状态 */
.chatHeader:active {
    cursor: grabbing !important;
}

/* 状态切换下拉菜单样式优化 */
.status-dropdown-menu .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 14px;
}

.status-dropdown-menu .el-dropdown-menu__item .el-icon {
  font-size: 16px;
  margin-right: 0; /* 去掉默认右边距，用 gap 控制间距 */
}

/* 可选：调整下拉菜单的最小宽度，避免过窄 */
.status-dropdown-menu {
  min-width: 100px;
}

.header-left {
    display: flex;
    align-items: center;
    gap: 15px;
    flex: 1;
    min-width: 0;
    overflow: hidden;
}

.header-right {
    flex-shrink: 0;
    margin-left: 15px;
}

.close-btn {
    transition: all 0.3s;
}

.close-btn:hover {
    transform: scale(1.1);
}

.menu-badge {
    display: flex;
    align-items: center;
    justify-content: center;
}

.menu-badge :deep(.el-badge__content) {
    position: absolute;
    top: -8px;
    right: -8px;
    z-index: 10;
    border: 2px solid #f8f9fa;
    font-size: 11px;
    height: 18px;
    line-height: 18px;
    padding: 0 5px;
    border-radius: 9px;
}

:deep(.nickname) {
    font-size: 18px;
    --el-text-color-primary: #ffffff;
    --el-text-color-regular: #ffffff;
    --el-text-color-secondary: #ffffff;
    display: block;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 120px;
}

:deep(.personalizedSignature) {
    font-size: 14px;
    --el-text-color-primary: #ffffff;
    --el-text-color-regular: #ffffff;
    --el-text-color-secondary: #ffffff;
    display: block;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 150px;
}

.sidebar-menu.vertical-layout {
    --icon-size: 24px;
    --icon-spacing: 4px;
    --item-spacing: 16px;
    height: 100%;
    width: 100%;
    width: var(--sidebar-width);
    display: flex;
    flex-direction: column;
    background-color: #f8f9fa;
    border-right: none;
    --glass-bg: rgba(255, 255, 255, 0.1);
    --glass-border: rgba(255, 255, 255, 0.8);
    --glass-shadow: rgba(0, 0, 0, 0.1);
    --blur-strength: 8px;
    --hover-scale: 1.1;
    --active-scale: 1.1;
    --transition-smooth: cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar-menu.vertical-layout,
.sidebar-menu.vertical-layout .el-menu-item {
    background-color: transparent !important;
}

.menu-section {
    display: flex;
    flex-direction: column;
    align-items: center;
}

.menu-section:first-child {
    flex: 1;
    justify-content: flex-start;
    padding-top: 16px;
}

/* 底部菜单区域样式：确保子元素水平居中 */
.bottom-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-end;
    padding-bottom: 20px;
}

/* 为状态切换下拉菜单添加自定义类，使其占满父容器宽度并居中 */
.status-dropdown-trigger {
    width: 100%;
    display: flex;
    justify-content: center;
}

/* 确保下拉组件内的触发元素与普通导航项样式一致 */
.nav-icon,
.el-dropdown .nav-icon {  /* 使用更通用的选择器，确保覆盖下拉组件的包裹元素 */
  width: calc(var(--icon-size) + var(--icon-spacing) * 2) !important;
  height: calc(var(--icon-size) + var(--icon-spacing) * 2) !important;
  margin: 0 0 var(--item-spacing) 0 !important;
  padding: 0 !important;
  display: flex !important;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  transition: all 0.4s var(--transition-smooth) !important;
  transform: translateZ(0);
  will-change: transform, box-shadow, background-color;
  background-color: transparent !important;
  cursor: pointer;
}

.nav-icon {
    width: calc(var(--icon-size) + var(--icon-spacing) * 2) !important;
    height: calc(var(--icon-size) + var(--icon-spacing) * 2) !important;
    min-width: auto !important;
    margin: 0 0 var(--item-spacing) 0 !important;
    padding: 0 !important;
    display: flex !important;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    transition: all 0.4s var(--transition-smooth) !important;
    transform: translateZ(0);
    will-change: transform, box-shadow, background-color;

    &::after {
        display: none !important;
    }
}

.nav-icon::after {
    display: none !important;
}

.nav-icon .el-icon {
    color: #606266;
    font-size: var(--icon-size);
    width: var(--icon-size);
    height: var(--icon-size);
    margin: 0;
    transition: color 0.4s var(--transition-smooth);
}

.nav-icon.is-active {
    transform: scale(var(--active-scale)) translateZ(0);
    background-color: rgba(64, 158, 255, 0.15) !important;
    backdrop-filter: blur(var(--blur-strength));
    -webkit-backdrop-filter: blur(var(--blur-strength));
    box-shadow:
        0 4px 12px rgba(64, 158, 255, 0.25),
        0 0 0 1.5px rgba(64, 158, 255, 0.4) inset,
        0 6px 20px -4px rgba(64, 158, 255, 0.2) !important;
}

.nav-icon:not(.is-active):hover {
    transform: scale(var(--hover-scale)) translateZ(0);
    background-color: var(--glass-bg) !important;
    backdrop-filter: blur(var(--blur-strength));
    -webkit-backdrop-filter: blur(var(--blur-strength));
    box-shadow:
        0 4px 12px var(--glass-shadow),
        0 0 0 1px var(--glass-border) inset,
        0 6px 20px -4px rgba(0, 0, 0, 0.15) !important;
}

.nav-icon.is-active .el-icon {
    color: #409eff !important;
}

.nav-icon:not(.is-active):hover .el-icon {
    color: #5c5c5c !important;
}

:deep(.nickname) {
    font-size: 18px;
    --el-text-color-primary: #ffffff;
    --el-text-color-regular: #ffffff;
    --el-text-color-secondary: #ffffff;
}

:deep(.personalizedSignature) {
    font-size: 14px;
    --el-text-color-primary: #ffffff;
    --el-text-color-regular: #ffffff;
    --el-text-color-secondary: #ffffff;
}

.Navigation {
    width: var(--sidebar-width);
    /* 64px（或 60px 在 1080p 下） */
    height: 100%;
    z-index: 12;
    border-right: 1px solid #d9d9d9;
    box-shadow:
        2px 0 6px -3px rgba(0, 0, 0, 0.12),
        4px 0 10px -5px rgba(0, 0, 0, 0.06);
    position: relative;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    flex-shrink: 0;
    /* 关键：禁止被 flex 压缩 */
    flex-grow: 0;
    /* 关键：禁止被 flex 拉伸 */
    background-color: #f8f9fa;
    /* 确保背景色一致 */
}

.Navigation::after {
    content: '';
    position: absolute;
    top: 0;
    right: -1px;
    height: 100%;
    width: 1px;
    background: linear-gradient(to bottom,
            rgba(255, 255, 255, 0.8) 0%,
            rgba(255, 255, 255, 0.4) 50%,
            rgba(255, 255, 255, 0.8) 100%);
    pointer-events: none;
}


/* 新增：状态提示样式（插槽后备用） */
.status-wrapper {
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #909399;
    gap: 8px;
}

.containerBox {
    height: 100%;
    width: 100%;
    padding: 0px;
    display: flex;
    justify-content: center;
    align-items: center;
}

.chatMain {
    flex: 1;
    /* 关键：填充 header 剩余的所有空间 */
    width: 99%;
    /* 撑满父级宽度 */
    min-height: 0;
    /* 关键：允许 flex 子项压缩 */
    padding: 0;
    display: flex;
    /* 内部也用 flex */
    background-color: rgba(255, 255, 255);
    border-radius: 8px;
    position: relative;
    overflow: hidden;
    border: 1px solid #e8e8e8;
    margin: 0 var(--space-sm);
    /* 左右 8px 边距，视觉透气 */
}

.chatMain::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    border-left: 1px solid white;
    border-right: 1px solid white;
    box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.05);
    border-radius: 8px 8px 0px 0px;
    pointer-events: none;
}

.chatBox {
    height: 100vh;
    /* 明确视口高度 */
    width: 100vw;
    /* 明确视口宽度 */
    will-change: transform;
    backface-visibility: hidden;
    display: flex;
    justify-content: center;
    align-items: center;
    background-image: linear-gradient(135deg, #3ab5b0 0%, #3d99be 31%, #56317a 100%);
    padding: var(--space-lg);
    /* 16px 内边距，防止贴边 */
    box-sizing: border-box;
    /* 确保 padding 不撑开 */
    overflow: hidden;
    /* 防止子元素溢出 */
}

.fade-enter-active,
.fade-leave-active {
    transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
    opacity: 0;
}

.fade-leave-active {
    position: absolute;
    width: 100%;
    height: 100%;
}

.chatRoot {
    background-color: #00ccffe3;
    transition: opacity 0.3s ease, box-shadow 0.3s ease;

    /* 核心修改：从视口相对改为父容器相对 + 硬边界 */
    height: 85%;
    /* 基于 .chatBox 的高度 */
    width: 60%;
    /* 基于 .chatBox 的宽度 */
    max-height: 900px;
    /* 2K 屏下最大高度限制 */
    max-width: var(--panel-max-width);
    /* 1200px */
    min-height: 600px;
    /* 1080p 下最小高度 */
    min-width: var(--panel-min-width);
    /* 900px - 关键修复！ */

    display: flex;
    flex-direction: column;
    /* 垂直布局：header + main */
    justify-content: flex-start;
    /* 从顶部开始排列 */
    align-items: stretch;
    /* 子元素水平撑满 */
    border-radius: 16px;
    padding: 0;
    border: 1px solid rgba(255, 255, 255, 0.2);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
    overflow: hidden;
    /* 关键：防止内部撑开 */

    /* 新增：拖动相关 */
    user-select: none;
    /* 防止拖动时选中文本 */
}

@media (max-width: 768px) {
    .chatRoot {
        height: min(85%, 90vh);
        width: min(90%, 95vw);
    }
}

/* ==================== Dialog 全局响应式适配 ==================== */

/* 基础：所有 Dialog 默认响应式 */
:deep(.el-dialog) {
    width: 90% !important;
    max-width: 560px;
    /* 2K/大屏下最大 560px（原 500px 的舒适上限） */
    min-width: 320px;
    /* 超小屏保底，防止过窄 */
    margin: 8vh auto !important;
    /* 上下留白 8%，不贴顶，视觉上更居中 */
    border-radius: 12px;
    /* 统一圆角 */
    overflow: hidden;
    /* 防止内容溢出圆角 */
}

/* 内容区 padding 优化（减少大 Dialog 的内边距浪费） */
:deep(.el-dialog__body) {
    padding: 20px 24px;
    /* 原默认可能 30px，稍微紧凑 */
    max-height: 70vh;
    /* 防止内容过多时 Dialog 撑满全屏 */
    overflow-y: auto;
    /* 内容过多时内部滚动 */
}

/* 1080p 下进一步压缩（关键优化） */
@media (max-width: 1440px) and (max-height: 900px) {
    :deep(.el-dialog) {
        max-width: 480px;
        /* 1080p 下最大 480px（比 560px 更紧凑） */
        margin: 6vh auto !important;
        /* 更少的上下留白，利用屏幕空间 */
        width: 92% !important;
        /* 稍微宽一点，利用横向空间 */
    }

    :deep(.el-dialog__body) {
        padding: 16px 20px;
        /* 更紧凑的内边距 */
    }

    /* 表单 label 宽度微调（让输入框更宽） */
    :deep(.el-form-item__label) {
        padding-right: 8px;
        /* 原 12px，减少 label 占用 */
    }
}

/* 超小屏（笔记本 1366×768 等） */
@media (max-width: 1366px) {
    :deep(.el-dialog) {
        max-width: 420px;
        /* 更小屏幕，更小 Dialog */
        margin: 5vh auto !important;
        width: 95% !important;
    }

    :deep(.el-dialog__header) {
        padding: 12px 16px;
        /* 紧凑头部 */
    }
}

/* Dialog 内部元素在 1080p 下的微调 */
@media (max-width: 1440px) {

    /* 头像上传区域稍微缩小 */
    :deep(.avatar-uploader .el-avatar) {
        --el-avatar-size: 80px !important;
        /* 原 90px，稍微缩小 */
    }

    /* 表单输入框高度微调（如果用了 size="large" 可能过大） */
    :deep(.el-input__inner) {
        /* 保持默认，但如果之前设置了大号，这里可以覆盖 */
    }
}

/* 左下角悬浮重置按钮 - 扁平蓝白版 */
.reset-btn-wrapper {
    position: fixed;
    left: 30px;
    bottom: 30px;
    z-index: 99999;
    cursor: pointer;
}

.reset-btn {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    background: #1890ff;
    /* 纯色扁平蓝 */
    display: flex;
    align-items: center;
    justify-content: center;
    color: #ffffff;
    font-weight: bold;
    transition: background-color 0.2s ease;
    border: none;
    /* 去掉边框 */
    /* 无阴影、无渐变、无高光 */
}

/* Hover 状态：稍亮的蓝色，无位移 */
.reset-btn:hover {
    background: #61b7fd;
    /* 更亮的天蓝 */
}

/* Active/点击状态：深蓝色反馈 */
.reset-btn:active {
    background: #096dd9;
    /* 深海蓝 */
    transform: scale(0.95);
    /* 仅保留轻微按下效果 */
}

/* 点击时的旋转动画 */
.reset-btn.is-spinning .el-icon {
    animation: spin 0.6s linear;
}

@keyframes spin {
    from {
        transform: rotate(0deg);
    }

    to {
        transform: rotate(360deg);
    }
}

/* 移动端适配 */
@media (max-width: 768px) {
    .reset-btn-wrapper {
        left: 20px;
        bottom: 20px;
    }

    .reset-btn {
        width: 44px;
        height: 44px;
    }
}
</style>