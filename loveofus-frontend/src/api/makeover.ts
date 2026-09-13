/**
 * AI 化妆建议 API
 *
 * 后端约定（与 docs/08-AI化妆建议-前端开发文档.md / docs/07-AI化妆建议-后端开发文档.md 对齐）：
 *   POST   /api/makeover              提交化妆建议（multipart：image + scene + description?）
 *   GET    /api/makeover              分页查询当前用户历史
 *   GET    /api/makeover/:id          查询详情
 *   DELETE /api/makeover/:id          软删单条记录
 *   GET    /api/makeover/:id/stream   订阅分析/出图进度（SSE）
 *
 * 鉴权：所有接口走现有 JwtAuthFilter，前端只需确保 Bearer Token 已携带。
 */

import request from '@/utils/request'
import { sseGet } from '@/utils/sse'
import { useUserStore } from '@/stores/user'

// ==================== 类型定义 ====================

/** 场景编码（与后端 MakeoverConstant.SCENE_NAME 对齐） */
export type SceneCode =
  | 'date'
  | 'commute'
  | 'party'
  | 'travel'
  | 'wedding'
  | 'daily'
  | 'other'

/** 状态机（与后端 STATUS_* 对齐） */
export enum MakeoverStatus {
  PENDING = 0,
  ANALYZING = 1,
  EDITING = 2,
  DONE = 3,
  FAILED = 4,
  CANCELED = 5
}

/** AI 返回的脸部特征 */
export interface FaceFeatures {
  faceShape: string
  skinTone: string
  eyeShape: string
  lipShape: string
  hairLength: string
}

/** 香水推荐 */
export interface PerfumeSuggestion {
  /** 香调族：floral=花香 / citrus=柑橘 / woody=木质 / oriental=东方 / fresh=清新 / gourmand=美食 / chypre=西普 */
  family: string
  /** 前调（15 分钟内可闻） */
  topNote: string
  /** 中调（2-4 小时核心气味） */
  heartNote: string
  /** 后调（留香最久的基调） */
  baseNote: string
  /** 推荐具体香水产品名 */
  product: string
  /** 适合场合/季节 */
  occasion: string
  /** 使用要点 */
  tips: string
}

/** AI 返回的妆造建议 */
export interface MakeoverSuggestions {
  makeup: {
    base: string
    eye: string
    lip: string
    brow: string
  }
  hair: { style: string; color: string }
  accessory: string[]
  /** 香水推荐（V8+ 可选，老数据可能为空对象） */
  perfume?: PerfumeSuggestion
  outfit: {
    style: string
    items: string[]
    colorTips: string
  }
  tips: string[]
}

/** 提交后立即返回（带原图 OSS URL） */
export interface MakeoverCreateVO {
  recordId: number
  status: MakeoverStatus
  originalUrl: string
}

/**
 * 改造总结：{ overall: string, steps: string[] }
 * 老数据（迁移前/未生成）此字段为 undefined，前端应隐藏该区域。
 */
export interface MakeoverSummary {
  overall?: string
  steps?: string[]
}

/** 详情 VO */
export interface MakeoverDetailVO extends MakeoverCreateVO {
  sceneCode: SceneCode
  /** 用户填写的场景补充描述 */
  sceneText?: string
  /** AI 出图后填入，失败时为空 */
  afterUrl?: string
  faceFeatures?: FaceFeatures
  suggestions?: MakeoverSuggestions
  /** 改造总结：整体思路 + 化妆步骤详解，与 suggestions 同期由 AI 一次返回 */
  summary?: MakeoverSummary
  errorMessage?: string
  /** 总耗时（毫秒） */
  costMs?: number
  createdAt: string
  /** 记录所有者 ID */
  ownerId?: number
  /** 当前用户是否为记录所有者（false=伴侣只读视角） */
  ownedByCurrent?: boolean
}

/** 列表项 VO */
export interface MakeoverListVO {
  recordId: number
  originalUrl: string
  afterUrl?: string
  sceneCode: SceneCode
  status: MakeoverStatus
  createdAt: string
}

