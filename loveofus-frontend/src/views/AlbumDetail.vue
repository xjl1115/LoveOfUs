<template>
  <div class="album-detail-page">
    <!-- 顶部导航 -->
    <van-nav-bar
      :title="album?.name || '相册详情'"
      left-arrow
      @click-left="$router.back()"
      fixed
      placeholder
    >
      <template #right>
        <van-icon name="ellipsis" size="20" @click="showMoreActions" />
      </template>
    </van-nav-bar>

    <!-- 照片列表 -->
    <div class="photos-section">
      <div v-if="photos.length === 0 && !loading" class="empty-state">
        <van-empty description="相册还没有照片">
          <van-button round type="primary" size="small" @click="goToUpload">
            去上传照片
          </van-button>
        </van-empty>
      </div>
      <div v-else class="photo-list">
        <div
          v-for="photo in photos"
          :key="photo.id"
          class="photo-card"
              :class="{ selected: selectedPhotos.includes(photo.id) }"
              @click="handlePhotoClick(photo)"
              @long-press="handleLongPress(photo)"
            >
              <div class="photo-image-wrap">
                <img
                  v-lazy="{
                    src: photo.storagePath,
                    error: '/images/photo-error.png'
                  }"
                  class="photo-image"
                />
                <div v-if="isSelectionMode" class="selection-overlay">
                  <van-icon
                    :name="selectedPhotos.includes(photo.id) ? 'checked' : 'circle'"
                    :class="{ checked: selectedPhotos.includes(photo.id) }"
                  />
                </div>
              </div>
              <div class="photo-meta">
                <div class="meta-top">
                  <div class="photo-user">
                    <van-icon name="contact" size="14" />
                    <span>{{ photo.userNickname }}</span>
                  </div>
                  <div class="photo-date">
                    <van-icon name="clock-o" size="14" />
                    <span>{{ photo.takenDate }}</span>
                  </div>
                </div>
                <div class="photo-location">
                  <van-icon name="map-marked" size="15" color="#ee0a24" />
                  <span class="location-text">{{ formatLocation(photo) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

    <!-- 底部选择栏 -->
    <div v-if="isSelectionMode" class="selection-bar">
      <div class="selection-info">
        <span>已选择 {{ selectedPhotos.length }} 张</span>
        <van-button type="primary" size="small" @click="selectAll">
          {{ isAllSelected ? '取消全选' : '全选' }}
        </van-button>
      </div>
      <div class="selection-actions">
        <van-button type="danger" size="small" :disabled="selectedPhotos.length === 0" @click="removeSelected">
          从相册移除
        </van-button>
        <van-button size="small" @click="cancelSelection">
          取消
        </van-button>
      </div>
    </div>

    <!-- 更多操作弹窗 -->
    <van-action-sheet
      v-model:show="showActionSheet"
      :actions="actionSheetActions"
      cancel-text="取消"
      close-on-click-action
      @select="onActionSelect"
    />

    <!-- 编辑相册弹窗 -->
    <van-dialog
      v-model:show="showEditDialog"
      title="编辑相册"
      show-cancel-button
      @confirm="updateAlbumInfo"
      @cancel="resetEditForm"
    >
      <van-form class="edit-form">
        <van-field
          v-model="editForm.name"
          label="相册名称"
          placeholder="请输入相册名称"
          maxlength="20"
          show-word-limit
        />
        <van-field
          v-model="editForm.description"
          label="描述"
          type="textarea"
          placeholder="添加相册描述（可选）"
          maxlength="100"
          show-word-limit
          rows="2"
        />
      </van-form>
    </van-dialog>

    <!-- 添加到其他相册弹窗 -->
    <van-popup v-model:show="showAlbumListPopup" position="bottom" round :style="{ height: '60%' }">
      <div class="album-list-popup">
        <div class="popup-header">
          <h3>添加到相册</h3>
          <van-icon name="cross" @click="showAlbumListPopup = false" />
        </div>
        <van-list>
          <van-cell
            v-for="item in otherAlbums"
            :key="item.id"
            :title="item.name"
            :label="`${item.photoCount} 张照片`"
            is-link
            @click="addToAlbum(item.id)"
          >
            <template #icon>
              <van-image
                :src="item.coverPhotoUrl"
                width="48"
                height="48"
                radius="4"
                fit="cover"
                style="margin-right: 12px;"
              />
            </template>
          </van-cell>
        </van-list>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog, showSuccessToast } from 'vant'
import { getAlbumDetail, updateAlbum, deleteAlbum, removePhotoFromAlbum, addPhotosToAlbum } from '@/api/album'
import type { AlbumDetail, AlbumPhoto, Album } from '@/types'

const route = useRoute()
const router = useRouter()
const albumId = computed(() => Number(route.params.id))

const album = ref<AlbumDetail | null>(null)
const photos = ref<AlbumPhoto[]>([])
const loading = ref(false)

// 选择模式
const isSelectionMode = ref(false)
const selectedPhotos = ref<number[]>([])

// 操作菜单
const showActionSheet = ref(false)
const actionSheetActions = computed(() => [
  { name: '选择照片', key: 'select' },
  { name: '编辑相册', key: 'edit' },
  { name: '添加照片', key: 'add' },
  { name: '删除相册', key: 'delete', color: '#ee0a24' }
])

// 编辑弹窗
const showEditDialog = ref(false)
const editForm = ref({
  name: '',
  description: ''
})

// 添加到其他相册
const showAlbumListPopup = ref(false)
const otherAlbums = ref<Album[]>([])

const isAllSelected = computed(() => {
  return photos.value.length > 0 && selectedPhotos.value.length === photos.value.length
})

onMounted(() => {
  loadAlbumDetail()
})

async function loadAlbumDetail() {
  loading.value = true
  try {
    const data = await getAlbumDetail(albumId.value)
    album.value = data
    photos.value = data.photos || []
  } catch (error) {
    showToast('加载相册信息失败')
  } finally {
    loading.value = false
  }
}

function formatLocation(photo: AlbumPhoto): string {
  const parts = [photo.country, photo.province, photo.city, photo.locationName].filter(Boolean)
  return parts.join(' · ') || '未知地点'
}

function goToUpload() {
  router.push('/upload')
}

function handlePhotoClick(photo: AlbumPhoto) {
  if (isSelectionMode.value) {
    toggleSelection(photo.id)
  } else {
    router.push(`/photo/${photo.id}`)
  }
}

function handleLongPress(photo: AlbumPhoto) {
  if (!isSelectionMode.value) {
    isSelectionMode.value = true
    selectedPhotos.value = [photo.id]
  }
}

function toggleSelection(photoId: number) {
  const index = selectedPhotos.value.indexOf(photoId)
  if (index > -1) {
    selectedPhotos.value.splice(index, 1)
  } else {
    selectedPhotos.value.push(photoId)
  }
}

function selectAll() {
  if (isAllSelected.value) {
    selectedPhotos.value = []
  } else {
    selectedPhotos.value = photos.value.map(p => p.id)
  }
}

function cancelSelection() {
  isSelectionMode.value = false
  selectedPhotos.value = []
}

async function removeSelected() {
  if (selectedPhotos.value.length === 0) return

  try {
    await showDialog({
      title: '确认移除',
      message: `确定要从相册中移除 ${selectedPhotos.value.length} 张照片吗？`,
      showCancelButton: true
    })

    // 批量移除
    for (const photoId of selectedPhotos.value) {
      await removePhotoFromAlbum(albumId.value, photoId)
    }

    showSuccessToast('移除成功')
    cancelSelection()
    loadAlbumDetail()
  } catch (error) {
    // 用户取消
  }
}

function showMoreActions() {
  showActionSheet.value = true
}

async function onActionSelect(action: { key: string }) {
  switch (action.key) {
    case 'select':
      isSelectionMode.value = true
      break
    case 'edit':
      if (album.value) {
        editForm.value = {
          name: album.value.name,
          description: album.value.description || ''
        }
        showEditDialog.value = true
      }
      break
    case 'add':
      router.push('/upload')
      break
    case 'delete':
      deleteAlbumConfirm()
      break
  }
}

async function updateAlbumInfo() {
  if (!editForm.value.name.trim()) {
    showToast('请输入相册名称')
    return
  }

  try {
    await updateAlbum(albumId.value, {
      name: editForm.value.name.trim(),
      description: editForm.value.description.trim() || undefined
    })
    showSuccessToast('更新成功')
    loadAlbumDetail()
  } catch (error) {
    showToast('更新失败')
  }
}

function resetEditForm() {
  editForm.value = { name: '', description: '' }
}

async function deleteAlbumConfirm() {
  try {
    await showDialog({
      title: '确认删除',
      message: `确定要删除相册 "${album.value?.name}" 吗？相册内的照片不会被删除。`,
      showCancelButton: true
    })

    await deleteAlbum(albumId.value)
    showSuccessToast('删除成功')
    router.replace('/albums')
  } catch (error) {
    // 用户取消
  }
}

async function addToAlbum(targetAlbumId: number) {
  if (selectedPhotos.value.length === 0) return

  try {
    await addPhotosToAlbum(targetAlbumId, selectedPhotos.value)
    showSuccessToast('添加成功')
    showAlbumListPopup.value = false
    cancelSelection()
  } catch (error) {
    showToast('添加失败')
  }
}
</script>

<style scoped lang="scss">
.album-detail-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 24px;
}

