<template>
  <div
    v-if="visible"
    ref="floatBtnRef"
    class="ai-float-btn"
    :style="btnStyle"
    @touchstart="onTouchStart"
    @touchmove.prevent="onTouchMove"
    @touchend="onTouchEnd"
    @mousedown="onMouseDown"
    @click="onBtnClick"
  >
    <van-badge :content="unreadCount > 0 ? unreadCount : ''" :max="99">
      <div class="btn-icon">
        <span class="ai-label">AI</span>
      </div>
    </van-badge>

    <!-- 长按/拖动提示气泡 -->
    <transition name="bubble">
      <div v-if="showHintBubble" class="hint-bubble" @click.stop="goChat">
        <div class="bubble-title">AI 情侣助手</div>
        <div class="bubble-desc">问照片、纪念日、写文案…</div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

// ==================== 显示控制 ====================

/**
 * 1. 未登录不显示（顶层路由守卫会拦截 /ai-chat）
 * 2. AI 服务总开关关闭时显示入口，聊天页内部降级
 * 直接复用 userStore 的 isLoggedIn，避免和真实 token 存储位置耦合
 */
const visible = computed(() => userStore.isLoggedIn)

// ==================== 未读计数 ====================

const UNREAD_KEY = 'ai_chat_unread'

const unreadCount = ref(parseInt(localStorage.getItem(UNREAD_KEY) || '0', 10))

function markRead() {
  unreadCount.value = 0
  localStorage.setItem(UNREAD_KEY, '0')
}

function bumpUnread() {
  unreadCount.value += 1
  localStorage.setItem(UNREAD_KEY, String(unreadCount.value))
}

// 监听跨标签页更新
function onStorage(e: StorageEvent) {
  if (e.key === UNREAD_KEY) {
    unreadCount.value = parseInt(e.newValue || '0', 10)
  }
}

// ==================== 拖动逻辑 ====================

const DEFAULT_POSITION = { right: 20, bottom: 150 } // 避开 SystemMessage 按钮
const position = ref({ ...DEFAULT_POSITION })
const isDragging = ref(false)
const hasMoved = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const btnStartPos = ref({ right: 0, bottom: 0 })

const btnStyle = computed(() => ({
  right: `${position.value.right}px`,
  bottom: `${position.value.bottom}px`
}))

function clampPosition(right: number, bottom: number) {
  const maxRight = window.innerWidth - 60
  const maxBottom = window.innerHeight - 60
  return {
    right: Math.max(10, Math.min(maxRight, right)),
    bottom: Math.max(70, Math.min(maxBottom, bottom))
  }
}

function onTouchStart(e: TouchEvent) {
  isDragging.value = true
  hasMoved.value = false
  const t = e.touches[0]
  dragStart.value = { x: t.clientX, y: t.clientY }
  btnStartPos.value = { ...position.value }
}

function onTouchMove(e: TouchEvent) {
  if (!isDragging.value) return
  const t = e.touches[0]
  const dx = t.clientX - dragStart.value.x
  const dy = t.clientY - dragStart.value.y
  if (Math.abs(dx) > 5 || Math.abs(dy) > 5) hasMoved.value = true
  position.value = clampPosition(btnStartPos.value.right - dx, btnStartPos.value.bottom - dy)
}

function onTouchEnd() {
  isDragging.value = false
}

function onMouseDown(e: MouseEvent) {
  isDragging.value = true
  hasMoved.value = false
  dragStart.value = { x: e.clientX, y: e.clientY }
  btnStartPos.value = { ...position.value }

  const onMove = (ev: MouseEvent) => {
    if (!isDragging.value) return
    const dx = ev.clientX - dragStart.value.x
    const dy = ev.clientY - dragStart.value.y
    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) hasMoved.value = true
    position.value = clampPosition(btnStartPos.value.right - dx, btnStartPos.value.bottom - dy)
  }
  const onUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

// ==================== 首次访问引导气泡 ====================

const HINT_KEY = 'ai_float_hint_shown'
const showHintBubble = ref(false)
let hintTimer: ReturnType<typeof setTimeout> | null = null

function maybeShowHint() {
  try {
    if (localStorage.getItem(HINT_KEY)) return
  } catch {
    return
  }
  showHintBubble.value = true
  hintTimer = setTimeout(() => {
    showHintBubble.value = false
    try {
      localStorage.setItem(HINT_KEY, '1')
    } catch {
      // ignore
    }
  }, 4000)
}

// ==================== 路由跳转 ====================

function goChat() {
  showHintBubble.value = false
  markRead()
  router.push('/ai-chat')
}

function onBtnClick() {
  if (hasMoved.value) return
  goChat()
}

// ==================== 生命周期 ====================

const floatBtnRef = ref<HTMLElement | null>(null)

onMounted(() => {
  window.addEventListener('storage', onStorage)
  maybeShowHint()

  // 监听自定义事件，供聊天页主动 bump 未读（用户切到别的页面时）
  window.addEventListener('ai-chat:new-message', bumpUnread)
})

onBeforeUnmount(() => {
  window.removeEventListener('storage', onStorage)
  window.removeEventListener('ai-chat:new-message', bumpUnread)
  if (hintTimer) clearTimeout(hintTimer)
})

// 暴露方法给外部调用
defineExpose({ bumpUnread, markRead })
</script>

<style scoped lang="scss">
.ai-float-btn {
  position: fixed;
  z-index: 997; // 低于 SystemMessageFloatBtn (998)，不会盖住
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6c8cff 0%, #8a5cff 100%);
  box-shadow: 0 4px 14px rgba(108, 140, 255, 0.45);
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

  .ai-label {
    font-size: 18px;
    font-weight: 700;
    letter-spacing: 0.5px;
    line-height: 1;
    font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Arial, sans-serif;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.18);
  }

  :deep(.van-badge__content) {
    font-size: 10px;
  }
}

.hint-bubble {
  position: absolute;
  right: 60px;
  bottom: 0;
  width: 180px;
  padding: 10px 12px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  z-index: 999;

  .bubble-title {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 2px;
  }

  .bubble-desc {
    font-size: 12px;
    color: $text-secondary;
  }
}

.bubble-enter-active,
.bubble-leave-active {
  transition: all 0.3s ease;
}
.bubble-enter-from,
.bubble-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>