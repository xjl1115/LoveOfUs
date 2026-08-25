<template>
  <div class="photo-detail-page">
    <!-- 沉浸式导航栏 -->
    <div class="photo-nav" :class="{ 'nav-hidden': navHidden }">
      <van-icon name="arrow-left" class="nav-btn" @click="$router.back()" />
      <span class="nav-title">{{ photoIndexText }}</span>
      <van-icon name="ellipsis" class="nav-btn" @click="showActions = true" />
    </div>

    <div v-if="photo" class="photo-content" @click="toggleNav">
      <!-- 全屏大图 -->
      <div class="photo-image-wrapper">
        <van-image
          :src="photo.storagePath"
          fit="contain"
          @click.stop="previewImage"
        >
          <template #error>
            <div class="img-error">
              <van-icon name="photo-fail" size="48" color="#999" />
              <span>图片加载失败</span>
            </div>
          </template>
        </van-image>
      </div>

      <!-- 左右切换按钮 -->
      <button
        v-if="adjacentPhotos.prev"
        class="swipe-btn prev-btn"
        @click.stop="viewPrev"
        aria-label="上一张"
      >
        <van-icon name="arrow-left" />
      </button>
      <button
        v-if="adjacentPhotos.next"
        class="swipe-btn next-btn"
        @click.stop="viewNext"
        aria-label="下一张"
      >
        <van-icon name="arrow" />
      </button>

      <!-- 底部信息面板 -->
      <div class="info-panel" :class="{ 'panel-expanded': panelExpanded }">
        <div class="panel-handle" @click.stop="panelExpanded = !panelExpanded">
          <div class="handle-bar"></div>
        </div>

        <div class="panel-content">
          <!-- 日期和地点 -->
          <div class="meta-row">
            <div class="meta-item">
              <van-icon name="calendar-o" />
              <span>{{ formatDate(photo.takenDate || photo.createdAt) }}</span>
            </div>
            <div v-if="photo.locationName" class="meta-item">
              <van-icon name="location-o" />
              <span>{{ photo.locationName }}</span>
            </div>
          </div>

          <!-- 描述文本 -->
          <div v-if="photo.description" class="description">
            <div class="quote-line"></div>
            <p>{{ photo.description }}</p>
          </div>

          <!-- AI 标签 -->
          <div v-if="photo.aiTags?.length" class="tags">
            <span class="tags-label">AI 识别</span>
            <div class="tags-list">
              <van-tag
                v-for="tag in photo.aiTags"
                :key="tag"
                round
                plain
                color="#FF6B6B"
                text-color="#FF6B6B"
              >
                {{ tag }}
              </van-tag>
            </div>
          </div>
        </div>
      </div>
    </div>

    <van-empty v-else description="照片不存在" />

    <!-- 更多操作 -->
    <van-action-sheet
      v-model:show="showActions"
      :actions="actions"
      @select="onActionSelect"
      cancel-text="取消"
      close-on-click-action
    />

    <!-- 分享弹窗 -->
    <van-popup v-model:show="showSharePopup" position="bottom" round style="max-height: 80vh;">
      <div class="share-popup">
        <div class="share-popup-header">
          <span>分享照片</span>
          <van-icon name="cross" @click="showSharePopup = false" />
        </div>

        <div class="share-popup-body">
          <!-- 照片预览 -->
          <div class="share-photo-preview">
            <van-image :src="photo?.storagePath" fit="cover" />
          </div>

          <!-- 照片信息 -->
          <div class="share-photo-info">
            <div class="share-info-item">
              <van-icon name="calendar-o" />
              <span>{{ formatDate(photo?.takenDate || photo?.createdAt) }}</span>
            </div>
            <div v-if="photo?.locationName" class="share-info-item">
              <van-icon name="location-o" />
              <span>{{ photo.locationName }}</span>
            </div>
          </div>

          <!-- 二维码 + 链接 -->
          <div class="share-qr-section">
            <div class="share-qr-code">
              <img v-if="qrCodeDataUrl" :src="qrCodeDataUrl" alt="二维码" />
              <van-loading v-else size="24px" />
            </div>
            <div class="share-link-area">
              <span class="share-link-label">照片链接</span>
              <div class="share-link-box">
                <span class="share-link-text">{{ shareUrl }}</span>
                <van-button size="small" round plain type="primary" @click="copyShareUrl">
                  复制
                </van-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </van-popup>

    <van-dialog v-model:show="dummyDialogVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showImagePreview, showConfirmDialog } from 'vant'
import { getPhotoDetail, deletePhoto, getTimelinePhotos } from '@/api/photo'
import { usePhotoStore } from '@/stores/photo'
import { formatDate } from '@/utils/date'
import type { PhotoDetail, Photo } from '@/types'


const route = useRoute()
const router = useRouter()
const photoStore = usePhotoStore()

