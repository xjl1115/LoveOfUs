import request from '@/utils/request'

export interface ShippingAddress {
  id: number
  userId: number
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  detailAddress: string
  isDefault: number
}

export interface ShippingAddressPayload {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  detailAddress: string
  isDefault?: number
}

/** 我的收货地址列表（默认地址优先） */
export function listAddresses() {
  return request.get<ShippingAddress[]>('/shipping-addresses')
}

/** 新增收货地址 */
export function createAddress(data: ShippingAddressPayload) {
  return request.post<ShippingAddress>('/shipping-addresses', data)
}

/** 修改收货地址 */
export function updateAddress(id: number, data: ShippingAddressPayload) {
  return request.put<ShippingAddress>(`/shipping-addresses/${id}`, data)
}

/** 删除收货地址 */
export function deleteAddress(id: number) {
  return request.delete(`/shipping-addresses/${id}`)
}

/** 设为默认收货地址 */
export function setDefaultAddress(id: number) {
  return request.put(`/shipping-addresses/${id}/default`)
}