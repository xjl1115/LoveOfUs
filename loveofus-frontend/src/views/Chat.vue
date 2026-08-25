<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatUnreadStore } from '@/stores/chatUnread'
import { getUserInfo } from '@/api/user'
import {
  fetchHistory,
  markAllRead,
  buildChatWsUrl,
  enterChatPage,
  heartbeatChatPage,
  leaveChatPage,
  deleteMessage as apiDeleteMessage,
  deleteMessagesBatch as apiDeleteMessagesBatch,
  recallMessage as apiRecallMessage,
  type ChatMessageVO,
  type WsChatMessage
} from '@/api/chat'
import { getNotificationSSE } from '@/api/systemMessage'
import { showToast, showConfirmDialog } from 'vant'

const router = useRouter()
const userStore = useUserStore()
const unreadStore = useChatUnreadStore()

const messages = ref<ChatMessageVO[]>([])
const inputText = ref('')
const sending = ref(false)
const ws = ref<WebSocket | null>(null)
const connected = ref(false)
const peerTyping = ref(false)
const messagesEndRef = ref<HTMLDivElement | null>(null)

// 当前登录用户 ID（兼容 id / userId 字段，登录返回的 UserInfoVO 使用 id）
const currentUserId = computed(() => userStore.userInfo?.id ?? (userStore.userInfo as any)?.userId ?? 0)

// 在线状态：登录中即视为在线（只要 token 有效就显示在线）
const isOnline = computed(() => !!userStore.token)

// 头像信息（兼容 partner 对象 / partnerId 旧字段）
const myAvatarUrl = computed(() => userStore.userInfo?.avatarUrl || '')
const myNickname = computed(() => userStore.userInfo?.nickname || '我')
const myInitial = computed(() => (myNickname.value || '我').charAt(0).toUpperCase())

const partnerInfo = computed(() => userStore.userInfo?.partner || {})
const partnerAvatarUrl = computed(() => (partnerInfo.value as any)?.avatarUrl || '')
const partnerNickname = computed(() => (partnerInfo.value as any)?.nickname || 'TA')
const partnerInitial = computed(() => (partnerNickname.value || 'TA').charAt(0).toUpperCase())

// ========== 多选 / 长按菜单 ==========
const selectingMode = ref(false)
const selectedIds = ref<Set<number | string>>(new Set())
const selectedCount = computed(() => selectedIds.value.size)

function getMsgId(msg: ChatMessageVO, idx: number): number | string {
  return (msg as any).clientMsgId ?? msg.id ?? `idx_${idx}`
}

function isSelected(msg: ChatMessageVO, idx: number): boolean {
  return selectedIds.value.has(getMsgId(msg, idx))
}

function toggleSelect(msg: ChatMessageVO, idx: number) {
  const id = getMsgId(msg, idx)
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
  // 取消全部选择后自动退出多选模式
  if (selectedIds.value.size === 0) {
    selectingMode.value = false
  }
}

function exitSelectMode() {
  selectingMode.value = false
  selectedIds.value = new Set()
}

let longPressTimer: number | null = null
let longPressTriggered = false

function onMsgTouchStart(msg: ChatMessageVO, idx: number) {
  longPressTriggered = false
  if (longPressTimer) clearTimeout(longPressTimer)
  longPressTimer = window.setTimeout(() => {
    longPressTriggered = true
    if (navigator.vibrate) navigator.vibrate(10)
    if (selectingMode.value) {
      // 多选模式下：长按直接切换选中
      toggleSelect(msg, idx)
    } else {
      showMessageActionSheet(msg, idx)
    }
  }, 500)
}

