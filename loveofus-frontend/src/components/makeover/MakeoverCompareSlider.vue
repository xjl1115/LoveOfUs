<template>
  <div ref="rootRef" class="compare" :style="{ aspectRatio: ratio || defaultRatio }">
    <!-- Before 图（底层） -->
    <img class="layer base" :src="before" alt="改造前" draggable="false" />

    <!-- After 图（顶层，通过 clip-path 显示 percent +% 部分） -->
    <img
      class="layer top"
      :src="after"
      alt="改造后"
      draggable="false"
      :style="{ clipPath: `inset(0 0 0 ${percent}%)` }"
    />

    <!-- 滑动条手柄 -->
    <div class="handle" :style="{ left: percent + '%' }" @pointerdown="onDown">
      <div class="handle-line" />
      <div class="handle-knob">
        <van-icon name="exchange" size="14" color="#fff" />
      </div>
    </div>

    <!-- 角标 -->
    <div class="tag tag-left">Before</div>
    <div class="tag tag-right">After</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'

defineProps<{
  before: string
  after: string
  /** 容器宽高比，宽/高（默认 1/1） */
  ratio?: string
}>()

// 默认 1:1 比例（妆容改造图通常为方形输出）
const defaultRatio = '1 / 1'

const rootRef = ref<HTMLElement | null>(null)
const percent = ref(50)
let dragging = false

function setFromClientX(clientX: number) {
  const el = rootRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = Math.min(Math.max(clientX - rect.left, 0), rect.width)
  percent.value = Math.round((x / rect.width) * 100)
}

function onDown(e: PointerEvent) {
  dragging = true
  ;(e.target as HTMLElement).setPointerCapture?.(e.pointerId)
  setFromClientX(e.clientX)
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

function onMove(e: PointerEvent) {
  if (!dragging) return
  setFromClientX(e.clientX)
}

function onUp() {
  dragging = false
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerup', onUp)
}

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerup', onUp)
})
</script>

<style scoped lang="scss">
.compare {
  position: relative;
  width: 100%;
  background: #f0f0f0;
  border-radius: $radius-md;
  overflow: hidden;
  user-select: none;
  touch-action: none;
}
.layer.base,
.layer.top {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.handle {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  transform: translateX(-50%);
  cursor: ew-resize;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;

  .handle-line {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 2px;
    background: #fff;
    box-shadow: 0 0 4px rgba(0, 0, 0, 0.4);
  }
  .handle-knob {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: $primary-color;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
    transform: rotate(90deg);
  }
}
.tag {
  position: absolute;
  top: 10px;
  padding: 4px 10px;
  font-size: 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  letter-spacing: 0.5px;
  z-index: 2;
}
.tag-left {
  left: 10px;
}
.tag-right {
  right: 10px;
}
</style>