import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

// 消息通知类型
export interface Notification {
  id: number
  type?: number
  typeName?: string
  title?: string
  content?: string
  text?: string
  businessId?: number
  isRead: boolean
  readAt?: string
  createdAt: string
}

// 消息列表响应
export interface NotificationListVO {
  total: number
  unreadCount: number
  list: Notification[]
}

// 获取当前用户ID
function getUserId(): number | undefined {
  const userStore = useUserStore()
  return userStore.userInfo?.id
}

// 获取消息列表（分页）
export function getNotificationList(params?: {
  page?: number
  size?: number
  isRead?: number // 0=未读, 1=已读, 不传=全部
}) {
  const userId = getUserId()
  return request.get<NotificationListVO>('/notifications', { 
    params: { userId, ...params } 
  })
}

// 获取未读消息数量
export function getUnreadCount() {
  const userId = getUserId()
  return request.get<number>('/notifications/unread-count', { params: { userId } })
}

// 标记消息为已读
export function markAsRead(id: number) {
  const userId = getUserId()
  return request.put<void>(`/notifications/${id}/read`, null, { params: { userId } })
}

// 标记所有消息为已读
export function markAllAsRead() {
  const userId = getUserId()
  return request.put<number>('/notifications/read-all', null, { params: { userId } })
}

// 删除消息
export function deleteNotification(id: number) {
  const userId = getUserId()
  return request.delete<void>(`/notifications/${id}`, { params: { userId } })
}

// 清空所有已读消息
export function clearReadNotifications() {
  const userId = getUserId()
  return request.delete<number>('/notifications/clear', { params: { userId } })
}

// 获取在线连接数（调试用）
export function getOnlineCount() {
  return request.get<number>('/notifications/online-count')
}

// SSE 连接管理器
export class NotificationSSE {
  private eventSource: EventSource | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private reconnectAttempts = 0
  private maxReconnectAttempts = 10
  private baseReconnectDelay = 1000
  private listeners: Map<string, Set<(data: any) => void>> = new Map()
  private closed = false

  // 获取 API 基础地址
  private getBaseUrl(): string {
    const base = import.meta.env.VITE_API_BASE_URL || '/api'
    return base.endsWith('/') ? base.slice(0, -1) : base
  }

  // 获取 SSE URL（包含 Token 用于认证）
  private getSseUrl(): string {
    const userStore = useUserStore()
    const userId = userStore.userInfo?.id
    const token = userStore.token
    const baseUrl = this.getBaseUrl()
    const url = `${baseUrl}/notifications/subscribe`
    const params = new URLSearchParams()
    if (userId) params.append('userId', String(userId))
    if (token) params.append('token', token)
    return params.toString() ? `${url}?${params.toString()}` : url
  }

  // 连接 SSE
  async connect() {
    if (this.eventSource || this.closed) return

    // Token 过期检查与自动刷新（SSE 不支持自定义 Header，只能通过 URL 传 Token）
    const userStore = useUserStore()
    if (userStore.tokenExpireAt && userStore.tokenExpireAt - Date.now() < 30000) {
      const refreshed = await userStore.doRefreshToken()
      if (!refreshed) {
        this.close()
        this.emit('token-expired', null)
        return
      }
    }

    this.eventSource = new EventSource(this.getSseUrl())

    this.eventSource.onopen = () => {
      this.reconnectAttempts = 0
      this.emit('connected', null)
    }

    this.eventSource.onerror = () => {
      this.eventSource?.close()
      this.eventSource = null
      this.emit('error', null)
      this.scheduleReconnect()
    }

    // 监听连接成功事件
    this.eventSource.addEventListener('connected', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        this.emit('connected', data)
      } catch { /* ignore */ }
    })

    // 监听新通知事件
    this.eventSource.addEventListener('new-notification', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data) as Notification
        this.emit('new-notification', data)
      } catch { /* ignore */ }
    })

    // 监听未读数更新事件
    this.eventSource.addEventListener('unread-count', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data) as { count: number }
        this.emit('unread-count', data)
      } catch { /* ignore */ }
    })

    // 监听心跳
    this.eventSource.addEventListener('heartbeat', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        this.emit('heartbeat', data)
      } catch { /* ignore */ }
    })

    // 监听聊天已读回执（接收方在聊天页时，发送方会实时收到）
    this.eventSource.addEventListener('chat-read', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data) as { lastReadId: number; partnerId: number; readAt?: string }
        this.emit('chat-read', data)
      } catch { /* ignore */ }
    })
  }

  // 指数退避重连
  private scheduleReconnect() {
    if (this.closed || this.reconnectAttempts >= this.maxReconnectAttempts) return

    const delay = this.baseReconnectDelay * Math.pow(2, this.reconnectAttempts)
    this.reconnectAttempts++

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      void this.connect()
    }, delay)
  }

  // 注册事件监听
  on(event: string, callback: (data: any) => void) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set())
    }
    this.listeners.get(event)!.add(callback)
  }

  // 移除事件监听
  off(event: string, callback: (data: any) => void) {
    this.listeners.get(event)?.delete(callback)
  }

  // 触发事件
  private emit(event: string, data: any) {
    this.listeners.get(event)?.forEach(cb => cb(data))
  }

  // 关闭连接
  close() {
    this.closed = true
    this.eventSource?.close()
    this.eventSource = null
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.listeners.clear()
  }
}

// 单例：跨组件共享 SSE 连接（避免每个组件各自建连）
let _singleton: NotificationSSE | null = null

/**
 * 获取全局共享的 NotificationSSE 实例。
 * 首次调用时自动 connect；后续调用复用同一实例并共享监听器。
 */
export function getNotificationSSE(): NotificationSSE {
  if (!_singleton) {
    _singleton = new NotificationSSE()
    _singleton.connect()
  }
  return _singleton
}
