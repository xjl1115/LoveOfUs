<template>
  <!-- 心愿清单核心内容（不含 nav-bar），供 LoveHub 复用 -->
  <div class="wishlist-content">
    <!-- 顶部统计卡 -->
    <div class="stats-card">
      <div class="stats-progress">
        <div class="progress-num">
          <span class="achieved">{{ stats.achieved }}</span>
          <span class="divider">/</span>
          <span class="total">{{ stats.total }}</span>
        </div>
        <div class="progress-label">已完成心愿</div>
      </div>
      <div class="stats-divider"></div>
      <div class="stats-streak">
        <div class="streak-num">
          <span class="fire">🔥</span>
          <span>{{ stats.streakDays }}</span>
        </div>
        <div class="streak-label">连续打卡天数</div>
      </div>
    </div>

    <div class="wishlist-toolbar">
      <span class="toolbar-title">我的心愿</span>
      <div class="toolbar-actions">
        <van-button
          round
          type="success"
          size="small"
          class="manual-create-btn"
          @click="openCreate"
        >
          <template #icon><van-icon name="plus" /></template>
          手动新建
        </van-button>
        <van-button
          round
          type="primary"
          size="small"
          class="ai-recommend-btn"
          @click="onAiRecommend"
        >
          <template #icon><van-icon name="star-o" /></template>
          AI 推荐
        </van-button>
      </div>
    </div>

    <!-- 分类 Tab -->
    <van-tabs v-model:active="activeCategory" line-width="20px">
      <van-tab title="全部" name="all" />
      <van-tab v-for="c in categoryOptions" :key="c.value" :title="c.label" :name="c.value" />
    </van-tabs>

    <!-- 心愿列表 -->
    <div class="wish-list" v-if="filteredWishes.length > 0">
      <WishCard
        v-for="wish in filteredWishes"
        :key="wish.id"
        :wish="wish"
        @plus="onPlus(wish.id)"
        @minus="onMinus(wish.id)"
        @achieve="showAchieveDialog(wish)"
        @card="openRecordCard(wish)"
        @edit="onEdit(wish)"
        @delete="onDelete(wish.id)"
      />
    </div>
    <EmptyState v-else text="还没有心愿，试试让 AI 推荐或手动新建吧～" />

    <!-- AI 推荐抽屉 -->
    <van-popup
      v-model:show="showRecommendDrawer"
      position="bottom"
      round
      :style="{ height: '75%' }"
      closeable
    >
      <div class="drawer-content">
        <div class="drawer-header">
          <h3>AI 为你们推荐的心愿</h3>
          <p>选择感兴趣的心愿，可一键加入清单</p>
        </div>
        <div class="recommend-categories">
          <span
            v-for="c in recommendCategoryOptions"
            :key="c.value"
            class="cat-chip"
            :class="{ active: recommendCategory === c.value }"
            @click="onRecommendCategoryChange(c.value)"
          >{{ c.label }}</span>
        </div>
        <div v-if="recommending" class="drawer-loading">
          <van-loading type="spinner" size="20" />
          <span>AI 正在挑选…</span>
        </div>
        <div v-else class="recommend-list">
          <div v-for="w in recommended" class="recommend-item">
            <span class="rec-icon">{{ w.icon }}</span>
            <div class="rec-info">
              <div class="rec-title">{{ w.title }}</div>
              <div class="rec-meta">{{ CATEGORY_LABEL[w.category] }} · 目标 {{ w.targetValue }} {{ w.unit }}</div>
            </div>
            <van-button size="small" round type="primary" @click="onAcceptRecommend(w)">加入</van-button>
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 新建/编辑心愿弹窗 -->
    <van-popup v-model:show="showCreate" round closeable position="bottom" :style="{ height: '70%' }">
      <div class="form-popup">
        <div class="form-header"><h3>{{ editing ? '编辑心愿' :'新建心愿' }}</h3></div>
        <van-field v-model="form.title" label="心愿名称" placeholder="例如：一起去 5 个新城市" maxlength="30" />
        <van-field label="分类">
          <template #input>
            <div class="category-picker">
              <span
                v-for="c in categoryOptions"
                :key="c.value"
                class="cat-chip"
                :class="{ active: form.category === c.value }"
                @click="form.category = c.value as WishCategory"
              >{{ c.label }}</span>
            </div>
          </template>
        </van-field>
        <van-field v-model.number="form.targetValue" label="目标值" type="digit" placeholder="数字" />
        <van-field v-model="form.unit" label="单位" placeholder="如：次、张、个、天" maxlength="10" />
        <van-field v-model="form.deadline" label="截止日期" type="date" placeholder="可选" />
        <van-cell center title="需要双方确认">
          <template #right-icon>
            <van-switch v-model="form.needBothConfirm" size="20" />
          </template>
        </van-cell>
        <div class="form-actions">
          <van-button round block @click="showCreate = false">取消</van-button>
          <van-button round block type="primary" :loading="saving" @click="onSubmit">
            {{ editing ? '保存修改' : '加入清单' }}
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 达成庆祝弹窗 -->
    <van-popup v-model:show="showAchievePopup" round closeable position="bottom" :style="{ height: '72%' }">
      <div class="achieve-popup" v-if="achievingWish">
        <div class="achieve-header">
          <div class="achieve-emoji">🎉</div>
          <h3>达成心愿！</h3>
          <p>{{ achievingWish.title }}</p>
        </div>
        <van-field
          v-model="achieveNote"
          label="回忆笔记"
          type="textarea"
          rows="3"
          placeholder="记录这次达成的小故事～"
          maxlength="200"
          show-word-limit
        />
        <p class="achieve-photo-tip">纪念照片（可选，仅 1 张）</p>
        <van-uploader
          v-model="achievePhotoList"
          class="achieve-photo-uploader"
          :max-count="1"
          :max-size="10 * 1024 * 1024"
          accept="image/*"
          @oversize="onPhotoOversize"
        />
        <div class="achieve-actions">
          <van-button round block type="primary" :loading="achieving" @click="onConfirmAchieve">
            生成庆祝卡片
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 心愿记录卡片（HTML 卡片 → 导出图片，可保存/分享/发给 TA） -->
    <van-popup v-model:show="showCardPopup" round closeable position="bottom" :style="{ height: '86%' }">
      <div class="record-card-popup">
        <div class="record-header">
          <h3>心愿记录卡片</h3>
          <p>可保存到相册、分享或直接发给 TA</p>
        </div>
        <div class="record-preview">
          <WishCardPoster
            v-if="cardWish"
            ref="posterRef"
            :wish="cardWish"
            :photo-url="cardPhotoUrl"
          />
        </div>
        <div class="record-photo">
          <van-uploader
            v-model="cardPhotoList"
            :max-count="1"
            :max-size="10 * 1024 * 1024"
            accept="image/*"
            :after-read="onCardPhotoRead"
            @oversize="onPhotoOversize"
          >
            <van-button size="small" round plain type="primary" icon="photograph" :loading="photoUploading">
              {{ cardPhotoUrl ? '更换照片' : '上传照片' }}
            </van-button>
          </van-uploader>
          <span class="record-photo-tip">仅支持 1 张照片</span>
        </div>
        <div class="record-actions">
          <van-button round block plain type="primary" :loading="cardLoading" @click="onSaveCardImage">
            保存图片
          </van-button>
          <van-button round block plain type="primary" :loading="cardLoading" @click="onShareCardImage">
            分享
          </van-button>
          <van-button round block type="primary" :loading="sendingCard" @click="onSendCardToPartner">
            发给 TA
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { showToast, showConfirmDialog, type UploaderFileListItem } from 'vant'
import {
  listWishes,
  createWish,
  updateWish,
  updateWishProgress,
  achieveWish,
  deleteWish,
  recommendWishes,
  getWishStats,
  uploadWishItemPhoto,
  fetchWishItemPhotoUrl,
  shareWishCard,
  type Wish,
  type WishCategory,
  type WishParams
} from '@/api/wishlist'
import WishCard from '@/components/WishCard.vue'
import WishCardPoster from '@/components/WishCardPoster.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  renderWishCardImage,
  saveCardImage,
  shareCardImage,
  isWechatBrowser,
  type WishCardImage
} from '@/utils/wishCardImage'

const CATEGORY_LABEL: Record<WishCategory, string> = {
  travel: '旅行',
  food: '美食',
  experience: '体验',
  growth: '成长',
  memory: '纪念'
}

const categoryOptions: Array<{ label: string; value: WishCategory | 'all' }> = [
  { label: '旅行', value: 'travel' },
  { label: '美食', value: 'food' },
  { label: '体验', value: 'experience' },
  { label: '成长', value: 'growth' },
  { label: '纪念', value: 'memory' }
]

const allWishes = ref<Wish[]>([])
const activeCategory = ref<WishCategory | 'all'>('all')

const filteredWishes = computed(() => {
  if (activeCategory.value === 'all') return allWishes.value
  return allWishes.value.filter((w) => w.category === activeCategory.value)
})

const stats = ref({ total: 0, achieved: 0, streakDays: 0 })

async function reloadAll() {
  allWishes.value = await listWishes()
  stats.value = await getWishStats()
}

/** 防止同一卡片连点时重复提交 */
const operatingIds = new Set<Wish['id']>()

async function onPlus(id: Wish['id']) {
  if (operatingIds.has(id)) return
  operatingIds.add(id)
  try {
    const updated = await updateWishProgress(id, 1)
    if (updated) {
      await reloadAll()
      if (updated.currentValue >= updated.targetValue) {
        showAchieveDialog(updated)
      }
    }
  } finally {
    operatingIds.delete(id)
  }
}

async function onMinus(id: Wish['id']) {
  if (operatingIds.has(id)) return
  operatingIds.add(id)
  try {
    await updateWishProgress(id, -1)
    await reloadAll()
  } finally {
    operatingIds.delete(id)
  }
}

const showAchievePopup = ref(false)
const achievingWish = ref<Wish | null>(null)
const achieveNote = ref('')
const achievePhotoList = ref<UploaderFileListItem[]>([])
const achieving = ref(false)

function showAchieveDialog(wish: Wish) {
  achievingWish.value = wish
  achieveNote.value = ''
  achievePhotoList.value = []
  showAchievePopup.value = true
}

function onPhotoOversize() {
  showToast('照片大小不能超过 10MB')
}

async function onConfirmAchieve() {
  const target = achievingWish.value
  if (!target) return
  // 达成时是否选了纪念照片（可选，不选则不做任何上传）
  const photo = achievePhotoList.value[0]?.file
  achieving.value = true
  try {
    await achieveWish(target.id, { note: achieveNote.value })
    showAchievePopup.value = false
    if (photo) {
      // 照片先落库；上传失败不影响心愿达成与卡片生成
      try {
        await uploadWishItemPhoto(target.id, photo)
      } catch (e) {
        console.error('纪念照片上传失败:', e)
      }
    }
    achievePhotoList.value = []
    await reloadAll()
    const fresh = allWishes.value.find((w) => w.id === target.id)
    if (photo && fresh) {
      // 只在传了照片时才自动展示卡片；没传照片不弹卡片，避免完成后又被要求上传
      await openRecordCard(fresh)
    } else {
      showToast('已达成，可在卡片上点「记录卡片」生成卡片')
    }
  } finally {
    achieving.value = false
  }
}

const showCardPopup = ref(false)
const cardWish = ref<Wish | null>(null)
const posterRef = ref<InstanceType<typeof WishCardPoster> | null>(null)
const cardPhotoUrl = ref('')
const cardPhotoList = ref<UploaderFileListItem[]>([])
const photoUploading = ref(false)
const cardLoading = ref(false)
const sendingCard = ref(false)
/** 已导出的卡片图；照片变化后置空重新导出 */
const cardImage = ref<WishCardImage | null>(null)

/** 打开记录卡片弹窗：拉取纪念照片（同域 blob URL，导出图片时才不会污染 canvas） */
async function openRecordCard(wish: Wish) {
  cardWish.value = wish
  cardImage.value = null
  cardPhotoList.value = []
  releaseCardPhoto()
  showCardPopup.value = true
  await loadCardPhoto()
}

async function loadCardPhoto() {
  const wish = cardWish.value
  if (!wish?.achievedPhotoUrl) return
  try {
    cardPhotoUrl.value = await fetchWishItemPhotoUrl(wish.id)
  } catch (e) {
    console.error('纪念照片读取失败:', e)
  }
}

function releaseCardPhoto() {
  if (cardPhotoUrl.value) {
    URL.revokeObjectURL(cardPhotoUrl.value)
    cardPhotoUrl.value = ''
  }
}

/** 上传/更换纪念照片（仅 1 张），成功后卡片预览立即展示新照片 */
async function onCardPhotoRead(file: UploaderFileListItem | UploaderFileListItem[]) {
  const item = Array.isArray(file) ? file[0] : file
  const wish = cardWish.value
  if (!item?.file || !wish) return
  cardPhotoList.value = []
  photoUploading.value = true
  try {
    await uploadWishItemPhoto(wish.id, item.file)
    await reloadAll()
    const fresh = allWishes.value.find((w) => w.id === wish.id)
    if (fresh) cardWish.value = fresh
    cardImage.value = null
    releaseCardPhoto()
    await loadCardPhoto()
    showToast('照片已保存')
  } catch (e) {
    console.error('纪念照片上传失败:', e)
  } finally {
    photoUploading.value = false
  }
}

/** 按需把 HTML 卡片导出成图片 */
async function ensureCardImage(): Promise<WishCardImage | null> {
  if (cardImage.value) return cardImage.value
  const el = posterRef.value?.$el as HTMLElement | undefined
  if (!el || !cardWish.value) return null
  cardLoading.value = true
  try {
    cardImage.value = await renderWishCardImage(el, cardWish.value)
    return cardImage.value
  } catch (e) {
    console.error('生成心愿记录卡片失败:', e)
    showToast('卡片生成失败，请稍后重试')
    return null
  } finally {
    cardLoading.value = false
  }
}

async function onSaveCardImage() {
  if (isWechatBrowser()) {
    showToast('请点击右上角，选择"在浏览器中打开"后再保存')
    return
  }
  const image = await ensureCardImage()
  if (!image) return
  saveCardImage(image.blob, image.filename)
  showToast('图片已保存，请查看下载目录')
}

async function onShareCardImage() {
  const image = await ensureCardImage()
  if (!image) return
  const result = await shareCardImage(image.blob, image.filename, '我的心愿达成啦 🎉')
  if (result === 'unsupported') {
    showToast('当前环境不支持直接分享，可保存图片后发送')
  } else if (result === 'failed') {
    showToast('分享失败，可保存图片后发送')
  }
}

/** 把卡片图片作为卡片消息发给伴侣 */
async function onSendCardToPartner() {
  const image = await ensureCardImage()
  if (!image || !cardWish.value) return
  sendingCard.value = true
  try {
    await shareWishCard(cardWish.value.id, image.blob)
    showToast('已发送给 TA')
    showCardPopup.value = false
  } finally {
    sendingCard.value = false
  }
}

onBeforeUnmount(releaseCardPhoto)

const showCreate = ref(false)
const editing = ref(false)
const editingId = ref<Wish['id'] | null>(null)
const saving = ref(false)

const form = reactive({
  title: '',
  category: 'travel' as WishCategory,
  targetValue: 1,
  unit: '次',
  deadline: '',
  needBothConfirm: false
})

function resetForm() {
  form.title = ''
  form.category = 'travel'
  form.targetValue = 1
  form.unit = '次'
  form.deadline = ''
  form.needBothConfirm = false
}

// 由 nav-bar 上的 + 或外部调用触发打开新建弹窗
function openCreate() {
  editing.value = false
  editingId.value = null
  resetForm()
  showCreate.value = true
}

function onEdit(wish: Wish) {
  editing.value = true
  editingId.value = wish.id
  form.title = wish.title
  form.category = wish.category
  form.targetValue = wish.targetValue
  form.unit = wish.unit
  form.deadline = wish.deadline || ''
  form.needBothConfirm = !!wish.needBothConfirm
  showCreate.value = true
}

async function onSubmit() {
  if (!form.title.trim()) {
    showToast('请填写心愿名称')
    return
  }
  if (form.targetValue <= 0) {
    showToast('目标值必须大于 0')
    return
  }
  saving.value = true
  try {
    const params: WishParams = {
      title: form.title.trim(),
      category: form.category,
      targetValue: form.targetValue,
      unit: form.unit.trim() || '次',
      deadline: form.deadline || undefined,
      needBothConfirm: form.needBothConfirm
    }
    if (editing.value && editingId.value) {
      await updateWish(editingId.value, params)
      showToast('已更新')
    } else {
      await createWish(params)
      showToast('已加入心愿清单')
    }
    showCreate.value = false
    resetForm()
    editing.value = false
    editingId.value = null
    await reloadAll()
  } finally {
    saving.value = false
  }
}

async function onDelete(id: Wish['id']) {
  try {
    await showConfirmDialog({ title: '删除心愿', message: '确定删除这个心愿吗？' })
    await deleteWish(id)
    showToast('已删除')
    await reloadAll()
  } catch {
    /* 用户取消 */
  }
}

const showRecommendDrawer = ref(false)
const recommending = ref(false)
const recommended = ref<Wish[]>([])
const recommendCategory = ref<WishCategory | 'all'>('all')

const recommendCategoryOptions: Array<{ label: string; value: WishCategory | 'all' }> = [
  { label: '综合', value: 'all' },
  ...categoryOptions
]

async function onAiRecommend() {
  showRecommendDrawer.value = true
  await loadRecommendations()
}

async function onRecommendCategoryChange(value: WishCategory | 'all') {
  if (recommendCategory.value === value) return
  recommendCategory.value = value
  await loadRecommendations()
}

async function loadRecommendations() {
  recommended.value = []
  recommending.value = true
  try {
    const list = await recommendWishes({
      count: 6,
      category: recommendCategory.value === 'all' ? undefined : recommendCategory.value
    })
    // 过滤清单中已存在的心愿，并对本次结果内部去重
    const existingTitles = new Set(allWishes.value.map((w) => w.title.trim()))
    const seenTitles = new Set<string>()
    recommended.value = list.filter((w) => {
      const title = w.title.trim()
      if (existingTitles.has(title) || seenTitles.has(title)) return false
      seenTitles.add(title)
      return true
    })
  } finally {
    recommending.value = false
  }
}

async function onAcceptRecommend(w: Wish) {
  await createWish({
    title: w.title,
    category: w.category,
    icon: w.icon,
    targetValue: w.targetValue,
    unit: w.unit,
    needBothConfirm: !!w.needBothConfirm
  })
  showToast('已加入心愿清单')
  recommended.value = recommended.value.filter((x) => x.title !== w.title || x.category !== w.category)
  await reloadAll()
}

onMounted(() => {
  reloadAll()
  // 监听全局事件：页面 nav-bar 上的"+"
  window.addEventListener('wishlist:create', openCreate)
})

defineExpose({ openCreate })
</script>

<style scoped lang="scss">
.wishlist-content {
  background: $bg-color;
}

.stats-card {
  position: relative;
  margin: 16px;
  padding: 18px;
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  color: #fff;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 4px 12px rgba($primary-color, 0.3);

  .stats-progress, .stats-streak {
    flex: 1;
    text-align: center;
  }

  .progress-num {
    font-size: 24px;
    font-weight: 700;
    line-height: 1.2;
    .achieved { font-size: 28px; }
    .divider { margin: 0 2px; opacity: 0.7; }
    .total { opacity: 0.85; }
  }

  .streak-num {
    font-size: 24px;
    font-weight: 700;
    line-height: 1.2;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    .fire { font-size: 22px; }
  }

  .progress-label, .streak-label {
    font-size: 12px;
    opacity: 0.85;
    margin-top: 4px;
  }

  .stats-divider {
    width: 1px;
    height: 36px;
    background: rgba(255, 255, 255, 0.3);
  }
}

.wishlist-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 16px;
  padding: 12px 14px;
  background: #fff;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;

  .toolbar-title {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .manual-create-btn {
    margin: 0;
    padding: 0 11px;
    background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
    border: none;
  }

  .ai-recommend-btn {
    margin: 0;
    padding: 0 11px;
    background: linear-gradient(135deg, #6c8cff 0%, #8a5cff 100%);
    border: none;
  }
}

.wish-list {
  padding: 8px 16px 16px;
}

.bottom-safe {
  height: 24px;
}

.drawer-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px 16px;

  .drawer-header {
    margin-bottom: 16px;
    h3 { margin: 0 0 6px; font-size: 18px; }
    p { margin: 0; font-size: 13px; color: $text-secondary; }
  }

  .drawer-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 40px 0;
    color: $text-secondary;
    font-size: 14px;
  }

  .recommend-categories {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;

    .cat-chip {
      display: inline-flex;
      align-items: center;
      padding: 4px 12px;
      border-radius: 14px;
      background: $bg-color;
      color: $text-secondary;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.18s;

      &.active {
        background: $primary-light-bg;
        color: $primary-color;
      }
    }
  }

  .recommend-list {
    flex: 1;
    overflow-y: auto;
  }

  .recommend-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    background: #fff;
    border-radius: $radius-md;
    margin-bottom: 8px;
    box-shadow: $shadow-sm;

    .rec-icon { font-size: 28px; width: 40px; text-align: center; }

    .rec-info {
      flex: 1;
      min-width: 0;
      .rec-title { font-size: 15px; font-weight: 500; color: $text-primary; margin-bottom: 2px; }
      .rec-meta { font-size: 12px; color: $text-tertiary; }
    }
  }
}

.form-popup {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding-bottom: 20px;

  .form-header {
    text-align: center;
    padding: 16px;
    border-bottom: 1px solid $border-color;
    h3 { margin: 0; font-size: 18px; }
  }

  .category-picker {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .cat-chip {
    display: inline-flex;
    align-items: center;
    padding: 4px 12px;
    border-radius: 14px;
    background: $bg-color;
    color: $text-secondary;
    font-size: 13px;
    cursor: pointer;
    transition: all 0.18s;
    &.active {
      background: $primary-light-bg;
      color: $primary-color;
    }
  }

  .form-actions {
    margin-top: auto;
    padding: 16px 16px 0;
    display: flex;
    gap: 12px;
    :deep(.van-button) { flex: 1; }
  }
}

.achieve-popup {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;

  .achieve-header {
    text-align: center;
    padding: 24px 20px 16px;
    background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);

    .achieve-emoji { font-size: 48px; margin-bottom: 8px; }
    h3 { margin: 0 0 6px; font-size: 22px; color: $primary-color; }
    p { margin: 0; color: $text-secondary; font-size: 14px; }
  }

  .achieve-actions {
    padding: 20px 16px;
    margin-top: auto;
  }

  .achieve-photo-tip {
    margin: 4px 16px 0;
    font-size: 12px;
    color: $text-tertiary;
  }

  .achieve-photo-uploader {
    padding: 8px 16px 0;
  }
}

.record-card-popup {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;

  .record-header {
    text-align: center;
    padding: 18px 16px 10px;

    h3 { margin: 0 0 4px; font-size: 17px; }
    p { margin: 0; font-size: 12px; color: $text-tertiary; }
  }

  .record-preview {
    flex-shrink: 0;
    padding: 8px 16px;
  }

  .record-photo {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px 0;

    .record-photo-tip {
      font-size: 12px;
      color: $text-tertiary;
    }
  }

  .record-actions {
    display: flex;
    gap: 8px;
    padding: 16px 16px 24px;

    :deep(.van-button) { flex: 1; }
  }
}
</style>
