import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchUnreadCount } from '@/api/chat'
import { getNotificationSSE } from '@/api/systemMessage'

/**
 * 聊天未读消息计数 store
 * <p>
 * 用法：
 *   const unread = useChatUnreadStore()
 *   unread.refresh()        // 拉取一次
 *   unread.increment()      // 收到 WS 消息时 +1
 *   unread.reset()          // 进入聊天页时清零（已读）
 *   unread.startRealtime()  // 订阅 SSE chat-unread-count，实时刷新
 *   unread.stopRealtime()   // 取消订阅
 */
export const useChatUnreadStore = defineStore('chatUnread', () => {
  const count = ref(0)
  const loading = ref(false)
  let sseHandler: ((data: { count: number; partnerId: number }) => void) | null = null

  const hasUnread = computed(() => count.value > 0)

  async function refresh() {
    if (loading.value) return
    loading.value = true
    try {
      const res: any = await fetchUnreadCount()
      // 兼容 Result<T> 包装：后端返回 { code, data } 或 直接 number
      const n = typeof res === 'number' ? res : (res?.data ?? 0)
      count.value = Number(n) || 0
    } catch {
      // 静默，不影响主流程
    } finally {
      loading.value = false
    }
  }

  function increment(by = 1) {
    count.value = Math.max(0, count.value + by)
  }

  function reset() {
    count.value = 0
  }

  /**
   * 订阅 SSE chat-unread-count 事件，实时更新 count
   * 多次调用是幂等的：若已订阅则直接返回
   */
  function startRealtime() {
    if (sseHandler) return // 已订阅
    const sse = getNotificationSSE()
    sseHandler = (data: { count: number; partnerId: number }) => {
      if (typeof data?.count !== 'number') return
      count.value = Math.max(0, data.count)
    }
    sse.on('chat-unread-count', sseHandler)
  }

  function stopRealtime() {
    if (!sseHandler) return
    try {
      getNotificationSSE().off('chat-unread-count', sseHandler)
    } catch {
      // ignore
    }
    sseHandler = null
  }

  return { count, hasUnread, loading, refresh, increment, reset, startRealtime, stopRealtime }
})