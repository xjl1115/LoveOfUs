<template>
  <div class="export-page">
    <!-- 顶部导航 -->
    <van-nav-bar title="导出照片" left-arrow @click-left="$router.back()" fixed placeholder />

    <div class="export-content">
      <!-- 省份/城市选择 -->
      <div class="section">
        <h3 class="section-title">📍 选择地点</h3>
        <div class="location-selector">
          <div class="location-field" @click="showProvincePicker = true">
            <span class="location-label">省份</span>
            <span class="location-value" :class="{ placeholder: !selectedProvince }">
              {{ selectedProvince || '全部省份' }}
            </span>
            <van-icon name="arrow" />
          </div>
          <div class="location-field" :class="{ disabled: !selectedProvince }" @click="selectedProvince && (showCityPicker = true)">
            <span class="location-label">城市</span>
            <span class="location-value" :class="{ placeholder: !selectedCity }">
              {{ selectedCity || (selectedProvince ? '全部城市' : '请先选择省份') }}
            </span>
            <van-icon name="arrow" />
          </div>
        </div>
      </div>

      <!-- 时间范围选择 -->
      <div class="section">
        <h3 class="section-title">📅 选择时间范围</h3>
        <div class="date-range">
          <div class="date-field" @click="showStartDatePicker = true">
            <span class="date-label">开始日期</span>
            <span class="date-value">{{ formatDate(startDate) }}</span>
          </div>
          <span class="date-separator">至</span>
          <div class="date-field" @click="showEndDatePicker = true">
            <span class="date-label">结束日期</span>
            <span class="date-value">{{ formatDate(endDate) }}</span>
          </div>
        </div>

        <!-- 时间轴预览 -->
        <div class="timeline-preview">
          <div class="timeline-track">
            <div
              v-for="(month, _index) in availableMonths"
              :key="month"
              class="timeline-point"
              :class="{
                'in-range': isInRange(month),
                'start': month === formatMonth(startDate),
                'end': month === formatMonth(endDate)
              }"
              @click="selectMonth(month)"
            >
              <div class="point-dot"></div>
              <span class="point-label">{{ formatMonthLabel(month) }}</span>
            </div>
          </div>
        </div>

        <div class="photo-count">
          共 <em>{{ selectedCount }}</em> 张照片
        </div>
      </div>

      <!-- 照片网格预览 -->
      <div class="section">
        <h3 class="section-title">🖼️ 照片预览</h3>
        <div class="photo-grid-section">
          <!-- 加载中 -->
          <van-loading v-if="loadingPhotos" size="24px" class="grid-loading">加载中...</van-loading>

          <!-- 无照片 -->
          <van-empty v-else-if="timelinePhotos.length === 0" description="暂无符合条件的照片" />

          <!-- 照片网格 -->
          <template v-else>
            <div class="photo-grid">
              <div
                v-for="p in displayedPhotos"
                :key="p.id"
                class="photo-grid-item"
                @click="$router.push(`/photo/${p.id}`)"
              >
                <img
                  v-if="p.storagePath"
                  v-lazy="{
                    src: p.storagePath,
                    error: '/images/photo-error.png'
                  }"
                  class="photo-grid-image"
                />
                <div v-else class="photo-grid-placeholder">
                  <van-icon name="photo" size="24" color="#ccc" />
                </div>
              </div>
            </div>

            <!-- 查看更多按钮 -->
            <div v-if="hasMorePhotos" class="view-more-btn" @click="showAllPhotos = !showAllPhotos">
              <span>{{ showAllPhotos ? '收起' : `查看更多（共 ${timelinePhotos.length} 张）` }}</span>
              <van-icon :name="showAllPhotos ? 'arrow-up' : 'arrow-down'" />
            </div>
          </template>
        </div>
      </div>

      <!-- 导出选项 -->
      <div class="section">
        <h3 class="section-title">⚙️ 导出选项</h3>
        <van-cell-group inset>
          <van-cell title="保留原始画质">
            <template #right-icon>
              <van-switch v-model="exportOptions.keepOriginal" size="20" />
            </template>
          </van-cell>
          <van-cell title="包含照片元数据">
            <template #right-icon>
              <van-switch v-model="exportOptions.includeMetadata" size="20" />
            </template>
          </van-cell>
          <van-cell title="添加水印">
            <template #right-icon>
              <van-switch v-model="exportOptions.addWatermark" size="20" />
            </template>
          </van-cell>
          <van-cell title="按日期分文件夹">
            <template #right-icon>
              <van-switch v-model="exportOptions.groupByDate" size="20" />
            </template>
          </van-cell>
        </van-cell-group>
      </div>

      <!-- 导出格式 -->
      <div class="section">
        <h3 class="section-title">📦 导出格式</h3>
        <div class="format-options">
          <div
            v-for="format in formats"
            :key="format.value"
            class="format-card"
            :class="{ active: selectedFormat === format.value }"
            @click="selectedFormat = format.value"
          >
            <van-icon :name="format.icon" size="28" />
            <span class="format-name">{{ format.name }}</span>
            <span class="format-desc">{{ format.desc }}</span>
          </div>
        </div>
      </div>

      <!-- PDF 专属选项 -->
      <div v-if="selectedFormat === 'pdf'" class="section">
        <h3 class="section-title">📄 PDF 选项</h3>
        <van-cell-group inset>
          <van-cell title="每页照片数" :value="pdfOptions.photosPerPage + ' 张'" is-link @click="showPhotosPerPagePicker = true" />
          <van-cell title="封面样式">
            <template #right-icon>
              <van-radio-group v-model="pdfOptions.coverStyle" direction="horizontal">
                <van-radio name="simple">简约</van-radio>
                <van-radio name="romantic">浪漫</van-radio>
              </van-radio-group>
            </template>
          </van-cell>
          <van-cell title="添加文字描述">
            <template #right-icon>
              <van-switch v-model="pdfOptions.includeDescription" size="20" />
            </template>
          </van-cell>
        </van-cell-group>
      </div>

      <!-- 导出按钮 -->
      <div class="export-action">
        <van-button
          type="primary"
          size="large"
          round
          block
          :loading="exporting"
          :disabled="selectedCount === 0"
          @click="startExport"
        >
          {{ exporting ? '导出中...' : '开始导出' }}
        </van-button>
      </div>
    </div>

    <!-- 日期选择器 -->
    <van-popup v-model:show="showStartDatePicker" position="bottom">
      <van-date-picker
        v-model="startDatePickerValue"
        title="选择开始日期"
        :min-date="minDate"
        :max-date="endDate ? new Date(endDate) : maxDate"
        @confirm="onStartDateConfirm"
        @cancel="showStartDatePicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showEndDatePicker" position="bottom">
      <van-date-picker
        v-model="endDatePickerValue"
        title="选择结束日期"
        :min-date="startDate ? new Date(startDate) : minDate"
        :max-date="maxDate"
        @confirm="onEndDateConfirm"
        @cancel="showEndDatePicker = false"
      />
    </van-popup>

    <!-- 每页照片数选择器 -->
    <van-popup v-model:show="showPhotosPerPagePicker" position="bottom">
      <van-picker
        :columns="photosPerPageOptions"
        @confirm="onPhotosPerPageConfirm"
        @cancel="showPhotosPerPagePicker = false"
      />
    </van-popup>

    <!-- 省份选择器 -->
    <van-popup v-model:show="showProvincePicker" position="bottom">
      <van-picker
        title="选择省份"
        :columns="provincePickerColumns"
        @confirm="onProvinceConfirm"
        @cancel="showProvincePicker = false"
      />
    </van-popup>

    <!-- 城市选择器 -->
    <van-popup v-model:show="showCityPicker" position="bottom">
      <van-picker
        title="选择城市"
        :columns="cityColumns"
        @confirm="onCityConfirm"
        @cancel="showCityPicker = false"
      />
    </van-popup>

    <!-- 导出进度弹窗 -->
    <van-dialog
      v-model:show="showProgressDialog"
      title="正在导出"
      :show-confirm-button="false"
      :show-cancel-button="false"
      close-on-click-overlay
      @close="handleCloseDialog"
    >
      <div class="progress-content">
        <van-circle
          :current-rate="exportProgress"
          :speed="0"
          :text="exportProgress + '%'"
          :stroke-width="60"
          size="120"
          :color="exportStatus === 'failed' ? '#ee0a24' : undefined"
        />
        <p class="progress-text">{{ progressText }}</p>
        <div v-if="exportStatus === 'processing'" class="progress-actions">
          <van-button
            size="small"
            plain
            type="default"
            :loading="cancelling"
            loading-text="取消中..."
            @click="handleCancelExport"
          >
            取消导出
          </van-button>
        </div>
        <div v-else-if="exportStatus === 'completed'" class="progress-actions">
          <van-button size="small" plain type="primary" @click="showProgressDialog = false">
            关闭
          </van-button>
        </div>
        <div v-else-if="exportStatus === 'failed'" class="progress-actions">
          <van-button size="small" plain type="danger" @click="handleRetryExport">
            重试
          </van-button>
        </div>
      </div>
    </van-dialog>

    <BottomTab />
  </div>
