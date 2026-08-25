<template>
  <div class="upload-page">
    <van-nav-bar
      title="上传照片"
      left-arrow
      @click-left="$router.back()"
      fixed
      placeholder
    />

    <div class="upload-content">
      <!-- 照片选择 -->
      <van-uploader
        v-model="fileList"
        multiple
        :max-count="20"
        :max-size="10 * 1024 * 1024"
        :preview-image="true"
        accept="image/jpeg,image/png,image/gif,image/webp"
        :before-read="beforeRead"
        @oversize="onOversize"
      />

      <!-- 表单 -->
      <van-form @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="form.takenDate"
            is-link
            readonly
            name="takenDate"
            label="拍摄日期"
            placeholder="选择日期"
            :rules="[{ required: true, message: '请选择拍摄日期' }]"
            @click="showDatePicker = true"
          />
          <van-field
            v-model="form.country"
            name="country"
            label="国家"
            placeholder="输入国家名称"
            :rules="[{ required: true, message: '请输入国家' }]"
          />
          <van-field
            v-model="form.province"
            is-link
            readonly
            name="province"
            label="省份"
            placeholder="选择省份"
            :rules="[{ required: true, message: '请选择省份' }]"
            @click="showProvincePicker = true"
          />
          <van-field
            v-model="form.city"
            is-link
            readonly
            name="city"
            label="市区"
            :placeholder="form.province ? '选择市区' : '请先选择省份'"
            :disabled="!form.province"
            :rules="[{ required: true, message: '请选择市区' }]"
            @click="form.province && (showCityPicker = true)"
          />
          <van-field
            v-model="form.locationName"
            name="locationName"
            label="景点名称"
            placeholder="输入具体景点名称"
            :rules="[{ required: true, message: '请输入景点名称' }]"
          />
          <van-field
            v-model="form.description"
            rows="3"
            autosize
            label="描述"
            type="textarea"
            maxlength="200"
            placeholder="添加这段回忆的描述..."
            show-word-limit
            :rules="[{ required: true, message: '请输入描述' }]"
          />
          <van-field
            :model-value="selectedAlbumName"
            is-link
            readonly
            name="albumId"
            label="相册"
            placeholder="选择或创建相册"
            :rules="[{ required: true, message: '请选择相册' }]"
            @click="showAlbumPicker = true"
          />
        </van-cell-group>

        <div class="form-actions">
          <van-button round block type="primary" native-type="submit" :loading="uploading">
            发布
          </van-button>
        </div>
      </van-form>
    </div>

    <!-- 日期选择器 -->
    <van-popup v-model:show="showDatePicker" position="bottom">
      <van-picker
        title="选择日期"
        :columns="dateColumns"
        :default-index="defaultDateIndex"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
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
        :columns="cityPickerColumns"
        @confirm="onCityConfirm"
        @cancel="showCityPicker = false"
      />
    </van-popup>

    <!-- 相册选择器 -->
    <van-popup v-model:show="showAlbumPicker" position="bottom">
      <van-picker
        title="选择相册"
        :columns="albumPickerColumns"
        @confirm="onAlbumConfirm"
        @cancel="showAlbumPicker = false"
      />
    </van-popup>

    <!-- 新建相册弹窗 -->
    <van-popup v-model:show="showCreateAlbum" round :style="{ width: '85%', maxWidth: '400px' }">
      <div class="create-album-popup">
        <div class="popup-header"><h3>新建相册</h3></div>
        <van-field v-model="newAlbumName" placeholder="请输入相册名称" maxlength="20" clearable />
        <div class="popup-actions">
          <van-button round block type="primary" @click="confirmCreateAlbum">创建</van-button>
        </div>
      </div>
    </van-popup>

    <BottomTab />
  </div>
</template>

<script setup lang="ts">
import BottomTab from '@/components/BottomTab.vue'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import dayjs from 'dayjs'
import { uploadPhoto } from '@/api/photo'
import { getAlbums, createAlbum, addPhotosToAlbum } from '@/api/album'
import type { UploaderFileListItem } from 'vant'
import type { Album } from '@/types'
import { provinces } from '@/data/regions'

// ==================== 文件上传安全验证 ====================

/** 允许的文件类型白名单 */
const ALLOWED_FILE_TYPES = [
  'image/jpeg',
  'image/jpg',
  'image/png',
  'image/gif',
  'image/webp'
]

/** 允许的文件扩展名白名单 */
const ALLOWED_FILE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp']

/** 最大文件大小 (10MB) */
const MAX_FILE_SIZE = 10 * 1024 * 1024

/** 文件名最大长度 */
const MAX_FILENAME_LENGTH = 100

