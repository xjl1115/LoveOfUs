<template>
  <van-tabbar v-model="active" route class="bottom-tab safe-area-bottom">
    <van-tabbar-item to="/home" icon="home-o">首页</van-tabbar-item>
    <van-tabbar-item to="/albums" icon="photo-o">相册</van-tabbar-item>
    <van-tabbar-item to="/upload" icon="plus" class="upload-btn">上传</van-tabbar-item>
    <van-tabbar-item to="/export" icon="share-o">导出</van-tabbar-item>
    <van-tabbar-item to="/profile" icon="user-o">我的</van-tabbar-item>
  </van-tabbar>
  <SystemMessageFloatBtn />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SystemMessageFloatBtn from './SystemMessageFloatBtn.vue'

const route = useRoute()
const active = ref(0)

const routeMap: Record<string, number> = {
  '/home': 0,
  '/albums': 1,
  '/upload': 2,
  '/export': 3,
  '/profile': 4
}

watch(() => route.path, (path) => {
  active.value = routeMap[path] ?? 0
}, { immediate: true })
</script>

<style scoped lang="scss">
.bottom-tab {
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
  overflow: visible !important;
  z-index: 999;

  :deep(.van-tabbar-item--active) {
    color: $primary-color;
  }

  :deep(.van-tabbar-item) {
    overflow: visible;
  }

  :deep(.upload-btn) {
    overflow: visible;

    .van-tabbar-item__icon {
      background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
      color: #fff;
      width: 50px;
      height: 50px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 26px;
      margin-top: -16px;
      box-shadow: 0 4px 14px rgba($primary-color, 0.45);
      position: relative;
      z-index: 1000;
    }
  }
}
</style>
