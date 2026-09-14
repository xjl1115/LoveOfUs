<template>
  <div class="must-do-content">
    <!-- 顶部统计条 -->
    <div class="stats-card">
      <div class="stats-info">
        <div class="stats-num">
          <span class="achieved">{{ stats.achieved }}</span>
          <span class="divider">/</span>
          <span class="total">{{ stats.total }}</span>
        </div>
        <div class="stats-label">已完成情侣必做</div>
      </div>
      <van-button class="refresh-btn" round size="small" plain @click="onResetProgress">
        重置
      </van-button>
    </div>

    <!-- 进度条 -->
    <div class="progress-bar">
      <div class="progress-fill" :style="{ width: `${Math.round(stats.rate * 100)}%` }" />
    </div>
    <div class="progress-text">{{ Math.round(stats.rate * 100) }}% 已完成</div>

    <!-- 分类筛选 -->
    <div class="category-bar">
      <span
        class="cat-chip"
        :class="{ active: filterCategory === 'all' }"
        @click="filterCategory = 'all'"
      >全部</span>
      <span
        v-for="c in categories"
        :key="c.value"
        class="cat-chip"
        :class="{ active: filterCategory === c.value }"
        :style="filterCategory === c.value ? { background: c.color, color: '#fff', borderColor: c.color } : {}"
        @click="filterCategory = c.value"
      >
        {{ c.emoji }} {{ c.label }}
      </span>
    </div>

    <!-- 100 件事列表 -->
    <div class="must-list">
      <div
        v-for="item in filteredItems"
        :key="item.id"
        class="must-item"
        :class="{ achieved: !!item.achievedAt }"
        @click="onItemClick(item)"
      >
        <div class="item-num">{{ String(item.order).padStart(3, '0') }}</div>
        <div class="item-icon">{{ resolveIcon(item) }}</div>
        <div class="item-body">
          <div class="item-title-row">
            <div class="item-title">{{ item.title }}</div>
            <div v-if="item.achievedAt" class="achieved-badge">
              <van-icon name="success" />
              <span>已达成</span>
            </div>
          </div>
          <div class="item-desc">{{ item.description }}</div>
          <div class="item-meta">
            <span class="meta-tag" :style="{ color: metaOf(item).color }">
              {{ metaOf(item).emoji }} {{ metaOf(item).label }}
            </span>
            <span v-if="item.photoCount" class="meta-tag photo-count">
              <van-icon name="photo-o" /> {{ item.photoCount }}
            </span>
          </div>
        </div>
        <div class="item-action" @click.stop="onItemClick(item)">
          <van-icon name="ellipsis" />
        </div>
      </div>
    </div>

    <!-- 操作弹层 -->
    <van-action-sheet
      v-model:show="showActionSheet"
      :actions="actionSheetItems"
      cancel-text="取消"
      close-on-click-action
      @select="onActionSelect"
    />

    <!-- 标记完成弹窗 -->
    <van-dialog
      v-model:show="showAchieveDialog"
      title="完成这件小事 🎉"
      show-cancel-button
      :before-close="onAchieveBeforeClose"
    >
      <div class="achieve-form">
        <div class="achieve-title">{{ activeItem?.title }}</div>
        <van-field
          v-model="achieveNote"
          label="写一句回忆"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          placeholder="选填，例如：今天凌晨 5 点爬上山⛰️"
        />
      </div>
    </van-dialog>

    <!-- 查看图片弹窗 -->
    <van-popup
      v-model:show="showPhotosPopup"
      round
      closeable
      position="bottom"
      :style="{ height: '75%' }"
    >
      <div class="photos-popup" v-if="activeItem">
        <div class="popup-header">
          <div class="popup-cover">{{ resolveIcon(activeItem) }}</div>
          <h3>{{ activeItem.title }}</h3>
          <p>共 {{ photosToShow.length }} 张照片 · 点击查看大图</p>
        </div>
        <div v-if="photosToShow.length === 0" class="empty-photos">
          <van-icon name="photo-o" size="40" />
          <p>还没有照片</p>
          <van-button round type="primary" size="small" @click="goUploadForActive">
            上传第一张
          </van-button>
        </div>
        <div v-else class="photos-grid">
          <div
            v-for="p in photosToShow"
            :key="p.id"
            class="photo-cell"
            @click="openPhotoPreview(p.url)"
          >
            <img :src="p.url" alt="必做事项图片" />
          </div>
        </div>
      </div>
    </van-popup>

    <van-image-preview
      v-model:show="showPhotoPreview"
      :images="previewImages"
      :closeable="true"
    />

    <div class="bottom-safe" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import {
  listMustItems,
  achieveMustItem,
  unachieveMustItem,
  getMustStats,
  listMustPhotos,
  deriveIconFromTitle,
  CATEGORY_META,
  type MustItem,
  type MustPhoto,
  type MustCategory
} from '@/api/mustList'

