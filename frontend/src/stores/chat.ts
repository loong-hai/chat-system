// stores/chat.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage, Conversation } from '@/types/message'
import { MessageStatus, MessageType, ReceiverType } from '@/types/message'
import { chatDB } from '@/db/chatDatabase'
import { wsService } from '@/services/websocketService'
import { useUserStore } from '@/stores/user'  // 关键：导入 useUserStore
import { toRaw } from 'vue'
export const useChatStore = defineStore('chat', () => {
    // State
    const conversations = ref<Conversation[]>([])
    const currentConversation = ref<Conversation | null>(null)
    const currentMessages = ref<ChatMessage[]>([])
    const unreadTotal = ref(0)
    const loading = ref(false)
    const isChatPanelActive = ref(false) // 标记当前是否正在查看聊天面板

    // Getters
    const sortedConversations = computed(() => {
        return [...conversations.value].sort((a, b) => {
            if (a.isPinned && !b.isPinned) return -1
            if (!a.isPinned && b.isPinned) return 1
            return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
        })
    })

    // ========== Actions ==========

    // 初始化：从 IndexedDB 加载会话列表
    async function initConversations(): Promise<void> {
        const allConversations = await chatDB.conversations
            .orderBy('updatedAt')
            .reverse()
            .toArray()
        conversations.value = allConversations
        updateUnreadTotal()
        // 去重
        const uniqueConversations = Array.from(
            new Map(allConversations.map(c => [c.id, c])).values()
        )
        conversations.value = uniqueConversations

        // 关键：同时刷新好友申请数（确保导航栏红点及时显示）
        try {
            const { useFriendStore } = await import('@/stores/friend')
            const friendStore = useFriendStore()
            await friendStore.fetchFriendRequests()
        } catch (e) {
            console.error('刷新好友申请失败:', e)
        }
    }

    /**
     * 加载会话消息（核心方法）
     * 策略：先加载未读消息，如果没有则加载最近50条
     */
async function loadConversationMessages(
    conversationId: string,
    userId?: number
): Promise<{ messages: ChatMessage[]; hasUnread: boolean }> {
    loading.value = true
    const actualUserId = userId ?? useUserStore().userInfo?.userId
    if (!actualUserId) {
        loading.value = false
        throw new Error('用户未登录')
    }

    try {
        // 1. 先尝试获取未读消息
        const unread = await chatDB.getUnreadMessages(conversationId, actualUserId)

        if (unread.length > 0) {
            // 加载上下文（最近的20条），getMessagesByConversation 现在返回正序
            const contextMessages = await chatDB.getMessagesByConversation(conversationId, 20)
            
            // 合并并去重（基于 messageId）
            const allMessages = [...contextMessages, ...unread]
            const uniqueMessages = Array.from(
                new Map(allMessages.map(m => [m.messageId, m])).values()
            )
            
            // 按时间正序排序（确保老的在前，新的在后）
            uniqueMessages.sort((a, b) => 
                new Date(a.clientTime).getTime() - new Date(b.clientTime).getTime()
            )
            
            currentMessages.value = uniqueMessages
            return { messages: currentMessages.value, hasUnread: true }
        }

        // 2. 没有未读，加载最近 50 条（getMessagesByConversation 已返回正序）
        const recent = await chatDB.getMessagesByConversation(conversationId, 50, 0)
        currentMessages.value = recent // 已经是正序 [老的..., 新的]
        return { messages: recent, hasUnread: false }
    } finally {
        loading.value = false
    }
}

    /**
     * 加载更多历史消息（下拉刷新）
     */
async function loadMoreMessages(): Promise<boolean> {
    if (!currentConversation.value) return false

    const currentCount = currentMessages.value.length
    // 加载更早的消息，offset 是当前数量，返回的是更早的 30 条（正序）
    const older = await chatDB.getMessagesByConversation(
        currentConversation.value.id,
        30,
        currentCount
    )

    if (older.length === 0) return false

    // older 是正序 [更老的..., 较老的...]，currentMessages 是 [较老的..., 新的...]
    // 合并后：[更老的..., 较老的..., 新的...]，符合时间线
    currentMessages.value = [...older, ...currentMessages.value]
    return true
}

    /**
     * 发送文本消息
     */
    async function sendTextMessage(
        content: string,
        receiverId: number,
        receiverType: 'USER' | 'GROUP' = 'USER'
    ): Promise<void> {
        const userStore = useUserStore()
        const userId = userStore.userInfo?.userId

        if (!userId) throw new Error('用户未登录')

        const conversationId = generateConversationId(userId, receiverId, receiverType)

        // 构造消息对象（不完整的，缺少 id 和 serverTime）
        const draftMessage: Omit<ChatMessage, 'id' | 'serverTime'> = {
            messageId: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            type: MessageType.TEXT,
            content,
            senderId: userId,
            senderName: userStore.userInfo?.nickname || '',
            senderAvatar: userStore.userInfo?.avatarUrl || '',
            receiverId,
            receiverType: receiverType === 'USER' ? ReceiverType.USER : ReceiverType.GROUP,
            receiverName: '', // 可以从好友列表获取
            conversationId,
            clientTime: new Date(),
            status: MessageStatus.SENDING
        }

        // 调用 WebSocket 服务（内部会处理本地存储和发送）
        await wsService.sendMessage(draftMessage)
    }

    /**
     * 添加消息（从 WebSocket 接收或本地发送）
     */
    function addMessage(message: ChatMessage): void {
        // 去重检查
        const exists = currentMessages.value.some(m => m.messageId === message.messageId)
        if (exists) return

        currentMessages.value.push(message)
    }

    /**
     * 更新消息状态（用于 UI 更新）
     */
    function updateMessageStatus(messageId: string, status: MessageStatus): void {
        const msg = currentMessages.value.find(m => m.messageId === messageId)
        if (msg) {
            msg.status = status
        }
    }

    // function deepClone<T>(obj: T): T {
    //     return JSON.parse(JSON.stringify(obj))
    // }

    /**
     * 更新会话的最后一条消息（维护会话列表）
     */
    async function updateConversationFromMessage(message: ChatMessage): Promise<void> {
        const existingIndex = conversations.value.findIndex(c => c.id === message.conversationId)

        if (existingIndex >= 0) {
            // 更新现有会话
            const conv = conversations.value[existingIndex]
            if (!conv) return

            conv.lastMessage = message
            // ✅ 确保使用服务器时间（如果可用）或客户端时间
            conv.updatedAt = message.serverTime ? new Date(message.serverTime) : new Date()

            // 深拷贝避免存储 Vue reactive 对象
            const convToSave = JSON.parse(JSON.stringify(toRaw(conv)))
            convToSave.updatedAt = new Date(convToSave.updatedAt) // 恢复 Date 对象

            conversations.value.splice(existingIndex, 1)
            conversations.value.unshift(conv)

            await chatDB.conversations.put(convToSave)
        } else {
            // 创建新会话
            const userStore = useUserStore()
            const currentUserId = userStore.userInfo?.userId
            const isSentByMe = message.senderId === currentUserId

            const newConv: Conversation = {
                id: message.conversationId,
                type: message.receiverType === ReceiverType.USER ? 'private' : 'group',
                targetId: isSentByMe ? message.receiverId : message.senderId,
                targetName: isSentByMe
                    ? (message.receiverName || '未知')
                    : (message.senderName || '未知'),
                targetAvatar: isSentByMe ? '' : (message.senderAvatar || ''),
                lastMessage: message,
                unreadCount: isSentByMe ? 0 : 1,
                updatedAt: new Date(),
                isPinned: false,
                isMuted: false
            }

            // 深拷贝避免存储 Vue reactive 对象
            const convToSave = JSON.parse(JSON.stringify(toRaw(newConv)))
            if (convToSave.updatedAt) {
                convToSave.updatedAt = new Date(convToSave.updatedAt)
            }

            conversations.value.unshift(newConv)
            await chatDB.conversations.put(convToSave)

            // 关键修复：新消息创建会话时也要更新未读总数！
            if (!isSentByMe) {
                updateUnreadTotal()
            }
        }
    }

    /**
     * 增加未读计数（修复版：确保响应式更新）
     */
    async function incrementUnread(conversationId: string): Promise<void> {
        const convIndex = conversations.value.findIndex(c => c.id === conversationId)
        if (convIndex !== -1) {
            const oldConv = conversations.value[convIndex]
            if (oldConv) {
                // 关键：创建新对象替换，确保 Vue 响应式追踪
                const newConv: Conversation = {
                    ...oldConv,
                    unreadCount: (oldConv.unreadCount || 0) + 1,
                    updatedAt: new Date()
                }
                // 使用 splice 触发数组更新
                conversations.value.splice(convIndex, 1, newConv)
            }
        }

        // 更新 IndexedDB
        const conv = await chatDB.conversations.get(conversationId)
        if (conv) {
            conv.unreadCount = (conv.unreadCount || 0) + 1
            conv.updatedAt = new Date()
            await chatDB.conversations.put(conv)
        }

        updateUnreadTotal()
    }

    /**
     * 清空未读（打开聊天窗口时）
     */
    /**
     * 清空未读（修复版：立即响应）
     */
    async function clearUnread(conversationId: string): Promise<void> {
        // 1. 立即清空内存中的数据（关键：先更新UI）
        const convIndex = conversations.value.findIndex(c => c.id === conversationId)
        if (convIndex !== -1) {
            const conv = conversations.value[convIndex]
            if (conv) {
                conv.unreadCount = 0
            }
        }

        // 2. 再更新 IndexedDB
        const conv = await chatDB.conversations.get(conversationId)
        if (conv) {
            conv.unreadCount = 0
            await chatDB.conversations.put(conv)
        }

        // 3. 立即更新总数
        updateUnreadTotal()
    }

    function updateUnreadTotal(): void {
        unreadTotal.value = conversations.value.reduce((sum, conv) => sum + (conv.unreadCount || 0), 0)
    }

    /**
     * 选择当前会话（切换聊天对象）
     */
    async function selectConversation(conv: Conversation): Promise<void> {
        currentConversation.value = conv
        currentMessages.value = []
        await clearUnread(conv.id)

        const userStore = useUserStore()
        if (userStore.userInfo?.userId) {
            await loadConversationMessages(conv.id, userStore.userInfo.userId)
        }
    }

    function generateConversationId(userId: number, targetId: number, type: 'USER' | 'GROUP'): string {
        if (type === 'USER') {
            const min = Math.min(userId, targetId)
            const max = Math.max(userId, targetId)
            return `user_${min}_${max}`
        }
        return `group_${targetId}`
    }

    function updateMessage(updatedMessage: ChatMessage) {
    const index = currentMessages.value.findIndex(m => m.messageId === updatedMessage.messageId)
    if (index !== -1) {
        currentMessages.value[index] = updatedMessage
    }
}

    return {
        conversations,
        currentConversation,
        currentMessages,
        unreadTotal,
        loading,
        isChatPanelActive,
        sortedConversations,
        initConversations,
        selectConversation,
        loadConversationMessages,
        loadLocalMessages: loadConversationMessages,  // 添加这行别名
        loadMoreMessages,
        sendTextMessage,
        addMessage,
        updateMessageStatus,
        updateConversationFromMessage,
        incrementUnread,
        clearUnread,
        updateUnreadTotal,
        updateMessage
    }
})