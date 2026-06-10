// types/ai.ts
/**
 * AI总结缓存DTO（对应后端 SummaryCacheDTO）
 */
export interface SummaryCacheDTO {
  /** 总结内容 */
  content: string;
  /** 生成时间 */
  generateTime?: string;
  /** 会话ID（功能2使用） */
  conversationId?: string;
  /** 基于的最后一条消息ID */
  lastMessageId?: string;
  /** 是否正在生成中 */
  isGenerating: boolean;
}