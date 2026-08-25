/**
 * 基于 fetch + ReadableStream 的 SSE 客户端
 *
 * 为什么不用原生 EventSource？
 *   - EventSource 不支持自定义 Header，无法携带 JWT
 *   - EventSource 仅支持 GET；这里需要 POST 请求体
 *
 * 与后端约定的 SSE 帧格式：
 *   event: chunk\n
 *   data: {"text":"..."}\n
 *   \n
 */
export interface SseEvent {
  /** 事件类型（默认 "message"） */
  event: string
  /** data 字段原文（JSON 字符串） */
  data: string
}

export interface SsePostOptions {
  url: string
  body?: unknown
  headers?: Record<string, string>
  onEvent: (evt: SseEvent) => void
  onError: (err: Error) => void
  /** 默认 60s，防止 LLM 长响应被中断 */
  timeoutMs?: number
}

const DEFAULT_TIMEOUT = 60_000

/**
 * 通过 POST 发起 SSE 请求，返回 cancel 函数
 */
export function ssePost(opts: SsePostOptions): () => void {
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const controller = new AbortController()
  const timeout = opts.timeoutMs ?? DEFAULT_TIMEOUT
  const timer = setTimeout(() => controller.abort(), timeout)

  // 触发请求，不 await 整体（内部异步消费流）
  fetch(`${baseURL}${opts.url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(opts.headers || {})
    },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
    credentials: 'include',
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok) {
        const text = await response.text().catch(() => '')
        throw new Error(`HTTP ${response.status} ${text.slice(0, 100)}`)
      }
      if (!response.body) {
        throw new Error('响应无 body')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      // 逐块读取，解析 SSE 帧
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // 按 \n\n 切分完整事件
        let sepIdx: number
        // eslint-disable-next-line no-cond-assign
        while ((sepIdx = buffer.indexOf('\n\n')) >= 0) {
          const rawEvent = buffer.slice(0, sepIdx)
          buffer = buffer.slice(sepIdx + 2)
          const evt = parseSseFrame(rawEvent)
          if (evt) opts.onEvent(evt)
        }
      }
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      opts.onError(err instanceof Error ? err : new Error(String(err)))
    })
    .finally(() => {
      clearTimeout(timer)
    })

  return () => {
    clearTimeout(timer)
    controller.abort()
  }
}

/** 解析一段 SSE 帧 */
function parseSseFrame(raw: string): SseEvent | null {
  let event = 'message'
  let data = ''

  const lines = raw.split('\n')
  for (const line of lines) {
    if (!line || line.startsWith(':')) continue // 注释行/空行
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      data += line.slice(5).trim()
    }
  }

  if (!data) return null
  return { event, data }
}