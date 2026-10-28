<template>
  <div class="vip-page">
    <van-nav-bar
      title="VIP 会员"
      left-arrow
      fixed
      placeholder
      right-text="账单"
      @click-left="onBack"
      @click-right="onOpenBills"
    />

    <div class="vip-banner">
      <div class="banner-title">LoveOfUs VIP</div>
      <div class="banner-subtitle">双人同享，解锁全部情侣专属权益</div>
      <div class="banner-status">
        <template v-if="isVip">
          {{ userInfo?.vipLevelName }}
          <span v-if="userInfo?.vipExpireAt"> · {{ formatExpire(userInfo.vipExpireAt) }} 到期</span>
          <span v-else> · 永久有效</span>
        </template>
        <template v-else>当前为普通用户</template>
      </div>
    </div>

    <div v-if="loading" class="vip-loading">
      <van-loading size="24" vertical>加载中...</van-loading>
    </div>

    <template v-else>
      <!-- 次卡区域（按需求放在周卡之前） -->
      <div class="quota-section">
        <div class="quota-card">
          <div class="quota-card-head">
            <span class="quota-title">次卡</span>
            <span class="quota-subtitle">不想开 VIP？按需购买单次配额</span>
          </div>
          <van-button block round class="quota-btn" @click="onOpenQuotaSheet">
            购买次卡
          </van-button>
        </div>
      </div>

      <div class="tier-list">
      <div
        v-for="tier in tiers"
        :key="tier.level"
        class="tier-card"
        :class="{ 'tier-card-current': tier.level === userInfo?.vipLevel }"
      >
        <div class="tier-head">
          <span class="tier-name">{{ tier.name }}</span>
          <span v-if="tier.level === userInfo?.vipLevel" class="tier-current">当前</span>
          <span class="tier-price"><em>{{ tier.priceYuan }}</em>元</span>
        </div>
        <div class="tier-duration">有效期 {{ tier.durationText }}</div>
        <ul class="tier-benefits">
          <li v-for="(benefit, index) in tier.benefits" :key="index">
            <van-icon name="success" class="benefit-icon" />
            <span>{{ benefit }}</span>
          </li>
        </ul>
        <van-button block round class="tier-btn" :disabled="isForever" @click="onOrder(tier)">
          {{ isForever ? '已是永久会员' : '立即开通' }}
        </van-button>
      </div>
    </div>
    </template>

    <div class="vip-footer">虚拟商品，开通后不支持退款</div>

    <!-- 账单：按情侣组展示全部订单，进行中的订单可自行取消 -->
    <van-popup v-model:show="showBills" round closeable position="bottom" :style="{ height: '70%' }">
      <div class="bill-popup">
        <div class="bill-header">
          <h3>我的账单</h3>
          <p>与 TA 的订单合并在同一个账单里</p>
        </div>

        <div v-if="ordersLoading" class="bill-loading">
          <van-loading size="24" vertical>加载中...</van-loading>
        </div>

        <van-tabs v-else v-model:active="billTab" line-width="20px">
          <van-tab
            v-for="group in billGroups"
            :key="group.name"
            :name="group.name"
            :title="group.title"
          >
            <div class="bill-list">
              <div v-for="order in group.list" :key="order.orderNo" class="bill-card">
                <div class="bill-card-head">
                  <span class="bill-tier">{{ orderTitle(order) }}</span>
                  <span class="bill-price"><em>{{ order.priceYuan }}</em>元</span>
                </div>
                <div class="bill-line">订单号 {{ order.orderNo }}</div>
                <div class="bill-line">下单时间 {{ formatDateTime(order.createdAt) }}</div>
                <div v-if="order.activatedAt" class="bill-line">
                  开通时间 {{ formatDateTime(order.activatedAt) }}
                </div>
                <div class="bill-card-foot">
                  <span class="bill-status" :class="statusClass(order.status)">
                    {{ statusText(order.status) }}
                  </span>
                  <van-button
                    v-if="order.status === ORDER_STATUS_PENDING"
                    size="small"
                    round
                    plain
                    type="primary"
                    :loading="cancellingNo === order.orderNo"
                    @click="onCancelOrder(order)"
                  >
                    取消订单
                  </van-button>
                </div>
              </div>
              <EmptyState v-if="group.list.length === 0" :text="group.emptyText" />
            </div>
          </van-tab>
        </van-tabs>
      </div>
    </van-popup>

    <!-- 次卡：选择类型弹框（受控） -->
    <van-dialog
      v-model:show="showQuotaTypeDialog"
      title="选择充值类型"
      show-cancel-button
      cancel-button-text="取消"
      confirm-button-text="下一步"
      :before-close="onQuotaTypeBeforeClose"
    >
      <div class="quota-type-list">
        <div
          v-for="opt in QUOTA_PRODUCTS"
          :key="opt.type"
          class="quota-type-item"
          :class="{ 'quota-type-item-selected': draftQuotaType === opt.type }"
          @click="draftQuotaType = opt.type"
        >
          <span class="quota-type-name">{{ opt.name }}</span>
          <span class="quota-type-desc">{{ opt.desc }}</span>
          <span class="quota-type-price">{{ formatPrice(opt.unitPrice) }} 元/次</span>
        </div>
      </div>
    </van-dialog>

    <!-- 次卡：次数加减弹框（受控） -->
    <van-dialog
      v-model:show="showQuotaCountDialog"
      title="购买次卡"
      show-cancel-button
      cancel-button-text="取消"
      confirm-button-text="下一步"
      confirm-button-color="#e74c3c"
      :message="quotaCountMessage"
      message-align="left"
      :before-close="onQuotaCountBeforeClose"
    >
      <div class="quota-count-row">
        <span class="quota-count-label">数量</span>
        <div class="quota-stepper">
          <button class="stepper-btn" @click="onStepQuotaCount(-1)">−</button>
          <span class="stepper-value">{{ draftQuotaCount }}</span>
          <button class="stepper-btn" @click="onStepQuotaCount(1)">+</button>
        </div>
      </div>
    </van-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showDialog, showToast } from 'vant'
