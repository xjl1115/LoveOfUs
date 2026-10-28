import request from '@/utils/request'

export interface PointProductVO {
  id: number
  name: string
  description?: string
  /** 所需积分 */
  pointsCost: number
  /** 需付款金额（元），null 或 0 表示纯积分兑换 */
  priceAmount?: number | null
  /** 促销开关：0=关 1=开 */
  promoStatus: number
  /** 促销积分 */
  promoPoints?: number | null
  /** 促销金额（元） */
  promoAmount?: number | null
  /** 库存：-1 表示不限 */
  stock: number
  /** 是否库存充足（前端兑换按钮可用性） */
  inStock: boolean
  imageUrl?: string
  status: number
  sortOrder: number
}

/** 我的兑换记录 / 商品订单 */
export interface PointExchangeVO {
  id: number
  orderNo?: string | null
  productId: number
  productName: string
  pointsCost: number
  quantity: number
  totalPoints: number
  /** 需付款金额（元），null 表示纯积分兑换 */
  priceAmount?: number | null
  /** 0=待顾问确认收款 1=已发放 2=已取消 */
  status: number
  confirmedAt?: string | null
  createdAt: string
}

/** 下单结果：附带顾问邮箱，用于提示用户联系顾问完成线下付款 */
export interface PointOrderResult {
  /** 订单号，纯积分兑换为 null */
  orderNo?: string | null
  productName: string
  /** 抵扣积分合计 */
  totalPoints: number
  /** 需付款金额（元），null 表示纯积分兑换 */
  priceAmount?: number | null
  /** 0=待顾问确认收款 1=已发放 2=已取消 */
  status: number
  /** 专属顾问邮箱 */
  advisorEmail?: string | null
}

/** 列出已上架积分商品 */
export function listPointProducts() {
  return request.get<PointProductVO[]>('/point-products')
}

/** 兑换商品（含金额商品时生成待确认订单；下单需传入收货地址） */
export function exchangePointProduct(productId: number, quantity = 1, addressId?: number) {
  return request.post<PointOrderResult>('/point-products/exchange', { productId, quantity, addressId })
}

/** 我的兑换记录 */
export function myExchanges() {
  return request.get<PointExchangeVO[]>('/point-products/my-exchanges')
}
