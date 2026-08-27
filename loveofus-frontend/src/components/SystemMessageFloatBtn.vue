<template>
  <div
    ref="floatBtnRef"
    class="system-message-float-btn"
    :style="btnStyle"
    @touchstart="onTouchStart"
    @touchmove.prevent="onTouchMove"
    @touchend="onTouchEnd"
    @mousedown="onMouseDown"
    @click="onBtnClick"
  >
    <van-badge :content="unreadCount > 0 ? unreadCount : ''" :max="99">
      <div class="btn-icon">
        <van-icon name="bell" size="24" />
      </div>
    </van-badge>

    <!-- 新消息通知气泡 -->
    <transition name="message-bubble">
      <div v-if="showNewMessageBubble" class="new-message-bubble" @click.stop="onBubbleClick">
        <div class="bubble-header">
          <van-icon name="volume-o" class="bubble-icon" />
          <span class="bubble-title">新消息</span>
          <span class="bubble-count">{{ unreadCount }}条未读</span>
        </div>
        <div class="bubble-content">{{ latestMessageTitle }}</div>
      </div>
    </transition>
  </div>

  <!-- 消息弹窗 -->
  <van-popup
    v-model:show="showPopup"
    round
    position="bottom"
    :style="{ height: '70%', maxHeight: '600px' }"
    closeable
  >
    <div class="message-popup">
      <div class="popup-header">
        <h3>系统消息</h3>
        <van-tabs v-model:active="activeTab" shrink @change="onTabChange">
          <van-tab :title="`未读 (${unreadCount})`" name="unread" />
          <van-tab title="已读" name="read" />
        </van-tabs>
      </div>

      <div class="message-list">
        <!-- 空状态 -->
        <van-empty
          v-if="loading"
          description="加载中..."
          image="search"
        />
        <van-empty
          v-else-if="currentMessages.length === 0"
          :description="activeTab === 'unread' ? '暂无未读消息' : '暂无已读消息'"
        />

        <!-- 消息列表 -->
        <div
          v-for="msg in currentMessages"
          v-else
          :key="msg.id"
          class="message-item"
          @click="onMessageClick(msg)"
        >
          <div class="message-header">
            <span class="message-type-tag">{{ msg.typeName || '系统' }}</span>
            <span class="message-title">{{ msg.title || '新消息' }}</span>
            <span class="message-time">{{ formatTime(msg.createdAt) }}</span>
          </div>
          <div class="message-summary">{{ (msg.content || msg.text || '').slice(0, 80) }}</div>
          <div class="message-actions">
            <van-button
              v-if="!msg.isRead"
              size="mini"
              type="primary"
              plain
              @click.stop="handleMarkAsRead(msg)"
            >
              标为已读
            </van-button>
            <van-button
              size="mini"
              type="danger"
              plain
              @click.stop="handleDeleteMessage(msg)"
            >
              删除
            </van-button>
          </div>
        </div>
      </div>
    </div>
  </van-popup>

  <!-- 消息详情弹窗 -->
  <van-popup
    v-model:show="showDetailPopup"
    round
    position="bottom"
    :style="{ height: '50%', maxHeight: '400px' }"
    closeable
  >
    <div class="message-detail-popup" v-if="selectedMessage">
      <div class="popup-header">
        <h3>{{ selectedMessage.title || '消息详情' }}</h3>
        <div class="detail-time">{{ formatTime(selectedMessage.createdAt) }}</div>
      </div>
      <div class="detail-content">
        {{ selectedMessage.content || selectedMessage.text || '' }}
      </div>
      <div class="detail-actions">
        <van-button
          v-if="!selectedMessage.isRead"
          round
          block
          type="primary"
          @click="markAsReadAndClose"
        >
          标为已读
        </van-button>
        <van-button
          round
          block
          type="danger"
          plain
          @click="deleteMessageAndClose"
        >
          删除消息
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import {
  getNotificationList,
  markAsRead,
  deleteNotification,
  getUnreadCount,
  getNotificationSSE,
  NotificationSSE,
  type Notification
} from '@/api/systemMessage'
import { useChatUnreadStore } from '@/stores/chatUnread'

