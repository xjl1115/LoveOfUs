<template>
  <div class="scene-picker">
    <van-grid :column-num="3" :gutter="10" clickable>
      <van-grid-item
        v-for="s in SCENE_OPTIONS"
        :key="s.code"
        :class="['scene-item', { active: modelValue === s.code }]"
        @click="onPick(s.code)"
      >
        <div class="scene-card">
          <!-- 选中态顶部高亮条 -->
          <div class="scene-bar" />

          <!-- 选中态右上角 check 图标 -->
          <transition name="check">
            <div v-if="modelValue === s.code" class="scene-check" aria-hidden="true">
              <van-icon name="success" size="14" color="#fff" />
            </div>
          </transition>

          <!-- emoji 圆形渐变徽章 -->
          <div class="scene-emoji">
            <span class="emoji">{{ s.emoji }}</span>
          </div>

          <!-- 文案 -->
          <div class="scene-name">{{ s.name }}</div>
          <div class="scene-desc">{{ s.desc }}</div>
        </div>
      </van-grid-item>
    </van-grid>
  </div>
</template>

<script setup lang="ts">
import { SCENE_OPTIONS } from '@/constants/makeover'
import type { SceneCode } from '@/api/makeover'

const props = defineProps<{
  modelValue: SceneCode
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: SceneCode): void
}>()

function onPick(code: SceneCode) {
  if (code !== props.modelValue) emit('update:modelValue', code)
}
</script>

<style scoped lang="scss">
.scene-picker {
  width: 100%;
}

.scene-item {
  // 覆盖 Vant grid-item 默认 padding，让卡片铺满格子
  :deep(.van-grid-item__content) {
    padding: 0;
    background: transparent;
    display: block;
  }
  :deep(.van-grid-item__icon),
  :deep(.van-grid-item__text) {
    display: none;
  }
}

.scene-card {
  position: relative;
  width: 100%;
  padding: 14px 8px 10px;
  border-radius: $radius-md;
  background: #fff;
  border: 1.5px solid transparent;
  text-align: center;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.22s ease, border-color 0.22s ease,
    box-shadow 0.22s ease, background 0.22s ease;
  will-change: transform;

  &:active {
    transform: scale(0.97);
  }
}

// 顶部高亮条（未选中态透明，选中态显示玫红渐变）
.scene-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #ff6b9d 0%, #ff8a8a 50%, #ffb199 100%);
  opacity: 0;
  transform: scaleX(0.4);
  transform-origin: center;
  transition: opacity 0.25s ease, transform 0.3s ease;
}

// 选中态右上角 check 小圆标
.scene-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: $primary-color;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba($primary-color, 0.4);
  z-index: 2;
}

// emoji 圆形渐变徽章
.scene-emoji {
  width: 48px;
  height: 48px;
  margin: 0 auto 6px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fff5f7 0%, #ffe1e8 100%);
  font-size: 24px;
  line-height: 1;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1),
    background 0.22s ease;
  .emoji {
    // emoji 走系统字体，filter 微调饱和度让它在浅底上更突出
    filter: saturate(1.15);
    line-height: 1;
  }
}

// 文案
.scene-name {
  font-size: 13px;
  color: $text-primary;
  line-height: 1.3;
  font-weight: 500;
  transition: color 0.22s ease;
}
.scene-desc {
  font-size: 11px;
  color: $text-tertiary;
  line-height: 1.3;
  margin-top: 3px;
  transition: color 0.22s ease;
}

// ==================== 选中态 ====================
.scene-item.active .scene-card {
  background: linear-gradient(135deg, #fff0f3 0%, #fff5f0 100%);
  border-color: $primary-color;
  box-shadow: 0 6px 18px rgba($primary-color, 0.22),
    0 2px 6px rgba($primary-color, 0.12);
  transform: translateY(-2px);

  // 顶部高亮条展开
  .scene-bar {
    opacity: 1;
    transform: scaleX(1);
  }
  // emoji 徽章变化 + 弹性放大
  .scene-emoji {
    background: linear-gradient(135deg, $primary-color 0%, #ff8a8a 100%);
    transform: scale(1.08) rotate(-6deg);
    box-shadow: 0 4px 10px rgba($primary-color, 0.35);
    .emoji {
      // emoji 在深底上保持清晰
      filter: brightness(1.05) saturate(1.15);
    }
  }
  .scene-name {
    color: $primary-color;
    font-weight: 600;
  }
}

// ==================== check 图标进出场 ====================
.check-enter-active,
.check-leave-active {
  transition: all 0.2s ease;
}
.check-enter-from,
.check-leave-to {
  opacity: 0;
  transform: scale(0.4);
}
</style>