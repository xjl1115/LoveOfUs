<template>
  <div
    v-if="visible"
    class="topbar-icon camera-icon"
    role="button"
    aria-label="上传图片"
    @click="goUpload"
  >
    <van-icon name="photograph" size="22" color="#FF6B6B" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 仅在首页显示（与 ChatFloatBtn 一致） */
const visible = computed(
  () => userStore.isLoggedIn && route.path === '/home'
)

function goUpload() {
  router.push('/upload')
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
</style>