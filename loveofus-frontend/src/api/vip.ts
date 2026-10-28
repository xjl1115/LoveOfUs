import request from '@/utils/request'

export interface VipTier {
  /** 等级：1-周卡，2-月卡，3-季卡，4-年卡，5-永久 */
  level: number
  name: string
  /** 价格（元），支持小数 */
  priceYuan: number
  /** 时长文案：如 "7天" / "永久" */
  durationText: string
  permanent: boolean
  benefits: string[]
}

export interface VipOrderResult {
  /** 订单号，联系顾问时提供 */
  orderNo: string
  /** 下单的 VIP 档位 */
  vipLevel: number
  vipLevelName: string
  priceYuan: number
  /** 订单类型：1=VIP 卡，2=次卡 */
  orderType?: number
  /** 次卡配额类型：1=AI化妆 2=AI约会 3=AI心愿 */
  quotaType?: number
  quotaTypeName?: string
  quotaCount?: number
  /** 订单状态：0-待开通，1-已开通，2-已取消 */
  status: number
  /** 专属顾问邮箱 */
  advisorEmail: string
  /** 开通后的到期时间，未开通或永久卡为 null */
  vipExpireAt?: string | null
  /** 下单时间，账单列表展示 */
  createdAt?: string | null
  /** 顾问开通时间，未开通为 null */
  activatedAt?: string | null
}

/** 查询 VIP 档位与权益 */
export function getVipTiers() {
  return request.get<VipTier[]>('/vip/tiers')
}

/** 后端 409 返回的旧订单摘要（用于二次确认弹窗） */
export interface PendingOrderHint {
  orderNo: string
  /** 1=VIP 卡，2=次卡 */
  orderType?: number
  vipLevel: number
  vipLevelName: string
  quotaType?: number
  quotaTypeName?: string
  quotaCount?: number
  priceYuan: number
  createdAt: string
}

/** 提交 VIP 下单（仅登记订单，顾问收款后开通） */
export function createVipOrder(level: number, confirmOldOrder = false) {
  return request.post<VipOrderResult>('/vip/orders', { level, confirmOldOrder })
}

/** 提交次卡下单（仅登记订单，顾问收款后开通，开通后自动入账配额） */
export function createQuotaOrder(quotaType: number, quotaCount: number, confirmOldOrder = false) {
  return request.post<VipOrderResult>('/vip/orders', {
    orderType: 2,
    quotaType,
    quotaCount,
    confirmOldOrder
  })
}

/** 配额快照条目 */
export interface QuotaItem {
  used: number
  limit: number
  remaining: number
  unlimited: boolean
}

/** 当前周期配额快照（AI 约会 / 心愿 / 导出） */
export interface VipQuotaSnapshot {
  aiRecommend: QuotaItem
  aiWishlist: QuotaItem
  export: QuotaItem
}

/** 查询当前周期配额 */
export function getCurrentQuotas() {
  return request.get<VipQuotaSnapshot>('/vip/quotas')
}

/** 查询我的账单（按情侣组查询全部订单，下单时间倒序） */
export function getVipOrders() {
  return request.get<VipOrderResult[]>('/vip/orders')
}

/** 取消待开通的 VIP 订单 */
export function cancelVipOrder(orderNo: string) {
  return request.post<VipOrderResult>(`/vip/orders/${orderNo}/cancel`)
}

/**
 * 价格项（与后端 VipPricingVO 对齐）
 * - productType=1：VIP 卡，vipLevel 有值
 * - productType=2：次卡，quotaType 有值
 */
export interface VipPricingItem {
  id: number
  productCode: string
  productName: string
  productType: number
  vipLevel?: number | null
  quotaType?: number | null
  priceYuan: number
  description?: string | null
  sortOrder?: number
}

/**
 * 拉取 VIP 与次卡的全量价格（前端开卡页与顾问台共用）。
 * 后端有 Redis 缓存，前端再以 localStorage 做二级缓存，避免每次开卡都打接口。
 */
export function getVipPricing() {
  return request.get<VipPricingItem[]>('/vip/pricing')
}

/* ===================== 价格前端缓存 ===================== */

/** 缓存键名：与业务命名空间隔离，方便未来按用户清除 */
const PRICING_CACHE_KEY = 'lovemap:vip:pricing'

/** 缓存有效期：5 分钟，调价后最长 5 分钟全网生效 */
const PRICING_CACHE_TTL_MS = 5 * 60 * 1000

interface PricingCache {
  data: VipPricingItem[]
  cachedAt: number
}

/** 读取本地缓存；过期或解析失败返回 null */
export function readPricingCache(): VipPricingItem[] | null {
  try {
    const raw = localStorage.getItem(PRICING_CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PricingCache
    if (!parsed || !Array.isArray(parsed.data) || typeof parsed.cachedAt !== 'number') {
      return null
    }
    if (Date.now() - parsed.cachedAt > PRICING_CACHE_TTL_MS) {
      return null
    }
    return parsed.data
  } catch {
    return null
  }
}

/** 写入本地缓存；失败仅记日志，不影响主流程 */
export function writePricingCache(data: VipPricingItem[]): void {
  try {
    const payload: PricingCache = { data, cachedAt: Date.now() }
    localStorage.setItem(PRICING_CACHE_KEY, JSON.stringify(payload))
  } catch (error) {
    console.warn('写入 VIP 价格本地缓存失败', error)
  }
}

/** 主动清除缓存：调价/退出登录时调用 */
export function clearPricingCache(): void {
  try {
    localStorage.removeItem(PRICING_CACHE_KEY)
  } catch {
    // 忽略
  }
}

/**
 * 拉取并刷新缓存：先返回本地缓存（命中），再异步请求接口覆盖缓存。
 * 命中失败时同步等待接口返回；命中成功时立即 resolve，再后台刷新。
 */
export async function loadVipPricing(forceRefresh = false): Promise<VipPricingItem[]> {
  const cached = readPricingCache()
  if (!forceRefresh && cached) {
    // 命中缓存：先返回缓存数据，再静默刷新
    void getVipPricing()
      .then((fresh) => writePricingCache(fresh))
      .catch((err) => console.warn('后台刷新 VIP 价格失败', err))
    return cached
  }
  const fresh = await getVipPricing()
  writePricingCache(fresh)
  return fresh
}

/* ===================== 顾问端价格管理 ===================== */

export interface VipPricingUpdateItem {
  id: number
  priceYuan: number
}

export function getAdminPricing() {
  return request.get<VipPricingItem[]>('/vip/admin/pricing')
}

export function updateAdminPricing(items: VipPricingUpdateItem[]) {
  return request.put<number>('/vip/admin/pricing', { items })
}
