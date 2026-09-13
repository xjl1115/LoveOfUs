<template>
  <!-- 全局组件注册（确保函数式调用的 CSS 和 JS 被正确加载） -->
  <van-toast />
  <van-image-preview />
  <van-dialog v-model:show="dummyDialogVisible" />
  <router-view v-slot="{ Component, route }">
    <transition name="fade" mode="out-in">
      <keep-alive :include="cachedViews">
        <component :is="Component" :key="route.path" />
      </keep-alive>
    </transition>
  </router-view>
  <!-- AI 全局入口（已登录自动出现） -->
  <AiFloatBtn />
  <!-- 右上角图标组：相机 + 私聊（嵌入上边框，非圆形） -->
  <div class="topright-bar">
    <CameraFloatBtn />
    <ChatFloatBtn />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
/**
 * 全局用户活跃度追踪
 */
import { useActive } from '@/composables/useActive'
useActive()
import AiFloatBtn from '@/components/AiFloatBtn.vue'
import ChatFloatBtn from '@/components/ChatFloatBtn.vue'
import CameraFloatBtn from '@/components/CameraFloatBtn.vue'
import { useUserStore } from '@/stores/user'
import { useChatUnreadStore } from '@/stores/chatUnread'
import { getNotificationSSE } from '@/api/systemMessage'

// 用于全局注册 van-dialog，供 showDialog / showConfirmDialog 使用
const dummyDialogVisible = ref(false)

// 需要缓存的页面（用户频繁往返的页面）
const cachedViews = ref(['Home', 'Albums', 'Profile'])

// ============ 全局聊天未读数 SSE 订阅 ============
// 修复：原实现把订阅挂在 Home/SystemMessageFloatBtn 各自 onMounted，跨页面会断链。
// 提升到 App.vue，应用整个生命周期持续接收，跨页面右上角实时更新。
const userStore = useUserStore()
const chatUnread = useChatUnreadStore()
let sseHandler: ((data: { count: number; partnerId: number }) => void) | null = null
let initHandler: (() => void) | null = null

function bindChatUnreadSse() {
  if (!userStore.token) return
  if (sseHandler) return // 已绑定，幂等
  const sse = getNotificationSSE()
  sseHandler = (data: { count: number; partnerId: number }) => {
    if (typeof data?.count !== 'number') return
    chatUnread.count = Math.max(0, data.count)
  }
  sse.on('chat-unread-count', sseHandler)
  // 进入 App 立即拉一次最新未读数（不等 30s 轮询）
  chatUnread.refresh().catch(() => {})
}

function unbindChatUnreadSse() {
  if (!sseHandler) return
  try {
    getNotificationSSE().off('chat-unread-count', sseHandler)
  } catch {
    // ignore
  }
  sseHandler = null
}

onMounted(() => {
  bindChatUnreadSse()
})

// 登出后释放订阅，避免下次登录残留
initHandler = () => unbindChatUnreadSse()
if (typeof window !== 'undefined') {
  window.addEventListener('lovemap:before-logout', initHandler)
}

onBeforeUnmount(() => {
  unbindChatUnreadSse()
  if (typeof window !== 'undefined' && initHandler) {
    window.removeEventListener('lovemap:before-logout', initHandler)
    initHandler = null
  }
})
</script>

<style scoped>
/* 右上角图标组：嵌入上边框、与 nav-bar 顶端对齐 */
.topright-bar {
  position: fixed;
  top: 0;
  right: 8px;
  display: flex;
  align-items: center;
  /* 让图标组视觉上嵌入 nav-bar 顶端，不浮在 nav-bar 下方 */
  height: calc(env(safe-area-inset-top, 0px) + 46px);
  padding-top: env(safe-area-inset-top, 0px);
  z-index: 1001; /* 高于 nav-bar (van-nav-bar 默认 1) 与 BottomTab (999) */
  gap: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
