import request from '@/utils/request'

export interface CoupleTaskVO {
  id: number
  content: string
  description?: string
  icon?: string
  points: number
  status: number
  sortOrder: number
  /** 完成方式：manual/message_send/photo_upload/date_plan_create */
  completeType?: string
  /** 周期：daily/weekly/monthly */
  periodType?: string
  /** 1=需手动领取积分 0=完成即发放 */
  claimRequired?: number
  /** 当前周期键（如 2026-10-17） */
  periodKey?: string
  /** 0=未完成 1=已完成待领取 2=已领取 */
  taskStatus: number
  completed: boolean
  /** 是否可领取积分 */
  claimable: boolean
  completedAt?: string
  claimedAt?: string
  pointsAwarded?: number
}

/** 列出情侣任务（含当前周期的完成状态） */
export function listCoupleTasks() {
  return request.get<CoupleTaskVO[]>('/couple-tasks')
}

/** 领取任务积分（已完成待领取 → 已领取） */
export function claimCoupleTask(taskId: number) {
  return request.post<CoupleTaskVO>('/couple-tasks/claim', { taskId })
}
