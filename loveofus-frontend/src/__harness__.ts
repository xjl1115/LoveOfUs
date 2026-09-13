// 临时视觉校验页（校验完即删除，不属于项目代码）
import { createApp, h, ref, onMounted } from 'vue'
import axios from 'axios'
import 'vant/lib/index.css'
import 'amfe-flexible'
import '@/styles/global.scss'
import WishCardPoster from '@/components/WishCardPoster.vue'
import { renderWishCardImage } from '@/utils/wishCardImage'
import type { Wish } from '@/api/wishlist'

const view = new URLSearchParams(location.search).get('view') || 'card'

const wish = {
  id: 1,
  title: '一起去看海边的日出',
  category: 'travel',
  icon: '🏖️',
  targetValue: 3,
  currentValue: 3,
  unit: '次',
  needBothConfirm: 1,
  status: 1,
  achievedAt: '2026-09-11T20:00:00',
  achievedNote: '凌晨四点起床，开到海边刚好赶上日出，风很大但很值得。',
  createdAt: '2026-06-01T10:00:00'
} as unknown as Wish

const PHOTO =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="800">' +
      '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">' +
      '<stop offset="0" stop-color="#ffb36b"/><stop offset="1" stop-color="#7d5fff"/>' +
      '</linearGradient></defs><rect width="600" height="800" fill="url(#g)"/>' +
      '<circle cx="300" cy="300" r="120" fill="#ffffff" opacity="0.6"/></svg>'
  )

// 拦截后端请求，避免校验页触发 401 跳登录
axios.defaults.adapter = async (config: any) => ({
  data: { code: 200, data: [] },
  status: 200,
  statusText: 'OK',
  headers: {},
  config
})

const CardView = {
  setup() {
    const out = ref('')
    const diag = ref('待生成')
    onMounted(async () => {
      const el = document.querySelector('.wish-poster') as HTMLElement | null
      if (!el) return
      const rect = el.getBoundingClientRect()
      const rootFont = getComputedStyle(document.documentElement).fontSize
      diag.value = `视图宽度=${window.innerWidth} 根字号=${rootFont} 卡片=${rect.width.toFixed(1)}x${rect.height.toFixed(1)} 比例=${(rect.height / rect.width).toFixed(3)}`
      try {
        const res = await renderWishCardImage(el, wish)
        out.value = res.dataUrl
        const probe = new Image()
        probe.onload = () => {
          diag.value += `\n导出图片=${probe.width}x${probe.height} 比例=${(probe.height / probe.width).toFixed(3)}`
        }
        probe.src = res.dataUrl
      } catch (e: any) {
        diag.value += `\n导出失败: ${e?.message}`
      }
    })
    return () =>
      h('div', { class: 'record-card-popup' }, [
        h('div', { class: 'record-preview' }, [h(WishCardPoster, { wish, photoUrl: PHOTO })]),
        h('pre', { class: 'diag' }, diag.value),
        out.value ? h('img', { class: 'out', src: out.value }) : null
      ])
  }
}

const PlanView = {
  setup() {
    const Content = ref<any>(null)
    onMounted(async () => {
      const mod = await import('@/components/DatePlanContent.vue')
      Content.value = mod.default
      await new Promise((r) => setTimeout(r, 400))
      const btn = document.querySelector('.section-action-btn') as HTMLElement | null
      btn?.click()
      await new Promise((r) => setTimeout(r, 600))
      const popup = document.querySelector('.add-plan-popup') as HTMLElement | null
      const diag = document.getElementById('diag')
      if (!popup || !diag) return
      const rows = Array.from(popup.children) as HTMLElement[]
      const lines = rows.map((r, i) => {
        const b = r.getBoundingClientRect()
        return `${i} ${r.className.split(' ')[0]} top=${b.top.toFixed(0)} bottom=${b.bottom.toFixed(0)} h=${b.height.toFixed(0)}`
      })
      rows.forEach((r, i) => {
        if (i === 0) return
        const prev = rows[i - 1].getBoundingClientRect()
        const cur = r.getBoundingClientRect()
        if (cur.top < prev.bottom - 0.5) {
          lines.push(`!! 重叠: ${rows[i - 1].className.split(' ')[0]} 与 ${r.className.split(' ')[0]}`)
        }
      })
      diag.textContent =
        `弹窗内容高=${popup.clientHeight} 内容滚动高=${popup.scrollHeight} 溢出=${popup.scrollHeight - popup.clientHeight}\n` +
        lines.join('\n')
    })
    return () =>
      h('div', [
        Content.value ? h(Content.value) : null,
        h('pre', { id: 'diag', class: 'diag' })
      ])
  }
}

createApp(view === 'card' ? CardView : PlanView).mount('#app')
