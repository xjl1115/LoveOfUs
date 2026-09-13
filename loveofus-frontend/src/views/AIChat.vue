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
      <!-- 输入区上方的图片预览条（点击 + 上传后显示，可点 × 移除单个预览） -->
      <div v-if="pendingPreviews.length > 0" class="preview-strip">
        <div class="preview-strip-inner">
          <div
            v-for="(p, idx) in pendingPreviews"
            :key="p.url + idx"
            class="preview-thumb"
          >
            <img :src="p.url" alt="预览" />
            <button
              type="button"
              class="preview-thumb-close"
              title="移除预览"
              @click="removePreview(idx)"
            >
              <van-icon name="cross" size="12" />
            </button>
          </div>
        </div>
      </div>
      <!-- 隐藏的文件选择器：被 + 按钮触发 -->
      <input
        ref="fileInputRef"
        type="file"
        accept="image/*"
        multiple
        hidden
        @change="onFileChange"
      />
      <div class="input-row">
        <button
          type="button"
          class="plus-btn"
          :disabled="aiDisabled || uploading"
          :title="uploading ? '上传中…' : '上传照片'"
          @click="triggerFilePicker"
        >
          <van-icon :name="uploading ? 'loading' : 'plus'" size="20" />
        </button>
        <van-field
          v-model="inputText"
          class="input-field"
          placeholder="说点什么吧…"
          :border="false"
          autosize
          rows="1"
          maxlength="500"
          :disabled="aiDisabled || loading || uploading"
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
    </div>

    <!-- 占位元素：与输入区等高，确保消息列表滚动到底时不被输入框遮挡 -->
    <div class="input-bar-placeholder" aria-hidden="true" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast, showLoadingToast, closeToast } from 'vant'
import MessageBubble from '@/components/MessageBubble.vue'
import QuickSuggestions, { type QuickQuestion } from '@/components/QuickSuggestions.vue'
import { getNotificationSSE } from '@/api/systemMessage'
import request from '@/utils/request'
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
  type ChatImageItem,
  type ExportCardPayload
} from '@/api/aiChat'

/** /photos/upload 响应结构（与后端 PhotoUploadVO 对齐） */
interface PhotoUploadVO {
  photoIds?: number[]
  successCount?: number
  albumId?: number
}

const router = useRouter()

// ==================== 状态 ====================

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const aiDisabled = ref(false) // 后端可返回 404/501 关闭
const scrollRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false) // 照片上传中

/** 输入框上方的待发送/已上传图片预览（仅本地状态，点 × 仅移除预览，不影响消息流/OSS） */
interface PendingPreview {
  /** 预览 URL（本地 blob URL 或服务端 URL） */
  url: string
  /** 是否为本地 blob URL（仅本地状态，组件卸载时需要 revoke） */
  isLocal: boolean
}
const pendingPreviews = ref<PendingPreview[]>([])

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
  // 释放残留的本地 blob URL，避免内存泄漏
  pendingPreviews.value.forEach((p) => {
    if (p.isLocal) URL.revokeObjectURL(p.url)
  })
  pendingPreviews.value = []
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
  // 配置了 routerLink -> 直接跳转页面
  if (item.routerLink) {
    router.push(item.routerLink)
    return
  }
  inputText.value = item.text
  onSend()
}

// ==================== 照片上传 ====================

/** 单张照片大小上限（与现有 Upload.vue 保持一致：10MB） */
const MAX_PHOTO_SIZE = 10 * 1024 * 1024
/** 每次选择允许的最大张数（防误操作） */
const MAX_PHOTO_COUNT = 9

/** 触发原生文件选择器 */
function triggerFilePicker() {
  if (aiDisabled.value || uploading.value) return
  fileInputRef.value?.click()
}

/** 移除单个预览（仅本地预览条，不影响已上传的图片/OSS/消息流） */
function removePreview(idx: number) {
  const item = pendingPreviews.value[idx]
  if (!item) return
  // 释放本地 blob URL，避免内存泄漏（服务端 URL 不需要 revoke）
  if (item.isLocal) URL.revokeObjectURL(item.url)
  pendingPreviews.value.splice(idx, 1)
}

