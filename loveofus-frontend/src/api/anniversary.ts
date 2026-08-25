import request from '@/utils/request'

/**
 * 纪念日数据类型
 */
export interface Anniversary {
  id: number
  name: string
  anniversaryDate: string
  isRecurring: boolean
  remindDays: number
  description: string
  daysUntil?: number
  createdAt: string
  updatedAt: string
}

/**
 * 创建/更新纪念日请求参数
 */
export interface AnniversaryParams {
  name: string
  anniversaryDate: string
  isRecurring?: boolean
  remindDays?: number
  description?: string
}

/**
 * 获取纪念日列表
 */
export function getAnniversaryList() {
  return request.get<Anniversary[]>('/anniversaries')
}

/**
 * 获取纪念日详情
 * @param id 纪念日ID
 */
export function getAnniversary(id: number) {
  return request.get<Anniversary>(`/anniversaries/${id}`)
}

/**
 * 创建纪念日
 * @param data 纪念日数据
 */
export function createAnniversary(data: AnniversaryParams) {
  return request.post<Anniversary>('/anniversaries', data)
}

/**
 * 更新纪念日
 * @param id 纪念日ID
 * @param data 纪念日数据
 */
export function updateAnniversary(id: number, data: AnniversaryParams) {
  return request.put<Anniversary>(`/anniversaries/${id}`, data)
}

/**
 * 删除纪念日
 * @param id 纪念日ID
 */
export function deleteAnniversary(id: number) {
  return request.delete<void>(`/anniversaries/${id}`)
}
