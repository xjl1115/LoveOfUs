import request from '@/utils/request'
import dayjs from 'dayjs'

/**
 * 约会策划数据类型（与后端 DatePlanVO 对齐）
 */
export type DatePlanTimeSlot = 'morning' | 'noon' | 'afternoon' | 'evening' | 'night'
export type DatePlanBudget = 'free' | 'low' | 'mid' | 'high' | 'luxury'

/** 状态：0-计划中，1-已完成，2-已取消 */
export type DatePlanStatus = 0 | 1 | 2

export interface DatePlan {
  id: number | string
  title: string
  /** 后端 VO 用 @JsonProperty("date") 返回 */
  date: string
  timeSlot: DatePlanTimeSlot
  scenes: string[]
  locationSuggestion: string
  /** 用户填写的地点（后端 location） */
  location: string
  /** 用户填写的备注（后端 description） */
  remark: string
  budget: DatePlanBudget
  reason: string
  tips: string[]
  /** 已完成约会时上传的照片 URL（后端 photos） */
  photos: string[]
  /** 0-计划中，1-已完成，2-已取消 */
  status: DatePlanStatus
  createdAt: string
}

/**
 * AI 推荐请求参数
 */
export interface DatePlanRecommendParams {
  /** 日期范围: today / weekend / nextWeekend / custom */
  range: 'today' | 'weekend' | 'nextWeekend' | 'custom'
  /** 自定义起始日期 YYYY-MM-DD（range=custom 时必填） */
  startDate?: string
  /** 自定义结束日期 YYYY-MM-DD（range=custom 时必填） */
  endDate?: string
  /** 场景偏好 */
  scenes: string[]
  /** 人均预算 */
  budget: 'free' | 'low' | 'mid' | 'high' | 'luxury'
  /** 地点（可为空） */
  location?: string
  /** 备注（可为空） */
  remark?: string
}

/**
 * AI 推荐响应（一次返回 3 套方案）
 */
export interface DatePlanRecommendResult {
  plans: DatePlan[]
}

/**
 * 调用 AI 推荐约会计划
 * POST /api/date-plans/recommend
 */
export async function recommendDatePlans(
  params: DatePlanRecommendParams
): Promise<DatePlanRecommendResult> {
  const prompt = buildRecommendPrompt(params)
  const res = await request.post<{ content: string }>('/date-plans/recommend', { prompt })
  return parseRecommendContent(res.content)
}

/**
 * 档期枚举 → 提示词中的具体日期文案
 * <p>
 * 直接算好真实日期再交给模型，避免把 weekend 这类内部枚举丢给模型后它凭训练数据编年份。
 */
function buildRangeText(
  range: DatePlanRecommendParams['range'],
  startDate?: string,
  endDate?: string
): string {
  if (range === 'custom') {
    return startDate && endDate ? `${startDate} 至 ${endDate}` : '自定义日期'
  }
  const today = dayjs()
  if (range === 'today') {
    return `今天（${today.format('YYYY-MM-DD')}）`
  }
  // 以周一为一周起点，算出本周六/周日；下周末再顺延 7 天
  const monday = today.subtract((today.day() + 6) % 7, 'day')
  const offset = range === 'nextWeekend' ? 7 : 0
  const saturday = monday.add(5 + offset, 'day')
  const sunday = monday.add(6 + offset, 'day')
  const label = range === 'nextWeekend' ? '下周末' : '本周末'
  return `${label}（${saturday.format('YYYY-MM-DD')} 至 ${sunday.format('YYYY-MM-DD')}）`
}

function buildRecommendPrompt(params: DatePlanRecommendParams): string {
  const sceneText = params.scenes.length > 0 ? params.scenes.join('、') : '不限'
  const dateText = buildRangeText(params.range, params.startDate, params.endDate)
  return `请为一对情侣推荐 3 个约会计划。要求：
- 时间：${dateText}
- 场景偏好：${sceneText}
- 预算：${params.budget}
- 地点：${params.location || '不限'}
- 备注：${params.remark || '无'}
每个计划包含：title（标题）、date（日期 YYYY-MM-DD）、timeSlot（时段，取 morning/noon/afternoon/evening/night 之一）、scenes（场景标签数组，如["浪漫","户外"]）、locationSuggestion（地点建议）、location（具体地点，可为空字符串）、budget（预算等级，取 free/low/mid/high/luxury 之一，与输入一致）、reason（AI 推荐理由，1 句）、remark（备注，可为空字符串）、tips（贴心提示数组，3 条）。
 只返回 JSON 数组，不要其他说明。`
}

