<template>
  <div class="points-shop-page">
    <van-nav-bar title="积分商城" left-arrow fixed placeholder @click-left="onBack" />

    <!-- 顶部积分卡 -->
    <div class="points-banner">
      <div class="banner-icon">💎</div>
      <div class="banner-info">
        <div class="banner-points">{{ points }}</div>
        <div class="banner-label">我的心动积分</div>
      </div>
      <van-button size="small" plain round @click="goCheckIn">
        每日签到 →
      </van-button>
    </div>

    <!-- 兑换记录入口 -->
    <div class="history-entry" @click="openHistory">
      <van-icon name="orders-o" />
      <span>我的兑换记录</span>
      <van-icon name="arrow" />
    </div>

    <!-- 商品网格 -->
    <div class="product-grid" v-if="!loading && products.length > 0">
      <div
        v-for="p in products"
        :key="p.id"
        class="product-card"
        :class="{ 'out-of-stock': !p.inStock }"
      >
        <div class="product-cover">
          <img v-if="p.imageUrl" :src="p.imageUrl" :alt="p.name" />
          <div v-else class="product-cover-fallback">🎁</div>
          <span v-if="hasPromo(p)" class="promo-badge">促销</span>
        </div>
        <div class="product-info">
          <div class="product-name" :title="p.name">{{ p.name }}</div>
          <div class="product-desc" v-if="p.description">{{ p.description }}</div>

          <!-- 价格：生效价在上，促销时原价缩小并加斜线置于右下角 -->
          <div class="price-box">
            <div class="price-current">
              <span class="cost-points">{{ effectivePoints(p) }}<i>积分</i></span>
              <template v-if="effectiveAmount(p) !== null">
                <span class="cost-plus">+</span>
                <span class="cost-amount">¥{{ formatAmount(effectiveAmount(p)) }}</span>
              </template>
            </div>
            <div v-if="hasPromo(p)" class="price-original">
              <span class="original-text">{{ originalPriceText(p) }}</span>
            </div>
          </div>

          <div class="stock-hint">库存：{{ stockText(p) }}</div>

          <van-button
            size="mini"
            round
            type="primary"
            block
            :disabled="!p.inStock"
            @click="onExchange(p)"
          >
            {{ actionText(p) }}
          </van-button>
        </div>
      </div>
    </div>
    <EmptyState v-else-if="!loading" text="商品筹备中，敬请期待" />
    <div v-else class="loading-block"><van-loading size="24" /></div>

    <!-- 兑换记录弹窗 -->
    <van-popup v-model:show="showHistory" round position="bottom" :style="{ height: '70%' }">
      <div class="history-popup">
        <div class="popup-header">
          <h3>我的兑换记录</h3>
        </div>
        <div v-if="exchanges.length === 0" class="empty-tip">还没有兑换记录</div>
        <div v-else class="history-list">
          <div v-for="e in exchanges" :key="e.id" class="history-item">
            <div class="history-top">
              <div class="history-name">{{ e.productName }} x{{ e.quantity }}</div>
              <span class="history-status" :class="statusClass(e.status)">{{ statusText(e.status) }}</span>
            </div>
            <div class="history-meta">
              <span>
                -{{ e.totalPoints }} 积分
                <template v-if="e.priceAmount"> / ¥{{ formatAmount(e.priceAmount) }}</template>
              </span>
              <span class="history-time">{{ formatTime(e.createdAt) }}</span>
            </div>
            <div v-if="e.orderNo" class="history-order">订单号：{{ e.orderNo }}</div>
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import EmptyState from '@/components/EmptyState.vue'
import { listPointProducts, myExchanges, type PointProductVO, type PointExchangeVO } from '@/api/pointProduct'
import { getCheckInStatus } from '@/api/points'

const router = useRouter()
const products = ref<PointProductVO[]>([])
const exchanges = ref<PointExchangeVO[]>([])
const loading = ref(false)
const showHistory = ref(false)
const points = ref(0)

async function loadAll() {
  loading.value = true
  try {
    const [prodRes, statusRes] = await Promise.all([
      listPointProducts(),
      getCheckInStatus()
    ])
    products.value = prodRes || []
    points.value = statusRes?.points ?? 0
  } catch (e) {
    console.error('加载积分商城失败', e)
  } finally {
    loading.value = false
  }
}

/** 促销是否生效：开启且配置了促销积分或促销金额 */
function hasPromo(p: PointProductVO) {
  return p.promoStatus === 1 && (p.promoPoints != null || p.promoAmount != null)
}

/** 生效积分：促销开启且有促销积分时取促销值 */
function effectivePoints(p: PointProductVO) {
  return hasPromo(p) && p.promoPoints != null ? p.promoPoints : p.pointsCost
}

/** 生效金额：促销开启时取「促销金额 ?: 原金额」，否则取原金额；无金额返回 null */
function effectiveAmount(p: PointProductVO): number | null {
  const raw = hasPromo(p) ? (p.promoAmount ?? p.priceAmount) : p.priceAmount
  if (raw == null) return null
  const num = Number(raw)
  return num > 0 ? num : null
}

/** 促销开启时展示在右下角的原价文案 */
function originalPriceText(p: PointProductVO) {
  const amount = p.priceAmount != null && Number(p.priceAmount) > 0
    ? ` + ¥${formatAmount(p.priceAmount)}`
    : ''
  return `${p.pointsCost} 积分${amount}`
}

function stockText(p: PointProductVO) {
  return p.stock === -1 ? '不限' : `${p.stock} 件`
}

function actionText(p: PointProductVO) {
  if (!p.inStock) return '缺货'
  return '兑换下单'
}

