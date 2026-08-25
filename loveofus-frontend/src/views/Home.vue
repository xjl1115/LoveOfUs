<template>
  <div class="home-page">
    <!-- 顶部导航 -->
    <van-nav-bar fixed placeholder>
      <template #left>
        <div class="nav-logo">
          <span class="logo-icon">❤️</span>
          <span class="logo-text">LoveMap</span>
        </div>
      </template>
      <template #right>
        <van-icon name="photograph" size="20" @click="$router.push('/upload')" />
      </template>
    </van-nav-bar>

    <!-- 筛选提示 -->
    <div v-if="activeProvince" class="filter-bar">
      <span>📌 正在查看：{{ activeProvince }}</span>
      <van-icon name="cross" @click="clearFilter" />
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

    <!-- 底部 Tab -->
    <BottomTab />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import ChinaMap from '@/components/ChinaMap.vue'
import BottomTab from '@/components/BottomTab.vue'
import { usePhotoStore } from '@/stores/photo'
import { getTimelinePhotos } from '@/api/photo'
import { getUserStats } from '@/api/user'
import type { ProvinceData } from '@/types'

const router = useRouter()
const photoStore = usePhotoStore()

const loading = ref(false)
const refreshing = ref(false)
const provinceData = ref<ProvinceData[]>([])

const activeProvince = computed(() => photoStore.activeProvince)
const hasMore = computed(() => photoStore.hasMore)
const timelineGroups = computed(() => photoStore.timelineGroups)

onMounted(() => {
  loadUserStats()
  loadPhotos(true)
})

// keep-alive 激活时刷新
onActivated(() => {
  loadUserStats()
  loadPhotos(true)
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
