import request from '@/utils/request'

export interface CheckInStatusVO {
  /** 当前可用积分 */
  points: number
  /** 历史累计获得 */
  totalPoints: number
  /** 当前连续签到天数 */
  continuousDays: number
  /** 累计签到天数 */
  totalCheckInDays: number
  /** 今天是否已签到 */
  todayCheckedIn: boolean
  /** 今天签到应得积分（已签到才有值） */
  todayScore: number
  /** 当前展示月份的第一天，YYYY-MM-DD */
  month: string
  /** 当月已签到的日期列表（yyyy-MM-dd） */
  checkedDates: string[]
  /** 当月每天签到获得的积分 map: yyyy-MM-dd -> 积分 */
  scoreByDate: Record<string, number>
}

export interface CheckInResultVO {
  /** 本次获得的积分 */
  score: number
  /** 连续签到天数 */
  continuousDays: number
  /** 当前总积分 */
  currentPoints: number
  /** 是否命中里程碑奖励 */
  isMilestone: boolean
}

/**
 * 获取签到状态与当月日历
 * @param month YYYY-MM 格式；不传则为当月
 */
export function getCheckInStatus(month?: string) {
  return request.get<CheckInStatusVO>('/points/check-in', { params: month ? { month } : undefined })
}

/** 执行签到（幂等：今天已签到则不重复加分） */
export function doCheckIn() {
  return request.post<CheckInResultVO>('/points/check-in')
}
