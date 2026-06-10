// /db/chatDatabase.ts
import Dexie from 'dexie'
import type { Table } from 'dexie'
import { toRaw } from 'vue'
import type { ChatMessage, Conversation } from '@/types/message'

// 添加辅助函数：清理对象使其可被 IndexedDB 存储
function prepareForStorage<T>(data: T): T {
    if (!data) return data

    // 1. 解除 Vue 的 reactive 代理
    const raw = toRaw(data)

    // 2. 深拷贝并清理（JSON 序列化会移除函数和不可克隆的数据）
    const cleaned = JSON.parse(JSON.stringify(raw))

    // 3. 恢复 Date 对象（因为 JSON.stringify 会把 Date 转为字符串）
    const restoreDates = (obj: any) => {
        if (obj && typeof obj === 'object') {
            // 恢复已知的时间字段
            if (obj.clientTime) obj.clientTime = new Date(obj.clientTime)
            if (obj.serverTime) obj.serverTime = new Date(obj.serverTime)
            if (obj.deliveredAt) obj.deliveredAt = new Date(obj.deliveredAt)
            if (obj.readAt) obj.readAt = new Date(obj.readAt)
            if (obj.updatedAt) obj.updatedAt = new Date(obj.updatedAt)

            // 递归处理嵌套对象（如 lastMessage）
            Object.keys(obj).forEach(key => {
                if (typeof obj[key] === 'object' && obj[key] !== null) {
                    restoreDates(obj[key])
                }
            })
        }
        return obj
    }

    return restoreDates(cleaned)
}

// 获取当前用户ID用于数据库命名
function getCurrentUserId(): number | null {
    const currentId = localStorage.getItem('current_user_id')
    return currentId ? Number(currentId) : null
}

// 动态数据库类，每个用户一个实例
export class ChatDatabase extends Dexie {
    messages!: Table<ChatMessage>
    conversations!: Table<Conversation>
    syncMarks!: Table<{ conversationId: string; lastSequence: number }>
    
    private userId: number

    constructor(userId: number) {
        // 数据库名包含用户ID：ChatSystemDB_1001
        super(`ChatSystemDB_${userId}`)
        this.userId = userId

        // 定义数据库版本和表结构（与原代码一致）
        this.version(2).stores({
            messages: '++id, messageId, [conversationId+clientTime], [conversationId+status], conversationId, senderId, receiverId, status',
            conversations: 'id, type, targetId, updatedAt',
            syncMarks: 'conversationId'
        })
    }

    // ========== 核心查询方法（必须完整保留） ==========

    /**
     * 获取会话消息 - 按时间倒序（最新消息在前）
     */
async getMessagesByConversation(
    conversationId: string,
    limit: number = 50,
    offset: number = 0
): Promise<ChatMessage[]> {
    // 1. 倒序查询（最新的在前），使用 offset 跳过已加载的
    const messages = await this.messages
        .where('[conversationId+clientTime]')
        .between([conversationId, Dexie.minKey], [conversationId, Dexie.maxKey])
        .reverse()
        .offset(offset)
        .limit(limit)
        .toArray()
    
    // 2. 反转为正序（老的在前，新的在后），这样最新消息会在数组末尾
    // 渲染时新消息自然出现在底部
    return messages.reverse()
}

    /**
     * 获取未读消息（优先加载）
     */
    async getUnreadMessages(conversationId: string, currentUserId: number): Promise<ChatMessage[]> {
        return await this.messages
            .where({ conversationId, status: 'DELIVERED' })
            .filter(msg => msg.receiverId === currentUserId && !msg.readAt)
            .toArray()
    }

    /**
     * 保存或更新单条消息（发送消息时使用）
     */
    async saveMessage(message: ChatMessage): Promise<string> {
        // 深拷贝解除 Vue reactive 代理
        const msgToSave = JSON.parse(JSON.stringify(toRaw(message)))

        // 恢复 Date 对象
        if (msgToSave.clientTime) msgToSave.clientTime = new Date(msgToSave.clientTime)
        if (msgToSave.serverTime) msgToSave.serverTime = new Date(msgToSave.serverTime)

        // 清理临时 ID
        delete (msgToSave as any).id

        const existing = await this.messages.where({ messageId: msgToSave.messageId }).first()

        if (existing && existing.id) {
            const updateData = { ...msgToSave, id: existing.id }
            await this.messages.put(updateData)
            return String(existing.id)
        } else {
            return await this.messages.add(msgToSave) as string
        }
    }

