// api/ai.ts
import request from '@/utils/request';
import type { SummaryCacheDTO } from '@/types/ai';

export const aiApi = {
  /**
   * 获取自动总结（最近1-2天已读消息）
   * GET /ai/summary/auto
   */
  getAutoSummary(): Promise<string> {
    return request.get('/ai/summary/auto',{timeout: 120000});
  },

  /**
   * 获取对话注意事项
   * GET /ai/summary/chat/{conversationId}
   * @param conversationId 会话ID
   */
  getChatSummary(conversationId: string): Promise<SummaryCacheDTO> {
    return request.get(`/ai/summary/chat/${conversationId}`,{timeout: 120000});
  },

  /**
   * 获取回复建议
   * POST /ai/suggest-reply
   * @param conversationId 会话ID
   * @param lastMessage 需要回复的最后一条消息
   */
  suggestReply(conversationId: string, lastMessage: string): Promise<string> {
    return request.post('/ai/suggest-reply', null, {
      params: { conversationId, lastMessage },
      timeout: 120000
    });
  }
};