// 悬浮按钮位置（默认右下角）
const DEFAULT_POSITION = { right: 20, bottom: 80 }
const position = ref({ ...DEFAULT_POSITION })
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const btnStartPos = ref({ right: 0, bottom: 0 })
const hasMoved = ref(false)

// 弹窗状态
const showPopup = ref(false)
const showDetailPopup = ref(false)
const activeTab = ref('unread')
const loading = ref(false)
const selectedMessage = ref<Notification | null>(null)

// 消息数据
const unreadMessages = ref<Notification[]>([])
const readMessages = ref<Notification[]>([])
const unreadCount = ref(0)

// 新消息气泡
const showNewMessageBubble = ref(false)
const latestMessageTitle = ref('')
let bubbleTimer: ReturnType<typeof setTimeout> | null = null

// 计算当前显示的消息
const currentMessages = computed(() => {
  return activeTab.value === 'unread' ? unreadMessages.value : readMessages.value
})

// 按钮样式
const btnStyle = computed(() => ({
  right: `${position.value.right}px`,
  bottom: `${position.value.bottom}px`
}))

// 触摸开始
function onTouchStart(e: TouchEvent) {
  isDragging.value = true
  hasMoved.value = false
  const touch = e.touches[0]
  dragStart.value = { x: touch.clientX, y: touch.clientY }
  btnStartPos.value = { ...position.value }
}

// 触摸移动
function onTouchMove(e: TouchEvent) {
  if (!isDragging.value) return

  const touch = e.touches[0]
  const deltaX = touch.clientX - dragStart.value.x
  const deltaY = touch.clientY - dragStart.value.y

  // 判断是否真的在拖动（超过5px才算拖动）
  if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
    hasMoved.value = true
  }

  const newRight = btnStartPos.value.right - deltaX
  const newBottom = btnStartPos.value.bottom - deltaY

  // 限制在屏幕范围内
  const maxRight = window.innerWidth - 60
  const maxBottom = window.innerHeight - 60

  position.value = {
    right: Math.max(10, Math.min(maxRight, newRight)),
    bottom: Math.max(70, Math.min(maxBottom, newBottom))
  }
}

// 触摸结束
function onTouchEnd() {
  isDragging.value = false
}

// 鼠标事件（PC端支持）
function onMouseDown(e: MouseEvent) {
  isDragging.value = true
  hasMoved.value = false
  dragStart.value = { x: e.clientX, y: e.clientY }
  btnStartPos.value = { ...position.value }

  const onMouseMove = (ev: MouseEvent) => {
    if (!isDragging.value) return
    const deltaX = ev.clientX - dragStart.value.x
    const deltaY = ev.clientY - dragStart.value.y

    if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
      hasMoved.value = true
    }

    const newRight = btnStartPos.value.right - deltaX
    const newBottom = btnStartPos.value.bottom - deltaY

    const maxRight = window.innerWidth - 60
    const maxBottom = window.innerHeight - 60

    position.value = {
      right: Math.max(10, Math.min(maxRight, newRight)),
      bottom: Math.max(70, Math.min(maxBottom, newBottom))
    }
  }

  const onMouseUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

// 点击气泡
function onBubbleClick() {
  showNewMessageBubble.value = false
  showPopup.value = true
  loadMessages()
}

// 点击按钮
function onBtnClick() {
  if (hasMoved.value) return
  showNewMessageBubble.value = false
  showPopup.value = true
  loadMessages()
}

