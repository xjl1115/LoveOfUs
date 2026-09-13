<template>
  <div class="vip-order-page">
    <van-nav-bar title="确认订单" left-arrow fixed placeholder @click-left="onBack" />

    <div class="order-content">
      <div class="order-card">
        <div class="order-card-top">
          <span class="order-tier-name">{{ tier ? tier.name : '--' }}</span>
          <span class="order-price"><em>{{ tier ? tier.priceYuan : '--' }}</em>元</span>
        </div>
        <div class="order-duration">有效期 {{ tier ? tier.durationText : '--' }}</div>
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

      <div class="order-tip">下单后需联系专属顾问完成支付，由顾问为您开通 VIP，权益双人同享</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showDialog, showToast } from 'vant'
import { getVipTiers, createVipOrder, type VipTier } from '@/api/vip'

interface PayMethod {
  key: string
  name: string
  icon: string
  /** 是否已接入，未接入的渠道确认订单后提示开发中 */
  available: boolean
}

const route = useRoute()
const router = useRouter()

const tiers = ref<VipTier[]>([])
const submitting = ref(false)

const level = computed(() => Number(route.query.level) || 0)
const tier = computed(() => tiers.value.find(item => item.level === level.value) || null)

const payMethods: PayMethod[] = [
  { key: 'qq', name: 'QQ', icon: '🐧', available: false },
  { key: 'wechat', name: '微信', icon: '💬', available: false },
  { key: 'alipay', name: '支付宝', icon: '💙', available: false },
  { key: 'email', name: '邮箱', icon: '✉️', available: true }
]

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/vip')
}

async function onSelectPay(method: PayMethod) {
  if (submitting.value) return
  const selectedTier = tier.value
  if (!selectedTier) {
    showToast('档位信息缺失，请重新选择')
    return
  }

  // 任何支付方式都先弹框确认订单信息，用户确认后才进行下一步
  try {
    await showConfirmDialog({
      title: '确认订单',
      message: `开通档位：${selectedTier.name}\n付费金额：${selectedTier.priceYuan} 元\n付款方式：${method.name}\n有效期：${selectedTier.durationText}`,
      messageAlign: 'left',
      confirmButtonText: '确认下单',
      cancelButtonText: '再想想'
    })
  } catch {
    // 用户取消，不进行下一步
    return
  }

  // 未接入的支付渠道：确认后统一提示开发中
  if (!method.available) {
    showToast('正在开发中')
    return
  }

  submitting.value = true
  try {
    const order = await createVipOrder(selectedTier.level)
    await showDialog({
      title: '下单成功',
      message: `订单号 ${order.orderNo}，请联系专属顾问 ${order.advisorEmail}，在 1 个工作日内完成下单流程。`,
      confirmButtonText: '我知道了'
    })
    router.replace('/vip')
  } catch (error) {
    console.error('提交 VIP 订单失败:', error)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    tiers.value = await getVipTiers()
  } catch (error) {
    console.error('加载 VIP 档位失败:', error)
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
