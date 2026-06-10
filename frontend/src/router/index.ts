import { createRouter, createWebHistory } from 'vue-router'
import { setupRouterGuards } from './guards'

const routes = [
  {
    path: '/',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/signup',
    name: 'SignUP',
    component: () => import('@/views/SignUP.vue'),
    meta: { title: '注册', public: true }
  },
  {
    path: '/index',
    name: 'Index',
    component: () => import('@/views/Index.vue'),
    meta: { title: '消息', requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 添加滚动行为
  scrollBehavior() {
    return { top: 0 }
  }
})

// 注册路由守卫
setupRouterGuards(router)

export default router