/**
 * 验证文件名安全性
 * - 防止路径遍历攻击 (../)
 * - 防止空文件名
 * - 限制文件名长度
 * - 只允许字母数字、中文、下划线、连字符、点
 */
const validateFileName = (fileName: string): { valid: boolean; message?: string } => {
  if (!fileName || fileName.trim().length === 0) {
    return { valid: false, message: '文件名不能为空' }
  }

  // 检查路径遍历攻击
  if (fileName.includes('..') || fileName.includes('/') || fileName.includes('\\')) {
    return { valid: false, message: '文件名包含非法字符' }
  }

  // 检查文件名长度
  if (fileName.length > MAX_FILENAME_LENGTH) {
    return { valid: false, message: `文件名长度不能超过${MAX_FILENAME_LENGTH}个字符` }
  }

  // 只允许安全的字符：字母数字、中文、空格、下划线、连字符、点
  const safeFileNameRegex = /^[\u4e00-\u9fa5a-zA-Z0-9_ .-]+$/
  if (!safeFileNameRegex.test(fileName)) {
    return { valid: false, message: '文件名包含非法字符' }
  }

  return { valid: true }
}

/**
 * 验证文件类型
 */
const validateFileType = (file: File): { valid: boolean; message?: string } => {
  // 检查 MIME 类型
  if (!ALLOWED_FILE_TYPES.includes(file.type.toLowerCase())) {
    return { valid: false, message: '仅支持 JPG、PNG、GIF、WebP 格式的图片' }
  }

  // 双重验证：检查文件扩展名
  const fileName = file.name.toLowerCase()
  const hasValidExtension = ALLOWED_FILE_EXTENSIONS.some(ext => fileName.endsWith(ext))
  if (!hasValidExtension) {
    return { valid: false, message: '文件扩展名不合法' }
  }

  return { valid: true }
}

/**
 * 验证文件大小
 */
const validateFileSize = (file: File): { valid: boolean; message?: string } => {
  if (file.size > MAX_FILE_SIZE) {
    return { valid: false, message: '文件大小不能超过 10MB' }
  }

  if (file.size === 0) {
    return { valid: false, message: '文件不能为空' }
  }

  return { valid: true }
}

/**
 * 文件读取前验证（van-uploader 的 before-read 回调）
 */
const beforeRead = (file: File | File[]): boolean | Promise<File | File[] | undefined> => {
  // 处理多文件上传
  const files = Array.isArray(file) ? file : [file]

  for (const f of files) {
    // 验证文件名
    const nameValidation = validateFileName(f.name)
    if (!nameValidation.valid) {
      showToast(nameValidation.message!)
      return false
    }

    // 验证文件类型
    const typeValidation = validateFileType(f)
    if (!typeValidation.valid) {
      showToast(typeValidation.message!)
      return false
    }

    // 验证文件大小
    const sizeValidation = validateFileSize(f)
    if (!sizeValidation.valid) {
      showToast(sizeValidation.message!)
      return false
    }
  }

  return true
}

const router = useRouter()

const fileList = ref<UploaderFileListItem[]>([])
const uploading = ref(false)
const showDatePicker = ref(false)
const showProvincePicker = ref(false)
const showCityPicker = ref(false)

// 日期选择器默认定位到当前日期
const now = dayjs()
const defaultDateIndex = [
  now.year() - 2000,
  now.month(),
  Math.min(now.date() - 1, 27)  // 最多 28 天
]

// 生成年份列（2000 年 ~ 今年）
const curYear = now.year()
const dateColumns = computed(() => {
  const years = Array.from({ length: curYear - 2000 + 1 }, (_, i) => ({
    text: `${2000 + i}年`,
    value: 2000 + i
  }))
  const months = Array.from({ length: 12 }, (_, i) => ({
    text: `${i + 1}月`,
    value: i + 1
  }))
  const days = Array.from({ length: 31 }, (_, i) => ({
    text: `${i + 1}日`,
    value: i + 1
  }))
  return [years, months, days]
})

// 省份列表（单列）
const provincePickerColumns = provinces.map(p => ({ text: p.name, value: p.name }))

// 城市列表（根据选中的省份动态生成）
const cityPickerColumns = computed(() => {
  if (!form.province) return []
  const p = provinces.find(p => p.name === form.province)
  return p ? p.cities.map(c => ({ text: c.name, value: c.name })) : []
})

const form = reactive({
  takenDate: '',
  country: '',
  province: '',
  city: '',
  locationName: '',
  description: ''
})

// 相册相关
const albums = ref<Album[]>([])
const selectedAlbumId = ref<number | null>(null)
const showAlbumPicker = ref(false)
const showCreateAlbum = ref(false)
const newAlbumName = ref('')

const selectedAlbumName = computed(() => {
  const album = albums.value.find(a => a.id === selectedAlbumId.value)
  return album ? album.name : ''
})

