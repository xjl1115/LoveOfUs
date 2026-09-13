<template>
  <div class="wishlist-page">
    <van-nav-bar title="心愿清单" left-arrow fixed placeholder @click-left="onBack">
      <template #right>
        <van-icon name="plus" size="20" @click="triggerCreate" />
      </template>
    </van-nav-bar>
    <WishlistContent ref="contentRef" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import WishlistContent from '@/components/WishlistContent.vue'

const router = useRouter()
const contentRef = ref<InstanceType<typeof WishlistContent> | null>(null)

// 顶栏 + 按钮：触发内容组件里的"新建心愿"弹窗
function triggerCreate() {
  // 内容组件的 form-popup 通过 showCreate 控制；通过抛事件或暴露方法触发
  // 简单方案：直接派发一个全局事件，WishlistContent 监听并打开
  window.dispatchEvent(new CustomEvent('wishlist:create'))
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}
</script>

<style scoped lang="scss">
.wishlist-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 24px;
}
</style>