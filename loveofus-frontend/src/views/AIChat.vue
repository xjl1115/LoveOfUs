<template>
  <div class="ai-chat-page">
    <!-- 顶部导航 -->
    <van-nav-bar
      title="AI 情侣助手"
      left-arrow
      fixed
      placeholder
      @click-left="onBack"
    >
      <template #right>
        <span class="nav-icon" @click="onNewSession" title="新建对话">
          <van-icon name="plus" size="22" />
        </span>
        <span class="nav-icon" @click="onOpenHistory" title="历史会话">
          <van-icon name="clock-o" size="20" />
        </span>
        <span
          v-if="messages.length > 1"
          class="nav-icon"
          @click="onClear"
          title="清空当前对话"
        >
          <van-icon name="delete-o" size="18" />
        </span>
      </template>
    </van-nav-bar>

    <!-- AI 服务不可用占位 -->
    <div v-if="aiDisabled" class="disabled-banner">
      <van-icon name="info-o" />
      <span>AI 助手暂未开启，敬请期待～</span>
    </div>

    <!-- 消息列表 -->
    <div ref="scrollRef" class="message-list">
      <MessageBubble
        v-for="(msg, idx) in messages"
        :key="msg.id"
        :message="msg"
        :show-avatar="shouldShowAvatar(idx)"
        :show-time="shouldShowTime(idx)"
      />

      <!-- 推荐问题：仅在无任何用户消息时显示 -->
      <QuickSuggestions
        v-if="showSuggestions"
        :questions="quickQuestions"
        @pick="onPickQuestion"
      />

      <!-- 加载占位 -->
      <div v-if="loading" class="loading-row">
        <van-loading type="spinner" size="18" />
        <span>AI 正在思考…</span>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-bar safe-area-bottom">
      <van-field
        v-model="inputText"
        class="input-field"
        placeholder="说点什么吧…"
        :border="false"
        autosize
        rows="1"
        maxlength="500"
        :disabled="aiDisabled || loading"
        @keyup.enter.prevent="onSend"
      />
      <van-button
        type="primary"
        round
        size="small"
        :disabled="!canSend"
        :loading="loading"
        @click="onSend"
      >
        发送
      </van-button>
    </div>

    <!-- 占位元素：与输入区等高，确保消息列表滚动到底时不被输入框遮挡 -->
    <div class="input-bar-placeholder" aria-hidden="true" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import MessageBubble from '@/components/MessageBubble.vue'
import QuickSuggestions, { type QuickQuestion } from '@/components/QuickSuggestions.vue'
import { getNotificationSSE } from '@/api/systemMessage'
import {
  getSessionId,
  newSession,
  loadHistory,
  saveHistory,
  clearHistory,
  chatStream,
  chatOnce,
  getAiSessionDetail,
  type ChatMessage,
  type ExportCardPayload
} from '@/api/aiChat'

const router = useRouter()

// ==================== 状态 ====================

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const aiDisabled = ref(false) // 后端可返回 404/501 关闭
const scrollRef = ref<HTMLElement | null>(null)

let cancelStream: (() => void) | null = null

// ==================== 推荐问题 ====================

const quickQuestions: QuickQuestion[] = [
  { icon: '📸', text: '帮我找最近 3 张海边的照片', tag: '照片' },
  { icon: '💕', text: '我们在一起多久了？下一个纪念日？', tag: '纪念日' },
  { icon: '✍️', text: '帮我写一段朋友圈文案', tag: '文案' },
  { icon: '🗺️', text: '我们一起去过哪些城市？', tag: '足迹' }
]

const showSuggestions = computed(
  () => !messages.value.some((m) => m.role === 'user')
)

const canSend = computed(
  () => !aiDisabled.value && !loading.value && inputText.value.trim().length > 0
)

// ==================== 头像/时间显示策略 ====================

function shouldShowAvatar(idx: number): boolean {
  // 系统/工具消息不显示头像；其余按相邻角色判断
  const m = messages.value[idx]
  if (!m || m.role === 'system' || m.role === 'tool') return false
  if (idx === 0) return true
  return messages.value[idx - 1].role !== m.role
}

function shouldShowTime(idx: number): boolean {
  const m = messages.value[idx]
  if (!m) return false
  if (idx === 0) return true
  const prev = messages.value[idx - 1]
  // 相邻消息间隔超过 5 分钟才显示
  return m.createdAt - prev.createdAt > 5 * 60 * 1000
}