function formatAmount(v: number | string | null | undefined) {
  if (v == null) return '0'
  const num = Number(v)
  return Number.isInteger(num) ? String(num) : num.toFixed(2)
}

function statusText(status: number) {
  if (status === 0) return '待确认'
  if (status === 2) return '已取消'
  return '已发放'
}

function statusClass(status: number) {
  if (status === 0) return 'pending'
  if (status === 2) return 'canceled'
  return 'done'
}

async function onExchange(p: PointProductVO) {
  if (!p.inStock) return
  const needPoints = effectivePoints(p)

  if (points.value < needPoints) {
    showToast('积分不足，先去签到攒积分吧')
    return
  }

  // 所有商品兑换均先选择收货地址，再进入确认订单页下单
  router.push({ path: '/addresses', query: { select: '1', productId: String(p.id), quantity: '1' } })
}

async function loadExchanges() {
  try {
    exchanges.value = (await myExchanges()) || []
  } catch {
    // 记录加载失败不打扰用户
  }
}

function openHistory() {
  showHistory.value = true
  loadExchanges()
}

function goCheckIn() {
  router.push('/profile')
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

function formatTime(s: string) {
  return s ? String(s).replace('T', ' ').slice(0, 16) : ''
}

onMounted(() => {
  loadAll()
  // 兑换记录请求后立即查询，避免弹窗打开瞬间空白
  loadExchanges()
})
</script>

<style scoped lang="scss">
.points-shop-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 32px;
}

.points-banner {
  margin: 12px 16px;
  padding: 20px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff9a9e 100%);
  border-radius: 14px;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 16px;

  .banner-icon {
    font-size: 44px;
  }
  .banner-info {
    flex: 1;
    .banner-points {
      font-size: 30px;
      font-weight: 700;
      line-height: 1.1;
    }
    .banner-label {
      font-size: 13px;
      opacity: 0.9;
      margin-top: 2px;
    }
  }
}

.history-entry {
  margin: 12px 16px;
  padding: 14px 16px;
  background: #fff;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #333;
  font-size: 14px;
  span { flex: 1; }
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 0 16px;
}

.product-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: transform 0.2s;

  &.out-of-stock {
    opacity: 0.55;
  }
  &:active {
    transform: scale(0.98);
  }
}

.product-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe0e0 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .product-cover-fallback {
    font-size: 60px;
    opacity: 0.7;
  }
  .promo-badge {
    position: absolute;
    top: 8px;
    left: 8px;
    padding: 2px 8px;
    border-radius: 10px;
    background: linear-gradient(135deg, #ff6b6b 0%, #ff3d3d 100%);
    color: #fff;
    font-size: 11px;
    font-weight: 600;
  }
}

.product-info {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.product-name {
  font-size: 14px;
  font-weight: 600;
  color: #23272f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-desc {
  font-size: 12px;
  color: #8a9099;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 28px;
  line-height: 14px;
}

/* 价格区：生效价 + 促销时的原价（右下角、缩小、带左上到右下斜线） */
.price-box {
  position: relative;
  display: flex;
  align-items: flex-end;
  min-height: 34px;
}

.price-current {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: #ff6b6b;
  .cost-points {
    font-size: 18px;
    font-weight: 700;
    i {
      font-size: 11px;
      font-style: normal;
      margin-left: 1px;
      font-weight: 500;
    }
  }
  .cost-plus {
    font-size: 12px;
    color: #ff9a9e;
  }
  .cost-amount {
    font-size: 15px;
    font-weight: 700;
  }
}

.price-original {
  margin-left: auto;
  align-self: flex-end;
  position: relative;
  .original-text {
    font-size: 10px;
    color: #b8bdc4;
    line-height: 1.2;
  }
  /* 左上到右下的斜线，表示该价格已失效 */
  &::after {
    content: '';
    position: absolute;
    left: -2px;
    right: -2px;
    top: -1px;
    bottom: -1px;
    background-image: linear-gradient(
      to bottom right,
      transparent calc(50% - 0.6px),
      #b8bdc4 calc(50% - 0.6px),
      #b8bdc4 calc(50% + 0.6px),
      transparent calc(50% + 0.6px)
    );
    pointer-events: none;
  }
}

.stock-hint {
  font-size: 11px;
  color: #8a9099;
}

.history-popup {
  padding: 16px 20px;
  .popup-header h3 {
    margin: 0 0 16px;
    font-size: 16px;
    font-weight: 600;
  }
  .empty-tip {
    text-align: center;
    color: #8a9099;
    padding: 40px 0;
  }
  .history-list {
    max-height: 60vh;
    overflow-y: auto;
  }
  .history-item {
    padding: 12px 0;
    border-bottom: 1px solid #f0f2f5;
    &:last-child {
      border-bottom: none;
    }
    .history-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .history-name {
      font-size: 14px;
      color: #23272f;
    }
    .history-status {
      font-size: 11px;
      padding: 1px 8px;
      border-radius: 8px;
      &.pending {
        color: #ff8f1f;
        background: #fff6e8;
      }
      &.done {
        color: #4caf50;
        background: #eefaf0;
      }
      &.canceled {
        color: #8a9099;
        background: #f2f4f7;
      }
    }
    .history-meta {
      font-size: 12px;
      color: #8a9099;
      margin-top: 4px;
      display: flex;
      justify-content: space-between;
      .history-time {
        color: #b8bdc4;
      }
    }
    .history-order {
      font-size: 11px;
      color: #b8bdc4;
      margin-top: 2px;
    }
  }
}

.loading-block {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}
</style>
