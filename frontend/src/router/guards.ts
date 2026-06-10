import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore, useUserStore, useFriendStore } from '@/stores'
import { useChatStore } from '@/stores/chat'
import { wsService } from '@/services/websocketService'

// 白名单路由（无需登录即可访问）
const WHITE_LIST = ['/', '/login', '/signup']

/**
 * 后台静默恢复 WebSocket（绝不阻塞任何流程）
 */
function silentRestoreWebSocket() {

  const authStore = useAuthStore()
  const chatStore = useChatStore()
  const friendStore = useFriendStore()

    // 添加调试日志
  console.log('WebSocket 恢复检查:', {
    hasToken: !!authStore.tokenData?.accessToken,
    isConnected: wsService.isConnected,
    currentUser: authStore.currentUserId
  })

  if (!authStore.tokenData?.accessToken || wsService.isConnected) return

  console.log('路由守卫：后台恢复 WebSocket...')

  // 先连接 WebSocket
  wsService.connect(
    authStore.tokenData.accessToken,
    authStore.currentUserStatus || 'online'
  ).then(() => {
    console.log('路由守卫：WebSocket 已连接，后台加载数据...')

    // 连接成功后，并行加载数据（不 await，不阻塞）
    Promise.all([
      chatStore.initConversations().catch(e => console.error('加载会话失败', e)),
      friendStore.fetchFriendRequests().catch(e => console.error('加载好友申请失败', e))
    ]).then(() => {
      // 数据加载完成后，同步离线消息
      wsService.syncOfflineMessages()
    })
  }).catch((error: any) => {
    console.error('路由守卫：WebSocket 恢复失败', error)
    // 如果是认证错误，清除状态（下次路由切换会重定向）
    if (error?.message?.includes('Unauthorized') || error?.message?.includes('401')) {
      authStore.clearAuth()
    }
  })
}

/**
 * 尝试加载用户信息（带超时保护）
 */
async function tryLoadUser(timeout: number = 5000): Promise<boolean> {
  const userStore = useUserStore()

  // 如果已有数据，直接返回
  if (userStore.currentUser?.userId) return true

  try {
    // 设置超时保护
    const timeoutId = setTimeout(() => {
      console.warn('路由守卫：获取用户信息超时')
    }, timeout)

    await userStore.fetchCurrentUser()
    clearTimeout(timeoutId)

    return !!userStore.currentUser?.userId
  } catch (error) {
    console.error('路由守卫：获取用户信息失败', error)
    return false
  }
}

export function setupRouterGuards(router: Router) {
  router.beforeEach(async (to, from, next) => {
    console.log(`[路由守卫] ${from.path} -> ${to.path}`)

    const authStore = useAuthStore()
    const userStore = useUserStore()

    // 1. 检查认证状态（同步）
    const isAuthenticated = authStore.checkLocalAuth()
    console.log(`[路由守卫] 认证状态: ${isAuthenticated}, 目标: ${to.path}`)

    // 2. 未登录处理
    if (!isAuthenticated) {
      if (WHITE_LIST.includes(to.path)) {
        console.log('[路由守卫] 未登录访问白名单，放行')
        next()
      } else {
        console.log('[路由守卫] 未登录访问受限页面，重定向到登录页')
        next({ path: '/', replace: true })
      }
      return
    }

    // 3. 已登录但访问登录/注册页，重定向到首页
    if (WHITE_LIST.includes(to.path)) {
      console.log('[路由守卫] 已登录访问登录页，重定向到首页')
      next({ path: '/index', replace: true })
      return
    }

    // 4. 关键逻辑：区分"从登录页跳转" vs "直接刷新/输入URL"
    // from.name === undefined 表示页面刷新或直接输入URL
    const isFromLogin = from.path === '/' || from.name === 'Login'
    const isPageRefresh = from.name === undefined

    try {
      // 场景A：从登录页跳转过来（Login.vue 已经获取过用户信息）
      if (isFromLogin && !isPageRefresh) {
        console.log('[路由守卫] 从登录页跳转，直接放行')
        next()

        // 后台恢复 WebSocket（延迟执行，确保页面已渲染）
        setTimeout(() => {
          silentRestoreWebSocket()
        }, 500)
        return
      }

      // 场景B：页面刷新或直接输入URL，需要确保用户信息已加载
      console.log('[路由守卫] 页面刷新/直接访问，检查用户信息...')

      if (!userStore.currentUser?.userId) {
        console.log('[路由守卫] 用户信息为空，后台加载...')
        // 先放行，避免阻塞渲染
        next()

        // 后台加载用户信息（不阻塞）
        tryLoadUser().then(loaded => {
          if (!loaded) {
            console.error('[路由守卫] 加载用户信息失败，强制登出')
            authStore.clearAuth()
            wsService.disconnect()
            router.replace('/')
          } else {
            // ✅ 修复：加载成功后，后台恢复 WebSocket
            console.log('[路由守卫] 用户信息加载完成，准备恢复 WebSocket')
            setTimeout(() => {
              silentRestoreWebSocket()
            }, 500)
          }
        })
        return
      }

      console.log('[路由守卫] 用户信息已确认，放行')
      next()

      // 刷新场景也后台恢复 WebSocket
      setTimeout(() => {
        silentRestoreWebSocket()
      }, 500)

    } catch (error) {
      console.error('[路由守卫] 异常:', error)
      // 出现异常也放行，避免死锁
      next()
    }
  })

  router.afterEach((to) => {
    document.title = (to.meta?.title as string) || 'Chat System'

    if (to.path === '/index') {
      const chatStore = useChatStore()
      chatStore.isChatPanelActive = true
    }
  })

  router.onError((error) => {
    console.error('路由错误:', error)
    ElMessage.error('页面加载失败，请刷新重试')
  })
}