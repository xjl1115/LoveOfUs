<template>
  <!-- 这是约会策划页的核心内容（不含 nav-bar），供 LoveHub 复用 -->
  <div class="date-plan-content">
    <!-- 顶部紧凑入口：让 AI 帮我们策划（默认进入心动页后第一眼看到的是下面的"我的约会计划"） -->
    <div class="hero-compact">
      <div class="hero-info">
        <div class="hero-title">💕 让 AI 为你们策划一次约会</div>
        <div class="hero-sub">选个时间场景，AI 会定制 3 套方案</div>
      </div>
      <van-button
        round
        type="primary"
        size="small"
        class="hero-btn"
        @click="toggleAiPanel"
      >
        {{ showAiPanel ? '收起' : '✨ 立即生成' }}
      </van-button>
    </div>

    <!-- AI 策划面板（默认收起，展开后显示筛选+结果） -->
    <transition name="slide-down">
      <div v-show="showAiPanel" class="filter-card">
        <div class="filter-row">
          <div class="filter-label">时间</div>
          <div class="filter-tags">
            <span
              v-for="opt in rangeOptions"
              :key="opt.value"
              class="tag"
              :class="{ active: form.range === opt.value }"
              @click="form.range = opt.value"
            >{{ opt.label }}</span>
          </div>
        </div>

        <div v-if="form.range === 'custom'" class="filter-row filter-dates">
          <van-field
            v-model="form.startDate"
            label="起始"
            type="date"
            placeholder="开始日期"
          />
          <van-field
            v-model="form.endDate"
            label="结束"
            type="date"
            placeholder="结束日期"
          />
        </div>

        <div class="filter-row">
          <div class="filter-label">场景</div>
          <div class="filter-tags">
            <span
              v-for="s in sceneOptions"
              :key="s"
              class="tag"
              :class="{ active: form.scenes.includes(s) }"
              @click="toggleScene(s)"
            >{{ s }}</span>
          </div>
        </div>

        <div class="filter-row">
          <div class="filter-label">预算</div>
          <div class="filter-tags">
            <span
              v-for="b in budgetOptions"
              :key="b.value"
              class="tag"
              :class="{ active: form.budget === b.value }"
              @click="form.budget = b.value"
            >{{ b.label }}</span>
          </div>
        </div>

        <div class="filter-row filter-fields">
          <van-field
            v-model="form.location"
            label="地点"
            maxlength="50"
            placeholder="想去的地方（可选）"
          />
          <van-field
            v-model="form.remark"
            label="备注"
            type="textarea"
            rows="2"
            autosize
            maxlength="200"
            placeholder="补充要求（可选），如：想安静一点"
          />
        </div>

        <van-button
          block
          round
          type="primary"
          :loading="recommending"
          loading-text="AI 正在思考…"
          class="recommend-btn"
          @click="onRecommend"
        >
          ✨ 让 AI 推荐
        </van-button>
      </div>
    </transition>

    <!-- AI 推荐结果 -->
    <div v-if="recommended.length > 0" class="recommend-section">
      <div class="section-title">
        <span class="bar"></span>
        <span>AI 推荐方案</span>
        <span class="section-extra">点击保存到我的计划</span>
      </div>
      <PlanCard
        v-for="plan in recommended"
        :key="plan.id"
        :plan="plan"
        @save="onSavePlan"
      />
    </div>

    <!-- 我的约会计划：默认第一眼看到 -->
    <!-- 新增入口放在内容标题区，避免遮挡底部导航 -->
    <div class="saved-section">
      <div class="section-title section-title-with-action">
        <span class="bar"></span>
        <span>我的约会计划</span>
        <span class="section-extra">共 {{ allPlans.length }} 个</span>
        <van-button
          round
          plain
          size="small"
          class="section-action-btn"
          @click="openAddPlanDialog"
        >
          <template #icon><van-icon name="plus" /></template>
          新增计划
        </van-button>
      </div>

      <van-tabs v-model:active="activeTab" line-width="20px">
        <van-tab :title="`进行中 (${myPlans.planned.length})`" :name="0">
          <PlanCard
            v-for="plan in myPlans.planned"
            :key="plan.id"
            :plan="plan"
            show-actions
            @done="onMarkDone(plan.id)"
            @edit="onEditPlan"
            @cancel="onCancelPlan(plan.id)"
            @delete="onDeletePlan(plan.id)"
          />
          <EmptyState v-if="myPlans.planned.length === 0" text="还没有计划，点上方“新增计划”吧～" />
        </van-tab>
        <van-tab :title="`已完成 (${myPlans.done.length})`" :name="1">
          <PlanCard
            v-for="plan in myPlans.done"
            :key="plan.id"
            :plan="plan"
            show-actions
            @detail="onShowPlanDetail"
            @delete="onDeletePlan(plan.id)"
          />
          <EmptyState v-if="myPlans.done.length === 0" text="完成一次约会就会出现在这里" />
        </van-tab>
        <van-tab :title="`已取消 (${myPlans.cancelled.length})`" :name="2">
          <PlanCard
            v-for="plan in myPlans.cancelled"
            :key="plan.id"
            :plan="plan"
            show-actions
            @delete="onDeletePlan(plan.id)"
          />
          <EmptyState v-if="myPlans.cancelled.length === 0" text="无已取消的计划" />
        </van-tab>
      </van-tabs>
    </div>

    <!-- 新增约会计划弹窗 -->
    <van-popup
      v-model:show="showAddPlan"
      round
      closeable
      position="bottom"
      :style="{ height: '70%' }"
    >
      <div class="add-plan-popup">
        <div class="form-header"><h3>新增约会计划</h3></div>
        <van-field
          v-model="newPlan.title"
          label="标题"
          placeholder="例如：一起去看樱花"
          maxlength="30"
        />
        <van-field
          v-model="newPlan.date"
          label="日期"
          type="date"
          placeholder="选择日期"
        />
        <van-field label="时段">
          <template #input>
            <div class="category-picker">
              <span
                v-for="t in timeSlots"
                :key="t.value"
                class="cat-chip"
                :class="{ active: newPlan.timeSlot === t.value }"
                @click="newPlan.timeSlot = t.value"
              >{{ t.label }}</span>
            </div>
          </template>
        </van-field>
        <van-field label="场景">
          <template #input>
            <div class="category-picker">
              <span
                v-for="s in sceneOptions"
                :key="s"
                class="cat-chip"
                :class="{ active: newPlan.scenes.includes(s) }"
                @click="toggleNewScene(s)"
              >{{ s }}</span>
            </div>
          </template>
        </van-field>
        <van-field label="预算">
          <template #input>
            <div class="category-picker">
              <span
                v-for="b in budgetOptions"
                :key="b.value"
                class="cat-chip"
                :class="{ active: newPlan.budget === b.value }"
                @click="newPlan.budget = b.value"
              >{{ b.label }}</span>
            </div>
          </template>
        </van-field>
        <van-field
          v-model="newPlan.locationSuggestion"
          label="地点"
          placeholder="例如：XX 公园（可选）"
        />
        <div class="form-actions">
          <van-button round block @click="showAddPlan = false">取消</van-button>
          <van-button round block type="primary" :loading="saving" @click="onSubmitNewPlan">
            添加到计划
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 完成约会方式选择 -->
    <van-action-sheet
      v-model:show="showDoneSheet"
      :actions="doneActions"
      cancel-text="取消"
      close-on-click-action
      @select="onDoneActionSelect"
    />

    <!-- 上传约会照片弹窗 -->
    <van-popup v-model:show="showPhotoUpload" round closeable position="bottom">
      <div class="photo-upload-popup">
        <div class="form-header"><h3>上传约会照片</h3></div>
        <van-uploader
          v-model="uploadFiles"
          multiple
          accept="image/*"
          :max-size="10 * 1024 * 1024"
          @oversize="onPhotoOversize"
        />
        <p class="photo-upload-tip">照片会上传到云端并保存到这次约会记录里</p>
        <div class="form-actions">
          <van-button round block @click="showPhotoUpload = false">取消</van-button>
          <van-button round block type="primary" :loading="finishing" @click="onFinishWithPhotos">
            上传并完成
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 修改约会计划弹窗 -->
    <van-popup
      v-model:show="showEditPlan"
      round
      closeable
      position="bottom"
      :style="{ height: '70%' }"
    >
      <div class="add-plan-popup">
        <div class="form-header"><h3>修改约会计划</h3></div>
        <van-field v-model="editPlan.title" label="标题" maxlength="30" placeholder="例如：一起去看樱花" />
        <van-field v-model="editPlan.date" label="日期" type="date" placeholder="选择日期" />
        <van-field label="时段">
          <template #input>
            <div class="category-picker">
              <span
                v-for="t in timeSlots"
                :key="t.value"
                class="cat-chip"
                :class="{ active: editPlan.timeSlot === t.value }"
                @click="editPlan.timeSlot = t.value"
              >{{ t.label }}</span>
            </div>
          </template>
        </van-field>
        <van-field label="场景">
          <template #input>
            <div class="category-picker">
              <span
                v-for="s in sceneOptions"
                :key="s"
                class="cat-chip"
                :class="{ active: editPlan.scenes.includes(s) }"
                @click="toggleEditScene(s)"
              >{{ s }}</span>
            </div>
          </template>
        </van-field>
        <van-field label="预算">
          <template #input>
            <div class="category-picker">
              <span
                v-for="b in budgetOptions"
                :key="b.value"
                class="cat-chip"
                :class="{ active: editPlan.budget === b.value }"
                @click="editPlan.budget = b.value"
              >{{ b.label }}</span>
            </div>
          </template>
        </van-field>
        <van-field
          v-model="editPlan.location"
          label="地点"
          maxlength="50"
          placeholder="具体地点（可选）"
        />
        <van-field
          v-model="editPlan.remark"
          label="备注"
          type="textarea"
          rows="2"
          autosize
          maxlength="200"
          placeholder="补充备注（可选）"
        />
        <div class="form-actions">
          <van-button round block @click="showEditPlan = false">取消</van-button>
          <van-button round block type="primary" :loading="saving" @click="onSubmitEditPlan">
            保存修改
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 已完成约会详情弹窗 -->
    <van-popup
      v-model:show="showDetail"
      round
      closeable
      position="bottom"
      :style="{ height: '70%' }"
    >
      <div v-if="detailPlan" class="plan-detail-popup">
        <div class="form-header"><h3>约会详情</h3></div>
        <div class="detail-body">
          <div class="detail-title">{{ detailPlan.title }}</div>
          <div class="detail-line">
            <van-icon name="calendar-o" />
            <span>{{ detailPlan.date || '未设定日期' }} · {{ timeSlotLabel(detailPlan.timeSlot) }}</span>
          </div>
          <div class="detail-scenes">
            <span v-for="s in detailPlan.scenes" :key="s" class="scene-tag">{{ s }}</span>
            <van-tag plain type="success">{{ budgetLabel(detailPlan.budget) }}</van-tag>
          </div>
          <div v-if="detailPlan.locationSuggestion" class="detail-line">
            <van-icon name="location-o" />
            <span>推荐地点：{{ detailPlan.locationSuggestion }}</span>
          </div>
          <div v-if="detailPlan.location" class="detail-line">
            <van-icon name="location-o" />
            <span>地点：{{ detailPlan.location }}</span>
          </div>
          <div v-if="detailPlan.remark" class="detail-line">
            <van-icon name="notes-o" />
            <span>备注：{{ detailPlan.remark }}</span>
          </div>
          <div v-if="detailPlan.reason" class="detail-line">
            <van-icon name="gem-o" />
            <span>AI 推荐理由：{{ detailPlan.reason }}</span>
          </div>
          <div v-if="detailPlan.tips.length > 0" class="detail-line">
            <van-icon name="bulb-o" />
            <span>贴心提示：{{ detailPlan.tips.join('；') }}</span>
          </div>

          <div class="detail-photos-title">约会照片（{{ detailPlan.photos.length }}）</div>
          <div v-if="detailPlan.photos.length === 0" class="detail-photos-empty">
            这次约会还没有照片
          </div>
          <div v-else class="detail-photos-grid">
            <div
              v-for="(url, index) in detailPlan.photos"
              :key="url"
              class="detail-photo-cell"
              @click="previewPhotos(index)"
            >
              <img :src="url" alt="约会照片" />
            </div>
          </div>
        </div>
      </div>
    </van-popup>

    <van-image-preview
      v-model:show="showPhotoPreview"
      :images="previewImages"
      :start-position="previewIndex"
      :closeable="true"
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed as vueComputed, onMounted } from 'vue'
import { showToast } from 'vant'
import {
  recommendDatePlans,
  listDatePlans,
  saveDatePlan,
  updateDatePlan,
  updateDatePlanStatus,
  uploadDatePlanPhoto,
  deleteDatePlan,
  type DatePlan,
  type DatePlanBudget,
  type DatePlanRecommendParams,
  type DatePlanTimeSlot
} from '@/api/datePlan'
import PlanCard from '@/components/PlanCard.vue'
import EmptyState from '@/components/EmptyState.vue'

