import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosError } from 'axios'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'

const axiosInstance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// ==================== Token 自动刷新机制 ====================

/** 内部标记——发刷新请求时带上，拦截器据此跳过刷新逻辑 */
const HEADER_SKIP_REFRESH = 'X-Internal-Skip-Refresh'

/** 正在刷新中的 Promise，用于防止并发刷新 */
let refreshPromise: Promise<boolean> | null = null

// ==================== 公开接口白名单 ====================

/** 无需认证的公开接口路径 */
const PUBLIC_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/captcha',
  '/auth/password/reset',
  '/auth/token/refresh'
]

/** 判断是否为公开接口 */
function isPublicPath(url: string | undefined): boolean {
  if (!url) return false
  return PUBLIC_PATHS.some(path => url.includes(path))
}

// ==================== 请求拦截器 ====================

axiosInstance.interceptors.request.use(
  async (config) => {
    const userStore = useUserStore()

    // 跳过 Token 刷新的请求（防止递归）
    if (config.headers?.get(HEADER_SKIP_REFRESH)) {
      return config
    }

    // 公开接口直接放行，不检查 Token
    if (isPublicPath(config.url)) {
      return config
    }

    // 检查 Token 状态：区分"已过期"和"即将过期"
    if (userStore.token && userStore.tokenExpireAt) {
      const remaining = userStore.tokenExpireAt - Date.now()

      if (remaining <= 0) {
        // Token 已过期，尝试用 refreshToken 刷新一次
        if (!refreshPromise) {
          refreshPromise = userStore.doRefreshToken().finally(() => { refreshPromise = null })
        }
        const ok = await refreshPromise
        if (!ok) {
          userStore.logout()
          showToast('登录已过期，请重新登录')
          window.location.href = '/'
          return Promise.reject(new Error('Token已过期'))
        }
      } else if (remaining <= userStore.refreshThresholdMs) {
        // Token 即将过期，静默刷新（不阻塞请求）
        if (!refreshPromise) {
          refreshPromise = userStore.doRefreshToken().finally(() => { refreshPromise = null })
        }
        await refreshPromise
      }
    }

    // 正常携带 Token
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// ==================== 响应拦截器 ====================

axiosInstance.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code !== 200) {
        showToast(res.message || '请求失败')
        return Promise.reject(new Error(res.message))
      }
      return res.data
    }
    return res
  },
  async (error: AxiosError) => {
    const { response, config } = error

    // 401 处理：登录过期，跳转登录页
    if (response?.status === 401) {
      const userStore = useUserStore()

      // 尝试用刷新机制补救
      const isRefreshRequest = config?.headers?.get(HEADER_SKIP_REFRESH)
      if (!isRefreshRequest && userStore.token) {
        if (!refreshPromise) {
          refreshPromise = userStore.doRefreshToken().finally(() => { refreshPromise = null })
        }
        const ok = await refreshPromise
        if (ok) {
          // 刷新成功，重试原请求
          const retryConfig = { ...config }
          retryConfig.headers!.set(HEADER_SKIP_REFRESH, 'true')
          retryConfig.headers!.Authorization = `Bearer ${userStore.token}`
          return axiosInstance(retryConfig)
        }
      }

      // 刷新失败或无刷新 Token，跳转登录
      userStore.logout()
      showToast('登录已过期，请重新登录')
      window.location.href = '/'
      return Promise.reject(error)
    }

    // 非 401 错误统一提示
    const backendMessage = (response?.data as any)?.message
    showToast(backendMessage || error.message || '网络错误')
    return Promise.reject(error)
  }
)

// ==================== 封装请求方法 ====================

const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return axiosInstance.get(url, config) as any
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return axiosInstance.post(url, data, config) as any
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return axiosInstance.put(url, data, config) as any
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return axiosInstance.delete(url, config) as any
  }
}

export default request