// 加载消息
async function loadMessages() {
  loading.value = true
  try {
    const [unreadRes, readRes] = await Promise.all([
      getNotificationList({ page: 1, size: 100, isRead: 0 }),
      getNotificationList({ page: 1, size: 100, isRead: 1 })
    ])
    unreadMessages.value = unreadRes?.list || []
    readMessages.value = readRes?.list || []
    unreadCount.value = unreadRes?.unreadCount || 0
  } catch {
    showToast('加载消息失败')
  } finally {
    loading.value = false
  }
}

// 切换tab
function onTabChange() {
  loadMessages()
}

// 点击消息
function onMessageClick(msg: Notification) {
  selectedMessage.value = msg
  showDetailPopup.value = true
}

// 标为已读
async function handleMarkAsRead(msg: Notification) {
  try {
    await markAsRead(msg.id)
    msg.isRead = true
    unreadMessages.value = unreadMessages.value.filter(m => m.id !== msg.id)
    readMessages.value.unshift(msg)
    unreadCount.value = unreadMessages.value.length
    showToast('已标为已读')
  } catch {
    showToast('操作失败')
  }
}

// 删除消息
async function handleDeleteMessage(msg: Notification) {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '确定要删除这条消息吗？'
    })
    await deleteNotification(msg.id)
    unreadMessages.value = unreadMessages.value.filter(m => m.id !== msg.id)
    readMessages.value = readMessages.value.filter(m => m.id !== msg.id)
    unreadCount.value = unreadMessages.value.length
    showToast('已删除')
  } catch {
    // 用户取消
  }
}

// 标为已读并关闭详情
async function markAsReadAndClose() {
  if (selectedMessage.value) {
    await handleMarkAsRead(selectedMessage.value)
    showDetailPopup.value = false
  }
}

// 删除消息并关闭详情
async function deleteMessageAndClose() {
  if (selectedMessage.value) {
    await handleDeleteMessage(selectedMessage.value)
    showDetailPopup.value = false
  }
}

// 格式化时间
function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
}

// SSE 连接实例（全局共享，由 systemMessage 单例管理生命周期）
let sse: NotificationSSE | null = null
// 持有监听器引用，便于卸载时解除绑定
const sseHandlers: { event: string; fn: (data: any) => void }[] = []

// 注册 SSE 监听器（不主动建连，避免与其他组件重复连接）
function registerSseListeners() {
  sse = getNotificationSSE()

  const onNewNotification = (msg: Notification) => {
    unreadMessages.value.unshift(msg)
    unreadCount.value = unreadMessages.value.length
    // 显示新消息气泡
    latestMessageTitle.value = msg.title || msg.text || '新消息'
    showNewMessageBubble.value = true
    // 5秒后自动隐藏
    if (bubbleTimer) clearTimeout(bubbleTimer)
    bubbleTimer = setTimeout(() => {
      showNewMessageBubble.value = false
    }, 5000)
  }
  sseHandlers.push({ event: 'new-notification', fn: onNewNotification })
  sse.on('new-notification', onNewNotification)

  const onUnreadCount = (data: { count: number }) => {
    unreadCount.value = data.count
  }
  sseHandlers.push({ event: 'unread-count', fn: onUnreadCount })
  sse.on('unread-count', onUnreadCount)

  // 同步转发聊天未读数到 chatUnread store（保证角标在所有页面实时更新）
  const onChatUnreadCount = (data: { count: number; partnerId: number }) => {
    try {
      const chatUnread = useChatUnreadStore()
      chatUnread.count = Math.max(0, Number(data?.count) || 0)
    } catch (e) {
      console.warn('[SSE] chat-unread-count handler failed', e)
    }
  }
  sseHandlers.push({ event: 'chat-unread-count', fn: onChatUnreadCount })
  sse.on('chat-unread-count', onChatUnreadCount)

  const onConnected = () => {
    console.log('[SSE] 消息通知连接已建立')
  }
  sseHandlers.push({ event: 'connected', fn: onConnected })
  sse.on('connected', onConnected)

  const onError = () => {
    console.warn('[SSE] 消息通知连接错误')
  }
  sseHandlers.push({ event: 'error', fn: onError })
  sse.on('error', onError)
}