// ============== AI 面板折叠 ==============
const showAiPanel = ref(false)
function toggleAiPanel() {
  showAiPanel.value = !showAiPanel.value
}

// ============== 筛选表单 ==============
const rangeOptions = [
  { label: '今晚', value: 'today' as const },
  { label: '本周末', value: 'weekend' as const },
  { label: '下周末', value: 'nextWeekend' as const },
  { label: '自定义', value: 'custom' as const }
]
const sceneOptions = ['浪漫', '户外', '美食', '文艺', '运动', '居家', '城市探索']
const budgetOptions = [
  { label: '免费', value: 'free' as const },
  { label: '<100', value: 'low' as const },
  { label: '100-300', value: 'mid' as const },
  { label: '300-500', value: 'high' as const },
  { label: '>500', value: 'luxury' as const }
]
const timeSlots: { label: string; value: DatePlanTimeSlot }[] = [
  { label: '上午', value: 'morning' },
  { label: '中午', value: 'noon' },
  { label: '下午', value: 'afternoon' },
  { label: '傍晚', value: 'evening' },
  { label: '晚上', value: 'night' }
]

const form = reactive<DatePlanRecommendParams & { startDate: string; endDate: string }>({
  range: 'weekend',
  startDate: '',
  endDate: '',
  scenes: ['浪漫', '美食'],
  budget: 'mid',
  location: '',
  remark: ''
})

