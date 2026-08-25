import request from '@/utils/request'

export interface SendCaptchaDTO {
  target: string
  channel: 'sms' | 'email'
  type: 'register' | 'login' | 'reset_password' | 'bind_phone' | 'bind_email'
}

export interface SendCaptchaResult {
  expireSeconds: number
  cooldownSeconds: number
}

export interface LoginDTO {
  account: string
  password?: string
  captcha?: string
  loginType: 'password' | 'captcha'
}

export interface RegisterDTO {
  nickname: string
  phone: string
  email?: string
  password: string
  confirmPassword: string
  captcha: string
  partnerCode?: string
}

export interface ResetPasswordDTO {
  target: string
  channel: 'sms' | 'email'
  captcha: string
  newPassword: string
  confirmPassword: string
}

export interface LoginResult {
  userId: number
  token: string
  refreshToken?: string
  expiresIn: number
  userInfo: {
    nickname: string
    avatarUrl: string | null
    isBound: boolean
  }
}

// 发送验证码
export function sendCaptcha(data: SendCaptchaDTO) {
  return request.post<SendCaptchaResult>('/auth/captcha/send', data)
}

// 登录
export function login(data: LoginDTO) {
  return request.post<LoginResult>('/auth/login', data)
}

// 注册
export function register(data: RegisterDTO) {
  return request.post<LoginResult>('/auth/register', data)
}

// 重置密码
export function resetPassword(data: ResetPasswordDTO) {
  return request.post('/auth/password/reset', data)
}

// 退出登录
export function logout() {
  return request.post('/auth/logout')
}

// 修改密码
export interface ChangePasswordDTO {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export function changePassword(data: ChangePasswordDTO) {
  return request.post('/auth/password/change', data)
}
