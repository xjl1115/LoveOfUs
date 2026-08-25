import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getUserInfo } from '@/api/user'

const routes = [
  {
    path: '/',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/photo/:id',
    name: 'PhotoDetail',
    component: () => import('@/views/PhotoDetail.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/upload',
    name: 'Upload',
    component: () => import('@/views/Upload.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/albums',
    name: 'Albums',
    component: () => import('@/views/Albums.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/albums/:id',
    name: 'AlbumDetail',
    component: () => import('@/views/AlbumDetail.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/export',
    name: 'Export',
    component: () => import('@/views/Export.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/account-settings',
    name: 'AccountSettings',
    component: () => import('@/views/AccountSettings.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/ai-chat',
    name: 'AIChat',
    component: () => import('@/views/AIChat.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    if (to.hash) {
      return { el: to.hash, behavior: 'smooth' }
    }
    return { top: 0 }
  }
})

/**
 * 从 sessionStorage 重新读取 Token 信息
 * 用于浏览器重新打开时，确保能获取到持久化的 Token
 */
function reloadTokenFromStorage() {
  const userStore = useUserStore()
  
  try {
    const token = sessionStorage.getItem('token')
    const tokenExpireAt = sessionStorage.getItem('tokenExpireAt')

    if (token && !userStore.token) {
      userStore.setToken(token)
    }
    if (tokenExpireAt && !userStore.tokenExpireAt) {
      const expireTime = Number(tokenExpireAt)
      userStore.tokenExpireAt = expireTime
    }

    return { token, tokenExpireAt: tokenExpireAt ? Number(tokenExpireAt) : null }
  } catch {
    // sessionStorage 访问失败时返回空值
    return { token: null, tokenExpireAt: null }
  }
}

// 开发环境日志工具 - 生产环境自动禁用
const isDev = import.meta.env.DEV
const logger = {
  debug: (...args: unknown[]) => isDev && console.log(...args),
  warn: (...args: unknown[]) => isDev && console.warn(...args),
  error: (...args: unknown[]) => console.error(...args)
}

// 路由导航守卫：验证登录状态和 Token 有效性
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 每次导航前都从 sessionStorage 重新读取 Token（应对浏览器重新打开的情况）
  const storageToken = reloadTokenFromStorage()

  // 仅开发环境输出调试日志
  logger.debug('[Router] 导航到:', to.path)
  logger.debug('[Router] isLoggedIn:', userStore.isLoggedIn)

  // 已登录用户访问登录页，自动跳转到首页
  if (to.name === 'Login' && (userStore.isLoggedIn || storageToken.token)) {
    // 检查 token 是否过期/即将过期
    if (userStore.tokenExpireAt) {
      const remaining = userStore.tokenExpireAt - Date.now()
      if (remaining <= 0) {
        logger.debug('[Router] Token已过期，留在登录页')
        userStore.logout()
        return true
      }
      // 即将过期时尝试刷新
      if (remaining <= userStore.refreshThresholdMs) {
        const ok = await userStore.doRefreshToken()
        if (!ok) {
          userStore.logout()
          return true
        }
      }
    }
    logger.debug('[Router] 已登录用户访问登录页，跳转到首页')
    return { name: 'Home' }
  }

  if (to.meta.requiresAuth) {
    // 未登录，重定向到登录页（同时检查 store 和 sessionStorage）
    if (!userStore.isLoggedIn && !storageToken.token) {
      logger.debug('[Router] 未登录，跳转到登录页')
      return { name: 'Login' }
    }
    // Token 已过期，尝试刷新一次
    if (userStore.tokenExpireAt) {
      const remaining = userStore.tokenExpireAt - Date.now()
      if (remaining <= 0) {
        const ok = await userStore.doRefreshToken()
        if (!ok) {
          logger.debug('[Router] Token已过期且刷新失败，登出并跳转')
          userStore.logout()
          return { name: 'Login' }
        }
      }
    }
    logger.debug('[Router] Token有效，继续访问')
    
    // 有 Token 但无用户信息时，向后端验证 Token 有效性
    if (!userStore.userInfo) {
      try {
        const userInfo = await getUserInfo()
        if (userInfo) {
          userStore.setUserInfo(userInfo)
        }
      } catch (error: any) {
        // 区分网络错误和认证错误
        if (error.response?.status === 401) {
          // Token 确实无效，执行登出
          logger.warn('[Router] Token 验证失败(401)，执行登出')
          userStore.logout()
          return { name: 'Login' }
        }
        // 网络错误，允许继续但记录警告
        logger.warn('[Router] 获取用户信息失败(网络错误)，使用缓存状态')
      }
    }
  }

  // 已登录或访问公开页面，正常放行
  return true
})

export default router
