<template>
  <div class="vip-order-page">
    <van-nav-bar title="确认订单" left-arrow fixed placeholder @click-left="onBack" />

    <div class="order-content">
      <div class="order-card">
        <div class="order-card-top">
          <span class="order-tier-name">{{ orderName }}</span>
          <span class="order-price"><em>{{ priceText }}</em>元</span>
        </div>
        <div class="order-duration">{{ orderDescription }}</div>
      </div>

      <div class="section-title">选择支付方式</div>
      <van-cell-group inset>
        <van-cell
          v-for="method in payMethods"
          :key="method.key"
          :title="method.name"
          is-link
          clickable
          @click="onSelectPay(method)"
        >
          <template #icon>
            <span class="pay-icon">{{ method.icon }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <div class="order-tip">{{ orderTip }}</div>
    </div>

    <!-- 存在未开通订单时的二次确认弹窗（受控，确保按钮始终渲染） -->
    <van-dialog
      v-model:show="showPendingHint"
      title="存在未开通订单"
      show-cancel-button
      cancel-button-text="取消"
      confirm-button-text="继续下单"
      :message="pendingHintMessage"
      message-align="left"
      @cancel="onPendingCancel"
      @confirm="onPendingConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import {
  getVipTiers,
  createVipOrder,
  createQuotaOrder,
  loadVipPricing,
  type VipTier,
  type VipOrderResult,
  type PendingOrderHint,
  type VipPricingItem,
} from '@/api/vip'
import type { BusinessError } from '@/utils/request'

interface PayMethod {
  key: string
  name: string
  icon: string
  /** 是否已接入，未接入的渠道确认订单后提示开发中 */
  available: boolean
}

interface QuotaProduct {
  type: number
  name: string
  /** 单价（元/次），从后端拉取 */
  unitPrice: number
}

const route = useRoute()
const router = useRouter()

const tiers = ref<VipTier[]>([])
const quotaProducts = ref<QuotaProduct[]>([])
const submitting = ref(false)
const showPendingHint = ref(false)
const pendingHint = ref<PendingOrderHint | null>(null)
const pendingHintMessage = ref('')
let pendingConfirmResolver: ((ok: boolean) => void) | null = null

function buildPendingMessage(hint: PendingOrderHint): string {
  const createdText = hint.createdAt
    ? hint.createdAt.replace('T', ' ').slice(0, 19)
    : '--'
  const typeName = hint.orderType === 2 ? '次卡' : 'VIP 卡'
  const product = hint.orderType === 2
    ? `${hint.quotaTypeName || '次卡'} x ${hint.quotaCount ?? '--'} 次`
    : hint.vipLevelName
  return (
    `订单号：${hint.orderNo}\n` +
    `订单类型：${typeName}\n` +
    `订单内容：${product}\n` +
    `订单金额：${formatPrice(hint.priceYuan)} 元\n` +
    `申请时间：${createdText}\n\n` +
    `继续下单后，旧订单将自动取消。是否继续？`
  )
}

function onPendingCancel() {
  if (pendingConfirmResolver) {
    const r = pendingConfirmResolver
    pendingConfirmResolver = null
    r(false)
  }
}

function onPendingConfirm() {
  if (pendingConfirmResolver) {
    const r = pendingConfirmResolver
    pendingConfirmResolver = null
    r(true)
  }
}

/** 用受控 dialog 等待用户选择，返回 true=继续下单，false=取消 */
function askToConfirmPending(hint: PendingOrderHint): Promise<boolean> {
  pendingHint.value = hint
  pendingHintMessage.value = buildPendingMessage(hint)
  showPendingHint.value = true
  return new Promise<boolean>((resolve) => {
    pendingConfirmResolver = resolve
  })
}

const level = computed(() => Number(route.query.level) || 0)
const tier = computed(() => tiers.value.find(item => item.level === level.value) || null)
const orderType = computed(() => Number(route.query.orderType) === 2 ? 2 : 1)
const isQuotaOrder = computed(() => orderType.value === 2)
const quotaType = computed(() => Number(route.query.quotaType) || 0)
const quotaCount = computed(() => Number(route.query.quotaCount) || 0)
const quotaProduct = computed(() => quotaProducts.value.find(item => item.type === quotaType.value) || null)
const isQuotaValid = computed(() => quotaProduct.value !== null && quotaCount.value > 0)

const orderName = computed(() => {
  if (isQuotaOrder.value) return quotaProduct.value?.name || '--'
  return tier.value?.name || '--'
})

const orderAmount = computed(() => {
  if (isQuotaOrder.value) {
    const unitPrice = quotaProduct.value?.unitPrice || 0
    return Math.round(unitPrice * quotaCount.value * 100) / 100
  }
  return tier.value?.priceYuan || 0
})

const priceText = computed(() => orderName.value === '--' ? '--' : formatPrice(orderAmount.value))

const orderDescription = computed(() => {
  if (isQuotaOrder.value) {
    if (!isQuotaValid.value) return '次卡信息缺失，请重新选择'
    return `单价 ${formatPrice(quotaProduct.value!.unitPrice)} 元/次 · 数量 ${quotaCount.value} 次`
  }
  return '有效期 ' + (tier.value ? tier.value.durationText : '--')
})

const orderTip = computed(() => isQuotaOrder.value
  ? '下单后需联系专属顾问完成支付，由顾问为您开通次卡，次数到账后即可使用'
  : '下单后需联系专属顾问完成支付，由顾问为您开通 VIP，权益双人同享'
)

