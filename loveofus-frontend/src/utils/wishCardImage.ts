import html2canvas from 'html2canvas'
import type { Wish } from '@/api/wishlist'
import { formatDate } from '@/utils/date'

/** 导出倍率：按 3 倍像素渲染，保证图片在聊天/相册中清晰 */
const EXPORT_SCALE = 3

export interface WishCardImage {
  /** 用于 <img> 预览 */
  dataUrl: string
  /** 用于保存 / 分享 / 发送给伴侣 */
  blob: Blob
  filename: string
}

/**
 * 把 HTML 卡片节点（WishCardPoster）截成一张可保存/分享的图片。
 * <p>
 * 卡片照片以同域 blob URL 作为背景图传入，canvas 不会被跨域图片污染，导出必定成功。
 */
export async function renderWishCardImage(el: HTMLElement, wish: Wish): Promise<WishCardImage> {
  await waitForFonts()
  const canvas = await html2canvas(el, {
    scale: EXPORT_SCALE,
    backgroundColor: '#ffffff',
    logging: false,
    useCORS: false
  })
  const blob = await toBlob(canvas)
  return {
    dataUrl: canvas.toDataURL('image/png'),
    blob,
    filename: buildFilename(wish)
  }
}

/** 触发浏览器保存图片（iOS / 微信的处理策略与导出页保持一致） */
export function saveCardImage(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !(window as any).MSStream
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.style.display = 'none'
  if (isIOS) {
    // iOS 必须在当前调用栈内触发，且用 target=_self 避免新窗口拦截
    a.target = '_self'
  }
  document.body.appendChild(a)
  a.click()
  // iOS Safari 需要延迟清理，否则下载会被中断
  setTimeout(() => {
    if (a.parentNode) document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, isIOS ? 1000 : 200)
}

export type ShareResult = 'shared' | 'cancelled' | 'unsupported' | 'failed'

interface ShareDataLike {
  files?: File[]
  title?: string
  text?: string
}

/** 调用系统分享面板分享图片；不支持的环境返回 unsupported，由调用方引导用户长按保存 */
export async function shareCardImage(
  blob: Blob,
  filename: string,
  text: string
): Promise<ShareResult> {
  const nav = navigator as Navigator & {
    share?: (data: ShareDataLike) => Promise<void>
    canShare?: (data?: ShareDataLike) => boolean
  }
  if (typeof nav.share !== 'function' || typeof File === 'undefined') return 'unsupported'

  const file = new File([blob], filename, { type: 'image/png' })
  if (typeof nav.canShare === 'function' && !nav.canShare({ files: [file] })) return 'unsupported'

  try {
    await nav.share({ files: [file], title: '心愿达成', text })
    return 'shared'
  } catch (e: any) {
    return e && e.name === 'AbortError' ? 'cancelled' : 'failed'
  }
}

/** 微信内置浏览器没有下载能力，需要引导用户到外部浏览器 */
export function isWechatBrowser(): boolean {
  return (
    typeof (window as any).WeixinJSBridge !== 'undefined' || /MicroMessenger/i.test(navigator.userAgent)
  )
}

// ==================== 工具 ====================

function toBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('图片生成失败'))
    }, 'image/png')
  })
}

function buildFilename(wish: Wish): string {
  const safeTitle = (wish.title || '心愿')
    .replace(/[\\/:*?"<>|\s]+/g, '_')
    .slice(0, 20)
  const date = formatDate(wish.achievedAt) || formatDate(new Date())
  return `LoveOfUs_心愿达成_${safeTitle}_${date}.png`
}

async function waitForFonts(): Promise<void> {
  const fonts = (document as Document & { fonts?: { ready?: Promise<unknown> } }).fonts
  if (!fonts || !fonts.ready) return
  try {
    await fonts.ready
  } catch {
    // 字体加载失败不阻塞出图
  }
}
