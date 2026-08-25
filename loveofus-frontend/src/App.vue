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
</template>

<script setup lang="ts">
import { ref } from 'vue'
/**
 * 全局用户活跃度追踪
 */
import { useActive } from '@/composables/useActive'
useActive()

// 用于全局注册 van-dialog，供 showDialog / showConfirmDialog 使用
const dummyDialogVisible = ref(false)

// 需要缓存的页面（用户频繁往返的页面）
const cachedViews = ref(['Home', 'Albums', 'Profile'])
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
