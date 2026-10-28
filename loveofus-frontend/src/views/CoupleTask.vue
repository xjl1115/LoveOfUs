<template>
  <div class="couple-task-page">
    <van-nav-bar title="情侣任务" left-arrow fixed placeholder @click-left="onBack" />

    <div class="task-hero">
      <div class="hero-emoji">💑</div>
      <div class="hero-text">
        <div class="hero-title">和 TA 一起完成任务</div>
        <div class="hero-sub">任务由系统自动识别完成，完成后记得回来领积分</div>
      </div>
    </div>

    <div class="task-list" v-if="!loading">
      <div
        v-for="t in tasks"
        :key="t.id"
        class="task-card"
        :class="{ done: t.completed }"
      >
        <div class="task-icon">{{ t.icon || '🌷' }}</div>
        <div class="task-main">
          <div class="task-content-row">
            <span class="task-content">{{ t.content }}</span>
            <span v-if="periodText(t)" class="period-tag">{{ periodText(t) }}</span>
          </div>
          <div v-if="t.description" class="task-desc">{{ t.description }}</div>
          <div class="task-hint">{{ completeHint(t) }}</div>
          <div v-if="t.completed" class="task-by">
            <van-icon name="checked" />
            <span>{{ completedText(t) }}</span>
          </div>
        </div>
        <div class="task-action">
          <div class="task-points">+{{ t.points }}</div>
          <van-button
            v-if="t.claimable"
            size="small"
            round
            type="primary"
            :loading="claimingId === t.id"
            @click="onClaim(t)"
          >
            领取积分
          </van-button>
          <span v-else-if="t.completed" class="status-tag done">已完成</span>
          <span v-else class="status-tag todo">未完成</span>
        </div>
      </div>
      <EmptyState v-if="tasks.length === 0" text="暂无可领取的任务，敬请期待" />
    </div>
    <div v-else class="loading-block"><van-loading size="24" /></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import EmptyState from '@/components/EmptyState.vue'
import { listCoupleTasks, claimCoupleTask, type CoupleTaskVO } from '@/api/coupleTask'

const router = useRouter()
const tasks = ref<CoupleTaskVO[]>([])
const loading = ref(false)
const claimingId = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    tasks.value = (await listCoupleTasks()) || []
  } catch (e) {
    console.error('加载任务失败', e)
  } finally {
    loading.value = false
  }
}

/** 系统自动识别的完成方式说明，让用户知道怎么做才会被判定完成 */
function completeHint(t: CoupleTaskVO) {
  switch (t.completeType) {
    case 'message_send':
      return '系统自动识别：给 TA 发送一条消息即完成'
    case 'photo_upload':
      return '系统自动识别：上传一张照片即完成'
    case 'date_plan_create':
      return '系统自动识别：新建一个约会计划即完成'
    default:
      return '完成后由系统自动标记'
  }
}

function periodText(t: CoupleTaskVO) {
  if (t.periodType === 'weekly') return '每周'
  if (t.periodType === 'monthly') return '每月'
  return ''
}

function completedText(t: CoupleTaskVO) {
  if (t.taskStatus === 2) {
    return `已领取 +${t.pointsAwarded || t.points} 积分`
  }
  return '已完成，待领取积分'
}

async function onClaim(t: CoupleTaskVO) {
  claimingId.value = t.id
  try {
    await claimCoupleTask(t.id)
    showToast(`+${t.points} 积分`)
    await load()
  } catch (e: any) {
    showToast(e?.message || '领取失败')
  } finally {
    claimingId.value = null
  }
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

onMounted(load)
</script>

<style scoped lang="scss">
.couple-task-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 32px;
}

.task-hero {
  margin: 12px 16px;
  padding: 18px 20px;
  background: linear-gradient(135deg, #fda4af 0%, #fb7185 100%);
  border-radius: 14px;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 14px;

  .hero-emoji { font-size: 38px; }
  .hero-title { font-size: 17px; font-weight: 600; }
  .hero-sub { font-size: 12px; opacity: 0.9; margin-top: 4px; }
}

.task-list {
  padding: 0 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  align-items: flex-start;
  gap: 12px;

  &.done {
    background: #f8fafc;
    .task-content { color: #6b7280; }
  }
}

.task-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #fff5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-content-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.task-content {
  font-size: 15px;
  color: #23272f;
  font-weight: 500;
}

.period-tag {
  flex-shrink: 0;
  font-size: 10px;
  color: #fb7185;
  background: #fff1f2;
  border-radius: 8px;
  padding: 1px 6px;
}

.task-desc {
  font-size: 12px;
  color: #8a9099;
  margin-top: 4px;
  line-height: 1.5;
}

.task-hint {
  font-size: 11px;
  color: #b8bdc4;
  margin-top: 4px;
  line-height: 1.5;
}

.task-by {
  font-size: 12px;
  color: #159a52;
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.task-action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.task-points {
  color: #ff6b6b;
  font-size: 16px;
  font-weight: 700;
}

.status-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 9px;
  white-space: nowrap;

  &.done {
    color: #159a52;
    background: #eefaf0;
  }
  &.todo {
    color: #8a9099;
    background: #f2f4f7;
  }
}

.loading-block {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}
</style>
