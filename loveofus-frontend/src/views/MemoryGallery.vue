<template>
  <div class="memory-gallery">
    <van-nav-bar title="回忆展览" left-text="相册" left-arrow @click-left="router.push('/albums')" />

    <main class="gallery-content">
      <header class="gallery-heading">
        <p class="eyebrow">OUR LITTLE MOMENTS</p>
        <h1>把日常，收藏成风景</h1>
        <p class="subtitle">{{ total ? `共 ${total} 张照片，每一张都有我们的故事` : '在照片里，重逢那些心动的瞬间' }}</p>
      </header>

      <div class="view-switch" role="group" aria-label="浏览方式">
        <button :aria-pressed="mode === 'wall'" :class="{ active: mode === 'wall' }" @click="mode = 'wall'">照片墙</button>
        <button :aria-pressed="mode === 'arc'" :class="{ active: mode === 'arc' }" @click="mode = 'arc'">弧形浏览</button>
        <button :aria-pressed="mode === 'flip'" :class="{ active: mode === 'flip' }" @click="mode = 'flip'">立体翻阅</button>
      </div>

      <div v-if="photos.length && mode === 'wall'" class="photo-wall">
        <button
          v-for="(photo, index) in photos"
          :key="photo.id"
          class="photo-card wall-card"
          :style="{ '--tilt': `${(photo.id % 7) - 3}deg` }"
          :aria-label="`查看照片：${photo.description || photo.takenDate || '我们的回忆'}`"
          @click="openPhoto(index)"
        >
          <van-image :src="photo.storagePath" fit="cover" :alt="photo.description || '我们的回忆'" />
          <span class="photo-date">{{ photo.takenDate || '值得收藏的一天' }}</span>
          <span class="photo-caption">{{ photo.locationName || photo.city || photo.description || '平凡日子里的小美好' }}</span>
        </button>
      </div>

      <section v-if="photos.length && mode === 'arc'" class="arc-view" aria-label="弧形照片浏览">
        <p class="browse-hint">左右滑动切换 · 点击中间照片查看详情</p>
        <div
          class="arc-stage"
          @pointerdown="startSwipe"
          @pointermove="moveSwipe"
          @pointerup="endSwipe"
          @pointercancel="cancelSwipe"
          @keydown.left.prevent="step(-1)"
          @keydown.right.prevent="step(1)"
        >
          <button
              v-for="item in arcPhotos"
              :key="item.photo.id"
              class="photo-card arc-card"
              :class="{ selected: item.index === activeIndex }"
              :style="arcStyle(item.index)"
              :tabindex="item.index === activeIndex ? 0 : -1"
              :aria-label="item.index === activeIndex ? '查看当前照片详情' : '将这张照片移到中间'"
              @click="selectArcPhoto(item.index)"
              @dragstart.prevent
          >
            <van-image :src="item.photo.storagePath" fit="cover" :alt="item.photo.description || '我们的回忆'" />
            <span class="photo-date">{{ item.photo.takenDate || '值得收藏的一天' }}</span>
            <span class="photo-caption">{{ item.photo.locationName || item.photo.city || '我们的回忆' }}</span>
          </button>
        </div>
        <div class="arc-controls">
          <button aria-label="上一张照片" :disabled="activeIndex === 0" @click="step(-1)">←</button>
          <span aria-live="polite">{{ activeIndex + 1 }} / {{ photos.length }} <small>已加载</small></span>
          <button aria-label="下一张照片" :disabled="activeIndex === photos.length - 1" @click="step(1)">→</button>
        </div>
        <p v-if="isFlipAtEdge" class="current-caption">已经翻到回忆的这一页尽头</p>
        <p v-else class="current-caption">{{ photos[activeIndex]?.description || '有些瞬间，值得再看一遍。' }}</p>
      </section>

      <section v-if="photos.length && mode === 'flip'" class="flip-view" aria-label="立体翻阅">
        <p class="browse-hint">左右滑动翻牌 · 点击当前照片查看详情</p>
        <div
          class="flip-stage"
          @pointerdown="startSwipe"
          @pointermove="moveSwipe"
          @pointerup="endSwipe"
          @pointercancel="cancelSwipe"
          @keydown.left.prevent="step(-1)"
          @keydown.right.prevent="step(1)"
        >
          <button
            v-for="item in flipPhotos"
            :key="item.photo.id"
            class="photo-card flip-card"
            :class="[item.role, { 'is-flipping': flipping && item.role === 'current' }]"
            :style="flipStyle(item)"
            :tabindex="item.role === 'current' ? 0 : -1"
            :aria-label="item.role === 'current' ? '查看当前照片详情' : (item.role === 'prev' ? '翻到上一张' : '翻到下一张')"
            @click="selectFlipPhoto(item)"
            @dragstart.prevent
          >
            <div class="flip-face">
              <van-image :src="item.photo.storagePath" fit="cover" :alt="item.photo.description || '我们的回忆'" />
              <span class="photo-date">{{ item.photo.takenDate || '值得收藏的一天' }}</span>
              <span class="photo-caption">{{ item.photo.locationName || item.photo.city || '我们的回忆' }}</span>
            </div>
          </button>
        </div>
        <div class="arc-controls">
          <button aria-label="翻到上一张" :disabled="activeIndex === 0" @click="step(-1)">←</button>
          <span aria-live="polite">{{ activeIndex + 1 }} / {{ photos.length }} <small>立体翻阅</small></span>
          <button aria-label="翻到下一张" :disabled="activeIndex === photos.length - 1" @click="step(1)">→</button>
        </div>
        <p v-if="isFlipAtEdge" class="current-caption">已经翻到回忆的这一页尽头</p>
        <p v-else class="current-caption">{{ photos[activeIndex]?.description || '翻到下一张，继续我们的故事。' }}</p>
      </section>

      <div class="gallery-status" aria-live="polite">
        <van-loading v-if="loading" size="24px" vertical>正在翻开回忆…</van-loading>
        <template v-else-if="failed">
          <p>照片暂时加载失败，已加载的回忆仍可浏览</p>
          <van-button round size="small" @click="loadPhotos">重新加载</van-button>
        </template>
        <van-empty v-else-if="!photos.length" description="还没有照片，先记录我们的第一个瞬间吧">
          <van-button round type="primary" @click="router.push('/upload')">去上传照片</van-button>
        </van-empty>
        <van-button v-else-if="hasMore" round plain type="primary" @click="loadPhotos">加载更多回忆</van-button>
        <p v-else>已经翻到回忆的这一页尽头</p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getTimelinePhotos } from '@/api/photo'
