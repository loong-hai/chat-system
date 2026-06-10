// 认证相关工具函数

// 保存登录信息
export function saveAuthInfo(tokenData: any) {
  localStorage.setItem('access_token', tokenData.accessToken)
  localStorage.setItem('refresh_token', tokenData.refreshToken)
  localStorage.setItem('session_id', tokenData.sessionId)
  localStorage.setItem('user_id', String(tokenData.userId))
  localStorage.setItem('username', tokenData.username)
  localStorage.setItem('nickname', tokenData.nickname)
  
  if (tokenData.avatarUrl) {
    localStorage.setItem('avatar_url', tokenData.avatarUrl)
  }
}

// 清除登录信息
export function clearAuthInfo() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('session_id')
  localStorage.removeItem('user_id')
  localStorage.removeItem('username')
  localStorage.removeItem('nickname')
  localStorage.removeItem('avatar_url')
}

// 检查是否登录
export function isLoggedIn(): boolean {
  return !!localStorage.getItem('access_token')
}

// 获取当前用户ID
export function getCurrentUserId(): number | null {
  const userId = localStorage.getItem('user_id')
  return userId ? parseInt(userId) : null
}

// 获取当前用户名
export function getCurrentUsername(): string | null {
  return localStorage.getItem('username')
}

// 获取Token
export function getToken(): string | null {
  return localStorage.getItem('access_token')
}

// 获取会话ID
export function getSessionId(): string | null {
  return localStorage.getItem('session_id')
}