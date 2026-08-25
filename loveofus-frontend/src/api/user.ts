import request from '@/utils/request'
import type { UserInfo, UserStats } from '@/types'

export function login(data: {
  account: string
  password?: string
  captcha?: string
  loginType: 'password' | 'captcha'
}) {
  return request.post<{
    token: string
    refreshToken?: string
    expiresIn: number
    userInfo: UserInfo
  }>('/auth/login', data)
}

export function register(data: {
  nickname: string
  phone?: string
  email?: string
  password: string
  confirmPassword: string
  captcha: string
  partnerCode?: string
}) {
  return request.post<{
    token: string
    refreshToken?: string
    expiresIn: number
    userInfo: UserInfo
  }>('/auth/register', data)
}

export function getUserInfo() {
  return request.get<UserInfo>('/user/profile')
}

export function getUserStats() {
  return request.get<UserStats>('/user/stats')
}

export function updateUserInfo(data: Partial<UserInfo>) {
  return request.put<UserInfo>('/user/profile', data)
}

export interface BindCodeResult {
  bindCode: string
}

export function generateBindCode() {
  return request.post<BindCodeResult>('/auth/bind-code/generate')
}

export function getBindCode() {
  return request.get<BindCodeResult>('/auth/bind-code')
}

export function bindPartner(partnerCode: string) {
  return request.post<{ partnerInfo: UserInfo }>('/auth/bind', { partnerCode })
}

export interface UnbindStatus {
  requesting: boolean
  effectiveDate?: string
  cooldownDays?: number
}

export function unbindPartner() {
  return request.post<UnbindStatus>('/auth/unbind')
}

export function deleteAccount() {
  return request.post('/user/delete')
}

// 通知设置
export interface NotificationSettings {
  enablePush: boolean
  photoUpload: boolean
  anniversary: boolean
  email: boolean
  system: boolean
}

export function getNotificationSettings() {
  return request.get<NotificationSettings>('/user/notification-settings')
}

export function updateNotificationSettings(data: NotificationSettings) {
  return request.put<NotificationSettings>('/user/notification-settings', data)
}
