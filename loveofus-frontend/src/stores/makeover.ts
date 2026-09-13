import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * AI 化妆建议模块 - 全局共享 store
 *
 * 设计原则（与 docs/08 §6 一致）：
 *   只放真正跨页面共享的状态（"最近一次 recordId"），用于推送跳转等场景。
 *   业务状态（进度 stage、详情等）保持页面内 local ref，避免过度 store 化。
 */
export const useMakeoverStore = defineStore('makeover', () => {
  /** 最近一次成功提交的 recordId（用于外部推送/通知跳转） */
  const recentRecordId = ref<number | null>(null)

  function setRecent(id: number) {
    recentRecordId.value = id
  }

  function clear() {
    recentRecordId.value = null
  }

  return { recentRecordId, setRecent, clear }
})