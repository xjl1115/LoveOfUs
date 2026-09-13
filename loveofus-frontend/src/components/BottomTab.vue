<template>
  <van-tabbar v-model="active" route class="bottom-tab safe-area-bottom">
    <van-tabbar-item to="/home" icon="home-o">首页</van-tabbar-item>
    <van-tabbar-item to="/albums" icon="photo-o">相册</van-tabbar-item>
    <van-tabbar-item to="/makeover" icon="like-o" class="makeover-btn">化妆建议</van-tabbar-item>
    <van-tabbar-item to="/upload" icon="plus" class="upload-btn">上传</van-tabbar-item>
    <van-tabbar-item to="/love-hub" icon="like-o" class="love-btn">心动</van-tabbar-item>
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

// 7 个 Tab（中间上传按钮两侧：化妆建议 ↔ 心动，左右对称）
//   原「私聊」入口已移至全局右上角浮按钮 ChatFloatBtn
//   AI化妆建议 = 新模块入口，与「心动」语义接近（让感情升温）放上传按钮左侧
const routeMap: Record<string, number> = {
  '/home': 0,
  '/albums': 1,
  '/makeover': 2,        // AI 化妆建议（原私聊位置）
  '/makeover/progress': 2,
  '/makeover/result': 2,
  '/makeover/history': 2,
  '/upload': 3,
  '/love-hub': 4,        // 心动：与化妆建议对称
  '/date-plan': 4,       // 约会页 → 心动
  '/wishlist': 4,        // 心愿页 → 心动
  '/export': 5,
  '/profile': 6
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

  // 中间圆形按钮：上传
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

  // 左右两侧凸起：化妆建议 / 心动 —— 高度严格一致（40 圆 + -10px 上移）
  :deep(.makeover-btn),
  :deep(.love-btn) {
    overflow: visible;

    // 覆盖 Vant 默认的 icon 容器样式，统一两边的盒模型
    .van-tabbar-item__icon {
      // 重置 Vant 默认 padding/svg 约束，确保自定义几何生效
      width: 40px !important;
      height: 40px !important;
      min-width: 40px;
      min-height: 40px;
      border-radius: 50%;
      display: flex !important;
      align-items: center !important;
      justify-content: center !important;
      margin-top: -10px;
      margin-bottom: 4px;
      padding: 0 !important;
      box-shadow: 0 3px 10px rgba(0, 0, 0, 0.18);
      transition: transform 0.2s;
      position: relative;
    }

    // 显式锁死内部 svg 尺寸，让两边视觉一致
    .van-tabbar-item__icon .van-icon {
      font-size: 22px !important;
      line-height: 1;
    }
  }

  // 化妆建议：玫红渐变（与「心动」同色系但略偏紫，区分模块）
  :deep(.makeover-btn) {
    .van-tabbar-item__icon {
      background: linear-gradient(135deg, #ff6b9d 0%, #ff8a8a 100%);
      color: #fff;
      box-shadow: 0 3px 10px rgba(255, 107, 157, 0.45);
    }

    &.van-tabbar-item--active .van-tabbar-item__icon {
      background: linear-gradient(135deg, #ffc1d6 0%, #ffd9d9 100%);
    }
  }

  // 心动：粉红渐变（让感情升温）
  :deep(.love-btn) {
    .van-tabbar-item__icon {
      background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
      color: #fff;
      box-shadow: 0 3px 10px rgba($primary-color, 0.45);
    }

    &.van-tabbar-item--active .van-tabbar-item__icon {
      background: linear-gradient(135deg, #ffc1c1 0%, #ffd9d9 100%);
    }
  }

  // 锁定 Vant tab 文字块高度，与自定义 tab-label 一致
  :deep(.van-tabbar-item__text) {
    font-size: 11px !important;
    line-height: 14px !important;
  }
}
</style>