// config/api.ts
export const API_CONFIG = {
    // REST API 基础路径
    BASE_URL: import.meta.env.VITE_API_BASE_URL || '/api',
    // WebSocket 基础路径
    WS_BASE_URL: import.meta.env.VITE_WS_BASE_URL ?? '',
} as const