onMounted(async () => {
  // 初始获取未读数量
  try {
    const count = await getUnreadCount()
    unreadCount.value = count || 0
  } catch {
    // 忽略错误
  }

  // 注册 SSE 监听器（全局单例负责建连/重连）
  registerSseListeners()
})

onUnmounted(() => {
  // 全局单例的生命周期独立于本组件，仅卸载本组件添加的监听器
  if (sse) {
    for (const h of sseHandlers) {
      sse.off(h.event, h.fn)
    }
    sseHandlers.length = 0
    sse = null
  }
  // 清除气泡定时器
  if (bubbleTimer) {
    clearTimeout(bubbleTimer)
  }
})
</script>

<style scoped lang="scss">
.system-message-float-btn {
  position: fixed;
  z-index: 998;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  box-shadow: 0 4px 12px rgba($primary-color, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  user-select: none;
  touch-action: none;

  &:active {
    transform: scale(0.95);
  }

  .btn-icon {
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  :deep(.van-badge) {
    .van-badge__content {
      font-size: 10px;
    }
  }

  // 新消息气泡
  .new-message-bubble {
    position: absolute;
    right: 60px;
    bottom: 0;
    width: 200px;
    padding: 12px;
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    cursor: pointer;
    z-index: 999;

    .bubble-header {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 6px;

      .bubble-icon {
        color: $primary-color;
        font-size: 16px;
      }

      .bubble-title {
        font-size: 13px;
        font-weight: 500;
        color: $text-primary;
        flex: 1;
      }

      .bubble-count {
        font-size: 12px;
        color: $primary-color;
        font-weight: 500;
      }
    }

    .bubble-content {
      font-size: 12px;
      color: $text-secondary;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

// 气泡动画
.message-bubble-enter-active,
.message-bubble-leave-active {
  transition: all 0.3s ease;
}

.message-bubble-enter-from,
.message-bubble-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.message-popup {
  height: 100%;
  display: flex;
  flex-direction: column;

  .popup-header {
    padding: 16px 20px 0;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0 0 12px;
      font-size: 18px;
      color: $text-primary;
      font-weight: 600;
    }
  }

  .message-list {
    flex: 1;
    overflow-y: auto;
    padding: 12px 16px;
  }

  .message-item {
    padding: 14px;
    margin-bottom: 10px;
    background: #fff;
    border-radius: $radius-md;
    box-shadow: $shadow-sm;
    cursor: pointer;
    transition: all 0.2s;

    &:active {
      transform: scale(0.98);
      background: $primary-light-bg;
    }

    .message-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      .message-type-tag {
        font-size: 12px;
        color: $primary-color;
        background: $primary-light-bg;
        padding: 2px 8px;
        border-radius: $radius-sm;
        margin-right: 8px;
        flex-shrink: 0;
      }

      .message-title {
        font-size: 15px;
        font-weight: 500;
        color: $text-primary;
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .message-time {
        font-size: 12px;
        color: $text-tertiary;
        margin-left: 12px;
        flex-shrink: 0;
      }
    }

    .message-summary {
      font-size: 13px;
      color: $text-secondary;
      line-height: 1.5;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      margin-bottom: 10px;
    }

    .message-actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
    }
  }
}

.message-detail-popup {
  height: 100%;
  display: flex;
  flex-direction: column;

  .popup-header {
    padding: 20px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0 0 8px;
      font-size: 18px;
      color: $text-primary;
      font-weight: 600;
    }

    .detail-time {
      font-size: 13px;
      color: $text-tertiary;
    }
  }

  .detail-content {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    font-size: 15px;
    line-height: 1.8;
    color: $text-primary;
    white-space: pre-wrap;
  }

  .detail-actions {
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    border-top: 1px solid $border-color;

    .van-button {
      height: 44px;
      font-size: 15px;
    }
  }
}
</style>