import {
  getVipTiers,
  getVipOrders,
  cancelVipOrder,
  loadVipPricing,
  type VipTier,
  type VipOrderResult,
  type VipPricingItem,
} from '@/api/vip'
import { getUserInfo } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/date'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const userStore = useUserStore()

const tiers = ref<VipTier[]>([])
const loading = ref(false)

const showBills = ref(false)
const ordersLoading = ref(false)
const orders = ref<VipOrderResult[]>([])
const billTab = ref('pending')
const cancellingNo = ref('')

/** 订单状态：0-待开通，1-已开通，2-已取消 */
const ORDER_STATUS_PENDING = 0
const ORDER_STATUS_ACTIVATED = 1
const ORDER_STATUS_CANCELED = 2

const userInfo = computed(() => userStore.userInfo)
const isVip = computed(() => (userInfo.value?.vipLevel ?? 0) > 0)
const isForever = computed(() => userInfo.value?.vipLevel === 5)

/** 到期时间只展示到日：2026-09-19T12:00:00 -> 2026-09-19 */
function formatExpire(expireAt: string): string {
  return String(expireAt).slice(0, 10)
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/profile')
}

function onOrder(tier: VipTier) {
  router.push({ name: 'VipOrder', query: { level: String(tier.level) } })
}

async function loadTiers() {
  loading.value = true
  try {
    tiers.value = await getVipTiers()
  } catch (error) {
    console.error('加载 VIP 档位失败:', error)
  }

  // 次卡价格也纳入 loading 控制：未加载完前整页持续显示“加载中...”，
  // 避免用户看到“购买次卡”按钮时 QUOTA_PRODUCTS 还是空数组。
  try {
    const items = await loadVipPricing()
    QUOTA_PRODUCTS.value = buildQuotaProducts(items)
  } catch (error) {
    console.error('加载次卡价格失败:', error)
  }

  loading.value = false
}

/** 刷新用户信息，确保页面展示的是最新 VIP 状态 */
async function refreshUserInfo() {
  try {
    const info = await getUserInfo()
    if (info) userStore.setUserInfo(info)
  } catch (error) {
    console.error('刷新用户信息失败:', error)
  }
}

