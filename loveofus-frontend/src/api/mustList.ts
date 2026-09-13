import request from '@/utils/request'

/**
 * 情侣必做 100 件事数据类型（与后端 ThingsListVO 对齐）
 *
 * 后端接口：
 *   GET    /api/things                  → 100 项列表
 *   GET    /api/things/stats            → 进度统计
 *   POST   /api/things/achieve          → 标记完成 / 取消
 *   GET    /api/things/{id}/photos      → 关联图片
 *   POST   /api/things/photos           → 上传图片关联到事项
 */
export type MustCategory =
  | 'travel'
  | 'romance'
  | 'daily'
  | 'food'
  | 'memory'
  | 'growth'

export interface MustItem {
  id: number
  order: number
  title: string
  description: string
  /**
   * 左侧图标（emoji 文本，列表卡片左侧展示）。
   * 后端字段为空（数据库默认 '' 或 NULL）时，前端按 title 推断 emoji。
   */
  icon?: string | null
  /** 兼容旧字段（已弃用） */
  coverEmoji?: string | null
  achievedAt: string | null
  achievedNote: string | null
  photoCount: number
}

export interface MustStats {
  total: number
  achieved: number
  rate: number
}

export interface MustPhoto {
  id: number
  url: string
}

export const CATEGORY_META: Record<MustCategory, { label: string; emoji: string; color: string }> = {
  travel:   { label: '旅行', emoji: '✈️', color: '#3b82f6' },
  romance:  { label: '浪漫', emoji: '💕', color: '#ec4899' },
  daily:    { label: '日常', emoji: '☀️', color: '#f59e0b' },
  food:     { label: '美食', emoji: '🍜', color: '#ef4444' },
  memory:   { label: '纪念', emoji: '🎁', color: '#a855f7' },
  growth:   { label: '成长', emoji: '🌱', color: '#10b981' }
}

/**
 * 前端兜底数据：后端不可用时显示 5 项示意
 */
const FALLBACK_ITEMS: MustItem[] = [
  { id: 1,   order: 1,   title: '一起看一次日出',     description: '凌晨起床，在山顶或海边迎接第一缕阳光', icon: '🌅', coverEmoji: '🌅', achievedAt: null, achievedNote: null, photoCount: 0 },
  { id: 2,   order: 2,   title: '一起看一次日落',     description: '找个有海、有山、有屋顶的地方，看完整个落日', icon: '🌇', coverEmoji: '🌇', achievedAt: null, achievedNote: null, photoCount: 0 },
  { id: 3,   order: 3,   title: '一起坐一趟绿皮火车', description: '不是为了去哪里，而是看沿途的风景慢慢变化', icon: '🚂', coverEmoji: '🚂', achievedAt: null, achievedNote: null, photoCount: 0 },
  { id: 4,   order: 4,   title: '一起去陌生的城市迷路', description: '不带攻略，刻意走错几次街，遇见意料之外的风景', icon: '🗺️', coverEmoji: '🗺️', achievedAt: null, achievedNote: null, photoCount: 0 },
  { id: 5,   order: 5,   title: '一起住一次民宿',     description: '不要酒店，住进有烟火气的房子，体验当地生活', icon: '🏡', coverEmoji: '🏡', achievedAt: null, achievedNote: null, photoCount: 0 }
]

export async function listMustItems(): Promise<MustItem[]> {
  try {
    const data = await request.get<MustItem[]>('/things')
    return Array.isArray(data) ? data : FALLBACK_ITEMS
  } catch {
    return FALLBACK_ITEMS
  }
}

export async function getMustStats(): Promise<MustStats> {
  try {
    const data = await request.get<MustStats>('/things/stats')
    return data ?? { total: 0, achieved: 0, rate: 0 }
  } catch {
    return { total: 0, achieved: 0, rate: 0 }
  }
}

/**
 * 标记完成 / 取消
 *
 * cancel=true 表示取消已完成；缺省（false）表示标记完成，未填回忆也视为完成
 */
export async function achieveMustItem(
  thingId: number,
  payload?: { note?: string; photoIds?: number[]; cancel?: boolean }
): Promise<void> {
  await request.post('/things/achieve', {
    thingId,
    note: payload?.note ?? null,
    photoIds: payload?.photoIds ?? null,
    cancel: payload?.cancel ?? false
  })
}

export async function unachieveMustItem(thingId: number): Promise<void> {
  await achieveMustItem(thingId, { cancel: true })
}

export async function listMustPhotos(thingId: number): Promise<MustPhoto[]> {
  const data = await request.get<MustPhoto[]>(`/things/${thingId}/photos`)
  return Array.isArray(data) ? data : []
}

