// /services/websocketService.ts
import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { ChatMessage } from '@/types/message'
import { MessageStatus } from '@/types/message'
import { chatDB } from '@/db/chatDatabase'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { API_CONFIG } from '@/config/api'

import { ReceiverType, MessageType } from '@/types/message'
// TODO: 用户需创建 api/storage.ts 并实现以下函数
import { uploadChatFile, getPresignedUrl, confirmUpload } from '@/api/storage'
import type { FileUploadResult } from '@/api/storage'

class WebSocketService {
    private client: Client | null = null
    private subscriptions: StompSubscription[] = []
    private reconnectAttempts = 0
    private maxReconnectAttempts = 5
    private messageQueue: ChatMessage[] = [] // 离线消息队列
    public isConnecting = false
    // ✅ 新增：心跳定时器
    private heartbeatInterval: number | null = null

    private currentUserId: number | null = null

    // ========== 在类中添加辅助函数（如果不存在） ==========
    /**
     * 生成会话ID（与后端算法一致）
     */
    private generateConversationId(userId: number, targetId: number, type: 'USER' | 'GROUP'): string {
        if (type === 'USER') {
            const min = Math.min(userId, targetId)
            const max = Math.max(userId, targetId)
            return `user_${min}_${max}`
        }
        return `group_${targetId}`
    }


