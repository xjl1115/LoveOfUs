<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  fetchHistory,
  fetchUnreadCount,
  markAllRead,
  buildChatWsUrl,
  type ChatMessageVO,
  type WsChatMessage
} from '@/api/chat'
import { showToast } from 'vant'

const router = useRouter()
const userStore = useUserStore()

const messages = ref<ChatMessageVO[]>([])
const inputText = ref('')
const sending = ref(false)
const ws = ref<WebSocket | null>(null)
const connected = ref(false)
const peerTyping = ref(false)
const messagesEndRef = ref<HTMLDivElement | null>(null)

// 当前登录用户 ID
const currentUserId = computed(() => userStore.userInfo?.id ?? 0)

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    messagesEndRef.value?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  })
}

// 加载历史
async function loadHistory() {
  try {
    const res = await fetchHistory(1, 50)
    if (res.code === 0) {
      messages.value = res.data.list
      scrollToBottom()
    } else {
      showToast(res.message || '加载历史消息失败')
    }
  } catch (e) {
    console.error('loadHistory', e)
    showToast('加载历史消息失败')
  }
}

// 标记已读
async function doMarkAllRead() {
  try {
    await markAllRead()
  } catch (e) {
    console.warn('markAllRead', e)
  }
}

// WebSocket
function connect() {
  const token = userStore.token
  if (!token) {
    showToast('请先登录')
    return
  }
  if (ws.value && ws.value.readyState === WebSocket.OPEN) return

  const url = buildChatWsUrl(token)
  ws.value = new WebSocket(url)

  ws.value.onopen = () => {
    connected.value = true
    doMarkAllRead()
  }

  ws.value.onclose = () => {
    connected.value = false
    // 5s 自动重连
    setTimeout(() => {
      if (!connected.value) connect()
    }, 5000)
  }

  ws.value.onerror = () => {
    // 由 onclose 处理重连
  }

  ws.value.onmessage = (ev) => {
    let msg: WsChatMessage
    try {
      msg = JSON.parse(ev.data)
    } catch {
      return
    }

    switch (msg.type) {
      case 'CHAT': {
        const incoming: ChatMessageVO = {
          id: msg.id,
          senderId: msg.senderId ?? 0,
          receiverId: msg.receiverId ?? 0,
          content: msg.content ?? '',
          msgType: msg.msgType ?? 1,
          isRead: msg.isRead ?? 0,
          createdAt: msg.createdAt
        }
        // 去重（基于 clientMsgId）
        if (msg.clientMsgId) {
          const idx = messages.value.findIndex((m: any) => m.clientMsgId === msg.clientMsgId)
          if (idx >= 0) {
            messages.value[idx] = { ...messages.value[idx], ...incoming }
            scrollToBottom()
            return
          }
        }
        messages.value.push(incoming)
        // 如果是对方发来的，自动标记已读
        if (incoming.senderId !== currentUserId.value) {
          doMarkAllRead()
        }
        scrollToBottom()
        break
      }
      case 'TYPING': {
        if (msg.senderId !== currentUserId.value) {
          peerTyping.value = true
          // 5s 内无新 TYPING 自动隐藏
          clearTimeout((peerTyping as any)._t)
          ;(peerTyping as any)._t = setTimeout(() => (peerTyping.value = false), 5000)
        }
        break
      }
      case 'READ': {
        // 对方已读：把当前用户所有未读消息改为已读
        messages.value = messages.value.map((m) =>
          m.senderId === currentUserId.value ? { ...m, isRead: 1 } : m
        )
        break
      }
      case 'PONG':
        break
      case 'ERROR':
        showToast(msg.error || '聊天服务异常')
        break
    }
  }
}

function disconnect() {
  if (ws.value) {
    ws.value.close()
    ws.value = null
  }
}

let typingTimer: number | null = null
function onInputChange() {
  if (!ws.value || ws.value.readyState !== WebSocket.OPEN) return
  if (typingTimer) return
  const payload: WsChatMessage = { type: 'TYPING' }
  ws.value.send(JSON.stringify(payload))
  typingTimer = window.setTimeout(() => (typingTimer = null), 2000)
}