.photos-section {
  padding: 12px;
}

.photo-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.photo-card {
  background: #fff;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;

  &:active {
    transform: scale(0.98);
  }

  &.selected {
    .van-image {
      opacity: 0.6;
    }
  }

  .photo-image-wrap {
    position: relative;
    width: 100%;
    aspect-ratio: 16 / 9;
    overflow: hidden;
    background: $bg-color;
    display: flex;
    align-items: center;
    justify-content: center;

    .van-image {
      width: 100% !important;
      height: 100% !important;
    }

    .selection-overlay {
      position: absolute;
      top: 8px;
      right: 8px;
      z-index: 1;

      .van-icon {
        font-size: 24px;
        color: $text-white;
        text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);

        &.checked {
          color: $primary-color;
        }
      }
    }
  }

  .photo-meta {
    padding: 14px 16px;

    .meta-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 10px;

      .photo-user,
      .photo-date {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 13px;
        color: $text-secondary;
      }
    }

    .photo-location {
      display: flex;
      align-items: flex-start;
      gap: 6px;

      .van-icon {
        flex-shrink: 0;
        margin-top: 2px;
      }

      .location-text {
        font-size: 14px;
        font-weight: 500;
        color: $text-primary;
        line-height: 1.5;
        word-break: break-all;
      }
    }
  }
}

.empty-state {
  padding: 60px 20px;
}

.selection-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: $bg-white;
  padding: 12px 16px;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.1);
  z-index: 100;

  .selection-info {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    span {
      font-size: 14px;
      color: $text-primary;
    }
  }

  .selection-actions {
    display: flex;
    gap: 12px;

    .van-button {
      flex: 1;
    }
  }
}

.edit-form {
  padding: 16px;
}

.album-list-popup {
  height: 100%;
  display: flex;
  flex-direction: column;

  .popup-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid $border-color;

    h3 {
      font-size: 16px;
      font-weight: 600;
      margin: 0;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
    }
  }
}
</style>
