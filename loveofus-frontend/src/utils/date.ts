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
