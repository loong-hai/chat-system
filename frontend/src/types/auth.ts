import request from '@/utils/request'
import type { TokenDTO, LoginDTO, RegisterDTO } from '@/types/user'

export const authApi = {
  // 登录
  login(data: LoginDTO): Promise<TokenDTO> {
    return request.post('/auth/login', data)
  },
  
  // 注册
  register(data: RegisterDTO): Promise<TokenDTO> {
    return request.post('/auth/register', data)
  },
  
  // 登出
  logout(): Promise<void> {
    return request.post('/auth/logout')
  },
  
  // 刷新Token
  refreshToken(refreshToken: string): Promise<TokenDTO> {
    return request.post('/auth/refresh', null, {
      params: { refreshToken }
    })
  }
}