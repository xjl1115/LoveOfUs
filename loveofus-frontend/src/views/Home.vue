<template>
  <div class="home-page">
    <!-- 顶部导航 -->
    <van-nav-bar fixed placeholder>
      <template #left>
        <div class="nav-logo">
          <span class="logo-icon">❤️</span>
          <span class="logo-text">LoveOfUs</span>
        </div>
      </template>
      <template #right>
        <!-- 私聊 / 上传入口已上移至全局右上角图标组（App.vue 的 TopRightBar），避免重复 -->
      </template>
    </van-nav-bar>

    <!-- 筛选提示 -->
    <div v-if="activeProvince" class="filter-bar">
      <span>📌 正在查看：{{ activeProvince }}</span>
      <van-icon name="cross" @click="clearFilter" />
    </div>

    <!-- 纪念日提醒：进入用户设置的提前提醒期后显示（在地图上方），点击直接查看详情 -->
    <div v-if="dueReminders.length > 0" class="reminder-section">
      <div v-for="item in dueReminders" :key="item.id" class="reminder-item" @click="openAnniversaryDetail(item)">
        <span class="reminder-icon">🔔</span>
        <div class="reminder-main">
          <div class="reminder-title">{{ item.name }}</div>
          <div class="reminder-sub">
            {{ item.anniversaryDate }} ·
            {{ item.daysUntil === 0 ? '就是今天' : `还有 ${item.daysUntil} 天` }}
          </div>
          <div v-if="item.description" class="reminder-desc">{{ item.description }}</div>
        </div>
      </div>
    </div>

    <!-- 中国足迹地图 -->
    <ChinaMap
      :province-data="provinceData"
      :active-province="activeProvince"
      @filter-by-province="handleProvinceFilter"
    />

    <!-- 时间线照片列表 -->
    <div class="timeline-section">
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="!hasMore"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <div
            v-for="group in timelineGroups"
            :key="group.month"
            class="timeline-group"
          >
            <div class="timeline-header">
              <h3>{{ group.monthLabel }}</h3>
              <span class="photo-count">{{ group.photos.length }} 张照片</span>
            </div>
            <div class="photo-grid">
              <div
                v-for="photo in group.photos"
                :key="photo.id"
                class="photo-item"
                @click="viewPhoto(photo.id)"
              >
                <div class="photo-img-wrap">
                  <img
                    v-lazy="{
                      src: photo.storagePath,
                      error: '/images/photo-error.png'
                    }"
                    :alt="photo.description || '照片'"
                    class="lazy-image"
                  />
                </div>
                <div v-if="photo.locationName" class="photo-location">
                  <van-icon name="location-o" />
                  <span>{{ photo.locationName }}</span>
                </div>
              </div>
            </div>
          </div>
        </van-list>
      </van-pull-refresh>
    </div>

    <!-- 纪念日详情弹窗（首页直接查看） -->
    <van-popup
      v-model:show="showAnniversaryDetailPopup"
      round
      closeable
      position="bottom"
      :style="{ height: '60%' }"
    >
      <div class="anniversary-detail-popup" v-if="selectedAnniversary">
        <div class="popup-header">
          <h3>纪念日详情</h3>
        </div>
        <div class="detail-content">
          <div class="detail-countdown-card">
            <div class="countdown-icon">💕</div>
            <div class="countdown-name">{{ selectedAnniversary.name }}</div>
            <div class="countdown-number">
              <span class="countdown-value" :class="{ today: selectedAnniversary.daysUntil === 0 }">
                {{ selectedAnniversary.daysUntil === 0 ? '今天' : (selectedAnniversary.daysUntil ?? 0) }}
              </span>
              <span v-if="selectedAnniversary.daysUntil !== 0" class="countdown-unit">天后</span>
            </div>
            <div class="countdown-date">{{ selectedAnniversary.anniversaryDate }}</div>
          </div>

          <div class="detail-info-list">
            <div class="detail-info-row">
              <van-icon name="clock-o" class="detail-info-icon" />
              <span class="detail-info-label">每年重复</span>
              <span class="detail-info-value">
                <van-tag v-if="selectedAnniversary.isRecurring" type="primary" round>每年重复</van-tag>
                <van-tag v-else type="default" round>单次</van-tag>
              </span>
            </div>
            <div class="detail-info-row">
              <van-icon name="bell" class="detail-info-icon" />
              <span class="detail-info-label">提前提醒</span>
              <span class="detail-info-value">
                {{ selectedAnniversary.remindDays ? selectedAnniversary.remindDays + '天' : '未设置' }}
              </span>
            </div>
            <div class="detail-info-row" v-if="selectedAnniversary.description">
              <van-icon name="notes-o" class="detail-info-icon" />
              <span class="detail-info-label">备注</span>
              <span class="detail-info-value description-value">{{ selectedAnniversary.description }}</span>
            </div>
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 底部 Tab -->
    <BottomTab />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import ChinaMap from '@/components/ChinaMap.vue'