import type { Photo } from '@/types'

const router = useRouter()
const mode = ref<'wall' | 'arc' | 'flip'>('wall')
const flipping = ref(false)
let flipTimer: number | null = null
type FlipRole = 'prev' | 'current' | 'next' | 'far'
const photos = ref<Photo[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const failed = ref(false)
const activeIndex = ref(0)
let scrollTop = 0
let swipe: { id: number; x: number; y: number } | null = null
let dragged = false

const arcPhotos = computed(() => photos.value
  .map((photo, index) => ({ photo, index }))
  .slice(Math.max(0, activeIndex.value - 3), activeIndex.value + 4))

const isFlipAtEdge = computed(() => photos.value.length > 0 && (activeIndex.value === 0 || activeIndex.value === photos.value.length - 1))

const flipPhotos = computed<{ photo: Photo; index: number; role: FlipRole }[]>(() => {
  const list: { photo: Photo; index: number; role: FlipRole }[] = []
  const cur = activeIndex.value
  const prev = photos.value[cur - 1]
  const next = photos.value[cur + 1]
  if (prev) list.push({ photo: prev, index: cur - 1, role: 'prev' })
  if (photos.value[cur]) list.push({ photo: photos.value[cur], index: cur, role: 'current' })
  if (next) list.push({ photo: next, index: cur + 1, role: 'next' })
  return list
})

async function loadPhotos() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  failed.value = false
  try {
    const result = await getTimelinePhotos({ page: page.value, size: 30 })
    const ids = new Set(photos.value.map(photo => photo.id))
    photos.value.push(...result.list.filter(photo => !ids.has(photo.id)))
    total.value = result.total
    hasMore.value = result.hasMore
    page.value += 1
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function openPhoto(index: number) {
  activeIndex.value = index
  router.push(`/photo/${photos.value[index].id}`)
}

function step(direction: number) {
  const target = activeIndex.value + direction
  const clamped = Math.max(0, Math.min(photos.value.length - 1, target))
  if (clamped === activeIndex.value || flipping.value) return
  if (mode.value === 'flip') {
    flipping.value = true
    if (flipTimer) { window.clearTimeout(flipTimer) }
    document.documentElement.style.setProperty('--flip-side', direction > 0 ? '1' : '-1')
    flipTimer = window.setTimeout(() => {
      activeIndex.value = clamped
      flipping.value = false
      flipTimer = null
    }, 380)
  } else {
    activeIndex.value = clamped
  }
}

function selectArcPhoto(index: number) {
  if (dragged) return
  if (index === activeIndex.value) openPhoto(index)
  else activeIndex.value = index
}

function selectFlipPhoto(item: { index: number; role: FlipRole }) {
  if (dragged || flipping.value) return
  if (item.role === 'current') openPhoto(item.index)
  else step(item.index > activeIndex.value ? 1 : -1)
}

function flipStyle(item: { index: number; role: FlipRole }): Record<string, string> {
  if (item.role === 'current') {
    return { zIndex: '30' }
  }
  if (item.role === 'next') {
    return { zIndex: '20', '--flip-side': '1' }
  }
  return { zIndex: '10', '--flip-side': '-1' }
}

function arcStyle(index: number) {
  const offset = index - activeIndex.value
  return {
    '--offset': offset,
    '--drop': `${offset * offset * 16}px`,
    '--angle': `${offset * 12}deg`,
    '--scale': 1 - Math.abs(offset) * 0.12,
    zIndex: 4 - Math.abs(offset)
  }
}

function startSwipe(event: PointerEvent) {
  if (!event.isPrimary || event.button !== 0) return
  swipe = { id: event.pointerId, x: event.clientX, y: event.clientY }
  dragged = false
  // 在原目标捕获指针，保留点击事件向照片按钮冒泡，同时接收区域外的滑动结束。
  ;(event.target as HTMLElement).setPointerCapture(event.pointerId)
}

function moveSwipe(event: PointerEvent) {
  if (swipe?.id !== event.pointerId) return
  if (Math.abs(event.clientX - swipe.x) > 8 || Math.abs(event.clientY - swipe.y) > 8) dragged = true
}

function endSwipe(event: PointerEvent) {
  if (swipe?.id !== event.pointerId) return
  const dx = event.clientX - swipe.x
  const dy = event.clientY - swipe.y
  dragged = dragged || Math.abs(dx) > 8 || Math.abs(dy) > 8
  if (Math.abs(dx) > 35 && Math.abs(dx) > Math.abs(dy)) step(dx < 0 ? 1 : -1)
  swipe = null
}

function cancelSwipe() {
  swipe = null
  dragged = true
}

onMounted(loadPhotos)
onDeactivated(() => {
  scrollTop = window.scrollY
  cancelSwipe()
  if (flipTimer) { window.clearTimeout(flipTimer); flipTimer = null }
  flipping.value = false
})
onActivated(() => {
  requestAnimationFrame(() => window.scrollTo(0, scrollTop))
})
</script>

<style scoped lang="scss">
.flip-view { text-align: center; }
.flip-stage {
  position: relative;
  height: 480px;
  margin: 0 -18px;
  perspective: 1400px;
  perspective-origin: 50% 45%;
  overflow: hidden;
  touch-action: pan-y;
  user-select: none;
}

.flip-card {
  position: absolute;
  top: 50%; left: 50%;
  width: min(72vw, 280px);
  cursor: pointer;
  transform-style: preserve-3d;
  transform-origin: center center;
  transition: transform 0.45s cubic-bezier(0.4, 0, 0.2, 1), filter 0.45s, box-shadow 0.45s, opacity 0.45s;
  backface-visibility: hidden;
  will-change: transform;

  .flip-face {
    display: flex;
    flex-direction: column;
    padding: 8px 8px 14px;
    background: #fffdf9;
    box-shadow: 0 6px 18px #604a3320;
  }
  :deep(.van-image) { display: block; width: 100%; aspect-ratio: 4 / 5; overflow: hidden; background: #eee7dd; }
  .photo-date { margin: 12px 4px 5px; font-size: 13px; }
  .photo-caption { margin: 0 4px; color: #a18f7e; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  &:focus-visible { outline: 2px solid #aa7552; outline-offset: 6px; }
  :deep(*) { touch-action: pan-y; }
}

.flip-card.prev {
  transform: translate(-50%, -50%) translateX(calc(-1 * min(28vw, 110px))) rotateY(40deg) translateZ(-140px) rotateX(2deg);
  filter: brightness(0.7) saturate(0.85);
  z-index: 10;
  pointer-events: auto;
}

.flip-card.current {
  transform: translate(-50%, -50%) rotateY(0deg) rotateX(2deg) translateZ(0);
  box-shadow: 0 28px 60px #604a3340;
  z-index: 30;
}

.flip-card.current.is-flipping {
  transform: translate(-50%, -50%) rotateY(calc(-180deg * var(--flip-side, 0))) rotateX(2deg) translateZ(0);
  box-shadow: 0 12px 30px #604a3320;
}

.flip-card.next {
  transform: translate(-50%, -50%) translateX(min(28vw, 110px)) rotateY(-40deg) translateZ(-140px) rotateX(2deg);
  filter: brightness(0.7) saturate(0.85);
  z-index: 20;
  pointer-events: auto;
}

@media (prefers-reduced-motion: reduce) {
  .flip-card { transition: none; }
}

.memory-gallery {
  min-height: 100vh;
  background: #f7f3ed;
  color: #554639;
}

.gallery-content {
  max-width: 960px;
  margin: 0 auto;
  padding: 30px 18px calc(36px + env(safe-area-inset-bottom));
}

.gallery-heading {
  text-align: center;

  .eyebrow { font-size: 10px; letter-spacing: 3px; color: #a1846d; }
  h1 { margin: 12px 0; font-size: 23px; font-weight: 500; }
  .subtitle { color: #958575; font-size: 12px; line-height: 1.8; }
}

.view-switch {
  display: flex;
  width: fit-content;
  margin: 24px auto 30px;
  padding: 4px;
  border: 1px solid #e8dfd3;
  border-radius: 28px;
  background: #eee7dd;

  button {
    border: 0;
    padding: 10px 22px;
    border-radius: 24px;
    background: transparent;
    font: inherit;
    font-size: 13px;
    color: #8c7968;
    cursor: pointer;

    &.active { background: #fffdf9; color: #684d39; box-shadow: 0 2px 8px #76523512; }
  }
}

.photo-wall {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 26px 16px;
  padding: 10px 4px 20px;
  align-items: start;
}

.photo-card {
  min-width: 0;
  border: 0;
  padding: 8px 8px 14px;
  background: #fffdf9;
  color: inherit;
  box-shadow: 0 6px 18px #604a3320;
  text-align: left;
  font: inherit;
  cursor: pointer;

  :deep(.van-image) { display: block; width: 100%; aspect-ratio: 4 / 5; overflow: hidden; background: #eee7dd; }
  .photo-date { display: block; margin: 12px 4px 5px; font-size: 12px; }
  .photo-caption { display: block; margin: 0 4px; color: #a18f7e; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  &:focus-visible { outline: 2px solid #aa7552; outline-offset: 4px; }
}

.wall-card {
  transform: rotate(var(--tilt));
  transition: transform 0.2s;
  &:nth-child(even) { margin-top: 20px; }
  &:hover, &:focus-visible { transform: rotate(0deg) translateY(-4px); }
}

.arc-view { text-align: center; }
.browse-hint { color: #a18f7e; font-size: 12px; }
.arc-stage {
  position: relative;
  height: 420px;
  margin: 0 -18px;
  overflow: hidden;
  touch-action: pan-y;
  user-select: none;
}

.arc-card {
  position: absolute;
  top: 42px;
  left: 50%;
  width: min(54vw, 220px);
  transform-origin: center 150%;
  transform: translateX(calc(-50% + var(--offset) * min(29vw, 150px))) translateY(var(--drop)) rotate(var(--angle)) scale(var(--scale));
  transition: transform 0.35s ease, box-shadow 0.35s;
  touch-action: pan-y;

  :deep(*) { touch-action: pan-y; }
  &.selected { box-shadow: 0 16px 36px #604a3330; }
}

.arc-controls {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24px;
  font-size: 14px;

  button { width: 40px; height: 40px; border: 1px solid #dacdbd; border-radius: 50%; color: #735940; background: #fffdf9; font-size: 20px; cursor: pointer; }
  button:disabled { opacity: 0.35; cursor: default; }
  small { color: #a18f7e; font-size: 10px; }
}

.current-caption { min-height: 24px; margin: 20px 12px 0; font-size: 13px; line-height: 1.8; overflow-wrap: anywhere; }
.gallery-status {
  padding: 30px 0 12px;
  text-align: center;
  color: #a18f7e;
  font-size: 12px;
  p { margin-bottom: 14px; }
}

@media (min-width: 768px) {
  .photo-wall { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 32px 24px; }
}

@media (prefers-reduced-motion: reduce) {
  .photo-card { transition: none; }
}
</style>