// ==================== 生命周期 ====================

onMounted(async () => {
  // 优先：从历史列表选中跳转过来 -> 拉后端详情
  const loadFromHistory = localStorage.getItem('ai_chat_load_from_history') === '1'
  if (loadFromHistory) {
    localStorage.removeItem('ai_chat_load_from_history')
    const sid = getSessionId()
    if (sid) {
      try {
        const detail = await getAiSessionDetail(sid)
        if (detail && Array.isArray(detail.messages) && detail.messages.length > 0) {
          messages.value = detail.messages.map((m) => ({
            id: String(m.id ?? Math.random().toString(36).slice(2)),
            role: m.role,
            content: m.content,
            toolName: m.toolName,
            createdAt: m.createdAt
              ? new Date(m.createdAt.replace(' ', 'T')).getTime()
              : Date.now()
          }))
          scrollToBottom()
          return
        }
      } catch (e) {
        console.warn('[AIChat] 加载历史详情失败', e)
      }
    }
  }

  // 普通启动：读 localStorage
  const cached = loadHistory()
  if (cached.length > 0) {
    messages.value = cached
    scrollToBottom()
  } else {
    pushSystemMessage('我是你的恋爱回忆管家 🌸 有什么事尽管问我～')
  }

  // 订阅 SSE 导出完成事件
  const sse = getNotificationSSE()
  const onExportCompleted = (data: ExportCardPayload) => {
    const payload: ExportCardPayload = {
      exportId: data.exportId,
      format: data.format,
      photoCount: data.photoCount,
      fileSize: data.fileSize,
      fileName: data.fileName,
      downloadUrl: data.downloadUrl,
      status: data.status || 'completed',
      error: data.error,
      completedAt: data.completedAt
    }
    const text =
      payload.status === 'failed'
        ? `导出失败了：${payload.error || '未知原因'}`
        : `导出完成 · ${(payload.format || '').toUpperCase()} · ${payload.photoCount ?? 0} 张`
    messages.value.push({
      id: cryptoId(),
      role: 'system',
      content: text,
      createdAt: Date.now(),
      export: payload
    })
    scrollToBottom()
    showToast(payload.status === 'failed' ? '导出失败' : '导出已完成，点此下载')
  }
  sse.on('ai-export-completed', onExportCompleted)
  _exportUnbind = () => sse.off('ai-export-completed', onExportCompleted)
})

// 用 module-level 引用存放 unbind 回调，避免 onMounted 作用域与 onBeforeUnmount 不共享
let _exportUnbind: (() => void) | null = null

onBeforeUnmount(() => {
  cancelStream?.()
  saveHistory(messages.value)
  // 解绑 SSE：在 onMounted 注册时已把 unbind 写到 _exportUnbind
  if (_exportUnbind) _exportUnbind()
})

// ==================== 工具方法 ====================

function pushSystemMessage(text: string) {
  messages.value.push({
    id: cryptoId(),
    role: 'system',
    content: text,
    createdAt: Date.now()
  })
}