const router = useRouter()

// ============== 数据 ==============
const items = ref<MustItem[]>([])
const stats = ref({ total: 0, achieved: 0, rate: 0 })
const filterCategory = ref<'all' | MustCategory>('all')

const categories: Array<{ value: MustCategory; label: string; emoji: string; color: string }> = [
  { value: 'travel',  label: CATEGORY_META.travel.label,  emoji: CATEGORY_META.travel.emoji,  color: CATEGORY_META.travel.color },
  { value: 'romance', label: CATEGORY_META.romance.label, emoji: CATEGORY_META.romance.emoji, color: CATEGORY_META.romance.color },
  { value: 'daily',   label: CATEGORY_META.daily.label,   emoji: CATEGORY_META.daily.emoji,   color: CATEGORY_META.daily.color },
  { value: 'food',    label: CATEGORY_META.food.label,    emoji: CATEGORY_META.food.emoji,    color: CATEGORY_META.food.color },
  { value: 'memory',  label: CATEGORY_META.memory.label,  emoji: CATEGORY_META.memory.emoji,  color: CATEGORY_META.memory.color },
  { value: 'growth',  label: CATEGORY_META.growth.label,  emoji: CATEGORY_META.growth.emoji,  color: CATEGORY_META.growth.color }
]

/**
 * 事项分类：以后端 things.category 为准；为空或未知（老库未执行 V18 迁移）时按「日常」兜底，
 * 保证每条事项都能落进一个分项，分项之和等于事项总数
 */
function categoryOf(item: MustItem): MustCategory {
  return item.category && CATEGORY_META[item.category] ? item.category : 'daily'
}

function metaOf(item: MustItem) {
  return CATEGORY_META[categoryOf(item)]
}

/**
 * 解析左侧展示图标：后端 icon 非空则直接用，否则按标题推断 emoji
 */
function resolveIcon(item: MustItem): string {
  if (item.icon && item.icon.trim() !== '') return item.icon
  return deriveIconFromTitle(item.title)
}

const filteredItems = computed(() => {
  if (filterCategory.value === 'all') return items.value
  return items.value.filter((i) => categoryOf(i) === filterCategory.value)
})

async function reload() {
  items.value = await listMustItems()
  stats.value = await getMustStats()
}

onMounted(() => {
  reload()
})

// ============== 点击单项：弹出 ActionSheet ==============
const showActionSheet = ref(false)
const activeItem = ref<MustItem | null>(null)