    /**
     * 发送多媒体消息（自动完成文件上传）
     * @param file 文件对象
     * @param receiverId 接收者ID
     * @param receiverType 接收类型（USER/GROUP）
     * @param extra 额外信息（如缩略图、时长等）
     */
    async sendMediaMessage(
        file: File,
        receiverId: number,
        receiverType: 'USER' | 'GROUP' = 'USER',
        extra?: Record<string, any>
    ): Promise<boolean> {
        const chatStore = useChatStore()
        const userStore = useUserStore()
        const userId = userStore.userInfo?.userId
        if (!userId) throw new Error('用户未登录')

        // 1. 生成临时消息ID
        const tempMessageId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`

        // 2. 构建基础消息对象（先占位，文件上传完成后更新）
        const draftMessage: Omit<ChatMessage, 'id' | 'serverTime'> = {
            messageId: tempMessageId,
            type: this.getFileMessageType(file), // 根据文件类型确定消息类型
            content: '', // 占位，上传后填入URL
            fileName: file.name,
            fileSize: file.size,
            fileExt: file.name.split('.').pop() || '',
            extra: extra || {},
            senderId: userId,
            senderName: userStore.userInfo?.nickname || '',
            senderAvatar: userStore.userInfo?.avatarUrl || '',
            receiverId,
            receiverType: receiverType === 'USER' ? ReceiverType.USER : ReceiverType.GROUP,
            conversationId: this.generateConversationId(userId, receiverId, receiverType),
            clientTime: new Date(),
            status: MessageStatus.SENDING,
            // 注意：ChatMessage 类型中可能没有 fileHash 字段，我们暂时放入 extra
        }

        // 3. 先存入本地（乐观更新）
        const localMessage = { ...draftMessage, isLocal: true }
        const tempId = await chatDB.saveMessage(localMessage)
        if (chatStore.currentConversation?.id === localMessage.conversationId) {
            chatStore.addMessage({ ...localMessage, id: tempId })
        }
        await chatStore.updateConversationFromMessage(localMessage)

        // 4. 上传文件
        try {
            let uploadResult: FileUploadResult
            // 简单判断文件大小：小于100MB使用服务器中转，否则使用预签名
            if (file.size < 100 * 1024 * 1024) {
                uploadResult = await uploadChatFile(file, localMessage.conversationId)
            } else {
                // 大文件：先申请预签名
                const presigned = await getPresignedUrl({
                    fileName: file.name,
                    fileSize: file.size,
                    fileType: 'chat',
                    contentType: file.type,
                    conversationId: localMessage.conversationId
                })
                if (presigned.isRapidUpload) {
                    // 秒传成功，直接使用现有URL
                    uploadResult = presigned
                } else {
                    // 上传文件到预签名URL
                    await this.uploadFileToPresignedUrl(presigned.presignedUrl!, file)
                    // 确认上传
                    uploadResult = await confirmUpload(presigned.fileKey, presigned.fileHash)
                }
            }

            // 5. 更新消息内容为文件URL，并将文件信息存入 extra
            const updatedMessage: ChatMessage = {
                ...localMessage,
                content: uploadResult.fileUrl,
                thumbnail: uploadResult.thumbnailUrl,
                fileSize: uploadResult.fileSize,
                // 将文件哈希和文件Key存入 extra（因为 ChatMessage 类型没有直接字段）
                extra: {
                    ...localMessage.extra,
                    fileHash: uploadResult.fileHash,
                    fileKey: uploadResult.fileKey,
                },
            }

            // 6. 通过WebSocket发送消息（实际发送的消息对象包含文件URL）
            const success = await this.sendMessage(updatedMessage)
            if (success) {
                // 更新本地消息状态为SENT
                await chatDB.updateMessageStatus(tempMessageId, 'SENT')
                chatStore.updateMessageStatus(tempMessageId, MessageStatus.SENT)

                // ✅ 新增：将包含最终 URL 的完整消息保存到本地，替换临时消息
                await chatDB.saveMessage(updatedMessage)  // 由于 messageId 相同，会更新原消息
                chatStore.updateMessage(updatedMessage)   // 需要先在 chatStore 中实现 updateMessage
            } else {
                // 标记为离线或失败
                await chatDB.updateMessageStatus(tempMessageId, 'FAILED')
                chatStore.updateMessageStatus(tempMessageId, MessageStatus.FAILED)
            }
            return success
        } catch (error) {
            console.error('发送多媒体消息失败:', error)
            await chatDB.updateMessageStatus(tempMessageId, 'FAILED')
            chatStore.updateMessageStatus(tempMessageId, MessageStatus.FAILED)
            return false
        }
    }

    /**
     * 根据文件类型获取消息类型
     */
    private getFileMessageType(file: File): MessageType {
        if (file.type.startsWith('image/')) return MessageType.IMAGE
        if (file.type.startsWith('audio/')) return MessageType.VOICE
        if (file.type.startsWith('video/')) return MessageType.VIDEO
        return MessageType.FILE
    }

    /**
     * 上传文件到预签名URL（PUT请求）
     */
    private async uploadFileToPresignedUrl(url: string, file: File): Promise<void> {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest()
            xhr.open('PUT', url, true)
            xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
            xhr.onload = () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve()
                } else {
                    reject(new Error(`上传失败: ${xhr.status}`))
                }
            }
            xhr.onerror = () => reject(new Error('网络错误'))
            xhr.send(file)
        })
    }



    // 初始化连接（登录后调用）
    async connect(token: string, status: 'online' | 'busy' | 'invisible' | 'offline' = 'online'): Promise<void> {
        if (this.isConnecting || this.client?.active) return
        // 如果用户变了，先断开旧连接
        const userId = Number(localStorage.getItem('current_user_id'))
        if (!userId) {
            throw new Error('No user logged in')
        }

        this.isConnecting = true
        this.currentUserId = userId

        const wsBaseUrl = API_CONFIG.WS_BASE_URL || ''

        const socket = new SockJS(`${wsBaseUrl}/ws?token=${token}&status=${status}`, null, {
            transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
            timeout: 5000
        })

        this.client = new Client({
            webSocketFactory: () => socket,
            reconnectDelay: 5000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            debug: (str) => {
                console.debug('STOMP:', str)
            },

            onConnect: async (frame) => {
                console.log('WebSocket 连接成功:', frame)
                this.isConnecting = false
                this.reconnectAttempts = 0
                
                try {
                    const { useUserStore } = await import('@/stores/user')
                    await useUserStore().fetchCurrentUser()
                } catch (e) {
                    console.error('恢复状态失败:', e)
                }
                
                this.setupSubscriptions()
                this.flushOfflineQueue()
                this.syncOfflineMessages()
                this.startHeartbeat()
            },

            onDisconnect: () => {
                console.log('WebSocket 断开连接')
                this.isConnecting = false
            },

            onStompError: (frame) => {
                console.error('STOMP 连接错误:', frame)
                this.isConnecting = false
                // STOMP 错误通常是认证问题
                if (frame.headers?.message?.includes('Unauthorized') || this.reconnectAttempts === 0) {
                    this.handleAuthFailure()
                } else {
                    this.handleReconnect(token, status)
                }
            },

            onWebSocketClose: async (event) => {  // ✅ async
                console.warn('WebSocket 连接关闭', event)
                this.isConnecting = false

                // 检查是否首次连接就失败（可能是 4010/4011 错误）
                if (this.reconnectAttempts === 0 && !this.client?.active) {
                    console.error('WebSocket 首次连接失败，可能是 Token 过期或无效')
                    await this.handleAuthFailure()  // ✅ await
                    return
                }

                this.handleReconnect(token, status)
            }
        })

        this.client.activate()
    }
    // ✅ 新增：启动应用级心跳（用于 Redis TTL 续期）
    private startHeartbeat(): void {
        // 先清除已有定时器，避免重复
        this.stopHeartbeat()

        // 每 30 秒发送一次应用级心跳（与 STOMP 协议心跳分开）
        // 后端 WebSocketController.wsHeartbeat 会处理此消息并续期 Redis TTL
        this.heartbeatInterval = window.setInterval(() => {
            if (this.client?.active) {
                this.client.publish({
                    destination: '/app/heartbeat',
                    body: JSON.stringify({
                        timestamp: Date.now(),
                        clientTime: new Date().toISOString()
                    })
                })
                console.debug('发送应用级心跳')
            }
        }, 30000) // 30秒间隔，可根据后端配置调整（建议小于 Redis 过期时间）
    }

    // 添加 getter 方便检查状态
    get connectionStatus(): 'connected' | 'connecting' | 'disconnected' {
        if (this.client?.active) return 'connected'
        if (this.isConnecting) return 'connecting'
        return 'disconnected'
    }

    // ✅ 新增：停止应用级心跳
    private stopHeartbeat(): void {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval)
            this.heartbeatInterval = null
            console.debug('停止应用级心跳')
        }
    }

    /**
     * 认证失败处理：清除本地认证并通知用户
     */
    private async handleAuthFailure(): Promise<void> {
        try {
            // ✅ 使用动态 import 替代 require
            const { useAuthStore } = await import('@/stores/auth')
            const authStore = useAuthStore()
            authStore.clearAuth()

            console.error('登录已过期，请重新登录')

            // 动态导入 router
            const router = (await import('@/router')).default
            router.push('/')
        } catch (e) {
            console.error('处理认证失败时出错:', e)
            // 兜底：强制刷新页面到登录页
            window.location.href = '/'
        }
    }

    private setupSubscriptions(): void {
        if (!this.client?.active) return

        // 订阅个人消息队列（私聊消息）
        const chatSub = this.client.subscribe('/user/queue/chat', async (message) => {
            try {
                const data: ChatMessage = JSON.parse(message.body)
                await this.handleIncomingMessage(data)
            } catch (error) {
                console.error('处理接收消息失败:', error)
            }
        })

        // 订阅系统通知（离线消息同步完成等）
        const systemSub = this.client.subscribe('/user/queue/system', (message) => {
            const notification = JSON.parse(message.body)
            this.handleSystemNotification(notification)
        })

        this.subscriptions.push(chatSub, systemSub)
    }

    /**
     * 处理接收到的消息 - 核心逻辑
     */
    /**
     * 处理接收到的消息 - 核心逻辑（修复版）
     */
    private async handleIncomingMessage(message: ChatMessage): Promise<void> {
        const userStore = useUserStore()
        const chatStore = useChatStore()

        // 系统消息处理（保持不变）
        if (message.type === 'SYSTEM' && message.extra?.type === 'offline_sync_complete') {
            console.log('离线消息同步完成，刷新本地数据')
            if (chatStore.currentConversation?.id) {
                chatStore.loadLocalMessages(chatStore.currentConversation.id)
            }
            chatStore.initConversations()
            return
        }

        // 1. 立即存入 IndexedDB（确保不丢失）
        const messageToSave: ChatMessage = {
            ...message,
            status: message.senderId === userStore.userInfo?.userId
                ? MessageStatus.SENT
                : MessageStatus.DELIVERED,
            clientTime: new Date(message.clientTime)
        }
        await chatDB.saveMessage(messageToSave)

        // 2. 关键修复：判断是否应该自动已读
        // 条件：是当前会话 AND 聊天面板激活 AND 不是我自己发的
        const isCurrentConversation = chatStore.currentConversation?.id === message.conversationId
        const isMyMessage = message.senderId === userStore.userInfo?.userId
        const shouldAutoRead = isCurrentConversation && chatStore.isChatPanelActive && !isMyMessage

        if (shouldAutoRead) {
            // 用户正在看聊天窗口，自动已读
            chatStore.addMessage(messageToSave)
            this.sendReadReceipt(message.messageId)
            await chatDB.updateMessageStatus(message.messageId, 'READ')
        } else {
            // 用户不在聊天界面或看的别的会话，增加未读计数
            if (!isMyMessage) {
                await chatStore.incrementUnread(message.conversationId)
                // 如果是当前会话但面板没激活（如在好友列表），也添加到消息列表但不标记已读
                if (isCurrentConversation) {
                    chatStore.addMessage(messageToSave)
                }
            }
        }

        // 3. 更新会话列表最后一条消息
        await this.updateConversationLastMessage(messageToSave)
    }



    /**
     * 发送消息 - 先本地后网络
     */
    async sendMessage(draftMessage: Omit<ChatMessage, 'id' | 'serverTime'>): Promise<boolean> {
        const chatStore = useChatStore()

        // 1. 立即写入本地（乐观更新）
        const localMessage: ChatMessage = {
            ...draftMessage,
            clientTime: draftMessage.clientTime || new Date(),
            status: MessageStatus.SENDING,
            isLocal: true // 标记为本地临时消息
        }

        // 生成临时 ID（用于 UI 更新）
        const tempId = await chatDB.saveMessage(localMessage)

        // 2. 立即更新 UI（无论网络状态）
        if (chatStore.currentConversation?.id === localMessage.conversationId) {
            chatStore.addMessage({ ...localMessage, id: tempId })
        }
        await this.updateConversationLastMessage({ ...localMessage, id: tempId })

        // 3. 检查连接状态
        if (!this.client?.active) {
            console.log('离线状态，消息已加入队列:', localMessage.messageId)
            this.messageQueue.push({ ...localMessage, id: tempId })
            return false // 表示离线 pending
        }

        try {
            // 4. 发送 WebSocket 消息
            this.client.publish({
                destination: '/app/chat.send',
                body: JSON.stringify({
                    ...draftMessage,
                    clientTime: localMessage.clientTime.toISOString()
                })
            })

            // 5. 更新本地状态为已发送
            await chatDB.updateMessageStatus(localMessage.messageId, 'SENT')
            chatStore.updateMessageStatus(localMessage.messageId, MessageStatus.SENT)

            return true
        } catch (error) {
            console.error('发送消息失败:', error)
            // 更新为失败状态
            await chatDB.updateMessageStatus(localMessage.messageId, 'FAILED')
            chatStore.updateMessageStatus(localMessage.messageId, MessageStatus.FAILED)
            return false
        }
    }

    /**
     * 重新发送失败的消息
     */
    async resendMessage(messageId: string): Promise<void> {
        const message = await chatDB.messages.where({ messageId }).first()
        if (message && this.client?.active) {
            await this.sendMessage(message)
        }
    }

    /**
     * 发送已读回执
     */
    sendReadReceipt(messageId: string): void {
        if (!this.client?.active) return
        this.client.publish({
            destination: '/app/chat.read',
            body: JSON.stringify({ messageId, timestamp: new Date().toISOString() })
        })
    }

    /**
     * 发送输入状态（可选功能）
     */
    sendTypingStatus(conversationId: string, isTyping: boolean): void {
        if (!this.client?.active) return
        this.client.publish({
            destination: '/app/chat.typing',
            body: JSON.stringify({ conversationId, isTyping })
        })
    }

    /**
     * 刷新离线消息（登录后调用）
     * 触发后端推送未读消息
     */
    syncOfflineMessages(): void {
        if (!this.client?.active) return
        this.client.publish({
            destination: '/app/sync.offline',
            body: JSON.stringify({ timestamp: Date.now() })
        })
    }

    private async flushOfflineQueue(): Promise<void> {
        if (this.messageQueue.length === 0) return

        console.log(`开始发送 ${this.messageQueue.length} 条离线消息`)
        const queue = [...this.messageQueue]
        this.messageQueue = []

        for (const msg of queue) {
            await this.sendMessage(msg)
        }
    }

    private handleReconnect(token: string, status: 'online' | 'busy' | 'invisible' | 'offline'): void {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('WebSocket 重连失败次数过多')
            // 达到最大重试次数，可能是认证失效
            this.handleAuthFailure()  // 不需要 await，让它异步执行
            return
        }

        this.reconnectAttempts++
        console.log(`WebSocket 将在 5s 后重试 (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)

        setTimeout(() => {
            this.connect(token, status)
        }, 5000)
    }

