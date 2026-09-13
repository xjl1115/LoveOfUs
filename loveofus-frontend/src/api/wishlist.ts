import request from '@/utils/request'

/**
 * 心愿分类
 */
export type WishCategory = 'travel' | 'food' | 'experience' | 'growth' | 'memory'

/**
 * 心愿数据（与后端 WishlistItemVO 对齐）
 */
export interface Wish {
  id: number | string
  title: string
  category: WishCategory
  icon: string
  description?: string
  targetValue: number
  currentValue: number
  unit: string
  /** 后端 targetDate，前端 deadline */
  deadline?: string
  /** 0-否，1-是 */
  needBothConfirm: 0 | 1
  /** 0-未完成，1-已完成 */
  status: 0 | 1
  achievedAt?: string
  achievedNote?: string
  /** 达成纪念照片 URL（由后端文件存储返回） */
  achievedPhotoUrl?: string
  createdAt: string
}

/**
 * AI 推荐心愿请求
 */
export interface WishRecommendParams {
  category?: WishCategory
  /** 期望数量，默认 5 */
  count?: number
}

/**
 * 心愿创建/更新参数
 */
export interface WishParams {
  title: string
  category: WishCategory
  icon?: string
  description?: string
  targetValue: number
  unit: string
  deadline?: string
  needBothConfirm?: boolean
}

const ICONS: Record<WishCategory, string[]> = {
  travel: ['🏖️', '✈️', '🚄', '🗺️', '🏔️'],
  food: ['🍜', '🍰', '🍷', '☕', '🍱'],
  experience: ['🎬', '🎮', '🎨', '🎤', '🎡'],
  growth: ['📚', '🏃', '💪', '🧘', '🎓'],
  memory: ['🎁', '💍', '🎂', '📷', '💌']
}

function defaultIcon(category: WishCategory): string {
  return ICONS[category][0]
}

/** 心愿分类枚举（AI 返回值必须落在这个范围内） */
const WISH_CATEGORIES: WishCategory[] = ['travel', 'food', 'experience', 'growth', 'memory']

/**
 * AI 推荐心愿
 * POST /api/wishlist-items/recommend
 *
 * 不传 category 时由 AI 混合推荐多个分类，避免结果清一色是「纪念」
 */
