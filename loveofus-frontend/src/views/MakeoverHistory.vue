<template>
  <div class="makeover-history">
    <van-nav-bar title="历史记录" left-arrow fixed placeholder @click-left="onBack" />

    <div class="history-content">
      <van-loading v-if="loading && list.length === 0" class="loading" />

      <EmptyState
        v-else-if="!loading && list.length === 0"
        text="还没有化妆建议，去生成第一条 →"
      >
        <van-button round type="primary" size="small" @click="$router.push('/makeover')">
          去生成
        </van-button>
      </EmptyState>

      <van-list
        v-else
        v-model:loading="loading"
        :finished="finished"
        :finished-text="list.length > 0 ? '没有更多了' : ''"
        @load="onLoadMore"
      >
        <div
          v-for="item in list"
          :key="item.recordId"
          class="record-card"
          @click="goDetail(item.recordId)"
          @contextmenu.prevent="onLongPress(item)"
          @touchstart="onTouchStart(item, $event)"
          @touchmove="onTouchMove"
          @touchend="onTouchEnd"
          @touchcancel="onTouchEnd"
        >
          <div class="thumb">
            <img :src="item.afterUrl || item.originalUrl" alt="缩略图" />
            <van-icon v-if="!item.afterUrl" name="photo-o" class="thumb-fallback" />
          </div>
          <div class="meta">
            <div class="row-1">
              <span class="scene-name">{{ sceneName(item.sceneCode) }}</span>
              <van-tag :type="statusTag(item.status).type" plain>
                {{ statusTag(item.status).text }}
              </van-tag>
            </div>
            <div class="row-2">{{ formatTime(item.createdAt) }}</div>
          </div>
          <van-icon name="arrow" class="arrow" />
        </div>
      </van-list>
    </div>

    <MakeoverDeleteSheet v-model="sheet.show" @delete="onConfirmDelete" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import dayjs from 'dayjs'
import EmptyState from '@/components/EmptyState.vue'
import MakeoverDeleteSheet from '@/components/makeover/MakeoverDeleteSheet.vue'
import { SCENE_OPTIONS } from '@/constants/makeover'
import { listMakeover, deleteMakeover, MakeoverStatus, type MakeoverListVO } from '@/api/makeover'

const router = useRouter()

const list = ref<MakeoverListVO[]>([])
const loading = ref(false)
const finished = ref(false)
let page = 1
const pageSize = 10

const sheet = ref<{ show: boolean; recordId: number | null }>({
  show: false,
  recordId: null
})

// 长按检测：移动端 @longpress 事件在 van-list 嵌套时不稳定，使用 touch 计时器实现
let longPressTimer: number | null = null
let longPressFired = false
let touchStartX = 0
let touchStartY = 0

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

function sceneName(code: string) {
  return SCENE_OPTIONS.find((s) => s.code === code)?.name || code
}

function statusTag(s: MakeoverStatus) {
  switch (s) {
    case MakeoverStatus.PENDING:
      return { type: 'default' as const, text: '排队中' }
    case MakeoverStatus.ANALYZING:
      return { type: 'primary' as const, text: '分析中' }
    case MakeoverStatus.EDITING:
      return { type: 'primary' as const, text: '出图中' }
    case MakeoverStatus.DONE:
      return { type: 'success' as const, text: '已完成' }
    case MakeoverStatus.FAILED:
      return { type: 'danger' as const, text: '失败' }
    default:
      return { type: 'default' as const, text: '未知' }
  }
}

function formatTime(s: string) {
  return dayjs(s).format('YYYY-MM-DD HH:mm')
}

function goDetail(id: number) {
  // 长按触发的 click 直接吞掉，避免重复跳转
  if (longPressFired) {
    longPressFired = false
    return
  }
  // 如果是分析中/出图中，跳转到进度页
  const item = list.value.find((v) => v.recordId === id)
  if (
    item &&
    (item.status === MakeoverStatus.PENDING ||
      item.status === MakeoverStatus.ANALYZING ||
      item.status === MakeoverStatus.EDITING)
  ) {
    router.push({ name: 'MakeoverProgress', params: { recordId: String(id) } })
  } else {
    router.push({ name: 'MakeoverResult', params: { recordId: String(id) } })
  }
}

function onTouchStart(item: MakeoverListVO, e: TouchEvent) {
  longPressFired = false
  if (longPressTimer !== null) window.clearTimeout(longPressTimer)
  const t = e.touches[0]
  touchStartX = t?.clientX ?? 0
  touchStartY = t?.clientY ?? 0
  longPressTimer = window.setTimeout(() => {
    longPressFired = true
    longPressTimer = null
    onLongPress(item)
  }, 600)
}

function onTouchMove(e: TouchEvent) {
  // 手指移动超过 10px 视为滚动，取消长按
  const t = e.touches[0]
  if (!t) return
  if (Math.abs(t.clientX - touchStartX) > 10 || Math.abs(t.clientY - touchStartY) > 10) {
    if (longPressTimer !== null) {
      window.clearTimeout(longPressTimer)
      longPressTimer = null
    }
  }
}

function onTouchEnd() {
  if (longPressTimer !== null) {
    window.clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function onLongPress(item: MakeoverListVO) {
  sheet.value = { show: true, recordId: item.recordId }
}

async function onConfirmDelete() {
  const id = sheet.value.recordId
  if (!id) return
  try {
    await deleteMakeover(id)
    list.value = list.value.filter((v) => v.recordId !== id)
    showToast('已删除')
  } catch {
    // 拦截器已显示后端 message，无需再 toast
  } finally {
    sheet.value.recordId = null
  }
}

async function loadPage(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) {
      page = 1
      finished.value = false
    }
    const res = await listMakeover(page, pageSize)
    const data = res?.list || []
    if (reset) {
      list.value = data
    } else {
      list.value.push(...data)
    }
    if (data.length < pageSize) {
      finished.value = true
    } else {
      page++
    }
  } catch {
    finished.value = true
    // 拦截器已显示后端 message，无需再 toast
  } finally {
    loading.value = false
  }
}

function onLoadMore() {
  if (finished.value) return
  loadPage(false)
}

onMounted(() => loadPage(true))
</script>

<style scoped lang="scss">
.makeover-history {
  min-height: 100vh;
  background: $bg-color;
}
.history-content {
  padding: 12px 16px;
}
.loading {
  display: block;
  text-align: center;
  padding: 40px 0;
}
.record-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: $radius-md;
  padding: 12px;
  margin-bottom: 10px;
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: background 0.15s;
  &:active {
    background: $bg-color;
  }
}
.thumb {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: $radius-sm;
  overflow: hidden;
  flex-shrink: 0;
  background: $bg-color;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
  .thumb-fallback {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: $text-tertiary;
    background: rgba(255, 255, 255, 0.5);
  }
}
.meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.row-1 {
  display: flex;
  align-items: center;
  gap: 8px;
  .scene-name {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }
}
.row-2 {
  font-size: 12px;
  color: $text-tertiary;
}
.arrow {
  color: $text-tertiary;
  font-size: 16px;
  flex-shrink: 0;
}
</style>