</template>

<script setup lang="ts">
import BottomTab from '@/components/BottomTab.vue'
import { ref, computed, onMounted, watch } from 'vue'
import { showToast, showSuccessToast } from 'vant'
import dayjs from 'dayjs'
import { getUserStats } from '@/api/user'
import { getTimelinePhotos } from '@/api/photo'
import { createExport, getExportStatus, cancelExport } from '@/api/export'
import type { Photo } from '@/types'
import { provinces } from '@/data/regions'

// 日期范围
const startDate = ref<string>('')
const endDate = ref<string>('')
const showStartDatePicker = ref(false)
const showEndDatePicker = ref(false)
const startDatePickerValue = ref<string[]>([])
const endDatePickerValue = ref<string[]>([])

// 省份/城市选择
const selectedProvince = ref('')
const selectedCity = ref('')
const showProvincePicker = ref(false)
const showCityPicker = ref(false)

// 省份列（使用 text/value 结构，避免 Vant 4 对象列问题）
const provincePickerColumns = computed(() => {
  const list = provinces.map(p => ({ text: p.name, value: p.name }))
  return [{ text: '全部省份', value: '' }, ...list]
})

// 城市列表（根据选中的省份动态生成）
const cityColumns = computed(() => {
  if (!selectedProvince.value) return [{ text: '全部城市', value: '' }]
  const p = provinces.find(p => p.name === selectedProvince.value)
  const cities = p ? p.cities.map(c => ({ text: c.name, value: c.name })) : []
  return [{ text: '全部城市', value: '' }, ...cities]
})