export async function recommendWishes(params: WishRecommendParams): Promise<Wish[]> {
  const category = params.category
  const count = params.count ?? 5
  const categoryRule = category
    ? `- 分类：${category}（本次全部使用该分类）`
    : '- 分类：从 travel（旅行）、food（美食）、experience（体验）、growth（成长）、memory（纪念）中挑选，至少覆盖 3 种分类，同一种分类最多 2 个'
  const prompt = `请为一对情侣推荐 ${count} 个心愿清单项。要求：
${categoryRule}
- 每个心愿包含：title（标题）、category（分类，取 travel/food/experience/growth/memory 之一）、icon（emoji 图标）、description（描述，可选）、targetValue（目标数量，整数）、unit（单位，如"次"/"个"/"天"）、targetDate（期望完成日期 YYYY-MM-DD，可选，可留空字符串）、needBothConfirm（是否需要双方确认，0 或 1）
只返回 JSON 数组，不要其他说明。`

  const res = await request.post<{ content: string }>('/wishlist-items/recommend', { prompt })
  const trimmed = res.content.trim()
  const jsonStart = trimmed.indexOf('[')
  const jsonEnd = trimmed.lastIndexOf(']')
  if (jsonStart < 0 || jsonEnd <= jsonStart) {
    throw new Error('AI 返回格式异常')
  }
  const list = JSON.parse(trimmed.slice(jsonStart, jsonEnd + 1)) as Omit<
    Wish,
    'id' | 'status' | 'currentValue' | 'createdAt'
  >[]
  return list
    .slice(0, count)
    .map((w) => {
      const category = WISH_CATEGORIES.includes(w.category) ? w.category : 'memory'
      return {
        ...w,
        category,
        id: `ai_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        status: 0 as 0 | 1,
        currentValue: 0,
        icon: w.icon || defaultIcon(category),
        createdAt: new Date().toISOString()
      }
    })
}

/** 获取心愿列表
 * GET /api/wishlist-items
 */
export async function listWishes(opts?: { category?: WishCategory | 'all' }): Promise<Wish[]> {
  const list = await request.get<Wish[]>('/wishlist-items')
  if (!opts?.category || opts.category === 'all') return list
  return list.filter((w) => w.category === opts.category)
}

/** 新建心愿
 * POST /api/wishlist-items
 */
export async function createWish(params: WishParams): Promise<Wish> {
  const payload = {
    title: params.title.trim(),
    description: '',
    category: params.category,
    icon: params.icon || defaultIcon(params.category),
    targetValue: params.targetValue,
    unit: params.unit,
    targetDate: params.deadline || null,
    needBothConfirm: params.needBothConfirm ? 1 : 0
  }
  return request.post<Wish>('/wishlist-items', payload)
}

/** 修改心愿基本信息
 * PUT /api/wishlist-items/{id}
 */
export async function updateWish(id: number | string, patch: Partial<WishParams>): Promise<Wish | null> {
  const payload: Record<string, any> = {}
  if (patch.title !== undefined) payload.title = patch.title.trim()
  if (patch.category !== undefined) payload.category = patch.category
  if (patch.icon !== undefined) payload.icon = patch.icon
  if (patch.targetValue !== undefined) payload.targetValue = patch.targetValue
  if (patch.unit !== undefined) payload.unit = patch.unit
  if (patch.deadline !== undefined) payload.targetDate = patch.deadline || null
  if (patch.needBothConfirm !== undefined) payload.needBothConfirm = patch.needBothConfirm ? 1 : 0
  return request.put<Wish>(`/wishlist-items/${id}`, payload)
}

/** 调整进度（+1 / -1），服务端原子增减并自动限制在 [0, targetValue]
 * PUT /api/wishlist-items/{id}/progress
 */
export async function updateWishProgress(id: number | string, delta: number): Promise<Wish | null> {
  return request.put<Wish>(`/wishlist-items/${id}/progress`, { delta })
}

/** 标记达成（写笔记 + 关联照片）
 * PUT /api/wishlist-items/{id}/status
 */
export async function achieveWish(
  id: number | string,
  payload: { note?: string; relatedPhotoIds?: number[] }
): Promise<Wish | null> {
  return request.put<Wish>(`/wishlist-items/${id}/status`, { status: 1, note: payload.note })
}

/** 删除心愿
 * DELETE /api/wishlist-items/{id}
 */
export async function deleteWish(id: number | string): Promise<void> {
  await request.delete(`/wishlist-items/${id}`)
}

/** 上传/替换达成纪念照片（仅 1 张，替换时后端清理旧文件）
 * POST /api/wishlist-items/{id}/photo
 */
export function uploadWishItemPhoto(id: number | string, file: File): Promise<Wish | null> {
  const fd = new FormData()
  fd.append('file', file)
  return request.post<Wish>(`/wishlist-items/${id}/photo`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 读取达成纪念照片（服务端同域转发），返回可直接给 <img> 用且不会污染 canvas 的 blob URL
 * GET /api/wishlist-items/{id}/photo/raw
 */
export async function fetchWishItemPhotoUrl(id: number | string): Promise<string> {
  const blob = await request.get<Blob>(`/wishlist-items/${id}/photo/raw`, {
    responseType: 'blob'
  })
  return URL.createObjectURL(blob)
}

/** 把记录卡片图片发给伴侣，返回聊天消息 ID
 * POST /api/wishlist-items/{id}/card
 */
export function shareWishCard(id: number | string, image: Blob, note?: string): Promise<number> {
  const fd = new FormData()
  fd.append('image', image, 'wish-card.png')
  if (note && note.trim()) fd.append('note', note.trim())
  return request.post<number>(`/wishlist-items/${id}/card`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 统计
 * GET /api/wishlist-items
 */
export async function getWishStats(): Promise<{ total: number; achieved: number; streakDays: number }> {
  const list = await listWishes()
  const achieved = list.filter((w) => w.status === 1 && w.achievedAt)
  const days = new Set<string>()
  for (const w of achieved) {
    if (w.achievedAt) days.add(w.achievedAt.slice(0, 10))
  }
  let streak = 0
  const cursor = new Date()
  while (true) {
    const key = cursor.toISOString().slice(0, 10)
    if (days.has(key)) {
      streak++
      cursor.setDate(cursor.getDate() - 1)
    } else {
      break
    }
  }
  return { total: list.length, achieved: achieved.length, streakDays: streak }
}

export default {
  recommendWishes,
  listWishes,
  createWish,
  updateWish,
  updateWishProgress,
  achieveWish,
  deleteWish,
  uploadWishItemPhoto,
  fetchWishItemPhotoUrl,
  shareWishCard,
  getWishStats
}