const pendingOrders = computed(() => orders.value.filter(order => order.status === ORDER_STATUS_PENDING))
const finishedOrders = computed(() => orders.value.filter(order => order.status !== ORDER_STATUS_PENDING))

/** 账单分组：进行中只含待开通；已完成含已开通与已取消（取消过的订单也要能看到） */
const billGroups = computed(() => [
  {
    name: 'pending',
    title: `进行中 (${pendingOrders.value.length})`,
    list: pendingOrders.value,
    emptyText: '暂无进行中的订单'
  },
  {
    name: 'finished',
    title: `已完成 (${finishedOrders.value.length})`,
    list: finishedOrders.value,
    emptyText: '暂无已完成的订单'
  }
])

function statusText(status: number): string {
  if (status === ORDER_STATUS_ACTIVATED) return '已开通'
  if (status === ORDER_STATUS_CANCELED) return '已取消'
  return '待开通'
}

function statusClass(status: number): string {
  if (status === ORDER_STATUS_ACTIVATED) return 'bill-status-done'
  if (status === ORDER_STATUS_CANCELED) return 'bill-status-canceled'
  return 'bill-status-pending'
}

function onOpenBills() {
  showBills.value = true
  loadOrders()
}

async function loadOrders() {
  ordersLoading.value = true
  try {
    orders.value = await getVipOrders()
  } catch (error) {
    console.error('加载账单失败:', error)
  } finally {
    ordersLoading.value = false
  }
}

function onCancelOrder(order: VipOrderResult) {
  showDialog({
    title: '取消订单',
    message: `确定取消订单 ${order.orderNo} 吗？取消后如需开通请重新下单。`,
    showCancelButton: true
  }).then(async () => {
    cancellingNo.value = order.orderNo
    try {
      await cancelVipOrder(order.orderNo)
      showToast('订单已取消')
      await loadOrders()
    } catch (error) {
      console.error('取消订单失败:', error)
    } finally {
      cancellingNo.value = ''
    }
  })
}

onMounted(() => {
  // loadTiers 内部已经把次卡价格纳入 loading 控制，无需单独再调一次
  loadTiers()
  refreshUserInfo()
})

/* ==================== 次卡 ==================== */

/** 次卡产品列表（从 /vip/pricing 拉取后填充）；空数组表示加载中或加载失败 */
const QUOTA_PRODUCTS = ref<{ type: number; name: string; desc: string; unitPrice: number }[]>([])

const QUOTA_MIN = 1
const QUOTA_MAX = 99

const showQuotaTypeDialog = ref(false)
const showQuotaCountDialog = ref(false)
const draftQuotaType = ref<number | null>(null)
const draftQuotaCount = ref(1)

/** 把接口返回的全量价格转换为页面用的次卡列表；产品为空时不抛错，仅保留空数组 */
function buildQuotaProducts(items: VipPricingItem[]) {
  return items
    .filter((it) => it.productType === 2 && it.quotaType != null)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map((it) => ({
      type: it.quotaType as number,
      name: it.productName,
      desc: it.description || '适用于对应模块',
      unitPrice: Number(it.priceYuan) || 0,
    }))
}

/** 打开次卡：第一步选择类型 */
async function onOpenQuotaSheet() {
  // 兜底：localStorage 缓存被清空、首次进入接口失败、Redis 缓存空数据等场景，
  // 进入页面时 QUOTA_PRODUCTS 可能仍为空；用户主动点击时再强制刷新一次。
  if (QUOTA_PRODUCTS.value.length === 0) {
    try {
      const items = await loadVipPricing(true)
      QUOTA_PRODUCTS.value = buildQuotaProducts(items)
    } catch (error) {
      console.error('刷新次卡价格失败:', error)
    }
  }
  if (QUOTA_PRODUCTS.value.length === 0) {
    showToast('次卡价格加载中，请稍后再试')
    return
  }
  draftQuotaType.value = QUOTA_PRODUCTS.value[0].type
  draftQuotaCount.value = 1
  showQuotaTypeDialog.value = true
}