const photo = ref<PhotoDetail | null>(null)
const showActions = ref(false)
const showSharePopup = ref(false)
const dummyDialogVisible = ref(false)
const navHidden = ref(false)
const panelExpanded = ref(false)
const qrCodeDataUrl = ref('')
const shareUrl = ref('')

const adjacentPhotos = ref<{ prev: Photo | null; next: Photo | null }>({
  prev: null,
  next: null
})

const actions = [
  { name: '分享', value: 'share' },
  { name: '删除', value: 'delete', color: '#ee0a24' }
]

const photoIndexText = computed(() => {
  const allPhotos = photoStore.photos
  const idx = allPhotos.findIndex(p => p.id === photo.value?.id)
  return idx >= 0 && allPhotos.length > 1 ? `${idx + 1} / ${allPhotos.length}` : ''
})

onMounted(() => {
  loadPhoto()
})

onUnmounted(() => {
  // 清理
})

async function loadPhoto() {
  const id = Number(route.params.id)
  if (!id) return

  try {
    const data = await getPhotoDetail(id)
    photo.value = data
    photoStore.setCurrentPhoto(data)

    // 如果 store 还没有照片列表，从 API 拉取第一页填充相邻导航
    if (photoStore.photos.length === 0) {
      const { list } = await getTimelinePhotos({ page: 1, size: 200 })
      if (list.length > 0) {
        photoStore.setPhotos(list, true)
      }
    }

    findAdjacentPhotos()
  } catch (error) {
    showToast('加载失败')
  }
}

function findAdjacentPhotos() {
  const allPhotos = photoStore.photos
  const currentIndex = allPhotos.findIndex(p => p.id === photo.value?.id)

  adjacentPhotos.value = {
    prev: currentIndex > 0 ? allPhotos[currentIndex - 1] : null,
    next: currentIndex < allPhotos.length - 1 ? allPhotos[currentIndex + 1] : null
  }
}

function previewImage() {
  if (photo.value?.storagePath) {
    showImagePreview([photo.value.storagePath])
  }
}

function viewPrev() {
  if (adjacentPhotos.value.prev) {
    router.replace(`/photo/${adjacentPhotos.value.prev.id}`)
    scrollTo(0, 0)
    loadPhoto()
  }
}

function viewNext() {
  if (adjacentPhotos.value.next) {
    router.replace(`/photo/${adjacentPhotos.value.next.id}`)
    scrollTo(0, 0)
    loadPhoto()
  }
}

function toggleNav() {
  navHidden.value = !navHidden.value
}

function onActionSelect(action: { name: string; value: string }) {
  switch (action.value) {
    case 'share':
      showShare()
      break
    case 'delete':
      onDelete()
      break
  }
}

function showShare() {
  // 构建分享 URL: https://domain/photo/{id}
  shareUrl.value = `${window.location.origin}/photo/${photo.value?.id}`
  showSharePopup.value = true
  generateQRCode()
}

async function generateQRCode() {
  try {
    const QRCode = await import('qrcode')
    qrCodeDataUrl.value = await QRCode.toDataURL(shareUrl.value, {
      width: 200,
      margin: 2,
      color: { dark: '#333', light: '#fff' }
    })
  } catch {
    showToast('生成二维码失败')
  }
}

async function copyShareUrl() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    showToast('链接已复制')
  } catch {
    showToast('复制失败，请手动复制')
  }
}

function onDelete() {
  showConfirmDialog({
    title: '确认删除',
    message: '删除后无法恢复，确定要删除吗？'
  }).then(async () => {
    try {
      await deletePhoto(photo.value!.id)
      showToast('删除成功')
      router.back()
    } catch {
      showToast('删除失败')
    }
  })
}
</script>

<style scoped lang="scss">
$primary: #FF6B6B;
$text-light: rgba(255, 255, 255, 0.9);
$text-muted: rgba(255, 255, 255, 0.55);

.photo-detail-page {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden; // 禁止滚动
  background: #0a0a0a;
  user-select: none;
  // 确保页面本身不会滚动
  overscroll-behavior: none;
  -webkit-overflow-scrolling: auto;
}

/* ========= 沉浸式导航 ========= */
.photo-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000; // 提高 z-index 确保在最上层
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 48px 16px 16px;
  background: linear-gradient(180deg, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.4) 60%, transparent 100%);
  transition: opacity 0.35s ease, transform 0.35s ease;
  pointer-events: auto; // 确保可点击

  &.nav-hidden {
    opacity: 0;
    transform: translateY(-100%);
    pointer-events: none;
  }

  .nav-title {
    color: $text-light;
    font-size: 15px;
    font-weight: 500;
  }

  .nav-btn {
    font-size: 22px;
    color: $text-light;
    padding: 4px;
    transition: opacity 0.2s;
    cursor: pointer;

    &:active {
      opacity: 0.6;
    }
  }
}