function parseRecommendContent(content: string): DatePlanRecommendResult {
  const trimmed = content.trim()
  const jsonStart = trimmed.indexOf('[')
  const jsonEnd = trimmed.lastIndexOf(']')
  if (jsonStart < 0 || jsonEnd <= jsonStart) {
    throw new Error('AI 返回格式异常')
  }
  const jsonText = trimmed.slice(jsonStart, jsonEnd + 1)
  const list = JSON.parse(jsonText) as (Omit<DatePlan, 'id' | 'status' | 'createdAt' | 'location' | 'remark'> & {
    location?: string
    remark?: string
  })[]
  return {
    plans: list.map((p) => ({
      ...p,
      location: p.location ?? '',
      remark: p.remark ?? '',
      id: `ai_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      photos: [],
      status: 0 as DatePlanStatus,
      createdAt: new Date().toISOString()
    }))
  }
}

/**
 * 获取我的约会计划列表
 * GET /api/date-plans
 */
export async function listDatePlans(status?: DatePlanStatus): Promise<DatePlan[]> {
  const params: Record<string, any> = {}
  if (status !== undefined) params.status = status
  const rows = await request.get<any[]>('/date-plans', { params })
  return rows.map(fromApi)
}

/**
 * 创建约会计划
 * POST /api/date-plans
 */
export async function saveDatePlan(
  plan: Omit<DatePlan, 'id' | 'createdAt'> & { id?: string | number }
): Promise<DatePlan> {
  const payload = {
    title: plan.title,
    planDate: plan.date || null,
    timeSlot: plan.timeSlot,
    scenes: plan.scenes,
    location: plan.location || '',
    locationSuggestion: plan.locationSuggestion,
    budget: plan.budget,
    reason: plan.reason,
    tips: plan.tips,
    description: plan.remark || ''
  }
  return request.post<any>('/date-plans', payload).then(fromApi)
}

/**
 * 变更约会计划状态
 * PUT /api/date-plans/{id}/status
 */
export async function updateDatePlanStatus(
  id: number | string,
  status: DatePlanStatus
): Promise<DatePlan> {
  return request.put<any>(`/date-plans/${id}/status`, { status }).then(fromApi)
}

/**
 * 更新约会计划字段（状态请用 updateDatePlanStatus）
 * PUT /api/date-plans/{id}
 */
export async function updateDatePlan(
  id: number | string,
  patch: Partial<
    Pick<
      DatePlan,
      | 'title'
      | 'date'
      | 'timeSlot'
      | 'scenes'
      | 'budget'
      | 'location'
      | 'locationSuggestion'
      | 'remark'
    >
  >
): Promise<DatePlan | null> {
  const payload: Record<string, any> = {}
  if (patch.title !== undefined) payload.title = patch.title
  if (patch.date !== undefined) payload.planDate = patch.date
  if (patch.timeSlot !== undefined) payload.timeSlot = patch.timeSlot
  if (patch.scenes !== undefined) payload.scenes = patch.scenes
  if (patch.budget !== undefined) payload.budget = patch.budget
  if (patch.location !== undefined) payload.location = patch.location
  if (patch.locationSuggestion !== undefined) payload.locationSuggestion = patch.locationSuggestion
  if (patch.remark !== undefined) payload.description = patch.remark
  return request.put<any>(`/date-plans/${id}`, payload).then(fromApi)
}

/**
 * 上传一张约会照片（完成约会时可选用，后端落库并写入 OSS）
 * POST /api/date-plans/{id}/photos
 */
export async function uploadDatePlanPhoto(
  id: number | string,
  file: File,
  onProgress?: (progress: number) => void
): Promise<DatePlan> {
  const formData = new FormData()
  formData.append('file', file)
  return request
    .post<any>(`/date-plans/${id}/photos`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          onProgress(Math.round((progressEvent.loaded * 100) / progressEvent.total))
        }
      }
    })
    .then(fromApi)
}

/**
 * 删除约会计划
 * DELETE /api/date-plans/{id}
 */
export async function deleteDatePlan(id: number | string): Promise<void> {
  await request.delete(`/date-plans/${id}`)
}

/**
 * 后端用 description 承载「备注」、location 可能为 null，统一归一化为前端字段
 */
function fromApi(row: any): DatePlan {
  const { description, ...rest } = row
  return {
    ...rest,
    location: rest.location ?? '',
    remark: description ?? '',
    photos: rest.photos ?? []
  }
}

export default {
  recommendDatePlans,
  listDatePlans,
  saveDatePlan,
  updateDatePlan,
  updateDatePlanStatus,
  uploadDatePlanPhoto,
  deleteDatePlan
}