/** 类型弹窗"下一步"：手动校验并切换到次数弹窗 */
function onQuotaTypeBeforeClose(action: string): Promise<boolean> {
  if (action === 'confirm') {
    if (draftQuotaType.value == null) {
      showToast('请选择充值类型')
      return Promise.reject()
    }
    showQuotaTypeDialog.value = false
    // 用 $nextTick 等关闭动画后弹出下一个弹框
    setTimeout(() => {
      showQuotaCountDialog.value = true
    }, 200)
    return Promise.reject()
  }
  // cancel 关闭弹窗
  return Promise.resolve(true)
}

/** 次数加减：min..max 范围内 */
function onStepQuotaCount(delta: number) {
  const next = draftQuotaCount.value + delta
  if (next < QUOTA_MIN || next > QUOTA_MAX) return
  draftQuotaCount.value = next
}

const quotaCountMessage = computed(() => {
  const productName =
    QUOTA_PRODUCTS.value.find(p => p.type === draftQuotaType.value)?.name || '次卡'
  return (
    `充值类型：${productName}\n` +
    `充值次数：${draftQuotaCount.value} 次\n` +
    `单价：${formatPrice(selectedQuotaProduct.value?.unitPrice || 0)} 元/次\n` +
    `订单金额：${formatPrice(draftQuotaCount.value * (selectedQuotaProduct.value?.unitPrice || 0))} 元\n\n` +
    `下单后由顾问收款并开通，开通后次数自动到账。`
  )
})

const selectedQuotaProduct = computed(() =>
  QUOTA_PRODUCTS.value.find(p => p.type === draftQuotaType.value) || null
)

/** 金额去掉无意义末尾零：0.5 -> 0.5，0.90 -> 0.9 */
function formatPrice(value: number): string {
  return value.toFixed(2).replace(/\.?0+$/, '')
}

/** 次数确定后进入确认订单页，选择支付方式并确认下单 */
function onQuotaCountBeforeClose(action: string): Promise<boolean> {
  if (action !== 'confirm') return Promise.resolve(true)
  if (draftQuotaType.value == null) {
    showToast('请选择充值类型')
    return Promise.reject()
  }
  showQuotaCountDialog.value = false
  router.push({
    name: 'VipOrder',
    query: {
      orderType: '2',
      quotaType: String(draftQuotaType.value),
      quotaCount: String(draftQuotaCount.value)
    }
  })
  return Promise.reject()
}

/** 账单订单标题：VIP 卡显示档位；次卡显示类型 + 次数 */
function orderTitle(order: VipOrderResult): string {
  if (order.orderType === 2) {
    return `${order.quotaTypeName || '次卡'} x ${order.quotaCount ?? '--'} 次`
  }
  return order.vipLevelName
}
</script>

<style scoped lang="scss">
.vip-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 24px;
}

.vip-banner {
  background: linear-gradient(135deg, #3d3226, #6b5636);
  color: $text-white;
  padding: 24px 16px 28px;

  .banner-title {
    font-size: 20px;
    font-weight: 600;
    letter-spacing: 1px;
  }

  .banner-subtitle {
    font-size: 13px;
    opacity: 0.85;
    margin-top: 6px;
  }

  .banner-status {
    display: inline-block;
    margin-top: 14px;
    padding: 4px 12px;
    font-size: 12px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.16);
  }
}

.vip-loading {
  padding: 60px 0;
  display: flex;
  justify-content: center;
}

.tier-list {
  padding: 12px;
}

.tier-card {
  background: $bg-white;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  padding: 16px;
  margin-bottom: 12px;
  border: 1px solid transparent;

  &.tier-card-current {
    border-color: #c9a227;
  }
}

