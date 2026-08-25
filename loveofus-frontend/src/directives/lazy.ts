import type { Directive, DirectiveBinding } from 'vue'

interface LazyOptions {
  src: string
  error?: string
  loading?: string
}

// 图片懒加载指令
const lazyDirective: Directive = {
  mounted(el: HTMLImageElement, binding: DirectiveBinding<LazyOptions | string>) {
    const options: LazyOptions = typeof binding.value === 'string'
      ? { src: binding.value }
      : binding.value

    if (!options.src) return

    // 设置占位图
    if (options.loading) {
      el.src = options.loading
    }

    // 使用 IntersectionObserver 实现懒加载
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const img = new Image()
            img.src = options.src
            img.onload = () => {
              el.src = options.src
              el.classList.add('lazy-loaded')
            }
            img.onerror = () => {
              if (options.error) {
                el.src = options.error
              }
              el.classList.add('lazy-error')
            }
            observer.unobserve(el)
          }
        })
      },
      {
        rootMargin: '50px 0px', // 提前 50px 开始加载
        threshold: 0.01
      }
    )

    observer.observe(el)

    // 保存 observer 用于卸载时清理
    ;(el as any)._lazyObserver = observer
  },

  unmounted(el: HTMLImageElement) {
    const observer = (el as any)._lazyObserver
    if (observer) {
      observer.disconnect()
    }
  }
}

export default lazyDirective
