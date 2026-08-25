<template>
  <div class="virtual-photo-list">
    <RecycleScroller
      class="scroller"
      :items="groupedPhotos"
      :item-size="itemSize"
      key-field="id"
      v-slot="{ item }"
    >
      <div class="timeline-group">
        <div class="timeline-header">
          <h3>{{ item.monthLabel }}</h3>
          <span class="photo-count">{{ item.photos.length }} 张照片</span>
        </div>
        <div class="photo-grid">
          <div
            v-for="photo in item.photos"
            :key="photo.id"
            class="photo-item"
            @click="handlePhotoClick(photo.id)"
          >
            <div class="photo-img-wrap">
              <img
                v-lazy="{
                  src: getThumbnailUrl(photo.storagePath),
                  error: '/images/photo-error.png'
                }"
                :alt="photo.description"
                class="photo-image"
              />
            </div>
            <div v-if="photo.locationName" class="photo-location">
              <van-icon name="location-o" />
              <span>{{ photo.locationName }}</span>
            </div>
          </div>
        </div>
      </div>
    </RecycleScroller>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RecycleScroller } from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import type { Photo } from '@/types'

interface TimelineGroup {
  id: string
  month: string
  monthLabel: string
  photos: Photo[]
}

interface Props {
  photos: Photo[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  photoClick: [id: number]
}>()

// 将照片按月份分组
const groupedPhotos = computed<TimelineGroup[]>(() => {
  const groups: Record<string, Photo[]> = {}

  props.photos.forEach((photo) => {
    const date = new Date(photo.createdAt || Date.now())
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`

    if (!groups[key]) {
      groups[key] = []
    }
    groups[key].push(photo)
  })

  return Object.entries(groups)
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([key, photos]) => ({
      id: key,
      month: key,
      monthLabel: `${key.split('-')[0]}年${parseInt(key.split('-')[1])}月`,
      photos
    }))
})

// 动态计算每项高度（根据照片数量）
const itemSize = computed(() => {
  // 基础高度：标题 50px + 间距 20px
  // 每行照片高度：照片宽度 * 宽高比 + 间距
  return 200 // 平均估算值，实际会根据内容自适应
})

// 获取缩略图 URL
function getThumbnailUrl(path: string | undefined): string {
  if (!path) return ''
  // 如果后端支持多尺寸，使用缩略图版本
  // 例如：/api/photos/thumbnail/xxx 或添加 ?size=small 参数
  return path
}

function handlePhotoClick(id: number) {
  emit('photoClick', id)
}
</script>

<style scoped lang="scss">
.virtual-photo-list {
  height: calc(100vh - 200px);
}

.scroller {
  height: 100%;
}

.timeline-group {
  padding: 0 16px;
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

  .photo-image {
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
