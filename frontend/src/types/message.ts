// types/message.ts
export enum MessageType {
    TEXT = 'TEXT',
    IMAGE = 'IMAGE',
    VOICE = 'VOICE',
    VIDEO = 'VIDEO',
    FILE = 'FILE',
    LOCATION = 'LOCATION',
    CONTACT = 'CONTACT',
    SYSTEM = 'SYSTEM',
    NOTICE = 'NOTICE',
    TIP = 'TIP'
}

export enum ReceiverType {
    USER = 'USER',
    GROUP = 'GROUP'
}

// 修改后：
export enum MessageStatus {
    SENDING = 'SENDING',      // 发送中（前端本地状态）
    IN_QUEUE = 'IN_QUEUE',    // ✅ 新增：已进入消息队列（服务端接收成功，待推送）
    SENT = 'SENT',            // 已发送（到达服务器，已持久化）
    DELIVERED = 'DELIVERED',  // 已送达（对方在线且已推送到设备）
    READ = 'READ',            // 已读
    FAILED = 'FAILED',        // 发送失败
    OFFLINE = 'OFFLINE',      // ✅ 新增：转为离线消息（对方不在线）
    RECALLED = 'RECALLED'     // 已撤回
}

// 添加这些类型到现有文件中
export interface MessageProps {
  message: ChatMessage
  currentUserId?: number
}

export interface ChatWindowProps {
  conversation?: Conversation
  conversationName?: string
  conversationId?: string
}

export interface ChatWindowEmits {
  (e: 'video-call'): void
  (e: 'audio-call'): void
  (e: 'more-action'): void
}

export interface ChatMessage {
    id?: string;                    // MongoDB ObjectId
    messageId: string;              // 业务ID (UUID)
    type: MessageType;
    content: string;
    thumbnail?: string;
    fileName?: string;
    fileSize?: number;
    fileExt?: string;
    duration?: number;
    extra?: Record<string, any>;

    // 发送者信息
    senderId: number;
    senderName?: string;
    senderAvatar?: string;

    // 接收者信息
    receiverId: number;
    receiverName?: string;
    receiverType: ReceiverType;

    // 状态
    status: MessageStatus;
    isDeleted?: boolean;
    isRecalled?: boolean;

    // 时间
    clientTime: Date;
    serverTime?: Date;
    deliveredAt?: Date;
    readAt?: Date;

    // 会话
    conversationId: string;
    sequence?: number;

    // 本地字段（不在DTO中）
    isLocal?: boolean;              // 标记是否本地生成（发送中）
    errorMsg?: string;              // 发送失败原因
}

export interface Conversation {
    id: string;                     // conversationId
    type: 'private' | 'group';
    targetId: number;               // 对方用户ID或群ID
    targetName: string;
    targetAvatar?: string;
    lastMessage?: ChatMessage;
    unreadCount: number;
    updatedAt: Date;
    isPinned?: boolean;
    isMuted?: boolean;
}