    /**
     * 批量添加消息（用于加载历史记录）
     */
    async batchAddMessages(messages: ChatMessage[]): Promise<void> {
        await this.transaction('rw', this.messages, async () => {
            for (const msg of messages) {
                const prepared = prepareForStorage(msg)
                delete (prepared as any).id

                const exists = await this.messages.where({ messageId: prepared.messageId }).count()
                if (exists === 0) {
                    await this.messages.add(prepared)
                }
            }
        })
    }

    /**
     * 更新消息状态（发送成功/失败/已读）
     */
    async updateMessageStatus(
        messageId: string,
        status: string,
        updateTime: Date = new Date()
    ): Promise<void> {
        const msg = await this.messages.where({ messageId }).first()
        if (msg && msg.id) {
            const updateData: Partial<ChatMessage> = {
                status: status as any,
                ...(status === 'DELIVERED' ? { deliveredAt: updateTime } : {}),
                ...(status === 'READ' ? { readAt: updateTime } : {})
            }
            await this.messages.update(msg.id, updateData)
        }
    }

    /**
     * 清理旧消息（保留最近100条每会话，防止存储膨胀）
     */
    async cleanupOldMessages(conversationId: string, keepCount: number = 100): Promise<void> {
        const count = await this.messages.where({ conversationId }).count()
        if (count > keepCount) {
            const oldMessages = await this.messages
                .where({ conversationId })
                .limit(count - keepCount)
                .toArray()

            await this.messages.bulkDelete(oldMessages.map(m => m.id!))
        }
    }
}

// 单例管理器，确保每个用户只有一个数据库实例
class DatabaseManager {
    private static instances: Map<number, ChatDatabase> = new Map()
    private static currentDb: ChatDatabase | null = null

    static getInstance(): ChatDatabase | null {
        const userId = getCurrentUserId()
        if (!userId) return null

        // 如果用户换了，关闭旧数据库
        if (this.currentDb && this.currentDb.name !== `ChatSystemDB_${userId}`) {
            this.currentDb.close()
            this.currentDb = null
        }

        if (!this.instances.has(userId)) {
            const db = new ChatDatabase(userId)
            this.instances.set(userId, db)
        }

        this.currentDb = this.instances.get(userId)!
        return this.currentDb
    }

    static closeAll(): void {
        this.instances.forEach(db => db.close())
        this.instances.clear()
        this.currentDb = null
    }

    static deleteUserDatabase(userId: number): Promise<void> {
        return Dexie.delete(`ChatSystemDB_${userId}`)
    }
}

// 导出兼容旧代码的接口（代理模式）
export const chatDB = {
    get messages() {
        const db = DatabaseManager.getInstance()
        if (!db) throw new Error('No user logged in')
        return db.messages
    },
    get conversations() {
        const db = DatabaseManager.getInstance()
        if (!db) throw new Error('No user logged in')
        return db.conversations
    },
    get syncMarks() {
        const db = DatabaseManager.getInstance()
        if (!db) throw new Error('No user logged in')
        return db.syncMarks
    },
    
    // 代理所有方法
    async getMessagesByConversation(conversationId: string, limit?: number, offset?: number) {
        const db = DatabaseManager.getInstance()
        if (!db) return []
        return db.getMessagesByConversation(conversationId, limit, offset)
    },
    
    async getUnreadMessages(conversationId: string, currentUserId: number) {
        const db = DatabaseManager.getInstance()
        if (!db) return []
        return db.getUnreadMessages(conversationId, currentUserId)
    },
    
    async saveMessage(message: ChatMessage) {
        const db = DatabaseManager.getInstance()
        if (!db) return ''
        return db.saveMessage(message)
    },
    
    async batchAddMessages(messages: ChatMessage[]) {
        const db = DatabaseManager.getInstance()
        if (!db) return
        return db.batchAddMessages(messages)
    },
    
    async updateMessageStatus(messageId: string, status: string, updateTime?: Date) {
        const db = DatabaseManager.getInstance()
        if (!db) return
        return db.updateMessageStatus(messageId, status, updateTime)
    },
    
    async cleanupOldMessages(conversationId: string, keepCount?: number) {
        const db = DatabaseManager.getInstance()
        if (!db) return
        return db.cleanupOldMessages(conversationId, keepCount)
    },
    
    async delete() {
        const userId = getCurrentUserId()
        if (userId) {
            await DatabaseManager.deleteUserDatabase(userId)
        }
    }
}