function onMsgTouchEnd() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function onMsgTouchMove() {
  // 滑动时取消长按
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

// 长按弹出的操作菜单状态
const actionSheetShow = ref(false)
const actionSheetMsg = ref<ChatMessageVO | null>(null)
const actionSheetIdx = ref(0)

function showMessageActionSheet(msg: ChatMessageVO, idx: number) {
  actionSheetMsg.value = msg
  actionSheetIdx.value = idx
  actionSheetShow.value = true
}

async function onActionSelect(action: { name: string }) {
  const msg = actionSheetMsg.value
  const idx = actionSheetIdx.value
  actionSheetShow.value = false
  if (!msg) return
  const id = getMsgId(msg, idx)
  switch (action.name) {
    case '复制':
      await copyMessage(msg)
      break
    case '转发':
      forwardMessage(msg)
      break
    case '撤回':
      await recallMessageAction(msg)
      break
    case '删除':
      deleteMessageAction(msg, id)
      break
    case '多选':
      selectingMode.value = true
      toggleSelect(msg, idx)
      break
  }
}

async function copyMessage(msg: ChatMessageVO) {
  try {
    await navigator.clipboard.writeText(msg.content || '')
    showToast('已复制')
  } catch {
    showToast('复制失败')
  }
}

function forwardMessage(msg: ChatMessageVO) {
  // 后端暂无 FORWARD 接口；降级为复制文本并提示
  navigator.clipboard?.writeText(msg.content || '')
  showToast('已复制，可粘贴到其他对话')
}

function deleteMessageAction(msg: ChatMessageVO, id: number | string) {
  // 撤回态的消息不允许删除
  if (msg.revoked === 1) {
    showToast('该消息已撤回')
    return
  }
  showConfirmDialog({
    title: '删除消息',
    message: '确定从当前会话中删除此消息？（仅你不可见，对方仍可见）'
  })
    .then(async () => {
      // 优先 WS（实时），失败回退 REST
      if (ws.value && ws.value.readyState === WebSocket.OPEN) {
        ws.value.send(JSON.stringify({ type: 'DELETE', id: msg.id }))
      } else if (msg.id != null) {
        try {
          await apiDeleteMessage(msg.id)
        } catch (e) {
          showToast('删除失败')
          return
        }
      }
      messages.value = messages.value.filter((m, i) => getMsgId(m, i) !== id)
      showToast('已删除')
    })
    .catch(() => {
      // 用户取消
    })
}

async function recallMessageAction(msg: ChatMessageVO) {
  // 仅本人消息可以撤回，且需 2 分钟内
  if (msg.senderId !== currentUserId.value) {
    showToast('只能撤回自己发送的消息')
    return
  }
  if (msg.revoked === 1) {
    showToast('该消息已撤回')
    return
  }
  if (msg.id == null) {
    showToast('消息尚未发送成功')
    return
  }
  showConfirmDialog({
    title: '撤回消息',
    message: '确定撤回这条消息吗？（超过 2 分钟将无法撤回）'
  })
    .then(async () => {
      try {
        await apiRecallMessage(msg.id!)
        // 乐观更新：本地标记为撤回（WS RECALL 也会再次覆盖）
        const idx = messages.value.findIndex((m, i) => getMsgId(m, i) === getMsgId(msg, 0) && m === msg)
        if (idx >= 0) {
          messages.value[idx] = { ...messages.value[idx], revoked: 1, revokedAt: new Date().toISOString() as any }
        }
        showToast('已撤回')
      } catch (e: any) {
        const msg2 = e?.response?.data?.message || e?.message || '撤回失败'
        showToast(msg2)
      }
    })
    .catch(() => {
      // 取消
    })
}

async function batchCopy() {
  if (selectedIds.value.size === 0) return
  const texts: string[] = []
  messages.value.forEach((m, i) => {
    if (selectedIds.value.has(getMsgId(m, i))) {
      if (m.content) texts.push(m.content)
    }
  })
  try {
    await navigator.clipboard.writeText(texts.join('\n'))
    showToast(`已复制 ${texts.length} 条消息`)
  } catch {
    showToast('复制失败')
  }
}

function batchForward() {
  batchCopy().then(() => {
    showToast('内容已复制，可粘贴到其他对话')
  })
}

async function batchDelete() {
  if (selectedIds.value.size === 0) return
  // 收集已选中且尚未撤回、有服务端 id 的消息（任何人都可以删除他人的消息，删除者视图不再显示）
  const ids: number[] = []
  messages.value.forEach((m) => {
    if (
      selectedIds.value.has(getMsgId(m, messages.value.indexOf(m))) &&
      m.revoked !== 1 &&
      m.id != null
    ) {
      ids.push(m.id)
    }
  })
  if (ids.length === 0) {
    showToast('没有可删除的消息')
    exitSelectMode()
    return
  }
  showConfirmDialog({
    title: '删除消息',
    message: `确定删除选中的 ${ids.length} 条消息？（仅你不可见，对方仍可见）`
  })
    .then(async () => {
      try {
        // 批量 REST 一次（失败回退逐条 WS DELETE）
        try {
          await apiDeleteMessagesBatch(ids)
        } catch {
          ids.forEach((id) => {
            if (ws.value && ws.value.readyState === WebSocket.OPEN) {
              ws.value.send(JSON.stringify({ type: 'DELETE', id }))
            }
          })
        }
        // 本地立即移除
        messages.value = messages.value.filter((m) => !selectedIds.value.has(getMsgId(m, messages.value.indexOf(m))))
        showToast(`已删除 ${ids.length} 条`)
        exitSelectMode()
      } catch (e) {
        showToast('删除失败，请稍后再试')
      }
    })
    .catch(() => {
      // 取消
    })
}

function onMsgClick(msg: ChatMessageVO, idx: number) {
  if (longPressTriggered) {
    longPressTriggered = false
    return
  }
  if (selectingMode.value) {
    toggleSelect(msg, idx)
  }
}

// ========== 桌面端右键菜单 ==========
const contextMenuShow = ref(false)
const contextMenuActions = ref<{ name: string; color?: string }[]>([])

function onMsgContextMenu(msg: ChatMessageVO, idx: number, e: MouseEvent) {
  // 仅在非触摸设备响应右键
  if (e && typeof e.preventDefault === 'function') e.preventDefault()
  actionSheetMsg.value = msg
  actionSheetIdx.value = idx
  const isMine = msg.senderId === currentUserId.value
  const canDelete = msg.revoked !== 1
  contextMenuActions.value = [
    { name: '复制' },
    { name: '转发' },
    ...(isMine && msg.revoked !== 1 ? [{ name: '撤回' }] : []),
    ...(canDelete ? [{ name: '删除', color: '#ee0a24' }] : []),
    { name: '多选' }
  ]
  contextMenuShow.value = true
}

async function onContextMenuSelect(action: { name: string }) {
  contextMenuShow.value = false
  await onActionSelect(action)
}

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
    messages.value = res.list
    scrollToBottom()
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
        // 如果是对方发来的，自动标记已读并刷新未读数
        if (incoming.senderId !== currentUserId.value) {
          doMarkAllRead().then(() => unreadStore.refresh())
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
      case 'RECALL': {
        // 撤回：替换气泡内容为"你撤回了一条消息"
        const targetId = msg.id
        messages.value = messages.value.map((m) =>
          m.id === targetId
            ? { ...m, revoked: 1, revokedAt: new Date().toISOString() as any, content: '' }
            : m
        )
        break
      }
      case 'DELETE_ACK': {
        // 单条删除 WS 确认：本地移除
        const targetId = msg.id
        messages.value = messages.value.filter((m) => m.id !== targetId)
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
  try {
    ws.value.send(JSON.stringify(payload))
  } catch {
    // 连接刚断开时 send 可能抛错，忽略即可（下次输入会重试）
  }
  typingTimer = window.setTimeout(() => (typingTimer = null), 2000)
}

function sendMessage() {
  const text = inputText.value.trim()
  if (!text) return

  // WS 未就绪：连接中时等待 2s 再发，仍连不上才报错
  if (!ws.value || ws.value.readyState !== WebSocket.OPEN) {
    if (ws.value && ws.value.readyState === WebSocket.CONNECTING) {
      showToast('正在建立连接，请稍候…')
      setTimeout(() => {
        if (ws.value && ws.value.readyState === WebSocket.OPEN) {
          sendMessage()
        } else {
          showToast('连接失败，请稍后再试')
        }
      }, 2000)
      return
    }
    showToast('聊天连接未就绪，请稍后再试')
    return
  }

  // 兼容后端 UserVO：partner 对象携带伴侣 ID；旧 partnerId 字段保留兼容
  const partnerId =
    userStore.userInfo?.partnerId ||
    (userStore.userInfo?.partner as any)?.id ||
    0
  if (!partnerId) {
    showToast('请先绑定伴侣')
    return
  }

  sending.value = true
  const clientMsgId = `c_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

  // 乐观更新：先显示本地消息
  messages.value.push({
    id: undefined,
    senderId: currentUserId.value,
    receiverId: partnerId,
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
  // 1. 登录态校验
  if (!userStore.token) {
    showToast('请先登录')
    router.replace('/')
    return
  }

  // 2. 拉取最新用户信息（userStore.userInfo 可能因刷新、换号或绑定后未更新而缺失/过期）
  //    后端 UserVO 没有顶层 partnerId 字段，仅返回 partner 对象与 isBound 标志
  //    关键：必须确保拿到当前 token 对应的真实 userInfo.id，否则消息左右判定会全错
  let info = userStore.userInfo
  // 关键：只要缺少 partner 信息（登录返回的 UserInfoVO 不含 partner 对象）就必须拉取完整 profile，
  //      否则发送消息时取不到 partnerId 会误报"请先绑定伴侣"
  const needFetch = !info || !info.id || (!info.partnerId && !info.partner)
  if (needFetch) {
    try {
      const fetched = await getUserInfo()
      if (fetched && fetched.id) {
        userStore.setUserInfo(fetched)
        info = fetched
      }
    } catch (e) {
      // 拉取失败时按未登录处理
      showToast('请先登录')
      router.replace('/')
      return
    }
  }

  // 再次兜底：拉取后仍无 id，说明 token 与用户信息不匹配，强制退出
  if (!info || !info.id) {
    showToast('用户信息异常，请重新登录')
    router.replace('/')
    return
  }

  // 3. 绑定态校验：兼容三种字段（partnerId 旧字段、partner 对象、isBound）
  const isBound = !!(info?.partnerId || info?.partner || info?.isBound)
  if (!isBound) {
    showToast('请先绑定伴侣')
    router.replace('/profile')
    return
  }

  await loadHistory()
  // 进入聊天页：未读清零
  unreadStore.reset()
  await unreadStore.refresh()
  connect()

  // 标记"停留在聊天页"：对方发来的消息会被服务端自动标记已读 + SSE 实时推回执
  const pid = Number(
    info.partnerId ||
      (info.partner as any)?.id ||
      0
  )
  if (pid) {
    enterChatPage(pid).catch((e) => console.warn('[chat] enterChatPage', e))
    // 启动心跳（20s 一次，TTL 30s，确保不会过期）
    if (presenceHeartbeatTimer) clearInterval(presenceHeartbeatTimer)
    presenceHeartbeatTimer = window.setInterval(() => {
      heartbeatChatPage().catch(() => {
        // 心跳失败静默，下一轮会重试
      })
    }, 20_000)
  }

  // 监听 SSE chat-read：伴侣在聊天页，对方实时推送已读 → 更新本地消息状态
  registerSseChatRead()
})

let presenceHeartbeatTimer: number | null = null
let sseChatReadHandler: ((data: any) => void) | null = null

function registerSseChatRead() {
  const sse = getNotificationSSE()
  sseChatReadHandler = (data: { lastReadId?: number; partnerId?: number }) => {
    if (!data || !data.lastReadId) return
    // 把所有 id <= lastReadId 且发送者为本人的消息标记为已读
    const lastId = Number(data.lastReadId)
    if (Number.isNaN(lastId)) return
    let changed = false
    messages.value = messages.value.map((m) => {
      if (m.senderId === currentUserId.value && m.id && m.id <= lastId && m.isRead !== 1) {
        changed = true
        return { ...m, isRead: 1 }
      }
      return m
    })
    if (changed) {
      // 不强求弹 toast，避免打扰用户
    }
  }
  sse.on('chat-read', sseChatReadHandler)
}

onBeforeUnmount(() => {
  disconnect()
  // 停止心跳 + 标记离开聊天页（容错：接口失败也不影响卸载流程）
  if (presenceHeartbeatTimer) {
    clearInterval(presenceHeartbeatTimer)
    presenceHeartbeatTimer = null
  }
  if (sseChatReadHandler) {
    getNotificationSSE().off('chat-read', sseChatReadHandler)
    sseChatReadHandler = null
  }
  leaveChatPage().catch(() => {})
  // 注销登出事件监听，避免内存泄漏
  window.removeEventListener('lovemap:before-logout', onBeforeLogout as EventListener)
})

// 监听全局登出事件：登出前主动关闭 WS、清理心跳，避免服务端"仍显示在线"
function onBeforeLogout() {
  try {
    if (presenceHeartbeatTimer) {
      clearInterval(presenceHeartbeatTimer)
      presenceHeartbeatTimer = null
    }
    // 主动调用离开聊天页 + 关闭 WS
    leaveChatPage().catch(() => {})
    disconnect()
  } catch {
    // 静默吞错，登出主流程不受影响
  }
}

// 注册一次全局登出事件监听（onBeforeUnmount 会清理）
if (typeof window !== 'undefined') {
  window.addEventListener('lovemap:before-logout', onBeforeLogout as EventListener)
}
</script>

<template>
  <div class="chat-page">
    <van-nav-bar
      :title="selectingMode ? `已选 ${selectedCount} 条` : '聊天'"
      :left-text="selectingMode ? '取消' : '返回'"
      :left-arrow="!selectingMode"
      fixed
      placeholder
      @click-left="selectingMode ? exitSelectMode() : router.back()"
    >
      <template v-if="!selectingMode" #right>
        <span class="conn-status" :class="{ online: isOnline }">
          <span class="dot"></span>
          {{ isOnline ? '在线' : '离线' }}
        </span>
      </template>
      <template v-else #right>
        <span class="select-done" @click="exitSelectMode">完成</span>
      </template>
    </van-nav-bar>

    <!-- 多选模式工具栏 -->
    <div v-if="selectingMode" class="select-toolbar">
      <van-button size="small" plain icon="copy-o" @click="batchCopy">复制</van-button>
      <van-button size="small" plain icon="share-o" @click="batchForward">转发</van-button>
      <van-button size="small" plain icon="delete-o" type="danger" @click="batchDelete">删除</van-button>
    </div>

    <div class="chat-content">
      <div v-if="messages.length === 0" class="empty-tip">
        <div class="empty-icon">💌</div>
        <div class="empty-title">还没有聊天记录</div>
        <div class="empty-sub">说点什么，开启你们的悄悄话吧～</div>
      </div>
      <van-swipe-cell
        v-for="(msg, idx) in messages"
        v-show="currentUserId > 0"
        :key="(msg as any).clientMsgId || `${msg.id}-${idx}`"
        :disabled="selectingMode || msg.revoked === 1"
        :right-width="msg.revoked !== 1 ? 64 : 0"
      >
        <template #right>
          <van-button
            v-if="msg.revoked !== 1"
            square
            type="danger"
            text="删除"
            style="height: 100%;"
            @click="deleteMessageAction(msg, getMsgId(msg, idx))"
          />
        </template>
        <div
          class="msg-row"
          :class="{
            mine: msg.senderId === currentUserId,
            selected: selectingMode && isSelected(msg, idx),
            revoked: msg.revoked === 1
          }"
          @click="onMsgClick(msg, idx)"
          @touchstart="onMsgTouchStart(msg, idx)"
          @touchend="onMsgTouchEnd"
          @touchmove="onMsgTouchMove"
          @contextmenu="onMsgContextMenu(msg, idx, $event)"
        >
          <!-- 对方头像（左侧消息时显示） -->
          <div v-if="msg.senderId !== currentUserId" class="avatar left">
            <img v-if="partnerAvatarUrl" :src="partnerAvatarUrl" :alt="partnerNickname" />
            <span v-else class="avatar-text">{{ partnerInitial }}</span>
          </div>

          <div class="bubble">
            <div v-if="selectingMode" class="select-check">
              <van-icon :name="isSelected(msg, idx) ? 'success' : 'circle'" size="20" />
            </div>
            <template v-if="msg.revoked === 1">
              <div class="bubble-text revoked-text">{{ msg.senderId === currentUserId ? '你撤回了一条消息' : '对方撤回了一条消息' }}</div>
            </template>
            <template v-else>
              <div class="bubble-text">{{ msg.content }}</div>
              <div class="bubble-meta">
                <span class="time">{{ formatTime(msg.createdAt) }}</span>
                <span
                  v-if="msg.senderId === currentUserId && msg.isRead === 1"
                  class="read-flag"
                >已读</span>
              </div>
            </template>
          </div>

          <!-- 本人头像（右侧消息时显示） -->
          <div v-if="msg.senderId === currentUserId" class="avatar right">
            <img v-if="myAvatarUrl" :src="myAvatarUrl" :alt="myNickname" />
            <span v-else class="avatar-text">{{ myInitial }}</span>
          </div>
        </div>
      </van-swipe-cell>
      <div v-if="peerTyping && currentUserId > 0" class="msg-row">
        <div class="avatar left">
          <img v-if="partnerAvatarUrl" :src="partnerAvatarUrl" :alt="partnerNickname" />
          <span v-else class="avatar-text">{{ partnerInitial }}</span>
        </div>
        <div class="bubble typing-bubble">
          <span class="typing-dot"></span>
          <span class="typing-dot"></span>
          <span class="typing-dot"></span>
        </div>
      </div>
      <div ref="messagesEndRef" />
    </div>

    <div class="chat-input-bar">
      <van-field
        v-model="inputText"
        class="input"
        placeholder="💭 说点什么吧…"
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

    <!-- 长按消息弹出的操作菜单 -->
    <van-action-sheet
      v-model:show="actionSheetShow"
      :actions="[
        { name: '复制' },
        { name: '转发' },
        { name: '撤回' },
        { name: '删除' },
        { name: '多选' }
      ]"
      cancel-text="取消"
      close-on-click-action
      @select="onActionSelect"
    />

    <!-- 桌面端右键弹出的操作菜单 -->
    <van-action-sheet
      v-model:show="contextMenuShow"
      :actions="contextMenuActions"
      cancel-text="取消"
      close-on-click-action
      @select="onContextMenuSelect"
    />
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  /* 优先使用动态视口高度，避免移动端浏览器地址栏收缩导致高度跳动 */
  height: 100vh;
  height: 100dvh;
  background: #f7f8fa;
  /* 防止父级滚动，确保只有 .chat-content 内部滚动 */
  overflow: hidden;
}
.conn-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #999;
}
.conn-status .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dc3545;
  transition: background-color 0.2s;
}
.conn-status.online .dot {
  background: #07c160;
  box-shadow: 0 0 0 3px rgba(7, 193, 96, 0.15);
}
.conn-status.online {
  color: #07c160;
}
.select-done {
  color: #1989fa;
  font-size: 14px;
  padding: 0 4px;
}
.select-toolbar {
  position: sticky;
  top: 46px;
  z-index: 99;
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 10px 12px;
  background: linear-gradient(135deg, #fff5f5 0%, #fff 100%);
  border-bottom: 1px solid #f0e6e6;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
}
.select-check {
  margin-bottom: 4px;
  color: #1989fa;
}
.msg-row.selected .bubble {
  border: 2px solid #1989fa;
  box-shadow: 0 0 0 2px rgba(25, 137, 250, 0.15);
}
.msg-row.selected.mine .bubble::before {
  border-left-color: #fff; /* 选中时尖角用白，避免绿底冲突视觉 */
}
.chat-content {
  /* flex 子项在父容器中正确收缩需要 min-height: 0，否则会被内容撑开 */
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 12px 12px 12px;
  background: #f7f8fa;
}
.empty-tip {
  text-align: center;
  color: #999;
  font-size: 14px;
  margin-top: 100px;
  padding: 0 24px;
}
.empty-tip .empty-icon {
  font-size: 56px;
  margin-bottom: 12px;
  filter: grayscale(0.2);
}
.empty-tip .empty-title {
  font-size: 16px;
  font-weight: 500;
  color: #555;
  margin-bottom: 6px;
}
.empty-tip .empty-sub {
  font-size: 13px;
  color: #aaa;
  line-height: 1.6;
}
.msg-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 14px;
  gap: 8px;
}
.msg-row.mine {
  justify-content: flex-end;
}
.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #ff8a8a 0%, #ffb3b3 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.avatar.left {
  background: linear-gradient(135deg, #87ceeb 0%, #b0e0e6 100%);
  color: #fff;
}
.avatar.right {
  background: linear-gradient(135deg, #ff8a8a 0%, #ffb3b3 100%);
  color: #fff;
}
.avatar-text {
  line-height: 1;
  user-select: none;
}
.bubble {
  max-width: 65%;
  padding: 9px 12px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  position: relative;
}
.bubble::before {
  content: '';
  position: absolute;
  top: 12px;
  width: 0;
  height: 0;
  border: 5px solid transparent;
}
/* 左侧消息的小尖角朝左 */
.msg-row:not(.mine) .bubble::before {
  left: -10px;
  border-right-color: #fff;
}
/* 右侧消息的小尖角朝右 */
.msg-row.mine .bubble::before {
  right: -10px;
  border-left-color: #95ec69;
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
.bubble-text.revoked-text {
  font-size: 13px;
  color: #999;
  font-style: italic;
  text-align: center;
  padding: 4px 0;
}
.msg-row.revoked .bubble {
  background: transparent !important;
  box-shadow: none;
}
.msg-row.revoked .bubble::before {
  display: none;
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
.typing-bubble {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 10px 14px;
  min-width: 54px;
  background: #fff;
}
.typing-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #b5b5b5;
  display: inline-block;
  animation: typing-blink 1.4s infinite both;
}
.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing-blink {
  0%, 80%, 100% { opacity: 0.3; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-2px); }
}
.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  /* iPhone X+ 等带底部安全区的机型：让输入框上移，避免被 home indicator 遮挡 */
  padding-bottom: calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #ebedf0;
  /* 关键：防止输入栏被 flex 收缩挤出视口 */
  flex-shrink: 0;
}
.input {
  flex: 1;
  background: #f7f8fa;
  border-radius: 18px;
  padding: 6px 12px;
}
:deep(.van-field__control) {
  font-size: 15px;
}
</style>