/** 文件选择变化：依次上传，缩略图插入用户消息流 */
async function onFileChange(ev: Event) {
  const input = ev.target as HTMLInputElement
  const files = input.files
  // 立刻清空 value，保证下次选择同一文件也能触发 change
  input.value = ''
  if (!files || files.length === 0) return

  const list = Array.from(files)
  if (list.length > MAX_PHOTO_COUNT) {
    showToast(`一次最多上传 ${MAX_PHOTO_COUNT} 张`)
    return
  }
  // 单文件大小校验
  for (const f of list) {
    if (!f.type.startsWith('image/')) {
      showToast('仅支持图片文件')
      return
    }
    if (f.size > MAX_PHOTO_SIZE) {
      showToast(`"${f.name}" 超过 10MB，请压缩后再上传`)
      return
    }
  }

  uploading.value = true
  showLoadingToast({ message: `上传中 0/${list.length}`, forbidClick: true })
  let success = 0
  let fail = 0
  for (let i = 0; i < list.length; i++) {
    const f = list[i]
    // 用本地预览 URL 先占位（上传前立刻可见，避免长时间空白）
    const localUrl = URL.createObjectURL(f)
    // 同步把本地预览追加到输入框上方预览条（选中即显示，不依赖上传结果）
    pendingPreviews.value.push({ url: localUrl, isLocal: true })
    const localItem: ChatImageItem = {
      imageUrl: localUrl
    }
    const userMsg: ChatMessage = {
      id: cryptoId(),
      role: 'user',
      content: list.length === 1 ? '📷 我上传了一张照片' : `📷 我上传了 ${list.length} 张照片`,
      createdAt: Date.now(),
      images: [localItem]
    }
    messages.value.push(userMsg)
    scrollToBottom()

    try {
      const fd = new FormData()
      // 后端 /photos/upload 约定：files(复数)、takenDate(必填)，其它字段可选。
      // 仅传 file + takenDate 即可走通最小上传路径；完整元数据请用户去"上传"页填写。
      fd.append('files', f)
      fd.append('takenDate', new Date().toISOString().slice(0, 10))

      const result: PhotoUploadVO = await request.post<PhotoUploadVO>('/photos/upload', fd, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      const photoId = result?.photoIds?.[0]
      if (photoId) {
        // 用服务端 photoId 替换本地预览（详情页/AI 工具可用 photoId）
        const idx = messages.value.findIndex((m) => m.id === userMsg.id)
        if (idx >= 0) {
          const imgs = messages.value[idx].images || []
          if (imgs[0]) imgs[0].photoId = photoId
        }
        // 预览已在选中时 push 到 pendingPreviews；上传成功仅更新 photoId，不重复 push。
        success++
      } else {
        // 上传返回失败 → 释放本地 blob URL，避免泄漏
        URL.revokeObjectURL(localUrl)
        // 同时移除对应的预览条条目（按 url 匹配）
        const pIdx = pendingPreviews.value.findIndex((p) => p.url === localUrl)
        if (pIdx >= 0) pendingPreviews.value.splice(pIdx, 1)
        fail++
      }
    } catch (e) {
      console.warn('[AIChat] 上传照片失败', e)
      // 上传失败时移除占位消息，避免脏数据
      const idx = messages.value.findIndex((m) => m.id === userMsg.id)
      if (idx >= 0) messages.value.splice(idx, 1)
      // 释放本地 blob URL，避免内存泄漏
      URL.revokeObjectURL(localUrl)
      // 同步移除预览条条目
      const pIdx = pendingPreviews.value.findIndex((p) => p.url === localUrl)
      if (pIdx >= 0) pendingPreviews.value.splice(pIdx, 1)
      fail++
    }
    showLoadingToast({ message: `上传中 ${i + 1}/${list.length}`, forbidClick: true })
  }
  closeToast()
  uploading.value = false
  if (fail > 0) {
    showToast(`上传完成：${success} 张成功，${fail} 张失败`)
  } else if (success > 0) {
    showToast(`已上传 ${success} 张照片`)
  }
  saveHistory(messages.value)
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
    (images, action) => {
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

      // P1-5：后端可在 done 帧里推 action，做"智能跳转/快捷卡片"
      if (action && action.type === 'navigate' && action.url) {
        const delay = action.delayMs ?? 600
        setTimeout(() => router.push(action.url!), delay)
      }
      // 注：action.type === 'card' 由 MessageBubble 根据 action 字段渲染对应气泡
      // 当前 MessageBubble 尚未消费 action，预留位；下一轮迭代加上
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
  flex-direction: column; // 关键：纵向布局，让 .preview-strip 自然排在 input 行之上
  gap: 6px;
  padding: 8px 12px;
  padding-bottom: calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid $border-color;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);

  // 内部真实输入行（+ 按钮、输入框、发送按钮）：仍然横向
  .input-row {
    display: flex;
    align-items: flex-end;
    gap: 8px;
  }

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

/* 输入区左侧"+":触发照片上传 */
.plus-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: $bg-color;
  color: $text-secondary;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.18s, color 0.18s;

  &:hover:not(:disabled) {
    background: $primary-light-bg;
    color: $primary-color;
  }

  &:active:not(:disabled) {
    transform: scale(0.94);
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}

// 与 fixed 定位的 .input-bar 等高，避免消息被遮挡
.input-bar-placeholder {
  flex-shrink: 0;
  // 输入区最小高度约 52px（8 上下 padding + 36 按钮高度），再加安全区
  // 额外预留 80px 给 .preview-strip（72px + 6px gap），即使没有预览条也不影响视觉
  min-height: calc(52px + env(safe-area-inset-bottom) + 86px);
}

.nav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  cursor: pointer;
  color: inherit;
}

// 输入区上方的图片预览条
// 关键：.preview-strip 嵌在 .input-bar 内部（input-bar 第一个子元素），随 input-bar 一起 fixed 显示
// 这样：1) 不与 input-bar 的 z-index 战争 2) 不受祖先 contain/transform 影响 3) flex column 自动排版
.preview-strip {
  flex-shrink: 0;
  width: calc(100% + 24px); // 抵消 input-bar 的左右 padding (12+12)，让预览条横跨整宽
  margin: -8px -12px 0; // 抵消 input-bar 的上 padding 和左右 padding，让预览条贴到 input-bar 顶部
  background: var(--bg-elevated, #fafafa);
  border-top: 1px solid var(--border-color, #ebedf0);
  border-bottom: 1px solid var(--border-color, #ebedf0);
  padding: 8px 12px;
}

.preview-strip-inner {
  display: flex;
  flex-direction: row;
  gap: 8px;
  overflow-x: auto;
  // 隐藏滚动条但保留滚动
  scrollbar-width: none;
  -ms-overflow-style: none;
  &::-webkit-scrollbar {
    display: none;
  }
}

.preview-thumb {
  position: relative;
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 8px;
  overflow: hidden;
  background: #f7f8fa;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.preview-thumb-close {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  line-height: 1;

  &:hover {
    background: rgba(0, 0, 0, 0.75);
  }
}
</style>