<template>
  <div class="checkin-calendar">
    <!-- 顶部信息 -->
    <div class="checkin-summary">
      <div class="summary-cell">
        <div class="cell-value">{{ status.continuousDays ?? 0 }}</div>
        <div class="cell-label">连续签到</div>
      </div>
      <div class="summary-cell">
        <div class="cell-value">{{ status.points ?? 0 }}</div>
        <div class="cell-label">可用积分</div>
      </div>
      <div class="summary-cell">
        <div class="cell-value">{{ status.totalCheckInDays ?? 0 }}</div>
        <div class="cell-label">累计天数</div>
      </div>
    </div>

    <!-- 签到按钮 -->
    <div class="checkin-btn-wrap">
      <van-button
        round
        block
        type="primary"
        size="large"
        :loading="checking"
        :disabled="status.todayCheckedIn"
        @click="onCheckIn"
      >
        {{ status.todayCheckedIn ? `今日已签到 · +${status.todayScore ?? 0} 积分` : '点击签到 · 今日 +1 积分起' }}
      </van-button>
      <div class="checkin-hint">
        连续 3 天 +10，7 天 +50，10 天 +100，20 天 +300，30 天 +900
      </div>
    </div>

    <!-- 月份切换 -->
    <div class="calendar-header">
      <van-icon name="arrow-left" class="nav-btn" @click="prevMonth" />
      <span class="month-label">{{ monthLabel }}</span>
      <van-icon name="arrow" class="nav-btn" @click="nextMonth" />
    </div>

    <!-- 日历本体 -->
    <div class="calendar-grid">
      <div v-for="w in weekHeaders" :key="w" class="week-header">{{ w }}</div>
      <div
        v-for="(day, idx) in cells"
        :key="idx"
        class="day-cell"
        :class="{
          empty: !day.date,
          checked: day.checked,
          today: day.isToday,
          future: day.isFuture,
          hasScore: day.score != null && day.score > 0
        }"
      >
        <template v-if="day.date">
          <div class="day-num">{{ day.day }}</div>
          <div v-if="day.checked" class="day-tag">✓</div>
          <div v-if="day.score != null && day.score > 0" class="day-score">+{{ day.score }}</div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { showToast } from 'vant'
import { getCheckInStatus, doCheckIn, type CheckInStatusVO } from '@/api/points'

const weekHeaders = ['日', '一', '二', '三', '四', '五', '六']

const status = ref<CheckInStatusVO>({
  points: 0,
  totalPoints: 0,
  continuousDays: 0,
  totalCheckInDays: 0,
  todayCheckedIn: false,
  todayScore: 0,
  month: '',
  checkedDates: [],
  scoreByDate: {}
})

const currentMonth = ref<string>('') // YYYY-MM
const checking = ref(false)

const monthLabel = computed(() => {
  if (!currentMonth.value) return ''
  const [y, m] = currentMonth.value.split('-')
  return `${y} 年 ${parseInt(m, 10)} 月`
})

interface DayCell {
  date?: string
  day?: number
  checked?: boolean
  isToday?: boolean
  isFuture?: boolean
  score?: number
}

const cells = computed<DayCell[]>(() => {
  if (!currentMonth.value) return []
  const [y, m] = currentMonth.value.split('-').map(Number)
  const firstDay = new Date(y, m - 1, 1)
  const lastDay = new Date(y, m, 0)
  const startWeekday = firstDay.getDay()
  const totalDays = lastDay.getDate()

  const today = new Date()
  const todayStr = formatYMD(today)
  const checkedSet = new Set(status.value.checkedDates || [])
  const scoreMap = status.value.scoreByDate || {}

  const arr: DayCell[] = []
  for (let i = 0; i < startWeekday; i++) {
    arr.push({})
  }
  for (let d = 1; d <= totalDays; d++) {
    const dt = new Date(y, m - 1, d)
    const ymd = formatYMD(dt)
    arr.push({
      date: ymd,
      day: d,
      checked: checkedSet.has(ymd),
      isToday: ymd === todayStr,
      isFuture: dt > today,
      score: scoreMap[ymd]
    })
  }
  return arr
})

