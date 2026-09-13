import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

export function formatDate(date: string | Date | undefined | null, format = 'YYYY-MM-DD'): string {
  if (!date) return ''
  const d = dayjs(date)
  return d.isValid() ? d.format(format) : ''
}

export function formatDateTime(date: string | Date | undefined | null): string {
  if (!date) return ''
  const d = dayjs(date)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : ''
}

export function formatMonthLabel(month: string): string {
  if (!month) return ''
  const d = dayjs(month)
  return d.isValid() ? d.format('YYYY年M月') : month
}

export function getDaysTogether(startDate: string): number {
  if (!startDate) return 0
  const start = dayjs(startDate)
  if (!start.isValid()) return 0
  const now = dayjs()
  return now.diff(start, 'day')
}

export function getRelativeTime(date: string | Date | undefined | null): string {
  if (!date) return ''
  const d = dayjs(date)
  return d.isValid() ? d.fromNow() : ''
}

/**
 * 每天 0 点（本地时区）触发一次回调，用于跨天后刷新按天计算的数据（如纪念日倒计时）。
 * 返回取消函数。
 */
export function scheduleDailyRefresh(callback: () => void): () => void {
  let timer: number | null = null

  const arm = () => {
    // 0:00:05 触发，避开与后端 0 点缓存过期/定时任务撞在同一瞬间
    const delay = dayjs().add(1, 'day').startOf('day').add(5, 'second').diff(dayjs())
    timer = window.setTimeout(() => {
      callback()
      arm()
    }, delay)
  }

  arm()
  return () => {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
  }
}
