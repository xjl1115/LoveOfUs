import request from '@/utils/request'

export interface VipTier {
  /** 等级：1-周卡，2-月卡，3-季卡，4-年卡，5-永久 */
  level: number
  name: string
  /** 价格（元） */
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

/** 提交 VIP 下单（仅登记订单，顾问收款后开通） */
export function createVipOrder(level: number) {
  return request.post<VipOrderResult>('/vip/orders', { level })
}

/** 查询我的账单（按情侣组查询全部订单，下单时间倒序） */
export function getVipOrders() {
  return request.get<VipOrderResult[]>('/vip/orders')
}

/** 取消待开通的 VIP 订单 */
export function cancelVipOrder(orderNo: string) {
  return request.post<VipOrderResult>(`/vip/orders/${orderNo}/cancel`)
}