import BottomTab from '@/components/BottomTab.vue'
import { usePhotoStore } from '@/stores/photo'
import { useChatUnreadStore } from '@/stores/chatUnread'
import { getAnniversaryList, type Anniversary } from '@/api/anniversary'
import { getTimelinePhotos } from '@/api/photo'
import { getUserStats } from '@/api/user'
import { scheduleDailyRefresh } from '@/utils/date'
import type { ProvinceData } from '@/types'

const router = useRouter()
const photoStore = usePhotoStore()
const chatUnread = useChatUnreadStore()

const loading = ref(false)
const refreshing = ref(false)
const provinceData = ref<ProvinceData[]>([])

const activeProvince = computed(() => photoStore.activeProvince)
const hasMore = computed(() => photoStore.hasMore)
const timelineGroups = computed(() => photoStore.timelineGroups)

// 纪念日提醒：进入用户为每个纪念日设置的提前提醒期（daysUntil <= remindDays）后展示
const dueReminders = ref<Anniversary[]>([])

async function loadDueReminders() {
  try {
    const list = await getAnniversaryList()
    dueReminders.value = list
      .filter((a) => a.daysUntil != null && a.daysUntil >= 0 && a.daysUntil <= (a.remindDays ?? 0))
      .sort((a, b) => (a.daysUntil ?? 0) - (b.daysUntil ?? 0))
  } catch (error) {
    // 属附加信息，失败不打断首页主流程
    console.error('加载纪念日提醒失败:', error)
  }
}

// 点提醒卡片：直接在首页打开该条纪念日详情（列表接口已带 description / remindDays 等字段）
const showAnniversaryDetailPopup = ref(false)
const selectedAnniversary = ref<Anniversary | null>(null)

function openAnniversaryDetail(item: Anniversary) {
  selectedAnniversary.value = item
  showAnniversaryDetailPopup.value = true
}

let chatUnreadTimer: number | null = null
// 跨天（0 点）自动刷新纪念日提醒，避免倒计时停留在昨天
let cancelDailyRefresh: (() => void) | null = null

onMounted(() => {
  loadUserStats()
  loadPhotos(true)
  loadDueReminders()
  cancelDailyRefresh = scheduleDailyRefresh(loadDueReminders)
  // 修复：SSE chat-unread-count 已在 App.vue 全局订阅，此处仅拉一次最新值 + 30s 轮询兜底
  chatUnread.refresh()
  chatUnreadTimer = window.setInterval(() => chatUnread.refresh(), 30_000)
})

// keep-alive 激活时刷新
onActivated(() => {
  loadUserStats()
  loadPhotos(true)
  loadDueReminders()
  chatUnread.refresh()
})

onBeforeUnmount(() => {
  if (chatUnreadTimer) {
    clearInterval(chatUnreadTimer)
    chatUnreadTimer = null
  }
  cancelDailyRefresh?.()
})

async function loadUserStats() {
  try {
    const stats = await getUserStats()
    provinceData.value = stats.cities || []
  } catch (error) {
    console.error('加载用户统计失败:', error)
  }
}

async function loadPhotos(reset = false) {
  if (loading.value) return
  loading.value = true

  try {
    const page = reset ? 1 : photoStore.currentPage
    const data = await getTimelinePhotos({
      page,
      size: 20,
      province: activeProvince.value || undefined
    })

    if (reset) {
      photoStore.setPhotos(data.list, true)
    } else {
      photoStore.setPhotos(data.list)
      photoStore.incrementPage()
    }
    photoStore.setHasMore(data.hasMore)
  } catch (error) {
    showToast('加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onLoad() {
  loadPhotos()
}

function onRefresh() {
  loadPhotos(true)
}

function handleProvinceFilter(province: string | null) {
  photoStore.setActiveProvince(province)
  loadPhotos(true)
}

function clearFilter() {
  photoStore.setActiveProvince(null)
  loadPhotos(true)
}

function viewPhoto(id: number) {
  router.push(`/photo/${id}`)
}
</script>

<style scoped lang="scss">
.home-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: calc(80px + env(safe-area-inset-bottom));
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 6px;

  .logo-icon {
    font-size: 20px;
  }

  .logo-text {
    font-size: 16px;
    font-weight: 600;
    color: $primary-color;
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $primary-light;
  color: $text-white;
  padding: 10px 16px;
  margin: 8px 16px;
  border-radius: $radius-md;
  font-size: 14px;

  .van-icon {
    font-size: 16px;
    cursor: pointer;
  }
}

// 纪念日提醒区（地图上方）
.reminder-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 12px 16px 0;
}

.reminder-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: transform 0.18s;

  &:active {
    transform: scale(0.98);
  }

  .reminder-icon {
    font-size: 20px;
    flex-shrink: 0;
  }

  .reminder-main {
    flex: 1;
    min-width: 0;
  }

  .reminder-title {
    font-size: 15px;
    font-weight: 600;
    color: #b8821b;
    margin-bottom: 2px;
  }

  .reminder-sub {
    font-size: 12px;
    color: $text-secondary;
  }

  .reminder-desc {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.45;
    color: #b8821b;
    opacity: 0.8;
    word-break: break-all;
  }
}