const payMethods: PayMethod[] = [
  { key: 'qq', name: 'QQ', icon: '🐧', available: false },
  { key: 'wechat', name: '微信', icon: '💬', available: false },
  { key: 'alipay', name: '支付宝', icon: '💙', available: false },
  { key: 'email', name: '邮箱', icon: '✉️', available: true }
]

function formatPrice(value: number): string {
  return value.toFixed(2).replace(/\.?0+$/, '')
}

/**
 * 把后端 vip_pricing 行（quotaType 在 1-3）转换为前端次卡列表，
 * 不显示后端未配置或类型不对应的条目。
 */
function buildQuotaProducts(items: VipPricingItem[]): QuotaProduct[] {
  return items
    .filter((item) => item.productType === 2 && item.quotaType != null)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map((item) => ({
      type: item.quotaType as number,
      name: item.productName,
      unitPrice: Number(item.priceYuan) || 0,
    }))
}

/** 将订单各字段拼接成纯文本；价格行用 <strong> 标记，由调用方配合 allowHtml 渲染以起到强调作用 */
function buildConfirmMessage(method: PayMethod): string {
  if (isQuotaOrder.value) {
    return (
      `订单类型：次卡
` +
      `充值类型：${quotaProduct.value!.name}
` +
      `单价：${formatPrice(quotaProduct.value!.unitPrice)} 元/次
` +
      `充值次数：${quotaCount.value} 次
` +
      `<strong>应付金额：${formatPrice(orderAmount.value)} 元</strong>
` +
      `付款方式：${method.name}`
    )
  }
  return (
    `订单类型：VIP 卡
` +
    `开通档位：${tier.value!.name}
` +
    `<strong>付费金额：${tier.value!.priceYuan} 元</strong>
` +
    `付款方式：${method.name}
` +
    `有效期：${tier.value!.durationText}`
  )
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/vip')
}

async function submitOrder(confirmOldOrder: boolean): Promise<VipOrderResult> {
  if (isQuotaOrder.value) {
    return createQuotaOrder(quotaType.value, quotaCount.value, confirmOldOrder)
  }
  return createVipOrder(level.value, confirmOldOrder)
}

async function submitAndNotify(confirmOldOrder: boolean) {
  const order = await submitOrder(confirmOldOrder)
  await showDialog({
    title: '下单成功',
    message: `订单号 ${order.orderNo}，请联系专属顾问 ${order.advisorEmail}，在 1 个工作日内完成下单流程。`,
    confirmButtonText: '我知道了',
  })
  router.replace('/vip')
}

async function onSelectPay(method: PayMethod) {
  if (submitting.value) return
  if (isQuotaOrder.value && !isQuotaValid.value) {
    showToast('次卡信息缺失，请重新选择')
    return
  }
  if (!isQuotaOrder.value && !tier.value) {
    showToast('档位信息缺失，请重新选择')
    return
  }

  try {
    await showConfirmDialog({
      title: '确认订单',
      message: buildConfirmMessage(method),
      messageAlign: 'left',
      allowHtml: true,
      confirmButtonText: '确认下单',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }

  if (!method.available) {
    showToast('正在开发中')
    return
  }

  submitting.value = true
  try {
    await submitAndNotify(false)
  } catch (error) {
    const businessError = error as BusinessError
    if (businessError?.code === 409) {
      const hint = businessError.data as PendingOrderHint | null
      if (!hint) {
        console.error('提交订单失败:', error)
        return
      }
      const confirmed = await askToConfirmPending(hint)
      if (!confirmed) return
      try {
        await submitAndNotify(true)
      } catch (retryError) {
        console.error('确认后提交订单失败:', retryError)
      }
      return
    }
    console.error('提交订单失败:', error)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 开卡页进价：VIP 档位从 /vip/tiers，次卡单价从 /vip/pricing
  const tasks: Promise<unknown>[] = [loadVipPricing().then((items) => {
    quotaProducts.value = buildQuotaProducts(items)
  })]

  if (!isQuotaOrder.value) {
    tasks.push(getVipTiers().then((list) => {
      tiers.value = list
    }))
  }

  const results = await Promise.allSettled(tasks)
  results.forEach((r, idx) => {
    if (r.status === 'rejected') {
      console.error('加载 VIP 价格失败:', r.reason, 'idx=', idx)
    }
  })

  if (isQuotaOrder.value) {
    if (!isQuotaValid.value) {
      showToast('次卡信息缺失，请重新选择')
      router.replace('/vip')
    }
    return
  }

  if (!tiers.value.some(item => item.level === level.value)) {
    showToast('档位信息缺失，请重新选择')
    router.replace('/vip')
  }
})
</script>

<style scoped lang="scss">
.vip-order-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 24px;
}

.order-content {
  padding: 12px;
}

.order-card {
  background: linear-gradient(135deg, #3d3226, #6b5636);
  color: $text-white;
  border-radius: $radius-lg;
  padding: 16px;

  .order-card-top {
    display: flex;
    align-items: center;
  }

  .order-tier-name {
    font-size: 17px;
    font-weight: 600;
  }

  .order-price {
    margin-left: auto;
    font-size: 12px;

    em {
      font-style: normal;
      font-size: 22px;
      font-weight: 600;
      margin-right: 2px;
    }
  }

  .order-duration {
    font-size: 12px;
    opacity: 0.85;
    margin-top: 6px;
  }
}

.section-title {
  font-size: 14px;
  color: $text-secondary;
  margin: 16px 0 8px 4px;
}

.pay-icon {
  margin-right: 10px;
  font-size: 16px;
}

.order-tip {
  font-size: 12px;
  color: $text-tertiary;
  text-align: center;
  margin-top: 16px;
}
</style>