function toggleScene(s: string) {
  const i = form.scenes.indexOf(s)
  if (i >= 0) form.scenes.splice(i, 1)
  else form.scenes.push(s)
}

// ============== 推荐结果 ==============
const recommended = ref<DatePlan[]>([])
const recommending = ref(false)

async function onRecommend() {
  if (form.range === 'custom' && (!form.startDate || !form.endDate)) {
    showToast('请选择起始和结束日期')
    return
  }
  recommending.value = true
  recommended.value = []
  try {
    const res = await recommendDatePlans({
      range: form.range,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
      scenes: form.scenes,
      budget: form.budget,
      location: form.location?.trim() || undefined,
      remark: form.remark?.trim() || undefined
    })
    recommended.value = res.plans
    if (res.plans.length === 0) showToast('暂时没有合适方案，换个条件试试')
  } catch {
    showToast('推荐失败，请稍后重试')
  } finally {
    recommending.value = false
  }
}

// ============== 我的计划 ==============
const allPlans = ref<DatePlan[]>([])
const activeTab = ref<0 | 1 | 2>(0)

const myPlans = vueComputed(() => ({
  planned: allPlans.value.filter((p) => p.status === 0),
  done: allPlans.value.filter((p) => p.status === 1),
  cancelled: allPlans.value.filter((p) => p.status === 2)
}))

async function loadMyPlans() {
  allPlans.value = await listDatePlans()
}

async function onSavePlan(plan: DatePlan) {
  await saveDatePlan({
    title: plan.title,
    date: plan.date,
    timeSlot: plan.timeSlot,
    scenes: plan.scenes,
    location: plan.location,
    locationSuggestion: plan.locationSuggestion,
    remark: plan.remark,
    budget: plan.budget,
    reason: plan.reason,
    tips: plan.tips,
    photos: plan.photos,
    status: 0
  })
  showToast('已加入「进行中」')
  await loadMyPlans()
}

// 完成约会：先让用户选择「上传照片」或「直接完成」
const showDoneSheet = ref(false)
const donePlanId = ref<DatePlan['id']>('')
// 用 key 区分动作：不能按名字判断（"不上传，直接完成" 同样包含子串「上传」）
const doneActions = [
  { name: '📷 上传照片并完成', key: 'upload' },
  { name: '✅ 不上传，直接完成', key: 'skip' }
]

function onMarkDone(id: DatePlan['id']) {
  donePlanId.value = id
  showDoneSheet.value = true
}

function onDoneActionSelect(action: { name: string; key?: string }) {
  if (action.key === 'upload') {
    uploadFiles.value = []
    showPhotoUpload.value = true
  } else {
    finishPlanDirectly()
  }
}

// 不上传照片：直接标记完成
const finishing = ref(false)

async function finishPlanDirectly() {
  const id = donePlanId.value
  finishing.value = true
  try {
    await updateDatePlanStatus(id, 1)
    showToast('已标记为完成 ✨')
    await loadMyPlans()
  } finally {
    finishing.value = false
  }
}

// 上传照片后再标记完成（后端落库 + 写入 OSS）
const showPhotoUpload = ref(false)
const uploadFiles = ref<any[]>([])

function onPhotoOversize() {
  showToast('单张照片不能超过 10MB')
}

async function onFinishWithPhotos() {
  const id = donePlanId.value
  finishing.value = true
  try {
    for (const item of uploadFiles.value) {
      if (item?.file) await uploadDatePlanPhoto(id, item.file)
    }
    await updateDatePlanStatus(id, 1)
    showToast('已上传照片并标记为完成 ✨')
    showPhotoUpload.value = false
    await loadMyPlans()
  } finally {
    finishing.value = false
  }
}

// ============== 修改约会计划 ==============
const showEditPlan = ref(false)
const editPlan = reactive<{
  id: DatePlan['id']
  title: string
  date: string
  timeSlot: DatePlanTimeSlot
  scenes: string[]
  budget: DatePlanBudget
  location: string
  remark: string
}>({
  id: '',
  title: '',
  date: '',
  timeSlot: 'night',
  scenes: [],
  budget: 'mid',
  location: '',
  remark: ''
})

function onEditPlan(plan: DatePlan) {
  editPlan.id = plan.id
  editPlan.title = plan.title
  editPlan.date = plan.date || ''
  editPlan.timeSlot = plan.timeSlot
  editPlan.scenes = [...plan.scenes]
  editPlan.budget = plan.budget
  editPlan.location = plan.location || ''
  editPlan.remark = plan.remark || ''
  showEditPlan.value = true
}

function toggleEditScene(s: string) {
  const i = editPlan.scenes.indexOf(s)
  if (i >= 0) editPlan.scenes.splice(i, 1)
  else editPlan.scenes.push(s)
}

async function onSubmitEditPlan() {
  if (!editPlan.title.trim()) {
    showToast('请填写计划标题')
    return
  }
  saving.value = true
  try {
    await updateDatePlan(editPlan.id, {
      title: editPlan.title.trim(),
      date: editPlan.date,
      timeSlot: editPlan.timeSlot,
      scenes: editPlan.scenes,
      budget: editPlan.budget,
      location: editPlan.location.trim(),
      remark: editPlan.remark.trim()
    })
    showToast('已保存修改')
    showEditPlan.value = false
    await loadMyPlans()
  } finally {
    saving.value = false
  }
}

// ============== 已完成约会详情 ==============
const showDetail = ref(false)
const detailPlan = ref<DatePlan | null>(null)

function onShowPlanDetail(plan: DatePlan) {
  detailPlan.value = plan
  showDetail.value = true
}

const showPhotoPreview = ref(false)
const previewImages = ref<string[]>([])
const previewIndex = ref(0)

function previewPhotos(index: number) {
  if (!detailPlan.value) return
  previewImages.value = detailPlan.value.photos
  previewIndex.value = index
  showPhotoPreview.value = true
}

function timeSlotLabel(value: DatePlanTimeSlot): string {
  return timeSlots.find((t) => t.value === value)?.label ?? value
}

function budgetLabel(value: DatePlanBudget): string {
  return budgetOptions.find((b) => b.value === value)?.label ?? value
}

async function onCancelPlan(id: DatePlan['id']) {
  await updateDatePlanStatus(id, 2)
  await loadMyPlans()
}

async function onDeletePlan(id: DatePlan['id']) {
  await deleteDatePlan(id)
  await loadMyPlans()
}

// ============== 新增约会计划（内容区操作） ==============
const showAddPlan = ref(false)
const saving = ref(false)

const newPlan = reactive<{
  title: string
  date: string
  timeSlot: DatePlanTimeSlot
  scenes: string[]
  budget: DatePlanBudget
  locationSuggestion: string
}>({
  title: '',
  date: '',
  timeSlot: 'night',
  scenes: ['浪漫'],
  budget: 'mid',
  locationSuggestion: ''
})

function resetNewPlan() {
  newPlan.title = ''
  newPlan.date = ''
  newPlan.timeSlot = 'night'
  newPlan.scenes = ['浪漫']
  newPlan.budget = 'mid'
  newPlan.locationSuggestion = ''
}

function openAddPlanDialog() {
  resetNewPlan()
  showAddPlan.value = true
}

function toggleNewScene(s: string) {
  const i = newPlan.scenes.indexOf(s)
  if (i >= 0) newPlan.scenes.splice(i, 1)
  else newPlan.scenes.push(s)
}

async function onSubmitNewPlan() {
  if (!newPlan.title.trim()) {
    showToast('请填写计划标题')
    return
  }
  saving.value = true
  try {
    await saveDatePlan({
      title: newPlan.title.trim(),
      date: newPlan.date || '',
      timeSlot: newPlan.timeSlot,
      scenes: newPlan.scenes,
      location: '',
      locationSuggestion: newPlan.locationSuggestion.trim(),
      remark: '',
      budget: newPlan.budget,
      reason: '',
      tips: [],
      photos: [],
      status: 0
    })
    showToast('已添加 ✨')
    showAddPlan.value = false
    await loadMyPlans()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadMyPlans()
})
</script>

<style scoped lang="scss">
.date-plan-content {
  background: $bg-color;
  position: relative;
}

// 顶部紧凑入口（让用户一眼看到主功能：新增计划、已有计划）
.hero-compact {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px;
  padding: 14px 16px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe7e7 100%);
  border-radius: $radius-lg;

  .hero-info {
    flex: 1;
    min-width: 0;
  }

  .hero-title {
    font-size: 15px;
    font-weight: 600;
    color: $primary-color;
    margin-bottom: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .hero-sub {
    font-size: 12px;
    color: $text-secondary;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .hero-btn {
    flex-shrink: 0;
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    border: none;
    padding: 0 14px;
  }
}

// AI 面板（默认收起）
.filter-card {
  margin: 0 16px 16px;
  padding: 16px;
  background: #fff;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;

  .filter-row { margin-bottom: 14px; &:last-child { margin-bottom: 0; } }
  .filter-label { font-size: 13px; color: $text-secondary; margin-bottom: 8px; }

  .filter-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .tag {
    display: inline-flex;
    align-items: center;
    padding: 6px 12px;
    border-radius: 16px;
    background: $bg-color;
    color: $text-secondary;
    font-size: 13px;
    cursor: pointer;
    transition: all 0.18s;

    &:active { transform: scale(0.96); }
    &.active {
      background: $primary-light-bg;
      color: $primary-color;
      font-weight: 500;
    }
  }

  .filter-dates {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  .filter-fields {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .filter-dates,
  .filter-fields {
    :deep(.van-field) {
      background: $bg-color;
      border-radius: $radius-md;
      padding: 4px 8px;
    }
  }

  .recommend-btn {
    margin-top: 6px;
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    border: none;
  }
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}
.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  max-height: 0;
  transform: translateY(-8px);
}
.slide-down-enter-to,
.slide-down-leave-from {
  opacity: 1;
  max-height: 800px;
  transform: translateY(0);
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 16px 16px 12px;
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;

  .bar {
    width: 3px;
    height: 14px;
    background: $primary-color;
    border-radius: 2px;
    align-self: center;
  }

  .section-extra {
    margin-left: auto;
    font-size: 12px;
    font-weight: 400;
    color: $text-tertiary;
  }
}

.recommend-section,
.saved-section {
  margin-bottom: 12px;
}

.section-title-with-action {
  flex-wrap: wrap;
}

.section-action-btn {
  margin: 0;
  padding: 0 10px;
}

@media (max-width: 360px) {
  .section-title-with-action {
    .section-extra {
      margin-left: 0;
    }
  }
}

// 新增弹窗样式
.add-plan-popup {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding-bottom: 20px;
  // 弹窗高度固定为 70%，内容超出时滚动查看，而不是把字段压缩变形
  overflow-y: auto;

  // van-cell 自带 overflow:hidden，自动最小高度为 0，高度不够时会被压得比内容还矮，
  // chip 就会溢出到分隔线上造成遮挡，这里禁止压缩
  :deep(.van-field) {
    flex-shrink: 0;
  }

  .form-header {
    text-align: center;
    padding: 16px;
    border-bottom: 1px solid $border-color;
    flex-shrink: 0;
    h3 { margin: 0; font-size: 18px; }
  }

  .category-picker {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    // chip 换行后与上下分隔线留出间距，避免挤在一起
    padding: 4px 0;
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
    flex-shrink: 0;
    margin-top: auto;
    padding: 16px 16px 0;
    display: flex;
    gap: 12px;
    :deep(.van-button) { flex: 1; }
  }
}

// 上传约会照片弹窗
.photo-upload-popup {
  padding-bottom: 20px;

  .form-header {
    text-align: center;
    padding: 16px;
    border-bottom: 1px solid $border-color;
    h3 { margin: 0; font-size: 18px; }
  }

  .van-uploader {
    padding: 16px;
  }

  .photo-upload-tip {
    margin: 0 16px;
    font-size: 12px;
    color: $text-tertiary;
  }

  .form-actions {
    margin-top: 16px;
    padding: 0 16px;
    display: flex;
    gap: 12px;
    :deep(.van-button) { flex: 1; }
  }
}

// 已完成约会详情弹窗
.plan-detail-popup {
  height: 100%;
  display: flex;
  flex-direction: column;

  .form-header {
    text-align: center;
    padding: 16px;
    border-bottom: 1px solid $border-color;
    h3 { margin: 0; font-size: 18px; }
  }

  .detail-body {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
  }

  .detail-title {
    font-size: 17px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 10px;
  }

  .detail-line {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.6;
    margin-bottom: 8px;

    .van-icon {
      margin-top: 2px;
      color: $primary-color;
      flex-shrink: 0;
    }
  }

  .detail-scenes {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 10px;

    .scene-tag {
      display: inline-flex;
      align-items: center;
      padding: 3px 10px;
      border-radius: 12px;
      background: $primary-light-bg;
      color: $primary-color;
      font-size: 12px;
    }
  }

  .detail-photos-title {
    margin: 16px 0 8px;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }

  .detail-photos-empty {
    font-size: 13px;
    color: $text-tertiary;
  }

  .detail-photos-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;

    .detail-photo-cell {
      position: relative;
      padding-top: 100%;
      border-radius: $radius-md;
      overflow: hidden;
      background: $bg-color;

      img {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }
  }
}
</style>
