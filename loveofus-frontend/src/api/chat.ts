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
  /** 是否撤回：0=否 1=是 */
  revoked?: number
  /** 撤回时间 */
  revokedAt?: string
}

/**
 * WebSocket 聊天消息协议
 */
export interface WsChatMessage {
  type:
    | 'CHAT'
    | 'TYPING'
    | 'READ'
    | 'PING'
    | 'PONG'
    | 'ERROR'
    | 'DELETE'
    | 'RECALL'
    | 'DELETE_ACK'
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
 * 进入聊天页（标记"在线于聊天页"，开启实时已读）
 */
export function enterChatPage(partnerId: number) {
  return request.post<void>('/chat/in-chat/enter', { partnerId })
}

/**
 * 聊天页心跳（保持在线状态不被清理）
 */
export function heartbeatChatPage() {
  return request.post<void>('/chat/in-chat/heartbeat', {})
}

/**
 * 离开聊天页
 */
export function leaveChatPage() {
  return request.post<void>('/chat/in-chat/leave', {})
}

/**
 * WebSocket 连接 URL（携带 token）
 * <p>
 * 默认与 axios 的 baseURL 一致：'/api'，最终路径为 '/api/ws/chat'
 */
export function deleteMessage(messageId: number) {
  return request.post<boolean>(`/chat/message/${messageId}`)
}

export function deleteMessagesBatch(messageIds: number[]) {
  return request.post<number>('/chat/messages/delete-batch', messageIds)
}

export function recallMessage(messageId: number) {
  return request.post<boolean>(`/chat/message/${messageId}/recall`)
}

export function buildChatWsUrl(token: string): string {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  const cleaned = base.replace(/\/$/, '')
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  // cleaned 已经带 '/api'（或完整 http(s)://host/api），直接拼 '/ws/chat'
  return `${scheme}://${window.location.host}${cleaned}/ws/chat?token=${encodeURIComponent(token)}`
}
