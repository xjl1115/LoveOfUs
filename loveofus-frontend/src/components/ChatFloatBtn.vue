<template>
  <div
    v-if="visible"
    class="topbar-icon chat-icon"
    role="button"
    aria-label="私聊"
    @click="goChat"
  >
    <van-badge
      :content="unreadCount > 0 ? unreadCount : ''"
      :max="99"
      :show-zero="false"
      :offset="[-4, 2]"
    >
      <van-icon name="chat-o" size="22" color="#FF6B6B" />
    </van-badge>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatUnreadStore } from '@/stores/chatUnread'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const chatUnread = useChatUnreadStore()

/** 仅在首页显示（与 CameraFloatBtn 一致） */
const visible = computed(
  () => userStore.isLoggedIn && route.path === '/home'
)

/** 复用 App.vue 已维护的未读数（来自 chat-unread-count SSE） */
const unreadCount = computed(() => chatUnread.count)

function goChat() {
  router.push('/chat')
}
</script>

<style scoped lang="scss">
// 嵌入上边框的纯图标（无圆、无背景、无阴影）
.topbar-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  cursor: pointer;
  transition: opacity 0.15s;

  &:active {
    opacity: 0.55;
  }
}

:deep(.van-badge__wrapper) {
  display: flex;
  align-items: center;
  justify-content: center;
}
:deep(.van-badge__content) {
  font-size: 10px;
}
</style>