// 照片网格
const timelinePhotos = ref<Photo[]>([])
const showAllPhotos = ref(false)
const loadingPhotos = ref(false)
const DISPLAY_LIMIT = 9

const displayedPhotos = computed(() => {
  return showAllPhotos.value ? timelinePhotos.value : timelinePhotos.value.slice(0, DISPLAY_LIMIT)
})

const hasMorePhotos = computed(() => timelinePhotos.value.length > DISPLAY_LIMIT)

// 可用月份（从用户统计数据获取）
const availableMonths = ref<string[]>([])

// 导出选项
const exportOptions = ref({
  keepOriginal: true,
  includeMetadata: true,
  addWatermark: false,
  groupByDate: true
})

// 导出格式
const formats = [
  { value: 'zip', name: 'ZIP 压缩包', desc: '保留原图和 EXIF', icon: 'cluster-o' },
  { value: 'pdf', name: 'PDF 相册', desc: '可打印的相册', icon: 'description-o' },
  { value: 'video', name: '视频影集', desc: '带配乐的幻灯片', icon: 'play-circle-o' }
]
const selectedFormat = ref('zip')

// PDF 选项
const pdfOptions = ref({
  photosPerPage: 4,
  coverStyle: 'romantic',
  includeDescription: true
})
const showPhotosPerPagePicker = ref(false)
const photosPerPageOptions = [
  { text: '1 张', value: 1 },
  { text: '2 张', value: 2 },
  { text: '4 张', value: 4 },
  { text: '6 张', value: 6 },
  { text: '9 张', value: 9 }
]

