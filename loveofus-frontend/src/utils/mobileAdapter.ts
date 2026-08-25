/**
 * 移动端适配工具
 * 包含安全区域适配、触摸事件优化、手势支持等
 */

// 安全区域适配
export function setupSafeArea() {
  // 设置 CSS 变量用于安全区域
  const setSafeAreaVars = () => {
    const safeTop = getComputedStyle(document.documentElement).getPropertyValue('--safe-area-top') || '0px'
    const safeBottom = getComputedStyle(document.documentElement).getPropertyValue('--safe-area-bottom') || '0px'

    document.documentElement.style.setProperty('--sat', safeTop)
    document.documentElement.style.setProperty('--sab', safeBottom)
  }

  setSafeAreaVars()
  window.addEventListener('resize', setSafeAreaVars)
}

// 检测是否为 iOS 设备
export function isIOS(): boolean {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) && !(window as any).MSStream
}

// 检测是否为刘海屏 iPhone
export function isNotchIPhone(): boolean {
  const iPhoneModels = [
    'iPhone X', 'iPhone XS', 'iPhone XS Max', 'iPhone XR',
    'iPhone 11', 'iPhone 11 Pro', 'iPhone 11 Pro Max',
    'iPhone 12', 'iPhone 12 Mini', 'iPhone 12 Pro', 'iPhone 12 Pro Max',
    'iPhone 13', 'iPhone 13 Mini', 'iPhone 13 Pro', 'iPhone 13 Pro Max',
    'iPhone 14', 'iPhone 14 Plus', 'iPhone 14 Pro', 'iPhone 14 Pro Max',
    'iPhone 15', 'iPhone 15 Plus', 'iPhone 15 Pro', 'iPhone 15 Pro Max'
  ]
  return iPhoneModels.some(model => navigator.userAgent.includes(model))
}

// 获取底部安全区域高度
export function getSafeAreaBottom(): number {
  if (!isIOS()) return 0

  // 使用 CSS env 变量获取安全区域
  const safeArea = getComputedStyle(document.documentElement)
    .getPropertyValue('env(safe-area-inset-bottom)')

  if (safeArea && safeArea !== 'env(safe-area-inset-bottom)') {
    return parseInt(safeArea, 10) || 0
  }

  // 回退方案：根据屏幕比例判断
  const screenRatio = window.screen.height / window.screen.width
  if (screenRatio > 2 && isNotchIPhone()) {
    return 34 // iPhone X+ 底部安全区域
  }

  return 0
}

// 触摸事件优化 - 防止 300ms 延迟
export function setupFastClick() {
  // 使用 touch-action CSS 属性
  document.addEventListener('touchstart', () => {}, { passive: true })
}

// 禁止页面缩放（可选，根据需求开启）
export function disableZoom() {
  const meta = document.querySelector('meta[name="viewport"]')
  if (meta) {
    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover')
  }
}

// 启用页面缩放
export function enableZoom() {
  const meta = document.querySelector('meta[name="viewport"]')
  if (meta) {
    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, viewport-fit=cover')
  }
}

// 监听屏幕方向变化
export function onOrientationChange(callback: (isLandscape: boolean) => void) {
  const handler = () => {
    const isLandscape = window.innerWidth > window.innerHeight
    callback(isLandscape)
  }

  window.addEventListener('orientationchange', handler)
  window.addEventListener('resize', handler)

  // 立即执行一次
  handler()

  return () => {
    window.removeEventListener('orientationchange', handler)
    window.removeEventListener('resize', handler)
  }
}

// 防止键盘弹出时页面滚动
export function preventKeyboardScroll() {
  if (!isIOS()) return

  const inputs = document.querySelectorAll('input, textarea')
  inputs.forEach((input) => {
    input.addEventListener('focus', () => {
      setTimeout(() => {
        input.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }, 300)
    })
  })
}

// 初始化所有移动端适配
export function initMobileAdapter() {
  setupSafeArea()
  setupFastClick()

  // 根据需求决定是否禁止缩放
  // disableZoom()
}