function cryptoId(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

function scrollToBottom() {
  nextTick(() => {
    if (!scrollRef.value) return
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

// ==================== 操作 ====================

function onBack() {
  cancelStream?.()
  saveHistory(messages.value)
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/home')
  }
}

function onClear() {
  showConfirmDialog({
    title: '清空对话',
    message: '确定清空当前对话吗？历史记录会从本机移除。'
  })
    .then(() => {
      cancelStream?.()
      clearHistory()
      newSession()
      messages.value = []
      pushSystemMessage('我是你的恋爱回忆管家 🌸 有什么事尽管问我～')
    })
    .catch(() => {
      // 取消
    })
}

function onNewSession() {
  cancelStream?.()
  saveHistory(messages.value)
  // 清空本地历史 → 生成新的 sessionId → 清空当前消息
  clearHistory()
  newSession()
  messages.value = []
  inputText.value = ''
  showToast('已新建对话')
}

function onOpenHistory() {
  cancelStream?.()
  saveHistory(messages.value)
  router.push('/ai-history')
}

function onPickQuestion(item: QuickQuestion) {
  inputText.value = item.text
  onSend()
}

async function onSend() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  // 1. 推入用户消息
  const userMsg: ChatMessage = {
    id: cryptoId(),
    role: 'user',
    content: text,
    createdAt: Date.now()
  }
  messages.value.push(userMsg)
  inputText.value = ''
  scrollToBottom()
  loading.value = true

  // 2. 准备 AI 占位气泡
  const aiMsg: ChatMessage = {
    id: cryptoId(),
    role: 'ai',
    content: '',
    createdAt: Date.now(),
    streaming: true
  }
  messages.value.push(aiMsg)
  scrollToBottom()

  // 3. 优先走 SSE 流式
  const sessionId = getSessionId()
  let sseOk = false

  cancelStream = chatStream(
    { sessionId, message: text },
    {
      // 长思考/长输出场景允许拉长 SSE 等待；服务端 SseEmitter 30 分钟
      timeoutMs: 30 * 60 * 1000
    },
    (chunk) => {
      // 仅第一次成功回调时，标记 sseOk（后续 chunk 不断追加）
      if (!sseOk) sseOk = true
      // 直接替换 aiMsg 对象引用，强制触发 Vue 响应式更新（比 [...messages.value] 更轻量）
      const idx = messages.value.findIndex((m) => m.id === aiMsg.id)
      if (idx >= 0) {
        messages.value[idx] = { ...messages.value[idx], content: messages.value[idx].content + chunk }
      }
      scrollToBottom()
    },
    (toolName, summary) => {
      // 工具调用作为独立 tool 消息插入
      messages.value.splice(messages.value.length - 1, 0, {
        id: cryptoId(),
        role: 'tool',
        toolName,
        content: summary || '已完成',
        createdAt: Date.now()
      })
      scrollToBottom()
    },
    (images) => {
      const idx = messages.value.findIndex((m) => m.id === aiMsg.id)
      if (idx >= 0) {
        messages.value[idx] = {
          ...messages.value[idx],
          streaming: false,
          images: images && images.length ? images : messages.value[idx].images
        }
      }
      loading.value = false
      cancelStream = null
      saveHistory(messages.value)
    },
    async (err) => {
      // 流式失败 -> 降级非流式
      if (!sseOk) {
        try {
          const resp = await chatOnce({ sessionId, message: text })
          const idx = messages.value.findIndex((m) => m.id === aiMsg.id)
          if (idx >= 0) {
            messages.value[idx] = {
              ...messages.value[idx],
              content: resp.message.content,
              streaming: false,
              error: false
            }
          }
        } catch (e) {
          const idx = messages.value.findIndex((m) => m.id === aiMsg.id)
          if (idx >= 0) {
            messages.value[idx] = {
              ...messages.value[idx],
              content: '抱歉，AI 助手暂时离开，请稍后再试～',
              streaming: false,
              error: true
            }
          }
          // 仅在网络/服务类错误时显示禁用提示（避免每次失败都关）
          console.warn('[AIChat] fallback failed', e)
        }
        loading.value = false
        cancelStream = null
        saveHistory(messages.value)
      } else {
        // 中途断流（已经有部分内容）
        const idx = messages.value.findIndex((m) => m.id === aiMsg.id)
        if (idx >= 0) {
          messages.value[idx] = { ...messages.value[idx], streaming: false, error: true }
        }
        loading.value = false
        cancelStream = null
        showToast(err.message || '连接中断')
      }
    }
  )
}
</script>

<style scoped lang="scss">
.ai-chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: $bg-color;
}

.disabled-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  background: #fff7e6;
  color: #b8821b;
  font-size: 13px;
  margin: 12px 16px 0;
  border-radius: 8px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
  -webkit-overflow-scrolling: touch;
}

.loading-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  color: $text-tertiary;
  font-size: 13px;
  margin-bottom: 16px;
}

.input-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 12px;
  padding-bottom: calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid $border-color;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);

  .input-field {
    flex: 1;
    background: $bg-color;
    border-radius: 18px;
    padding: 6px 12px;
    max-height: 100px;
    overflow-y: auto;
  }

  .van-button {
    flex-shrink: 0;
    height: 36px;
    padding: 0 16px;
  }
}

// 与 fixed 定位的 .input-bar 等高，避免消息被遮挡
.input-bar-placeholder {
  flex-shrink: 0;
  // 输入区最小高度约 52px（8 上下 padding + 36 按钮高度），再加安全区
  min-height: calc(52px + env(safe-area-inset-bottom));
}

.nav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  cursor: pointer;
  color: inherit;
}
</style>