/** 本月额度（免费次数 + VIP 档位额外次数） */
export interface MakeoverQuota {
  /** 是否不限量（永久会员） */
  unlimited: boolean
  /** 本月总额度；不限量时为 null */
  total: number | null
  /** 本月已使用次数 */
  used: number
  /** 本月剩余次数；不限量时为 null */
  remaining: number | null
}

/** 进度订阅事件（与后端 stream 帧对齐） */
export type MakeoverSseEvent =
  | { stage: 'analyze' | 'image_edit'; status: 'running' | 'done' }
  | { stage: 'analyze' | 'image_edit'; status: 'done'; done: true }
  | { recordId: number } // done - 生成完成
  | { message: string } // error

// ==================== REST 接口 ====================

/**
 * 提交化妆建议（上传自拍照 + 场景编码 + 可选描述）
 * @param image 自拍照文件
 * @param scene 场景编码
 * @param description 用户补充描述（可选，≤100 字）
 * @param onUploadProgress 上传进度回调（0-100）
 */
export function createMakeover(
  image: File,
  scene: SceneCode,
  description?: string,
  onUploadProgress?: (percent: number) => void
): Promise<MakeoverCreateVO> {
  const fd = new FormData()
  fd.append('image', image)
  fd.append('scene', scene)
  if (description && description.trim()) fd.append('description', description.trim())
  return request.post<MakeoverCreateVO>('/makeover', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onUploadProgress
      ? (e) => {
          if (!e.total) return
          const percent = Math.round((e.loaded * 100) / e.total)
          onUploadProgress(percent)
        }
      : undefined
  })
}

/** 查询详情 */
export function getMakeoverDetail(recordId: number): Promise<MakeoverDetailVO> {
  return request.get<MakeoverDetailVO>(`/makeover/${recordId}`)
}

/** 分页查询历史列表 */
export function listMakeover(page = 1, size = 10) {
  return request.get<PageResult<MakeoverListVO>>('/makeover', {
    params: { page, size }
  })
}

/** 查询本月剩余额度 */
export function getMakeoverQuota(): Promise<MakeoverQuota> {
  return request.get<MakeoverQuota>('/makeover/quota')
}

/** 软删 */
export function deleteMakeover(recordId: number): Promise<void> {
  return request.delete(`/makeover/${recordId}`)
}

/** 分享妆造建议给伴侣（生成卡片聊天消息） */
export function shareMakeover(recordId: number): Promise<number> {
  return request.post<number>(`/makeover/${recordId}/share`)
}

// ==================== SSE 订阅 ====================

/**
 * 订阅分析进度（SSE）
 * @param recordId 提交后返回的 recordId
 * @param onEvent 进度事件回调
 * @param onError 连接错误回调（前端会自动降级为 1.5s 轮询）
 * @returns cancel 取消订阅
 */
export function subscribeMakeoverProgress(
  recordId: number,
  onEvent: (e: MakeoverSseEvent) => void,
  onError: (err: Error) => void
): () => void {
  const userStore = useUserStore()
  const token = userStore.token || ''
  return sseGet({
    url: `/makeover/${recordId}/stream`,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    timeoutMs: 5 * 60 * 1000,
    onEvent: (raw) => {
      try {
        if (!raw.data) return
        const data = JSON.parse(raw.data)
        if (raw.event === 'stage') {
          onEvent({ stage: data.stage, status: data.status })
        } else if (raw.event === 'done') {
          onEvent({ recordId: data.recordId })
        } else if (raw.event === 'error') {
          onEvent({ message: data.message || '生成失败' })
        } else {
          // 兼容 event 字段缺失的默认 message 事件
          if (data && data.stage) {
            onEvent({ stage: data.stage, status: data.status })
          } else if (data && data.recordId) {
            onEvent({ recordId: data.recordId })
          } else if (data && data.message) {
            onEvent({ message: data.message })
          }
        }
      } catch (e) {
        onError(e instanceof Error ? e : new Error('解析 SSE 失败'))
      }
    },
    onError
  })
}

// ==================== PageResult（与后端分页 VO 对齐） ====================

interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}