// 导出状态
const exporting = ref(false)
const showProgressDialog = ref(false)
const exportProgress = ref(0)
const exportStatus = ref<'pending' | 'processing' | 'completed' | 'failed'>('pending')
const progressText = ref('准备中...')
const currentExportId = ref<number | null>(null)
const cancelling = ref(false)
const progressInterval = ref<ReturnType<typeof setInterval> | null>(null)
const pollFailCount = ref(0)

// 日期边界
const minDate = new Date(2020, 0, 1)
const maxDate = new Date()

// 计算选中的照片数量
const selectedCount = computed(() => timelinePhotos.value.length)

onMounted(() => {
  // 默认选择最近一个月
  endDate.value = dayjs().format('YYYY-MM-DD')
  startDate.value = dayjs().subtract(1, 'month').format('YYYY-MM-DD')

  loadUserStats()
  loadTimelinePhotos()
})

// 省份/城市变更时重新加载
watch([selectedProvince, selectedCity, startDate, endDate], () => {
  showAllPhotos.value = false
  loadTimelinePhotos()
})

async function loadTimelinePhotos() {
  loadingPhotos.value = true
  try {
    const params: { page?: number; size?: number; province?: string; startDate?: string; endDate?: string } = { page: 1, size: 200 }
    if (selectedProvince.value) params.province = selectedProvince.value
    if (startDate.value) params.startDate = startDate.value
    if (endDate.value) params.endDate = endDate.value
    const { list } = await getTimelinePhotos(params)
    timelinePhotos.value = list || []
  } catch {
    timelinePhotos.value = []
  } finally {
    loadingPhotos.value = false
  }
}

async function loadUserStats() {
  try {
    const stats = await getUserStats()
    if (stats.monthlyTimeline) {
      availableMonths.value = stats.monthlyTimeline.map((m: { month: string }) => m.month)
    }
  } catch (error) {
    console.error('加载用户统计失败:', error)
  }
}

function formatDate(date: string) {
  if (!date) return '请选择'
  return dayjs(date).format('YYYY年M月D日')
}

function formatMonth(date: string) {
  if (!date) return ''
  return dayjs(date).format('YYYY-MM')
}

function formatMonthLabel(month: string) {
  if (!month) return ''
  return dayjs(month).format('M月')
}

function isInRange(month: string) {
  const start = formatMonth(startDate.value)
  const end = formatMonth(endDate.value)
  return month >= start && month <= end
}

function selectMonth(month: string) {
  const date = dayjs(month + '-01')
  if (!startDate.value || (startDate.value && endDate.value)) {
    startDate.value = date.format('YYYY-MM-DD')
    endDate.value = ''
  } else {
    if (date.isBefore(dayjs(startDate.value))) {
      startDate.value = date.format('YYYY-MM-DD')
    } else {
      endDate.value = date.endOf('month').format('YYYY-MM-DD')
    }
  }
}

function onStartDateConfirm({ selectedValues }: { selectedValues: string[] }) {
  startDate.value = selectedValues.join('-')
  showStartDatePicker.value = false
}

function onEndDateConfirm({ selectedValues }: { selectedValues: string[] }) {
  endDate.value = selectedValues.join('-')
  showEndDatePicker.value = false
}

function onPhotosPerPageConfirm({ selectedValues }: { selectedValues: { value: number }[] }) {
  pdfOptions.value.photosPerPage = selectedValues[0].value
  showPhotosPerPagePicker.value = false
}

function onProvinceConfirm(e: any) {
  const raw = e?.selectedValues?.[0] ?? e?.[0] ?? ''
  selectedProvince.value = typeof raw === 'string' ? raw : (raw?.value || raw?.text || '')
  selectedCity.value = ''
  showProvincePicker.value = false
}

function onCityConfirm(e: any) {
  const raw = e?.selectedValues?.[0] ?? e?.[0] ?? ''
  selectedCity.value = typeof raw === 'string' ? raw : (raw?.value || raw?.text || '')
  showCityPicker.value = false
}

async function startExport() {
  if (!startDate.value || !endDate.value) {
    showToast('请选择时间范围')
    return
  }

  exporting.value = true
  showProgressDialog.value = true
  exportProgress.value = 0
  exportStatus.value = 'pending'
  progressText.value = '准备中...'

  try {
    const result = await createExport({
      startDate: startDate.value,
      endDate: endDate.value,
      format: selectedFormat.value as 'zip' | 'pdf' | 'video',
      options: {
        ...exportOptions.value,
        ...(selectedFormat.value === 'pdf' ? pdfOptions.value : {})
      }
    })

    currentExportId.value = result.id
    exportStatus.value = 'processing'

    // 启动轮询检查后端状态
    pollExportStatus()
  } catch (error) {
    showToast('导出失败')
    exporting.value = false
    showProgressDialog.value = false
  }
}

function pollExportStatus() {
  clearProgressInterval()
  progressInterval.value = setInterval(async () => {
    if (!currentExportId.value) {
      console.warn('轮询：currentExportId 为空，停止轮询')
      clearProgressInterval()
      return
    }

    try {
      console.log('轮询导出状态，ID:', currentExportId.value)
      const status = await getExportStatus(currentExportId.value)
      console.log('后端返回状态:', status.status)
      const backendStatus = status.status

      // 根据后端状态更新前端显示
      if (backendStatus === 'completed') {
        exportStatus.value = 'completed'
        exportProgress.value = 100
        progressText.value = '导出完成！'
        clearProgressInterval()
        exporting.value = false
      } else if (backendStatus === 'failed') {
        exportStatus.value = 'failed'
        progressText.value = '导出失败'
        clearProgressInterval()
        exporting.value = false
      } else if (backendStatus === 'processing') {
        // 模拟进度：从当前值逐步增加到 95%
        if (exportProgress.value < 95) {
          exportProgress.value += Math.floor(Math.random() * 5) + 1
          if (exportProgress.value > 95) exportProgress.value = 95
        }
        progressText.value = exportProgress.value < 30 ? '正在收集照片...' :
                             exportProgress.value < 60 ? '正在处理...' :
                             '正在生成文件...'
      } else if (backendStatus === 'pending') {
        progressText.value = '等待处理...'
      }
    } catch (error) {
      console.error('轮询导出状态失败:', error)
      // 连续失败3次则停止轮询
      pollFailCount.value++
      if (pollFailCount.value >= 3) {
        console.error('轮询连续失败3次，停止轮询')
        clearProgressInterval()
        exportStatus.value = 'failed'
        progressText.value = '获取状态失败'
        exporting.value = false
      }
    }
  }, 1500) // 每 1.5 秒轮询一次
}

function clearProgressInterval() {
  if (progressInterval.value) {
    clearInterval(progressInterval.value)
    progressInterval.value = null
  }
}

async function handleCancelExport() {
  if (!currentExportId.value) return
  cancelling.value = true
  try {
    await cancelExport(currentExportId.value)
    clearProgressInterval()
    exportStatus.value = 'failed'
    progressText.value = '已取消导出'
    showToast('已取消导出')
  } catch (error: any) {
    showToast(error?.message || '取消失败')
  } finally {
    cancelling.value = false
  }
}

function handleCloseDialog() {
  clearProgressInterval()
  if (exportStatus.value === 'completed') {
    showSuccessToast('导出成功')
  }
  exporting.value = false
  currentExportId.value = null
}

function handleRetryExport() {
  showProgressDialog.value = false
  exporting.value = false
  exportProgress.value = 0
  exportStatus.value = 'pending'
  currentExportId.value = null
  setTimeout(() => startExport(), 300)
}
</script>

<style scoped lang="scss">
.export-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 24px;
}

.export-content {
  padding: 12px;
}

.section {
  margin-bottom: 20px;

  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 12px;
    padding-left: 4px;
  }
}

.date-range {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $bg-white;
  padding: 16px;
  border-radius: $radius-lg;
  margin-bottom: 16px;

  .date-field {
    flex: 1;
    text-align: center;
    cursor: pointer;

    .date-label {
      display: block;
      font-size: 12px;
      color: $text-secondary;
      margin-bottom: 4px;
    }

    .date-value {
      display: block;
      font-size: 14px;
      color: $primary-color;
      font-weight: 500;
    }
  }

  .date-separator {
    color: $text-secondary;
    padding: 0 16px;
  }
}

.timeline-preview {
  background: $bg-white;
  padding: 20px 16px;
  border-radius: $radius-lg;
  margin-bottom: 12px;
  overflow-x: auto;

  .timeline-track {
    display: flex;
    align-items: center;
    min-width: max-content;
    gap: 8px;
  }

  .timeline-point {
    display: flex;
    flex-direction: column;
    align-items: center;
    cursor: pointer;
    padding: 4px;

    .point-dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: $border-color;
      transition: all 0.3s;
    }

    .point-label {
      font-size: 11px;
      color: $text-secondary;
      margin-top: 4px;
    }

    &.in-range .point-dot {
      background: $primary-light;
    }

    &.start .point-dot,
    &.end .point-dot {
      background: $primary-color;
      transform: scale(1.3);
    }

    &.start .point-label,
    &.end .point-label {
      color: $primary-color;
      font-weight: 600;
    }
  }
}

.photo-count {
  text-align: center;
  font-size: 14px;
  color: $text-secondary;

  em {
    color: $primary-color;
    font-size: 18px;
    font-weight: 600;
    font-style: normal;
  }
}

/* ========= 地点选择器 ========= */
.location-selector {
  display: flex;
  gap: 12px;

  .location-field {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 6px;
    background: $bg-white;
    padding: 12px 14px;
    border-radius: $radius-lg;
    cursor: pointer;
    transition: box-shadow 0.2s;

    &:active {
      box-shadow: inset 0 0 0 1px rgba($primary-color, 0.3);
    }

    &.disabled {
      opacity: 0.5;
      pointer-events: none;
    }

    .location-label {
      font-size: 12px;
      color: $text-secondary;
      white-space: nowrap;
    }

    .location-value {
      flex: 1;
      font-size: 14px;
      color: $text-primary;
      font-weight: 500;
      text-align: right;

      &.placeholder {
        color: $text-secondary;
        font-weight: 400;
      }
    }

    .van-icon-arrow {
      font-size: 14px;
      color: $text-secondary;
    }
  }
}

/* ========= 照片网格 ========= */
.photo-grid-section {
  background: $bg-white;
  border-radius: $radius-lg;
  padding: 16px;
}

.grid-loading {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;

  .photo-grid-item {
    position: relative;
    aspect-ratio: 1;
    overflow: hidden;
    border-radius: 4px;
    background: #f5f5f5;

    .photo-grid-image {
      width: 100%;
      height: 100%;
      object-fit: cover;
      opacity: 0;
      transition: opacity 0.3s ease;

      &.lazy-loaded {
        opacity: 1;
      }
    }

    .photo-grid-placeholder {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f5f5;
    }

    &:active {
      opacity: 0.8;
    }
  }
}

.view-more-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 12px 0 4px;
  font-size: 13px;
  color: $primary-color;
  cursor: pointer;

  &:active {
    opacity: 0.7;
  }
}

.format-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.format-card {
  background: $bg-white;
  border-radius: $radius-lg;
  padding: 16px 8px;
  text-align: center;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;

  .van-icon {
    color: $text-secondary;
    margin-bottom: 8px;
  }

  .format-name {
    display: block;
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
    margin-bottom: 4px;
  }

  .format-desc {
    display: block;
    font-size: 11px;
    color: $text-secondary;
  }

  &.active {
    border-color: $primary-color;
    background: $primary-light-bg;

    .van-icon {
      color: $primary-color;
    }

    .format-name {
      color: $primary-color;
    }
  }
}

.export-action {
  padding: 16px;
  margin-top: 24px;
}

.progress-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px;

  .progress-text {
    margin-top: 16px;
    font-size: 14px;
    color: $text-secondary;
  }

  .progress-actions {
    margin-top: 20px;
    display: flex;
    gap: 12px;
    justify-content: center;
  }
}

.download-icon {
  color: $primary-color;
  font-size: 20px;
  margin-left: 8px;
}

.status-completed {
  color: #07c160;
}

.status-processing {
  color: $primary-color;
}

.status-failed {
  color: #ee0a24;
}
</style>
