<template>
  <div class="point-order-page">
    <van-nav-bar title="确认订单" left-arrow fixed placeholder @click-left="onBack" />

    <div class="order-content">
      <div class="order-card">
        <div class="order-card-top">
          <span class="order-tier-name">{{ product?.name || '--' }}</span>
          <span v-if="orderAmount > 0" class="order-price"><em>{{ priceText }}</em>元</span>
        </div>
        <div class="order-duration">{{ orderDescription }}</div>
      </div>

      <div class="address-card" @click="goChooseAddress">
        <div class="address-icon">📍</div>
        <div class="address-info">
          <template v-if="address">
            <div class="address-receiver">
              <span class="addr-name">{{ address.receiverName }}</span>
              <span class="addr-phone">{{ address.receiverPhone }}</span>
            </div>
            <div class="address-full">{{ address.province }}{{ address.city }}{{ address.detailAddress }}</div>
          </template>
          <div v-else class="address-empty">请选择收货地址</div>
        </div>
        <van-icon name="arrow" class="address-arrow" />
      </div>

      <template v-if="isPaidProduct">
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

        <div class="order-tip">下单后需联系专属顾问完成支付，由顾问确认收款后发放商品并扣除积分</div>
      </template>

      <div v-else class="footer-bar">
        <van-button block round type="primary" :loading="submitting" @click="onConfirmExchange">确认兑换</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import { listPointProducts, exchangePointProduct, type PointProductVO } from '@/api/pointProduct'
import { listAddresses, type ShippingAddress } from '@/api/address'

interface PayMethod {
  key: string
  name: string
  icon: string
  /** 是否已接入，未接入的渠道确认订单后提示开发中 */
  available: boolean
}

const route = useRoute()
const router = useRouter()

const product = ref<PointProductVO | null>(null)
const address = ref<ShippingAddress | null>(null)
const submitting = ref(false)

const productId = computed(() => Number(route.query.productId) || 0)
const quantity = computed(() => Number(route.query.quantity) || 1)
const addressId = computed(() => Number(route.query.addressId) || 0)

const payMethods: PayMethod[] = [
  { key: 'qq', name: 'QQ', icon: '🐧', available: false },
  { key: 'wechat', name: '微信', icon: '💬', available: false },
  { key: 'alipay', name: '支付宝', icon: '💙', available: false },
  { key: 'email', name: '邮箱', icon: '✉️', available: true }
]

function formatPrice(value: number): string {
  return value.toFixed(2).replace(/\.?0+$/, '')
}

/** 促销是否生效：开启且配置了促销积分或促销金额 */
function hasPromo(p: PointProductVO) {
  return p.promoStatus === 1 && (p.promoPoints != null || p.promoAmount != null)
}

/** 生效积分：促销开启且有促销积分时取促销值 */
function effectivePoints(p: PointProductVO) {
  return hasPromo(p) && p.promoPoints != null ? p.promoPoints : p.pointsCost
}

/** 生效金额：促销开启时取「促销金额 ?: 原金额」，否则取原金额；无金额返回 0 */
function effectiveAmount(p: PointProductVO): number {
  const raw = hasPromo(p) ? (p.promoAmount ?? p.priceAmount) : p.priceAmount
  if (raw == null) return 0
  const num = Number(raw)
  return num > 0 ? num : 0
}

/** 应付金额合计（单份 × 数量） */
const orderAmount = computed(() => {
  if (!product.value) return 0
  return Math.round(effectiveAmount(product.value) * quantity.value * 100) / 100
})

const priceText = computed(() => product.value ? formatPrice(orderAmount.value) : '--')

/** 含金额商品才需要线下支付环节，纯积分商品直接确认兑换 */
const isPaidProduct = computed(() => orderAmount.value > 0)

const orderDescription = computed(() => {
  if (!product.value) return '商品信息缺失，请重新选择'
  const points = effectivePoints(product.value) * quantity.value
  return `需 ${points} 积分 · 数量 ${quantity.value} 件`
})

/** 将订单各字段拼接成纯文本；价格行用 <strong> 标记，由调用方配合 allowHtml 渲染以起到强调作用 */
function buildConfirmMessage(method: PayMethod): string {
  return (
    `订单类型：积分商品\n` +
    `商品名称：${product.value!.name}\n` +
    `兑换数量：${quantity.value} 件\n` +
    `抵扣积分：${effectivePoints(product.value!) * quantity.value} 积分\n` +
    `收货地址：${address.value!.receiverName} ${address.value!.province}${address.value!.city}${address.value!.detailAddress}\n` +
    `<strong>需付款：${formatPrice(orderAmount.value)} 元</strong>\n` +
    `付款方式：${method.name}`
  )
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/points-shop')
}

