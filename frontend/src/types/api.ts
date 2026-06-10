// API响应格式（对应后端的ResponseMessage）
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp?: number
}

// 分页响应
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

// 分页参数
export interface PageParams {
  page?: number
  size?: number
  sort?: string
  direction?: 'ASC' | 'DESC'
}