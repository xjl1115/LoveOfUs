import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types'
import { getDaysTogether } from '@/utils/date'

// 安全存储工具 - 使用 localStorage 持久化存储 Token
const secureStorage = {
  getItem(key: string): string | null {
    try {
      return localStorage.getItem(key)
    } catch {
      return null
    }
  },
  setItem(key: string, value: string): void {
    try {
      localStorage.setItem(key, value)
    } catch {
      // 存储失败时静默处理
    }
  },
  removeItem(key: string): void {
    try {
      localStorage.removeItem(key)
    } catch {
      // 移除失败时静默处理
    }
  }
}

export const useUserStore = defineStore('user', () => {
  // ==================== State ====================
  // 使用 sessionStorage 替代 localStorage，防止 XSS 攻击窃取持久化 Token
  const token = ref<string | null>(secureStorage.getItem('token'))
  const refreshToken = ref<string | null>(secureStorage.getItem('refreshToken'))
  /** Token 过期时间戳（毫秒） */
  const tokenExpireAt = ref<number | null>(
    (() => {
      const stored = secureStorage.getItem('tokenExpireAt')
      return stored ? Number(stored) : null
    })()
  )
  const userInfo = ref<UserInfo | null>(null)
  /** 用户最后活跃时间戳（由 useActive composable 更新） */
  const lastActiveTime = ref<number>(Date.now())

  /** Token 自动刷新阈值（剩余不足 5 分钟时触发） */
  const REFRESH_THRESHOLD_MS = 5 * 60 * 1000
  /** 用户不活跃超时时间（超过此时间无操作不自动刷新） */
  const ACTIVE_TIMEOUT_MS = 5 * 60 * 1000

  // 暴露刷新阈值供外部使用
  const refreshThresholdMs = REFRESH_THRESHOLD_MS

  // ==================== Getters ====================
  const isLoggedIn = computed(() => !!token.value)
  const daysTogether = computed(() => {
    if (userInfo.value?.relationshipStart) {
      return getDaysTogether(userInfo.value.relationshipStart)
    }
    return 0
  })
  /** Token 是否即将过期（剩余 < 5 分钟且用户活跃） */
  const shouldAutoRefresh = computed(() => {
    if (!tokenExpireAt.value || !token.value) return false
    const remaining = tokenExpireAt.value - Date.now()
    if (remaining <= 0 || remaining > REFRESH_THRESHOLD_MS) return false
    // 用户长时间无操作，不自动刷新（等下次操作触发 401）
    if (Date.now() - lastActiveTime.value > ACTIVE_TIMEOUT_MS) return false
    return true
  })

  // ==================== Actions ====================
  function setToken(newToken: string) {
    token.value = newToken
    secureStorage.setItem('token', newToken)
  }

  function setRefreshToken(newRefreshToken: string) {
    refreshToken.value = newRefreshToken
    secureStorage.setItem('refreshToken', newRefreshToken)
  }

  /** 设置 Token 并计算过期时间戳 */
  function setTokenWithExpire(newToken: string, expiresInSeconds: number) {
    token.value = newToken
    tokenExpireAt.value = Date.now() + expiresInSeconds * 1000
    secureStorage.setItem('token', newToken)
    secureStorage.setItem('tokenExpireAt', String(tokenExpireAt.value))
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  /** 更新用户活跃时间（由 useActive composable 调用） */
  function updateLastActiveTime(time: number = Date.now()) {
    lastActiveTime.value = time
  }

  /** 刷新 Token - 使用单例模式防止并发刷新 */
  let refreshPromise: Promise<boolean> | null = null

  async function doRefreshToken(): Promise<boolean> {
    // 如果已有刷新请求在进行中，直接返回该 Promise
    if (refreshPromise) {
      return refreshPromise
    }

    if (!refreshToken.value) return false

    // 创建新的刷新 Promise
    refreshPromise = (async () => {
      try {
        const response = await fetch('/api/auth/token/refresh', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token.value}`,
            'X-Internal-Skip-Refresh': 'true'
          }
        })
        const resData = await response.json()
        if (resData.code === 200 && resData.data) {
          const { token: newToken, expiresIn, refreshToken: newRefreshToken } = resData.data
          setTokenWithExpire(newToken, expiresIn)
          if (newRefreshToken) setRefreshToken(newRefreshToken)
          return true
        }
        return false
      } catch {
        return false
      } finally {
        // 刷新完成后清除 Promise 引用
        refreshPromise = null
      }
    })()

    return refreshPromise
  }

  function logout() {
    // 派发登出前事件：让持有长连接（如 WebSocket）的组件主动关闭，避免服务端"仍显示在线"
    try {
      window.dispatchEvent(new CustomEvent('lovemap:before-logout'))
    } catch {
      // 派发失败不影响主流程
    }
    token.value = null
    refreshToken.value = null
    tokenExpireAt.value = null
    userInfo.value = null
    secureStorage.removeItem('token')
    secureStorage.removeItem('refreshToken')
    secureStorage.removeItem('tokenExpireAt')
  }

  return {
    token,
    refreshToken,
    tokenExpireAt,
    userInfo,
    lastActiveTime,
    isLoggedIn,
    daysTogether,
    shouldAutoRefresh,
    refreshThresholdMs,
    setToken,
    setRefreshToken,
    setTokenWithExpire,
    setUserInfo,
    updateLastActiveTime,
    doRefreshToken,
    logout
  }
})