const actionSheetItems = computed(() => {
  if (!activeItem.value) return []
  const achieved = !!activeItem.value.achievedAt
  const hasPhotos = activeItem.value.photoCount > 0
  return [
    {
      name: achieved ? '✓ 标记为未完成' : '✅ 标记已完成',
      subname: achieved ? '撤销已完成状态' : '为这件小事打勾',
      color: achieved ? '#999' : '#07c160'
    },
    {
      name: '📷 上传图片',
      subname: '拍照上传，留存这一刻',
      color: '#1989fa'
    },
    {
      name: hasPhotos ? `🖼 查看图片（${activeItem.value.photoCount}）` : '🖼 查看图片',
      subname: hasPhotos ? '打开这件小事关联的照片' : '暂时还没有照片',
      color: hasPhotos ? '#ff976a' : '#ccc',
      disabled: !hasPhotos
    }
  ]
})

function onItemClick(item: MustItem) {
  activeItem.value = item
  showActionSheet.value = true
}

function onActionSelect(action: { name: string }) {
  if (!activeItem.value) return
  if (action.name.includes('标记')) {
    onToggleAchieve(activeItem.value)
  } else if (action.name.includes('上传')) {
    goUploadForActive()
  } else if (action.name.includes('查看')) {
    showPhotosForActive()
  }
}

// ============== 标记完成 ==============
const showAchieveDialog = ref(false)
const achieveNote = ref('')

async function onToggleAchieve(item: MustItem) {
  if (item.achievedAt) {
    try {
      await showConfirmDialog({
        title: '取消已完成？',
        message: '撤销后这件小事会回到待办列表'
      })
      await unachieveMustItem(item.id)
      showToast('已撤销')
      await reload()
    } catch {
      /* 用户取消 */
    }
    return
  }
  activeItem.value = item
  achieveNote.value = ''
  showAchieveDialog.value = true
}

async function onAchieveBeforeClose(action: string): Promise<boolean> {
  if (action !== 'confirm') return true
  if (!activeItem.value) return true
  try {
    await achieveMustItem(activeItem.value.id, {
      note: achieveNote.value.trim() || undefined
    })
    showToast('已标记完成 🎉')
    await reload()
    return true
  } catch {
    return false
  }
}

// ============== 上传图片 ==============
function goUploadForActive() {
  if (!activeItem.value) return
  router.push({
    path: '/upload',
    query: {
      source: 'must-item',
      itemId: activeItem.value.id,
      itemTitle: activeItem.value.title
    }
  })
}

// ============== 查看图片 ==============
const showPhotosPopup = ref(false)
const photosToShow = ref<MustPhoto[]>([])

async function showPhotosForActive() {
  if (!activeItem.value) return
  try {
    photosToShow.value = await listMustPhotos(activeItem.value.id)
    if (photosToShow.value.length === 0) {
      showToast('暂无关联照片')
    }
  } catch {
    photosToShow.value = []
    showToast('加载图片失败')
  }
  showPhotosPopup.value = true
}

// ============== 大图预览 ==============
const showPhotoPreview = ref(false)
const previewImages = ref<string[]>([])

function openPhotoPreview(url: string) {
  previewImages.value = [url]
  showPhotoPreview.value = true
}

// ============== 重置 ==============
async function onResetProgress() {
  try {
    await showConfirmDialog({
      title: '重置已完成进度？',
      message: '将清空所有已完成记录，但不会删除已上传的图片。'
    })
    for (const item of items.value) {
      if (item.achievedAt) await unachieveMustItem(item.id)
    }
    await reload()
    showToast('已重置')
  } catch {
    /* 用户取消 */
  }
}
</script>

<style scoped lang="scss">
.must-do-content {
  background: $bg-color;
  padding-bottom: 24px;
}

.stats-card {
  display: flex;
  align-items: center;
  margin: 16px 16px 8px;
  padding: 16px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe7e7 100%);
  border-radius: $radius-lg;

  .stats-info { flex: 1; }

  .stats-num {
    font-size: 24px;
    font-weight: 700;
    color: $primary-color;
    line-height: 1.1;
    .achieved { font-size: 28px; }
    .divider { margin: 0 2px; opacity: 0.5; }
    .total { opacity: 0.85; }
  }
  .stats-label {
    font-size: 12px;
    color: $text-secondary;
    margin-top: 4px;
  }

  .refresh-btn {
    border-color: $primary-color;
    color: $primary-color;
  }
}

.progress-bar {
  height: 6px;
  margin: 0 16px;
  background: rgba($primary-color, 0.15);
  border-radius: 3px;
  overflow: hidden;

  .progress-fill {
    height: 100%;
    background: linear-gradient(90deg, $primary-color 0%, $primary-light 100%);
    border-radius: 3px;
    transition: width 0.3s ease;
  }
}

.progress-text {
  font-size: 12px;
  color: $text-secondary;
  text-align: right;
  margin: 6px 16px 0;
}

.category-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px 8px;

  .cat-chip {
    padding: 5px 12px;
    border-radius: 14px;
    background: #fff;
    color: $text-secondary;
    font-size: 13px;
    border: 1px solid transparent;
    cursor: pointer;
    transition: all 0.18s;

    &:active { transform: scale(0.96); }
    &.active {
      background: $primary-color;
      color: #fff;
      border-color: $primary-color;
      font-weight: 500;
    }
  }
}

.must-list {
  padding: 8px 16px;
}

.must-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 8px;
  background: #fff;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: transform 0.18s;
  position: relative;

  &:active { transform: scale(0.98); }

  &.achieved {
    background: linear-gradient(135deg, #f6ffed 0%, #f0fff4 100%);

    .item-num {
      color: $primary-color;
      opacity: 0.8;
    }

    .item-title {
      text-decoration: line-through;
      color: $text-secondary;
    }
  }

  .item-num {
    width: 32px;
    flex-shrink: 0;
    font-size: 11px;
    color: $text-tertiary;
    font-weight: 600;
    text-align: center;
    letter-spacing: 0.5px;
  }

  .item-icon {
    width: 40px;
    height: 40px;
    flex-shrink: 0;
    border-radius: $radius-md;
    background: linear-gradient(135deg, rgba($primary-color, 0.10) 0%, rgba($primary-color, 0.05) 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    line-height: 1;
  }

  .item-body {
    flex: 1;
    min-width: 0;
  }

  .item-title-row {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 2px;
  }

  .item-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .achieved-badge {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding: 1px 6px;
    border-radius: 8px;
    background: $primary-color;
    color: #fff;
    font-size: 10px;
    flex-shrink: 0;
  }

  .item-desc {
    font-size: 12px;
    color: $text-tertiary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-bottom: 4px;
  }

  .item-meta {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .meta-tag {
    font-size: 11px;
    color: $text-secondary;
    display: inline-flex;
    align-items: center;
    gap: 2px;
  }

  .photo-count {
    color: $text-tertiary;
  }

  .item-action {
    width: 28px;
    height: 28px;
    flex-shrink: 0;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: $text-tertiary;
    font-size: 18px;
  }
}

.achieve-form {
  padding: 12px 16px;

  .achieve-title {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 12px;
    text-align: center;
  }
}

.photos-popup {
  height: 100%;
  display: flex;
  flex-direction: column;

  .popup-header {
    text-align: center;
    padding: 20px 16px 12px;
    border-bottom: 1px solid $border-color;

    .popup-cover {
      font-size: 48px;
      margin-bottom: 8px;
    }
    h3 { margin: 0 0 4px; font-size: 18px; }
    p { margin: 0; font-size: 12px; color: $text-secondary; }
  }

  .empty-photos {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    color: $text-tertiary;
    padding: 40px 16px;

    p { margin: 0; font-size: 14px; }
  }

  .photos-grid {
    flex: 1;
    overflow-y: auto;
    padding: 12px;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .photo-cell {
    aspect-ratio: 1 / 1;
    border-radius: $radius-md;
    overflow: hidden;
    background: $bg-color;
    cursor: pointer;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
  }
}

.bottom-safe {
  height: 24px;
}
</style>