.tier-head {
  display: flex;
  align-items: center;
  gap: 8px;

  .tier-name {
    font-size: 17px;
    font-weight: 600;
    color: $text-primary;
  }

  .tier-current {
    font-size: 11px;
    color: #c9a227;
    border: 1px solid #c9a227;
    border-radius: 8px;
    padding: 0 6px;
    line-height: 16px;
  }

  .tier-price {
    margin-left: auto;
    font-size: 12px;
    color: $text-secondary;

    em {
      font-style: normal;
      font-size: 20px;
      font-weight: 600;
      color: $primary-color;
      margin-right: 2px;
    }
  }
}

.tier-duration {
  font-size: 12px;
  color: $text-tertiary;
  margin-top: 4px;
}

.tier-benefits {
  list-style: none;
  margin: 12px 0 16px;
  padding: 0;

  li {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    font-size: 13px;
    color: $text-secondary;
    line-height: 22px;
  }

  .benefit-icon {
    color: #c9a227;
    margin-top: 4px;
  }
}

.tier-btn {
  background: linear-gradient(135deg, #3d3226, #6b5636);
  border: none;
  color: $text-white;

  &:disabled {
    opacity: 0.5;
  }
}

.vip-footer {
  text-align: center;
  font-size: 12px;
  color: $text-tertiary;
  padding: 8px 0;
}

/* 次卡区域 */
.quota-section {
  padding: 12px 12px 0;
}
.quota-card {
  background: linear-gradient(135deg, #fff8f5, #ffffff);
  border-radius: $radius-lg;
  border: 1px solid #ffd6c8;
  padding: 14px 16px;
}
.quota-card-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.quota-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}
.quota-subtitle {
  font-size: 12px;
  color: $text-tertiary;
}
.quota-btn {
  background: linear-gradient(135deg, #ff7a5a, #e74c3c);
  border: none;
  color: $text-white;
}

/* 次卡：类型选择 */
.quota-type-list {
  padding: 8px 0;
}
.quota-type-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  border-radius: $radius-md;
  border: 1px solid #eee;
  margin: 8px 16px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.quota-type-item-selected {
  border-color: $primary-color;
  background: $primary-light-bg;
}
.quota-type-name {
  font-size: 15px;
  font-weight: 500;
  color: $text-primary;
}
.quota-type-desc {
  font-size: 12px;
  color: $text-tertiary;
}
.quota-type-price {
  font-size: 13px;
  font-weight: 600;
  color: $primary-color;
}

/* 次卡：次数加减 */
.quota-count-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
}
.quota-count-label {
  font-size: 14px;
  color: $text-secondary;
}
.quota-stepper {
  display: flex;
  align-items: center;
  gap: 12px;
}
.stepper-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid #ddd;
  background: #fff;
  font-size: 18px;
  color: $text-primary;
  cursor: pointer;
  line-height: 1;
}
.stepper-btn:active {
  background: #f5f5f5;
}
.stepper-value {
  min-width: 32px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.bill-popup {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding-bottom: 20px;

  .bill-header {
    flex-shrink: 0;
    text-align: center;
    padding: 18px 16px 10px;

    h3 { margin: 0 0 4px; font-size: 17px; }
    p { margin: 0; font-size: 12px; color: $text-tertiary; }
  }
}

.bill-loading {
  padding: 60px 0;
  display: flex;
  justify-content: center;
}

.bill-list {
  padding: 12px 16px 4px;
}

.bill-card {
  background: $bg-white;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  padding: 14px;
  margin-bottom: 12px;
}

.bill-card-head {
  display: flex;
  align-items: center;

  .bill-tier {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  .bill-price {
    margin-left: auto;
    font-size: 12px;
    color: $text-secondary;

    em {
      font-style: normal;
      font-size: 18px;
      font-weight: 600;
      color: $primary-color;
      margin-right: 2px;
    }
  }
}

.bill-line {
  margin-top: 6px;
  font-size: 12px;
  color: $text-tertiary;
}

.bill-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.bill-status {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  background: $bg-color;
  color: $text-secondary;

  &.bill-status-pending {
    background: $primary-light-bg;
    color: $primary-color;
  }

  &.bill-status-done {
    background: rgba(201, 162, 39, 0.12);
    color: #c9a227;
  }

  &.bill-status-canceled {
    background: $bg-color;
    color: $text-tertiary;
  }
}
</style>