function sendMessage() {
  const text = inputText.value.trim()
  if (!text) return
  if (!ws.value || ws.value.readyState !== WebSocket.OPEN) {
    showToast('聊天连接未就绪')
    return
  }
  if (!userStore.userInfo?.partnerId) {
    showToast('请先绑定伴侣')
    return
  }

  sending.value = true
  const clientMsgId = `c_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

  // 乐观更新：先显示本地消息
  messages.value.push({
    id: undefined,
    senderId: currentUserId.value,
    receiverId: userStore.userInfo.partnerId,
    content: text,
    msgType: 1,
    isRead: 0,
    createdAt: new Date().toISOString(),
    ...({ clientMsgId } as any)
  } as any)

  const payload: WsChatMessage = {
    type: 'CHAT',
    clientMsgId,
    content: text,
    msgType: 1
  }
  ws.value.send(JSON.stringify(payload))
  inputText.value = ''
  sending.value = false
  scrollToBottom()
}

function formatTime(s?: string) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  if (sameDay) return `${hh}:${mm}`
  return `${d.getMonth() + 1}-${d.getDate()} ${hh}:${mm}`
}

onMounted(async () => {
  if (!userStore.userInfo) {
    showToast('请先登录')
    router.replace('/')
    return
  }
  if (!userStore.userInfo.partnerId) {
    showToast('请先绑定伴侣')
    router.replace('/profile')
    return
  }
  await loadHistory()
  connect()
})

onBeforeUnmount(() => {
  disconnect()
})
</script>

<template>
  <div class="chat-page">
    <van-nav-bar
      title="聊天"
      left-text="返回"
      left-arrow
      fixed
      placeholder
      @click-left="router.back()"
    >
      <template #right>
        <span class="conn-status" :class="{ online: connected }">
          {{ connected ? '在线' : '离线' }}
        </span>
      </template>
    </van-nav-bar>

    <div class="chat-content">
      <div v-if="messages.length === 0" class="empty-tip">还没有聊天记录，发个消息吧 ��</div>
      <div
        v-for="(msg, idx) in messages"
        :key="(msg as any).clientMsgId || `${msg.id}-${idx}`"
        class="msg-row"
        :class="{ mine: msg.senderId === currentUserId }"
      >
        <div class="bubble">
          <div class="bubble-text">{{ msg.content }}</div>
          <div class="bubble-meta">
            <span class="time">{{ formatTime(msg.createdAt) }}</span>
            <span
              v-if="msg.senderId === currentUserId && msg.isRead === 1"
              class="read-flag"
            >已读</span>
          </div>
        </div>
      </div>
      <div v-if="peerTyping" class="typing-indicator">
        <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        <span class="typing-text">对方正在输入...</span>
      </div>
      <div ref="messagesEndRef" />
    </div>

    <div class="chat-input-bar">
      <van-field
        v-model="inputText"
        class="input"
        placeholder="说点什么..."
        :border="false"
        @input="onInputChange"
        @keyup.enter="sendMessage"
      />
      <van-button
        type="primary"
        size="small"
        :loading="sending"
        :disabled="!inputText.trim()"
        @click="sendMessage"
      >发送</van-button>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f7f8fa;
}
.conn-status {
  font-size: 12px;
  color: #999;
}
.conn-status.online {
  color: #07c160;
}
.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px 12px 12px;
}
.empty-tip {
  text-align: center;
  color: #999;
  font-size: 14px;
  margin-top: 80px;
}
.msg-row {
  display: flex;
  margin-bottom: 12px;
}
.msg-row.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.msg-row.mine .bubble {
  background: #95ec69;
}
.bubble-text {
  font-size: 15px;
  line-height: 1.4;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble-meta {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: 4px;
  gap: 6px;
}
.time {
  font-size: 11px;
  color: #999;
}
.read-flag {
  font-size: 11px;
  color: #576b95;
}
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  color: #999;
  font-size: 12px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #999;
  animation: blink 1.4s infinite both;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-2px); }
}
.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #fff;
  border-top: 1px solid #ebedf0;
}
.input {
  flex: 1;
  background: #f7f8fa;
  border-radius: 18px;
  padding: 6px 12px;
}
:deep(.van-field__control) {
  font-size: