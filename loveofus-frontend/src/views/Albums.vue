<template>
  <div class="albums-page">
    <!-- 顶部导航 -->
    <van-nav-bar title="相册" fixed placeholder>
      <template #right>
        <van-icon name="plus" size="20" @click="showCreateDialog = true" />
      </template>
    </van-nav-bar>

    <!-- 相册列表 -->
    <div class="albums-content">
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <!-- 智能相册 -->
        <div v-if="aiAlbums.length > 0" class="album-section">
          <h3 class="section-title">🤖 AI 智能相册</h3>
          <div class="album-grid">
            <div
              v-for="album in aiAlbums"
              :key="album.id"
              class="album-card"
              @click="viewAlbum(album.id)"
              @touchstart="handleTouchStart(album)"
              @touchend="handleTouchEnd"
            >
              <div class="album-cover">
                <img
                  v-lazy="{
                    src: album.coverPhotoUrl || defaultCover,
                    error: defaultCover
                  }"
                  class="cover-image"
                />
                <div v-if="album.isAiGenerated" class="ai-badge">AI</div>
              </div>
              <div class="album-info">
                <h4 class="album-name">{{ album.name }}</h4>
                <p class="album-count">{{ album.photoCount }} 张照片</p>
                <van-icon name="ellipsis" class="btn-more" @click.stop="showAlbumMenu(album)" />
              </div>
            </div>
          </div>
        </div>

        <!-- 手动创建的相册 -->
        <div class="album-section">
          <h3 class="section-title">📁 我的相册</h3>
          <div v-if="manualAlbums.length === 0 && !loading" class="empty-state">
            <van-empty description="还没有创建相册">
              <van-button round type="primary" size="small" @click="showCreateDialog = true">
                创建相册
              </van-button>
            </van-empty>
          </div>
          <div v-else class="album-grid">
            <div
              v-for="album in manualAlbums"
              :key="album.id"
              class="album-card"
              @click="viewAlbum(album.id)"
              @touchstart="handleTouchStart(album)"
              @touchend="handleTouchEnd"
            >
              <div class="album-cover">
                <img
                  v-lazy="{
                    src: album.coverPhotoUrl || defaultCover,
                    error: defaultCover
                  }"
                  class="cover-image"
                />
              </div>
              <div class="album-info">
                <h4 class="album-name">{{ album.name }}</h4>
                <p class="album-count">{{ album.photoCount }} 张照片</p>
                <van-icon name="ellipsis" class="btn-more" @click.stop="showAlbumMenu(album)" />
              </div>
            </div>
          </div>
        </div>
      </van-pull-refresh>
    </div>

    <!-- 创建相册弹窗 -->
    <van-dialog
      v-model:show="showCreateDialog"
      title="新建相册"
      show-cancel-button
      @confirm="createAlbum"
      @cancel="resetCreateForm"
    >
      <van-form class="create-form">
        <van-field
          v-model="createForm.name"
          label="相册名称"
          placeholder="请输入相册名称"
          maxlength="20"
          show-word-limit
          :rules="[{ required: true, message: '请输入相册名称' }]"
        />
        <van-field
          v-model="createForm.description"
          label="描述"
          type="textarea"
          placeholder="添加相册描述（可选）"
          maxlength="100"
          show-word-limit
          rows="2"
        />
      </van-form>
    </van-dialog>

    <!-- 重命名相册弹窗 -->
    <van-dialog
      v-model:show="showRenameDialog"
      title="重命名相册"
      show-cancel-button
      @confirm="renameAlbum"
      @cancel="resetRenameForm"
    >
      <van-form class="create-form">
        <van-field
          v-model="renameForm.name"
          label="相册名称"
          placeholder="请输入新名称"
          maxlength="20"
          show-word-limit
          :rules="[{ required: true, message: '请输入相册名称' }]"
        />
        <van-field
          v-model="renameForm.description"
          label="相册描述"
          type="textarea"
          placeholder="请输入相册描述（选填）"
          maxlength="100"
          show-word-limit
          autosize
        />
      </van-form>
    </van-dialog>

    <!-- 长按菜单 -->
    <van-action-sheet
      v-model:show="showActionSheet"
      :actions="actionSheetActions"
      cancel-text="取消"
      close-on-click-action
      @select="onActionSelect"
    />

    <!-- 底部 Tab -->
    <BottomTab />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import BottomTab from '@/components/BottomTab.vue'
import { getAlbums, createAlbum as apiCreateAlbum, deleteAlbum, updateAlbum } from '@/api/album'
import type { Album } from '@/types'

const router = useRouter()

const albums = ref<Album[]>([])
const loading = ref(false)
const refreshing = ref(false)
const showCreateDialog = ref(false)
const showRenameDialog = ref(false)
const showActionSheet = ref(false)
const selectedAlbum = ref<Album | null>(null)
const defaultCover = '/default-album-cover.jpg'

const createForm = ref({
  name: '',
  description: ''
})

const renameForm = ref({
  name: '',
  description: ''
})

// 智能相册
const aiAlbums = computed(() => albums.value.filter(a => a.isAiGenerated))
// 手动相册
const manualAlbums = computed(() => albums.value.filter(a => !a.isAiGenerated))

const actionSheetActions = [
  { name: '重命名', key: 'rename' },
  { name: '删除', key: 'delete', color: '#ee0a24' }
]

let longPressTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  loadAlbums()
})

// keep-alive 激活时刷新
onActivated(() => {
  loadAlbums()
})

async function loadAlbums() {
  loading.value = true
  try {
    const data = await getAlbums()
    albums.value = data
  } catch (error) {
    showToast('加载相册失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onRefresh() {
  loadAlbums()
}

function viewAlbum(id: number) {
  router.push(`/albums/${id}`)
}

async function createAlbum() {
  if (!createForm.value.name.trim()) {
    showToast('请输入相册名称')
    return
  }

  try {
    await apiCreateAlbum({
      name: createForm.value.name.trim(),
      description: createForm.value.description.trim() || undefined
    })
    showToast('创建成功')
    resetCreateForm()
    loadAlbums()
  } catch (error) {
    showToast('创建失败')
  }
}

function resetCreateForm() {
  createForm.value = { name: '', description: '' }
}

function handleTouchStart(album: Album) {
  longPressTimer = setTimeout(() => {
    selectedAlbum.value = album
    showActionSheet.value = true
  }, 500)
}

function handleTouchEnd() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

async function onActionSelect(action: { key: string }) {
  if (!selectedAlbum.value) return

  if (action.key === 'rename') {
    if (selectedAlbum.value) {
      renameForm.value.name = selectedAlbum.value.name
      renameForm.value.description = selectedAlbum.value.description || ''
      showRenameDialog.value = true
    }
  } else if (action.key === 'delete') {
    confirmDelete(selectedAlbum.value)
  }
}

function showAlbumMenu(album: Album) {
  selectedAlbum.value = album
  showActionSheet.value = true
}

async function renameAlbum() {
  if (!selectedAlbum.value || !renameForm.value.name.trim()) return
  try {
    await updateAlbum(selectedAlbum.value.id, {
      name: renameForm.value.name.trim(),
      description: renameForm.value.description.trim() || undefined
    })
    showToast('重命名成功')
    loadAlbums()
  } catch (error) {
    showToast('重命名失败')
  }
}

function resetRenameForm() {
  renameForm.value.name = ''
  renameForm.value.description = ''
}

function confirmDelete(album: Album) {
  showDialog({
    title: '确认删除',
    message: `确定要删除相册 "${album.name}" 吗？相册内的照片不会被删除。`,
    showCancelButton: true
  }).then(async () => {
    try {
      await deleteAlbum(album.id)
      showToast('删除成功')
      loadAlbums()
    } catch (error) {
      showToast('删除失败')
    }
  })
}
</script>

<style scoped lang="scss">
.albums-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 80px;
}

.albums-content {
  padding: 12px;
}

.album-section {
  margin-bottom: 24px;

  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 12px;
    padding-left: 4px;
  }
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;

  @media (min-width: 768px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (min-width: 1024px) {
    grid-template-columns: repeat(4, 1fr);
  }
}

.album-card {
  background: $bg-white;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;

  &:active {
    transform: scale(0.98);
  }

  &:hover {
    box-shadow: $shadow-md;
  }
}

.album-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: $bg-color;

  .cover-image {
    width: 100%;
    height: 100%;
  }

  .cover-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, $primary-light 0%, $primary-color 100%);
    color: $text-white;
  }

  .ai-badge {
    position: absolute;
    top: 8px;
    right: 8px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    font-size: 10px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 10px;
  }
}

.album-info {
  padding: 12px;
  position: relative;

  .album-name {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
    margin-bottom: 4px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    padding-right: 24px;
  }

  .album-count {
    font-size: 12px;
    color: $text-secondary;
  }

  .btn-more {
    position: absolute;
    bottom: 12px;
    right: 12px;
    font-size: 20px;
    color: $text-secondary;
    cursor: pointer;
    padding: 4px;
    border-radius: 50%;
    transition: color 0.2s, background 0.2s;

    &:hover {
      color: $primary-color;
      background: rgba($primary-color, 0.08);
    }

    &:active {
      transform: scale(0.9);
    }
  }
}

.empty-state {
  padding: 40px 20px;
}

.create-form {
  padding: 16px;
}
</style>
