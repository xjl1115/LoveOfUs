<template>
  <div class="makeover-progress">
    <van-nav-bar title="分析中" left-arrow fixed placeholder :left-arrow-disabled="stage === 'done'" @click-left="onBack" />

    <div class="progress-content">
      <div class="header">
        <van-icon name="like-o" size="22" color="#FF6B6B" />
        <span>AI 化妆建议分析中</span>
      </div>

      <MakeoverStageSteps :stage="stage" />

      <!-- 当前阶段文字提示 -->
      <div class="stage-tip" role="status" aria-live="polite">
        <template v-if="stage === 'pending'">
          <van-loading type="spinner" size="16" />
          <span>排队中，马上开始分析…</span>
        </template>
        <template v-else-if="stage === 'analyze'">
          <van-loading type="spinner" size="16" />
          <span>正在识别你的脸部特征…</span>
        </template>
        <template v-else-if="stage === 'edit'">
          <van-loading type="spinner" size="16" />
          <span>正在生成改造效果图…</span>
        </template>
        <template v-else-if="stage === 'failed'">
          <van-icon name="warning-o" color="#ee0a24" />
          <span>{{ errorMessage || '生成失败，可重试' }}</span>
        </template>
        <template v-else>
          <van-icon name="success" color="#07c160" />
          <span>已完成</span>
        </template>
      </div>

      <!-- 失败时的重试按钮 -->
      <div v-if="stage === 'failed'" class="retry-wrap">
        <van-button round type="primary" @click="retry">重新提交</van-button>
      </div>

      <!-- 兜底轮询提示 -->
      <div v-if="polling" class="poll-hint">网络不佳，已自动切换为轮询模式</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import MakeoverStageSteps from '@/components/makeover/MakeoverStageSteps.vue'
import {
  subscribeMakeoverProgress,
  getMakeoverDetail,
  MakeoverStatus,
  type MakeoverSseEvent
} from '@/api/makeover'

const route = useRoute()
const router = useRouter()

const recordId = Number(route.params.recordId)

type Stage = 'pending' | 'analyze' | 'edit' | 'done' | 'failed'
const stage = ref<Stage>('pending')
const errorMessage = ref('')
const polling = ref(false)

let cancelSse: (() => void) | null = null
let pollTimer: number | null = null

function onBack() {
  if (stage.value === 'done') return // 完成后禁用返回，避免重复触发跳转
  goBack()
}

function goBack() {
  // 任何状态下离开都清理订阅
  cleanup()
  if (window.history.length > 1) router.back()
  else router.push('/makeover')
}

function goResult() {
  cleanup()
  router.replace({ name: 'MakeoverResult', params: { recordId: String(recordId) } })
}

function retry() {
  cleanup()
  router.replace('/makeover')
}

function cleanup() {
  cancelSse?.()
  cancelSse = null
  stopPolling()
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function statusToStage(s: MakeoverStatus): Stage {
  switch (s) {
    case MakeoverStatus.PENDING:
      return 'pending'
    case MakeoverStatus.ANALYZING:
      return 'analyze'
    case MakeoverStatus.EDITING:
      return 'edit'
    case MakeoverStatus.DONE:
      return 'done'
    case MakeoverStatus.FAILED:
      return 'failed'
    default:
      return 'pending'
  }
}

/** 拉一次快照：处理「用户刷新页面」「从历史进入分析中记录」的情况 */
async function fetchSnapshot() {
  try {
    const detail = await getMakeoverDetail(recordId)
    const mapped = statusToStage(detail.status)
    if (mapped === 'done') {
      goResult()
      return
    }
    if (mapped === 'failed') {
      stage.value = 'failed'
      errorMessage.value = detail.errorMessage || '生成失败'
      return
    }
    stage.value = mapped
  } catch {
    // 快照失败：保留 pending，依赖 SSE / 轮询推进
  }
}

function startSseWithFallback() {
  cancelSse = subscribeMakeoverProgress(
    recordId,
    (e: MakeoverSseEvent) => {
      // 帧类型判定
      if ('stage' in e) {
        const status = e.status
        if (e.stage === 'analyze') {
          stage.value = status === 'done' ? 'edit' : 'analyze'
        } else if (e.stage === 'image_edit') {
          stage.value = status === 'done' ? 'edit' : 'edit'
        }
      } else if ('recordId' in e) {
        stage.value = 'done'
        goResult()
      } else if ('message' in e) {
        stage.value = 'failed'
        errorMessage.value = e.message
        showFailToast(e.message)
      }
    },
    (err) => {
      // 断连：仅记录日志，自动降级为轮询，不打扰用户
      console.warn('[Makeover SSE] 断开，降级轮询', err)
      startPolling()
    }
  )
}

function startPolling() {
  if (polling.value) return
  polling.value = true
  stopPolling()
  pollTimer = window.setInterval(async () => {
    try {
      const d = await getMakeoverDetail(recordId)
      const mapped = statusToStage(d.status)
      if (mapped === 'done') {
        goResult()
      } else if (mapped === 'failed') {
        stage.value = 'failed'
        errorMessage.value = d.errorMessage || '生成失败'
        showFailToast(errorMessage.value)
        stopPolling()
      } else {
        stage.value = mapped
      }
    } catch (e) {
      // 轮询失败：保留上一次状态，等下个 tick 继续
      console.warn('[Makeover] 轮询失败', e)
    }
  }, 1500)
}

onMounted(async () => {
  if (!recordId || Number.isNaN(recordId)) {
    showFailToast('记录不存在')
    router.replace('/makeover')
    return
  }
  await fetchSnapshot()
  startSseWithFallback()
})

onBeforeUnmount(() => {
  cleanup()
})
</script>

<style scoped lang="scss">
.makeover-progress {
  min-height: 100vh;
  background: $bg-color;
}
.progress-content {
  padding: 20px 16px;
}
.header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  font-size: 15px;
  color: $text-primary;
  font-weight: 600;
}
.stage-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 18px;
  font-size: 13px;
  color: $text-secondary;
  background: #fff;
  padding: 10px 14px;
  border-radius: $radius-md;
}
.retry-wrap {
  margin-top: 20px;
}
.poll-hint {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: $text-tertiary;
}
</style>