function formatYMD(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function shiftMonth(delta: number) {
  if (!currentMonth.value) return
  const [y, m] = currentMonth.value.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  const ny = d.getFullYear()
  const nm = String(d.getMonth() + 1).padStart(2, '0')
  load(`${ny}-${nm}`)
}

function prevMonth() { shiftMonth(-1) }
function nextMonth() { shiftMonth(1) }

async function load(month?: string) {
  try {
    const data = await getCheckInStatus(month)
    status.value = data
    if (!currentMonth.value || month) {
      // 服务端返回 month 是当月首日；解析为 YYYY-MM
      const m = (data.month || '').slice(0, 7)
      if (m) currentMonth.value = m
    }
  } catch (e) {
    console.error('加载签到状态失败', e)
  }
}

async function onCheckIn() {
  if (status.value.todayCheckedIn) return
  checking.value = true
  try {
    const result = await doCheckIn()
    showToast({
      message: result.isMilestone ? `里程碑达成！+${result.score} 积分` : `签到成功 +${result.score}`,
      duration: 1800
    })
    await load(currentMonth.value)
    emit('changed')
  } catch (e: any) {
    showToast(e?.message || '签到失败')
  } finally {
    checking.value = false
  }
}

const emit = defineEmits<{ changed: [] }>()

// 不允许查看未来月份
watch(currentMonth, (v) => {
  if (!v) return
  const [y, m] = v.split('-').map(Number)
  const today = new Date()
  const cur = new Date(y, m - 1, 1)
  const thisMonth = new Date(today.getFullYear(), today.getMonth(), 1)
  if (cur > thisMonth) {
    currentMonth.value = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
  }
})

defineExpose({ load })
onMounted(() => load())
</script>

<style scoped lang="scss">
.checkin-calendar {
  background: #fff;
  border-radius: 14px;
  padding: 16px;
}

.checkin-summary {
  display: flex;
  background: #fff8f8;
  border-radius: 10px;
  padding: 12px 0;
  margin-bottom: 14px;

  .summary-cell {
    flex: 1;
    text-align: center;
    .cell-value {
      font-size: 20px;
      font-weight: 700;
      color: #ff6b6b;
    }
    .cell-label {
      font-size: 11px;
      color: #8a9099;
      margin-top: 2px;
    }
  }
}

.checkin-btn-wrap {
  margin-bottom: 14px;
  .checkin-hint {
    margin-top: 6px;
    font-size: 11px;
    color: #8a9099;
    text-align: center;
  }
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0 10px;
  .month-label {
    font-size: 15px;
    font-weight: 600;
    color: #23272f;
  }
  .nav-btn {
    color: #8a9099;
    padding: 6px;
    font-size: 18px;
  }
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.week-header {
  text-align: center;
  font-size: 11px;
  color: #8a9099;
  padding: 6px 0;
}

.day-cell {
  position: relative;
  aspect-ratio: 1;
  border-radius: 8px;
  background: #f7f8fa;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #23272f;

  &.empty {
    background: transparent;
  }
  &.checked {
    background: linear-gradient(135deg, #ffb1b1 0%, #ff7e7e 100%);
    color: #fff;
    .day-tag {
      position: absolute;
      top: 1px;
      right: 3px;
      font-size: 10px;
      opacity: 0.8;
    }
    .day-score {
      color: #fff;
    }
  }
  &.today:not(.checked) {
    border: 1.5px solid #ff6b6b;
  }
  &.future {
    color: #c8ccd3;
    background: #fafbfc;
  }
  .day-num {
    font-weight: 500;
  }
  .day-score {
    font-size: 9px;
    color: #ff6b6b;
    margin-top: 2px;
  }
}
</style>