// 纪念日详情弹窗（与个人页详情保持一致的观感）
.anniversary-detail-popup {
  height: 100%;
  display: flex;
  flex-direction: column;

  .popup-header {
    text-align: center;
    padding: 16px 20px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
      color: $text-primary;
      font-weight: 600;
    }
  }

  .detail-content {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 20px;

    .detail-countdown-card {
      background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
      border-radius: $radius-lg;
      padding: 24px;
      text-align: center;
      color: #fff;
      box-shadow: 0 4px 12px rgba($primary-color, 0.3);

      .countdown-icon {
        font-size: 32px;
        margin-bottom: 8px;
      }

      .countdown-name {
        font-size: 18px;
        font-weight: 600;
        margin-bottom: 12px;
      }

      .countdown-number {
        margin-bottom: 8px;

        .countdown-value {
          font-size: 48px;
          font-weight: 700;
          line-height: 1;

          &.today {
            font-size: 32px;
          }
        }

        .countdown-unit {
          font-size: 16px;
          margin-left: 4px;
          opacity: 0.9;
        }
      }

      .countdown-date {
        font-size: 14px;
        opacity: 0.85;
      }
    }

    .detail-info-list {
      background: #fff;
      border-radius: $radius-md;
      padding: 16px;
      box-shadow: $shadow-sm;

      .detail-info-row {
        display: flex;
        align-items: center;
        padding: 12px 0;
        border-bottom: 1px solid $border-color;

        &:last-child {
          border-bottom: none;
        }

        .detail-info-icon {
          font-size: 18px;
          color: $primary-color;
          margin-right: 12px;
          flex-shrink: 0;
        }

        .detail-info-label {
          font-size: 14px;
          color: $text-secondary;
          min-width: 80px;
        }

        .detail-info-value {
          flex: 1;
          text-align: right;
          font-size: 14px;
          color: $text-primary;

          &.description-value {
            text-align: left;
            margin-left: 12px;
            color: $text-secondary;
            line-height: 1.5;
            word-break: break-all;
          }
        }
      }
    }
  }
}

// 心动入口双卡（约会策划 + 心愿清单）
.love-entries {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 0 16px;
  margin: 12px 0;

  .entry-card {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px;
    background: #fff;
    border-radius: $radius-lg;
    box-shadow: $shadow-sm;
    cursor: pointer;
    transition: transform 0.18s;
    position: relative;
    overflow: hidden;

    &:active { transform: scale(0.98); }

    .entry-icon {
      width: 40px;
      height: 40px;
      border-radius: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;
    }

    .entry-info {
      flex: 1;
      min-width: 0;

      .entry-title {
        font-size: 14px;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: 2px;
      }

      .entry-desc {
        font-size: 11px;
        color: $text-tertiary;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .entry-arrow {
      color: $text-tertiary;
      font-size: 14px;
    }

    // 主题色：约会用渐变红 / 心愿用渐变橙
    &.entry-plan .entry-icon {
      background: linear-gradient(135deg, #ffd9e0 0%, #ffe7e7 100%);
    }
    &.entry-wish .entry-icon {
      background: linear-gradient(135deg, #fff3d6 0%, #ffe7ba 100%);
    }
  }
}

.timeline-section {
  padding: 0 16px;
}

.timeline-group {
  margin-bottom: 24px;
}

.timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  h3 {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin: 0;
  }

  .photo-count {
    font-size: 12px;
    color: $text-tertiary;
  }
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;

  @media (min-width: 768px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (min-width: 1024px) {
    grid-template-columns: repeat(4, 1fr);
  }
}

.photo-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: $radius-md;
  overflow: hidden;
  cursor: pointer;
  background: $border-color;

  .photo-img-wrap {
    width: 100%;
    height: 100%;
  }

  .lazy-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
    opacity: 0;
    transition: opacity 0.3s ease;

    &.lazy-loaded {
      opacity: 1;
    }
  }

  .photo-location {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    background: linear-gradient(transparent, rgba(0, 0, 0, 0.6));
    color: #fff;
    padding: 20px 8px 8px;
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 4px;

    .van-icon {
      font-size: 12px;
    }

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  &:active {
    opacity: 0.9;
  }
}
</style>
