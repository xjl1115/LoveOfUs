import type { SceneCode } from '@/api/makeover'

/**
 * 场景选项（前端常量）。emoji 占用列宽小，移动端 3 列网格下视觉清晰。
 */
export const SCENE_OPTIONS: Array<{
  code: SceneCode
  name: string
  emoji: string
  desc: string
}> = [
  { code: 'date', name: '情侣约会', emoji: '💕', desc: '温柔有亲和力' },
  { code: 'commute', name: '日常通勤', emoji: '💼', desc: '干练不刻意' },
  { code: 'party', name: '派对聚会', emoji: '🎉', desc: '高光抢眼' },
  { code: 'travel', name: '外出旅行', emoji: '✈️', desc: '上镜明亮' },
  { code: 'wedding', name: '婚礼仪式', emoji: '💒', desc: '端庄大气' },
  { code: 'daily', name: '日常休闲', emoji: '🌿', desc: '舒适自然' },
  { code: 'other', name: '其他场合', emoji: '✨', desc: '我来描述' }
]

/** 最大图片大小：8MB（与 docs/08 §3.4 一致） */
export const MAX_IMAGE_SIZE = 8 * 1024 * 1024

/** 允许的图片 MIME 列表 */
export const ALLOWED_IMAGE_MIME = ['image/jpeg', 'image/png', 'image/webp']