/* ========= 分享弹窗 ========= */
.share-popup {
  padding: 20px;

  .share-popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;

    span {
      font-size: 17px;
      font-weight: 600;
      color: #333;
    }

    .van-icon-cross {
      font-size: 20px;
      color: #999;
      padding: 4px;
      cursor: pointer;

      &:active {
        color: #666;
      }
    }
  }

  .share-popup-body {
    max-height: 65vh;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }

  .share-photo-preview {
    width: 100%;
    height: 180px;
    border-radius: 8px;
    overflow: hidden;
    margin-bottom: 16px;
    background: #f5f5f5;

    .van-image {
      width: 100%;
      height: 100%;
    }
  }

  .share-photo-info {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 16px;

    .share-info-item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: #666;

      .van-icon {
        font-size: 14px;
        color: #999;
      }
    }
  }

  .share-qr-section {
    display: flex;
    gap: 16px;
    align-items: center;
    padding: 16px;
    background: #f8f8f8;
    border-radius: 8px;

    .share-qr-code {
      flex-shrink: 0;
      width: 100px;
      height: 100px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #fff;
      border-radius: 6px;
      overflow: hidden;

      img {
        width: 100%;
        height: 100%;
      }
    }

    .share-link-area {
      flex: 1;
      min-width: 0;

      .share-link-label {
        display: block;
        font-size: 12px;
        color: #999;
        margin-bottom: 8px;
      }

      .share-link-box {
        display: flex;
        align-items: center;
        gap: 8px;

        .share-link-text {
          flex: 1;
          min-width: 0;
          font-size: 13px;
          color: #333;
          word-break: break-all;
          line-height: 1.4;
          background: #fff;
          padding: 8px 10px;
          border-radius: 6px;
          border: 1px solid #eee;
        }
      }
    }
  }
}

/* ========= 图片容器 ========= */
.photo-content {
  position: relative;
  width: 100%;
  height: 100vh;
}

.photo-image-wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0a0a0a;

  .van-image {
    width: 100%;
    height: 100%;

    :deep(img) {
      transition: opacity 0.4s ease;
    }
  }

  .img-error {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: #888;
    font-size: 13px;
  }
}

/* ========= 左右切换按钮 ========= */
.swipe-btn {
  position: absolute;
  top: 50%;
  z-index: 50; // 降低 z-index，确保在导航栏之下
  transform: translateY(-50%);
  width: 40px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
  color: rgba(255, 255, 255, 0.8);
  font-size: 20px;
  cursor: pointer;
  transition: all 0.25s ease;
  outline: none;
  -webkit-tap-highlight-color: transparent;
  // 确保按钮不会覆盖导航栏区域
  margin-top: 40px;

  &.prev-btn {
    left: 0;
    border-radius: 0 8px 8px 0;
  }

  &.next-btn {
    right: 0;
    border-radius: 8px 0 0 8px;
  }

  &:hover,
  &:active {
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
  }

  &:active {
    transform: translateY(-50%) scale(0.95);
  }
}

/* ========= 底部信息面板 ========= */
.info-panel {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100; // 提高 z-index，但仍低于导航栏
  background: linear-gradient(0deg, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.4) 70%, transparent 100%);
  padding: 28px 20px calc(constant(safe-area-inset-bottom) + 16px);
  padding: 28px 20px calc(env(safe-area-inset-bottom) + 16px);
  transition: padding 0.35s ease;
  // 确保底部面板不会向上延伸到导航栏区域
  max-height: 50vh;
  overflow-y: auto;

  &.panel-expanded {
    background: rgba(0,0,0,0.92);
    backdrop-filter: blur(20px);
    padding-top: 20px;
  }

  .panel-handle {
    display: flex;
    justify-content: center;
    padding-bottom: 16px;
    cursor: pointer;

    .handle-bar {
      width: 36px;
      height: 4px;
      border-radius: 2px;
      background: rgba(255,255,255,0.3);
      transition: background 0.2s;
    }

    &:active .handle-bar {
      background: rgba(255,255,255,0.5);
    }
  }

  .panel-content {
    transition: all 0.35s ease;
  }

  .meta-row {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 12px;

    .meta-item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: $text-muted;
      font-size: 13px;

      .van-icon {
        font-size: 14px;
      }
    }
  }

  .description {
    display: flex;
    gap: 10px;
    margin-bottom: 14px;

    .quote-line {
      flex-shrink: 0;
      width: 3px;
      border-radius: 2px;
      background: $primary;
      opacity: 0.7;
    }

    p {
      margin: 0;
      font-size: 15px;
      line-height: 1.7;
      color: $text-light;
      word-break: break-word;
    }
  }

  .tags {
    margin-bottom: 4px;

    .tags-label {
      display: block;
      font-size: 12px;
      color: $text-muted;
      margin-bottom: 8px;
    }

    .tags-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  }
}
</style>