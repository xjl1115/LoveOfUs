import request from '@/utils/request'

/** 分页响应 */
interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/**
 * 聊天消息 VO
 */
export interface ChatMessageVO {
  id?: number
  senderId: number
  receiverId: number
  content: string
  msgType?: number
  isRead?: number
  createdAt?: string
  readAt?: string | null
}

/**
 * WebSocket 聊天消息协议
 */
export interface WsChatMessage {
  type: 'CHAT' | 'TYPING' | 'READ' | 'PING' | 'PONG' | 'ERROR'
  clientMsgId?: string
  id?: number
  senderId?: number
  receiverId?: number
  content?: string
  msgType?: number
  isRead?: number
  lastReadId?: number
  createdAt?: string
  error?: string
}

/**
 * 历史消息分页
 */
export function fetchHistory(page = 1, size = 20) {
  return request.get<PageResult<ChatMessageVO>>('/chat/history', {
    params: { page, size }
  })
}

/**
 * 未读消息数
 */
export function fetchUnreadCount() {
  return request.get<number>('/chat/unread-count')
}

/**
 * 标记全部已读
 */
export function markAllRead() {
  return request.post<number>('/chat/read-all')
}

/**
 * WebSocket 连接 URL（携带 token）
 */
export function buildChatWsUrl(token: string): string {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  // VITE_API_BASE_URL 通常形如 http://host/api 或 /api
  const cleaned = base.replace(/\/$/, '')
  if (cleaned.startsWith('http')) {
    // http(s)://host/api → http(s)://host/api/ws/chat
    return `${cleaned.replace(/\/api$/, '')}/api/ws/chat?token=${encodeURIComponent(token)}`
  }
  // 相对路径 /api → /api/ws/chat
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}${cleaned}/ws/chat?token=${encodeURIComponent(token)}`
}