const albumPickerColumns = computed(() => {
  const options = albums.value.map(a => ({ text: a.name, value: a.id }))
  options.push({ text: '＋ 新建相册', value: 0 })
  return options
})

onMounted(() => {
  loadAlbums()
})

async function loadAlbums() {
  try {
    albums.value = await getAlbums()
  } catch {
    albums.value = []
  }
}

async function confirmCreateAlbum() {
  const name = newAlbumName.value.trim()
  if (!name) {
    showToast('请输入相册名称')
    return
  }
  try {
    const newAlbum = await createAlbum({ name })
    albums.value.push(newAlbum)
    selectedAlbumId.value = newAlbum.id
    showCreateAlbum.value = false
    showToast('相册创建成功')
  } catch {
    showToast('创建相册失败')
  }
}

function onOversize() {
  showToast('文件大小不能超过 10MB')
}

function onDateConfirm({ selectedValues, selectedOptions }: any) {
  console.log('日期确认 - selectedValues:', selectedValues)
  console.log('日期确认 - selectedOptions:', JSON.stringify(selectedOptions))

  // Vant 4 非级联 Picker: selectedValues 返回每列选中项的 value 值（现在是数字）
  if (!selectedValues || selectedValues.length < 3) {
    showToast('日期数据异常，请重试')
    showDatePicker.value = false
    return
  }

  const year = selectedValues[0]
  const month = String(selectedValues[1]).padStart(2, '0')
  const day = String(selectedValues[2]).padStart(2, '0')

  form.takenDate = `${year}-${month}-${day}`
  console.log('设置日期:', form.takenDate)
  showDatePicker.value = false
}

function onProvinceConfirm(e: any) {
  const raw = e?.selectedValues?.[0] ?? e?.[0] ?? ''
  form.province = typeof raw === 'string' ? raw : (raw?.value || raw?.text || '')
  form.city = ''
  showProvincePicker.value = false
}

function onCityConfirm(e: any) {
  const raw = e?.selectedValues?.[0] ?? e?.[0] ?? ''
  form.city = typeof raw === 'string' ? raw : (raw?.value || raw?.text || '')
  showCityPicker.value = false
}

function onAlbumConfirm(e: any) {
  console.log('相册确认事件:', e)
  const selectedValues = e?.selectedValues || e || []
  const value = Number(selectedValues[0])
  if (value === 0) {
    showAlbumPicker.value = false
    newAlbumName.value = ''
    showCreateAlbum.value = true
    return
  }
  selectedAlbumId.value = value
  showAlbumPicker.value = false
}

async function onSubmit() {
  if (fileList.value.length === 0) {
    showToast('请选择要上传的照片')
    return
  }

  if (!selectedAlbumId.value) {
    showToast('请选择相册')
    return
  }

  uploading.value = true

  try {
    const uploadedIds: number[] = []

    for (const file of fileList.value) {
      const formData = new FormData()
      formData.append('files', file.file!)
      formData.append('takenDate', form.takenDate)
      formData.append('country', form.country)
      if (form.province) formData.append('province', form.province.replace(/^(北京|上海|天津|重庆)市$/, '$1').replace(/^(香港|澳门)特别行政区$/, '$1').replace(/^(广西壮族|宁夏回族|新疆维吾尔|内蒙古|西藏)自治区$/, '$1').replace(/^(.*)省$/, '$1'))
      if (form.city) formData.append('city', form.city)
      if (form.locationName) formData.append('locationName', form.locationName)
      formData.append('description', form.description)
      if (selectedAlbumId.value && selectedAlbumId.value > 0) {
        formData.append('albumId', String(selectedAlbumId.value))
      }

      const uploaded = await uploadPhoto(formData, (progress) => {
        file.status = 'uploading'
        file.message = `${progress}%`
      })
      if (uploaded?.id) uploadedIds.push(uploaded.id)
    }

    // 上传完成后，将照片加入选中的相册
    if (selectedAlbumId.value && selectedAlbumId.value > 0 && uploadedIds.length > 0) {
      await addPhotosToAlbum(selectedAlbumId.value, uploadedIds)
    }

    showToast('上传成功')
    router.push('/home')
  } catch (error) {
    showToast('上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped lang="scss">
.upload-page {
  min-height: 100vh;
  background: $bg-color;
}

.upload-content {
  padding: 16px;

  .van-uploader {
    margin-bottom: 16px;
  }
}

.form-actions {
  margin: 24px 16px;
}

.create-album-popup {
  padding: 20px;

  .popup-header {
    text-align: center;
    margin-bottom: 16px;

    h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
    }
  }

  .popup-actions {
    margin-top: 16px;
  }
}
</style>
