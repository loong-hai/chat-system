// src/api/message.ts
import request from '@/utils/request'

export const messageApi = {
  markConversationRead(conversationId: string): Promise<void> {
    return request.post('/messages/read/conversation', null, {
      params: { conversationId }
    })
  }
}