    private async updateConversationLastMessage(message: ChatMessage): Promise<void> {
        const chatStore = useChatStore()
        // 更新 Pinia 中的会话列表
        await chatStore.updateConversationFromMessage(message)
    }

    // 在 handleSystemNotification 中添加
    private handleSystemNotification(notification: any): void {
        const chatStore = useChatStore()

        if (notification.type === 'offline_sync_complete') {
            console.log('离线消息同步完成，刷新本地数据')
            if (chatStore.currentConversation) {
                chatStore.loadLocalMessages(chatStore.currentConversation.id)
            }
            chatStore.initConversations()
        }

        // 好友申请通知：使用 setTimeout 避免阻塞和 async/await 问题
        if (notification.type === 'friend_request_new') {
            console.log('收到新好友申请')
            setTimeout(async () => {
                try {
                    const { useFriendStore } = await import('@/stores/friend')
                    const friendStore = useFriendStore()
                    await friendStore.fetchFriendRequests()
                } catch (e) {
                    console.error('刷新好友申请失败:', e)
                }
            }, 0)
        }

        // 新消息通知
        if (notification.type === 'new_message') {
            chatStore.initConversations()
        }
    }
    // 断开连接（登出时调用）
    disconnect(): void {
        this.stopHeartbeat()
        this.subscriptions.forEach(sub => sub.unsubscribe())
        this.subscriptions = []
        this.client?.deactivate()
        this.client = null
        this.isConnecting = false
        this.currentUserId = null
    }

    // 是否连接中
    get isConnected(): boolean {
        return this.client?.active ?? false
    }
}

// 导出一个单例实例
export const wsService = new WebSocketService()