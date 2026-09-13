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

    <div v-else class="tier-list">
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
                  <span class="bill-tier">{{ order.vipLevelName }}</span>
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
  type VipTier,
  type VipOrderResult
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
  } finally {
    loading.value = false
  }
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
  loadTiers()
  refreshUserInfo()
})
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
