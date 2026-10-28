/**
 * 顾问控制台：积分商品管理 API
 * 调用时需要在请求头携带 X-Vip-Admin-Token；与 VIP 控制台共用密钥
 */
import request from '@/utils/request'

export interface AdminPointProductVO {
  id: number
  name: string
  description?: string
  pointsCost: number
  /** 需付款金额（元），null 表示纯积分兑换 */
  priceAmount?: number | null
  /** 促销开关：0=关 1=开 */
  promoStatus: number
  promoPoints?: number | null
  promoAmount?: number | null
  stock: number
  imageUrl?: string
  status: number
  sortOrder: number
}

export interface AdminPointProductPayload {
  id?: number
  name: string
  description?: string
  pointsCost: number
  priceAmount?: number | null
  promoStatus: number
  promoPoints?: number | null
  promoAmount?: number | null
  stock: number
  imageUrl?: string
  status: number
  sortOrder?: number
}

export interface AdminCoupleTaskVO {
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
}

export interface AdminCoupleTaskPayload {
  id?: number
  content: string
  description?: string
  icon?: string
  points: number
  status: number
  sortOrder?: number
  completeType: string
  periodType: string
  claimRequired: number
}

/** 待确认收款的积分商品订单 */
export interface AdminPointOrderVO {
  id: number
  orderNo: string
  userId: number
  productId: number
  productName: string
  pointsCost: number
  quantity: number
  totalPoints: number
  priceAmount?: number | null
  status: number
  createdAt: string
}

/** 创建带 admin token 的 request 函数 */
function adminReq() {
  const token = localStorage.getItem('vip_admin_token') || ''
  return { headers: { 'X-Vip-Admin-Token': token } }
}

// ============ 积分商品 ============

export function adminListPointProducts() {
  return request.get<AdminPointProductVO[]>('/admin/points/products', adminReq())
}

export function adminCreatePointProduct(data: AdminPointProductPayload) {
  return request.post<number>('/admin/points/products', data, adminReq())
}

export function adminUpdatePointProduct(id: number, data: AdminPointProductPayload) {
  return request.put<number>(`/admin/points/products/${id}`, data, adminReq())
}

export function adminDeletePointProduct(id: number) {
  return request.delete(`/admin/points/products/${id}`, adminReq())
}

// ============ 积分商品订单（含金额商品需顾问确认收款） ============

export function adminListPendingPointOrders() {
  return request.get<AdminPointOrderVO[]>('/admin/points/orders/pending', adminReq())
}

export function adminActivatePointOrder(orderNo: string) {
  return request.post<AdminPointOrderVO>(`/admin/points/orders/${orderNo}/activate`, {}, adminReq())
}

export function adminCancelPointOrder(orderNo: string) {
  return request.post(`/admin/points/orders/${orderNo}/cancel`, {}, adminReq())
}

/** 上传商品图片到阿里云 OSS，返回可访问 URL */
export function adminUploadProductImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<string>('/admin/points/upload', formData, {
    headers: {
      'X-Vip-Admin-Token': localStorage.getItem('vip_admin_token') || '',
      'Content-Type': 'multipart/form-data'
    }
  })
}

// ============ 情侣任务 ============

export function adminListCoupleTasks() {
  return request.get<AdminCoupleTaskVO[]>('/admin/points/tasks', adminReq())
}

export function adminCreateCoupleTask(data: AdminCoupleTaskPayload) {
  return request.post<number>('/admin/points/tasks', data, adminReq())
}

export function adminUpdateCoupleTask(id: number, data: AdminCoupleTaskPayload) {
  return request.put<number>(`/admin/points/tasks/${id}`, data, adminReq())
}

export function adminDeleteCoupleTask(id: number) {
  return request.delete(`/admin/points/tasks/${id}`, adminReq())
}
