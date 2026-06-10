import axios, {
    type AxiosInstance,
    type AxiosRequestConfig,
    type AxiosResponse,
    type InternalAxiosRequestConfig
} from 'axios'

import { API_CONFIG } from '@/config/api'

import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'
import type { ApiResponse } from '@/types/api'

// 创建axios实例
const service: AxiosInstance = axios.create({
    baseURL: API_CONFIG.BASE_URL || '/api',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json;charset=utf-8'
    }
})

// 请求拦截器
service.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        config.headers = config.headers || {}
        
        // 动态获取当前用户的 token
        const currentUserId = localStorage.getItem('current_user_id')
        if (currentUserId) {
            const token = localStorage.getItem(`user_${currentUserId}_access_token`)
            if (token) {
                config.headers.Authorization = `Bearer ${token}`
            }
            
            const sessionId = localStorage.getItem(`user_${currentUserId}_session_id`)
            if (sessionId) {
                config.headers['X-Session-Id'] = sessionId
            }
        }

        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// 响应拦截器
service.interceptors.response.use(
    (response: AxiosResponse) => {
        const res = response.data

        // 如果响应是ApiResponse格式
        if (res && typeof res === 'object' && 'code' in res) {
            const apiRes = res as ApiResponse<any>

            // 成功响应
            if (apiRes.code === 200) {
                return apiRes.data
            }

            // 处理特定错误码
            switch (apiRes.code) {
                case 401:
                    ElMessageBox.confirm('登录已过期，请重新登录', '提示', {
                        confirmButtonText: '重新登录',
                        cancelButtonText: '取消',
                        type: 'warning'
                    }).then(() => {
                        localStorage.clear()
                        router.push('/')
                    })
                    break
                case 403:
                    ElMessage.warning('无权限访问')
                    break
                case 202:
                    ElMessage.warning('正在生成中')
                    break
                default:
                    ElMessage.error(apiRes.message || '操作失败')
            }

            return Promise.reject(new Error(apiRes.message || '操作失败'))
        }

        // 非标准格式直接返回
        return res
    },
    (error) => {
        // 网络错误处理
        if (error.response) {
            switch (error.response.status) {
                case 401:
                    ElMessage.error('未授权，请重新登录')
                    localStorage.clear()
                    router.push('/')
                    break
                case 403:
                    ElMessage.error('禁止访问')
                    break
                case 404:
                    ElMessage.error('资源不存在')
                    break
                case 500:
                    ElMessage.error('服务器内部错误')
                    break
                default:
                    ElMessage.error(error.response.data?.message || '请求失败')
            }
        } else if (error.request) {
            ElMessage.error('网络连接失败')
        } else {
            ElMessage.error('请求配置错误')
        }

        return Promise.reject(error)
    }
)

// 封装常用请求方法
export const request = {
    // 修改：允许 data 为 null 或 undefined
    get<T = any>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
        return service.get(url, { params, ...config })
    },

    // 修改：data 类型改为 any 或 object | null | undefined
    post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
        return service.post(url, data, config)
    },

    // 修改：data 类型改为 any 或 object | null | undefined
    put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
        return service.put(url, data, config)
    },

    delete<T = any>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
        return service.delete(url, { params, ...config })
    },

    // 修改：data 类型改为 any 或 object | null | undefined
    patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
        return service.patch(url, data, config)
    },

    // 文件上传方法
    upload<T = any>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> {
        return service.post(url, formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            },
            ...config
        })
    }
}

export default request