function goChooseAddress() {
  router.push({
    path: '/addresses',
    query: { select: '1', productId: String(productId.value), quantity: String(quantity.value) }
  })
}

async function onSelectPay(method: PayMethod) {
  if (submitting.value) return
  if (!product.value) {
    showToast('商品信息缺失，请重新选择')
    return
  }
  if (!address.value) {
    showToast('请选择收货地址')
    goChooseAddress()
    return
  }

  try {
    await showConfirmDialog({
      title: '确认订单',
      message: buildConfirmMessage(method),
      messageAlign: 'left',
      allowHtml: true,
      confirmButtonText: '确认下单',
      cancelButtonText: '再想想'
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
    const order = await exchangePointProduct(productId.value, quantity.value, addressId.value)
    await showDialog({
      title: '下单成功',
      message: `订单号 ${order.orderNo}，请联系专属顾问 ${order.advisorEmail}，在 1 个工作日内完成订单流程。`,
      confirmButtonText: '我知道了'
    })
    router.replace('/points-shop')
  } catch (error) {
    showToast((error as Error)?.message || '下单失败')
  } finally {
    submitting.value = false
  }
}

/** 纯积分商品：确认收货地址后直接兑换 */
async function onConfirmExchange() {
  if (submitting.value) return
  if (!product.value) {
    showToast('商品信息缺失，请重新选择')
    return
  }
  if (!address.value) {
    showToast('请选择收货地址')
    goChooseAddress()
    return
  }

  const message =
    `商品名称：${product.value.name}\n` +
    `兑换数量：${quantity.value} 件\n` +
    `抵扣积分：${effectivePoints(product.value) * quantity.value} 积分\n` +
    `收货地址：${address.value.receiverName} ${address.value.province}${address.value.city}${address.value.detailAddress}`
  try {
    await showConfirmDialog({
      title: '确认兑换',
      message,
      messageAlign: 'left',
      confirmButtonText: '确认兑换',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }

  submitting.value = true
  try {
    await exchangePointProduct(productId.value, quantity.value, addressId.value)
    showToast('兑换成功')
    router.replace('/points-shop')
  } catch (error) {
    showToast((error as Error)?.message || '兑换失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (!productId.value) {
    showToast('商品信息缺失，请重新选择')
    router.replace('/points-shop')
    return
  }

  // 未携带收货地址时回到地址选择页
  if (!addressId.value) {
    goChooseAddress()
    return
  }

  try {
    const list = await listPointProducts()
    product.value = (list || []).find(item => item.id === productId.value) || null
  } catch (error) {
    console.error('加载积分商品失败:', error)
  }

  try {
    const addrList = (await listAddresses()) || []
    address.value = addrList.find(item => item.id === addressId.value) || null
  } catch {
    address.value = null
  }

  if (!product.value) {
    showToast('商品信息缺失，请重新选择')
    router.replace('/points-shop')
    return
  }
  if (!address.value) {
    showToast('收货地址不存在，请重新选择')
    goChooseAddress()
  }
})
</script>

<style scoped lang="scss">
.point-order-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 24px;
}

.order-content {
  padding: 12px;
}

.order-card {
  background: linear-gradient(135deg, #e8437f, #ff8fa3);
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

.address-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: $radius-lg;
  padding: 14px 16px;
  margin-top: 12px;

  .address-icon {
    font-size: 20px;
    margin-right: 12px;
  }

  .address-info {
    flex: 1;
    min-width: 0;

    .address-receiver {
      display: flex;
      align-items: center;
      gap: 8px;

      .addr-name {
        font-size: 15px;
        font-weight: 600;
        color: $text-primary;
      }

      .addr-phone {
        font-size: 13px;
        color: $text-secondary;
      }
    }

    .address-full {
      font-size: 13px;
      color: $text-secondary;
      margin-top: 4px;
      line-height: 1.5;
    }

    .address-empty {
      font-size: 14px;
      color: $text-tertiary;
    }
  }

  .address-arrow {
    color: $text-tertiary;
    margin-left: 8px;
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

.footer-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
}
</style>
