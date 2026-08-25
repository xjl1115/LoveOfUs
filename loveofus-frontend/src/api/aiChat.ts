import request from '@/utils/request'
import { ssePost } from '@/utils/sse'
import { useUserStore } from '@/stores/user'

/**
 * AI 情侣聊天助手 API
 * 后端约定：
 * - POST /api/ai/chat          非流式，返回完整结果（降级用）
 * - POST /api/ai/chat/stream   SSE 流式，逐字返回内容
 * - GET  /api/ai/chat/sessions 获取会话列表（预留，本期不强制依赖）
 */

// ==================== 类型定义 ====================

/** 单条消息角色 */
export type ChatRole = 'user' | 'ai' | 'tool' | 'system'

/** 单条消息 */
export interface ChatMessage {
  /** 前端本地 id（uuid） */
  id: string
  role: ChatRole
  /** 消息文本内容 */
  content: string
  /** 创建时间（毫秒） */
  createdAt: number
  /** 工具调用名称（role=tool 时有值，如 "查询照片"） */
  toolName?: string
  /** 是否正在流式接收中（仅 ai 角色） */
  streaming?: boolean
  /** 失败标记 */
  error?: boolean
}

/** 流式请求体 */
export interface ChatRequest {
  sessionId: string
  message: string
}

/** 非流式响应 data */
export interface ChatResponse {
  sessionId: string
  message: ChatMessage
}

// ==================== 常量 ====================

/** localStorage 键：当前会话 id */
const SESSION_ID_KEY = 'ai_chat_session_id'

/** localStorage 键：会话历史 */
const HISTORY_KEY = 'ai_chat_history'

/** 历史消息最大条数 */
const MAX_HISTORY = 50

// ==================== 会话管理 ====================

/** 获取当前会话 id；首次访问自动生成 */
export function getSessionId(): string {
  let sid = localStorage.getItem(SESSION_ID_KEY)
  if (!sid) {
    sid = `ai_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    localStorage.setItem(SESSION_ID_KEY, sid)
  }
  return sid
}

/** 主动开启新会话（不重置历史，仅生成新 sid） */
export function newSession(): string {
  const sid = `ai_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  localStorage.setItem(SESSION_ID_KEY, sid)
  return sid
}

// ==================== 历史持久化 ====================

/** 读取历史消息 */
export function loadHistory(): ChatMessage[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    if (!raw) return []
    const list = JSON.parse(raw) as ChatMessage[]
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

/** 保存历史消息（自动裁剪长度） */
export function saveHistory(list: ChatMessage[]): void {
  try {
    const trimmed = list.slice(-MAX_HISTORY)
    localStorage.setItem(HISTORY_KEY, JSON.stringify(trimmed))
  } catch {
    // localStorage 容量超限或被禁用，忽略
  }
}

/** 清空历史 */
export function clearHistory(): void {
  localStorage.removeItem(HISTORY_KEY)
}

// ==================== 后端调用 ====================

/**
 * 非流式调用（降级方案）
 * 后端 controller 返回统一 Result 格式，request 拦截器已解包 .data
 */
export async function chatOnce(req: ChatRequest): Promise<ChatResponse> {
  return request.post<ChatResponse>('/ai/chat', req)
}

/**
 * 流式调用（SSE）
 * @param req 请求体
 * @param onChunk  每收到一段正文回调（仅增量文本）
 * @param onTool   工具调用事件（可为空）
 * @param onDone   全部完成回调
 * @param onError  失败回调
 * @returns cancel 取消函数
 */
export function chatStream(
  req: ChatRequest,
  onChunk: (text: string) => void,
  onTool: ((toolName: string, summary?: string) => void) | null,
  onDone: () => void,
  onError: (err: Error) => void
): () => void {
  const userStore = useUserStore()
  const token = userStore.token || ''

  const cancel = ssePost({
    url: '/ai/chat/stream',
    body: req,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    onEvent: (event) => {
      // 约定后端推送的事件类型：
      // - "chunk"  data: { text: string }
      // - "tool"   data: { name: string, summary?: string }
      // - "done"   data: {}
      // - "error"  data: { message: string }
      try {
        if (!event.data) return
        const payload = JSON.parse(event.data)
        if (event.event === 'chunk') {
          onChunk(payload.text || '')
        } else if (event.event === 'tool') {
          onTool?.(payload.name || '工具', payload.summary)
        } else if (event.event === 'done') {
          onDone()
        } else if (event.event === 'error') {
          onError(new Error(payload.message || 'AI 服务异常'))
        }
      } catch (e) {
        onError(e instanceof Error ? e : new Error('解析 SSE 失败'))
      }
    },
    onError
  })

  return cancel
}