// api/storage.ts
import request from '@/utils/request'

export interface FileUploadResult {
  isRapidUpload: boolean
  fileUrl: string
  thumbnailUrl?: string
  fileKey: string
  fileHash: string
  fileSize: number
  presignedUrl?: string
  expiresIn?: number
  uploadId?: string
  message?: string
}

/**
 * 上传聊天文件（服务器中转，适用于小文件）
 * @param file 文件
 * @param conversationId 会话ID（可选）
 */
export function uploadChatFile(file: File, conversationId?: string): Promise<FileUploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  if (conversationId) {
    formData.append('conversationId', conversationId)
  }
  return request.post('/storage/chat/file', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 获取预签名上传URL（大文件专用）
 */
export interface PresignedUploadRequest {
  fileName: string
  fileSize: number
  fileHash?: string
  fileType: 'avatar' | 'chat' | 'other'
  contentType?: string
  conversationId?: string
}

export function getPresignedUrl(data: PresignedUploadRequest): Promise<FileUploadResult> {
  return request.post('/storage/presigned-url', data)
}

/**
 * 确认预签名上传完成
 */
export function confirmUpload(fileKey: string, fileHash: string): Promise<FileUploadResult> {
  return request.post('/storage/confirm-upload', null, {
    params: { fileKey, fileHash }
  })
}