export async function uploadMustPhoto(thingId: number, url: string): Promise<MustPhoto> {
  const data = await request.post<MustPhoto>('/things/photos', { thingId, url })
  return data
}

/**
 * 按标题关键词推断 emoji（后端 icon 为空时的前端兜底）
 * 优先级：按关键词列表顺序匹配，命中即返回
 */
export function deriveIconFromTitle(title: string): string {
  const KEYWORDS: Array<[string[], string]> = [
    // 旅行
    [['日出', '日落', '极光', '烟花'], '🌅'],
    [['海', '沙滩', '海边', '海岛', '漂流'], '🏖️'],
    [['滑雪', '雪', '冰'], '❄️'],
    [['山', '登山', '徒步'], '⛰️'],
    [['城市', '古镇', '街道'], '🏙'],
    [['民宿', '酒店'], '🏡'],
    [['露营', '帐篷'], '⛺'],
    [['骑行', '骑车', '自行车'], '🚲'],
    [['热气球'], '🎈'],
    [['摩天轮'], '🎡'],
    [['动物园'], '🐼'],
    [['游乐园', '过山车', '鬼屋'], '🎢'],
    // 运动
    [['射箭'], '🏹'],
    [['保龄球'], '🎳'],
    [['羽毛球'], '🏸'],
    [['卡丁车'], '🏎️'],
    [['攀岩'], '🧗'],
    [['滑雪'], '⛷️'],
    [['划船'], '🛶'],
    [['球赛', '看球'], '⚽'],
    // 娱乐
    [['KTV', '唱K'], '🎤'],
    [['游戏', '电玩', '桌游'], '🎮'],
    [['猫咖', '撸猫', '宠物'], '🐱'],
    [['花鸟', '采摘', '菜'], '🌿'],
    [['网红', '打卡'], '📍'],
    [['音乐节', '演唱会'], '🎵'],
    [['脱口秀'], '🎤'],
    [['博物馆'], '🏛️'],
    [['私人影院', '电影'], '🎬'],
    [['书店', '看书'], '📚'],
    [['漂流'], '🛟'],
    [['写真', '拍', '记录'], '📸'],
    [['公益'], '🤝'],
    [['自驾'], '🚗'],
    [['野营', '烧烤'], '🍖'],
    // 浪漫
    [['求婚', '表白', '婚礼'], '💍'],
    [['情书', '写信'], '💌'],
    [['烟花'], '🎆'],
    [['温泉'], '♨️'],
    [['惊喜', 'Surprise', '等待'], '🎁'],
    [['跨年', '倒数'], '🎉'],
    [['夜市'], '🏮'],
    // 日常
    [['做饭', '烘焙', '甜点', '蛋糕'], '🍰'],
    [['做饭', '做菜'], '🍳'],
    [['散步'], '🚶'],
    [['面膜'], '🧖'],
    [['打扫', '整理'], '🧹'],
    [['化妆', '口红'], '💄'],
    [['指甲'], '💅'],
    [['锻炼', '健身', '瑜伽'], '🏋️'],
    [['全家桶'], '🍗'],
    // 美食
    [['烛光', '晚餐'], '🕯️'],
    [['早茶'], '🍵'],
    [['菜市场', '买菜'], '🥬'],
    [['路边摊'], '🍢'],
    [['早餐店', '豆浆', '油条', '包子'], '🥟'],
    [['小龙虾'], '🦐'],
    [['火锅'], '🍲'],
    [['咖啡'], '☕'],
    [['喝酒'], '🍷'],
    // 纪念
    [['相册', '时间线'], '📷'],
    [['礼物'], '🎀'],
    [['信'], '✉️'],
    [['梦想', '基金'], '🏦'],
    [['票根'], '🎫'],
    [['契约'], '📜'],
    [['小时候'], '🧒'],
    [['视频'], '🎥'],
    [['生日'], '🎂'],
    // 成长
    [['读书', '看书', '学习'], '📖'],
    [['倾听'], '👂'],
    [['沟通'], '🗣️'],
    [['性格', '测试'], '🧠'],
    [['运动'], '🏃'],
    [['家人'], '👨‍👩‍👧'],
    [['10 年', '未来'], '⏳'],
    [['陶艺'], '🏺']
  ]

  for (const [keys, emoji] of KEYWORDS) {
    if (keys.some((k) => title.includes(k))) return emoji
  }
  return '💕'
}

/**
 * 安全获取 icon（兼容 null / 空串 / 旧 coverEmoji 字段）
 */
export function getMustIcon(item: Pick<MustItem, 'icon' | 'coverEmoji' | 'title'>): string {
  if (item.icon && item.icon.trim() !== '') return item.icon
  return deriveIconFromTitle(item.title)
}
