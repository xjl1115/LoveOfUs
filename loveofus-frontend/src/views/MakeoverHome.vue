<template>
  <div class="makeover-home">
    <van-nav-bar title="AI 化妆建议" left-arrow fixed placeholder @click-left="onBack">
      <template #right>
        <van-icon
          name="clock-o"
          size="20"
          color="#FF6B6B"
          aria-label="历史记录"
          @click="goHistory"
        />
      </template>
    </van-nav-bar>

    <div class="home-content">
      <!-- 1. 上传区 -->
      <section class="card upload-card">
        <van-uploader
          v-model="fileList"
          :max-count="1"
          :max-size="MAX_IMAGE_SIZE"
          :preview-image="true"
          :before-read="beforeRead"
          :after-read="afterRead"
          accept="image/jpeg,image/png,image/webp"
          @oversize="onOversize"
          @delete="onDeleteFile"
        >
          <template #default>
            <div class="upload-tip">
              <van-icon name="photograph" size="32" color="#FF6B6B" />
              <p>点击上传自拍照</p>
              <p class="sub">支持 jpg/png/webp，≤ 8MB</p>
            </div>
          </template>
        </van-uploader>
        <div v-if="compressing" class="compress-hint">
          <van-loading type="spinner" size="14" />
          <span>正在压缩图片…</span>
        </div>
      </section>

      <!-- 2. 场景选择 -->
      <section class="card scene-section">
        <h3 class="section-title">选择场景</h3>
        <MakeoverScenePicker v-model="form.scene" />
      </section>

      <!-- 3. 额外描述 -->
      <section class="card desc-section">
        <van-field
          v-model="form.description"
          label="补充描述"
          type="textarea"
          rows="2"
          autosize
          maxlength="100"
          show-word-limit
          placeholder="如：希望显白一点 / 避开浓香"
        />
      </section>

      <!-- 4. 提交 -->
      <div class="submit-wrap">
        <van-button
          type="primary"
          block
          round
          :loading="submitting"
          :disabled="!canSubmit || submitting || quotaExhausted"
          @click="onSubmit"
        >
          生成建议
        </van-button>
        <p v-if="quota" class="quota-line" :class="{ 'is-empty': quotaExhausted }">
          {{ quotaText }}
        </p>
        <p v-if="quotaExhausted" class="upgrade-line" @click="goVip">
          本月次数已用完，升级 VIP 获得更多次数
          <van-icon name="arrow" />
        </p>
        <p class="submit-hint">通常需要 10-30 秒，分析中请勿关闭页面</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, type UploaderFileListItem } from 'vant'
import MakeoverScenePicker from '@/components/makeover/MakeoverScenePicker.vue'
import { MAX_IMAGE_SIZE, ALLOWED_IMAGE_MIME } from '@/constants/makeover'
import { createMakeover, getMakeoverQuota, type SceneCode, type MakeoverQuota } from '@/api/makeover'
import { useMakeoverStore } from '@/stores/makeover'

const router = useRouter()
const makeoverStore = useMakeoverStore()

const fileList = ref<UploaderFileListItem[]>([])
const form = reactive<{ scene: SceneCode; description: string }>({
  scene: 'date',
  description: ''
})
const submitting = ref(false)
const compressing = ref(false)

/** 本月额度：免费次数 + VIP 档位额外次数 */
const quota = ref<MakeoverQuota | null>(null)
const quotaExhausted = computed(
  () => !!quota.value && !quota.value.unlimited && (quota.value.remaining ?? 0) <= 0
)
const quotaText = computed(() => {
  const q = quota.value
  if (!q) return ''
  return q.unlimited ? '本月剩余：不限次' : `本月剩余：${q.remaining ?? 0}/${q.total ?? 0} 次`
})

const canSubmit = computed(() => fileList.value.length === 1 && !!form.scene)

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

function goHistory() {
  router.push('/makeover/history')
}

/** 查询本月额度；失败时按可用处理，不阻塞生成 */
async function loadQuota() {
  try {
    quota.value = await getMakeoverQuota()
  } catch {
    quota.value = null
  }
}

/** 额度用尽后引导升级 */
function goVip() {
  router.push('/vip')
}

onMounted(loadQuota)

/** van-uploader oversize 回调（max-size 超出） */
function onOversize() {
  showToast('图片不超过 8MB')
}

/** 删除已选图片 */
function onDeleteFile() {
  fileList.value = []
}

/** 上传前校验：MIME 与文件大小 */
function beforeRead(file: File | File[]): boolean {
  const files = Array.isArray(file) ? file : [file]
  for (const f of files) {
    if (!ALLOWED_IMAGE_MIME.includes(f.type)) {
      showToast('仅支持 jpg / png / webp')
      return false
    }
    if (f.size > MAX_IMAGE_SIZE) {
      showToast('图片不超过 8MB')
      return false
    }
  }
  return true
}

/**
 * 图片上传前的客户端压缩（docs/08 §9）
 * - 目标：最长边 1280px（节省 Qwen-VL token + 加快上传）
 * - 失败回退：若浏览器不支持 createImageBitmap/canvas，返回原文件
 */
async function compressImage(file: File, maxSide = 1280): Promise<File> {
  // 不需要压缩：图片本身已经够小（最长边估算）
  try {
    const bitmap = await (window as any).createImageBitmap?.(file)
    if (!bitmap) return file
    const { width, height } = bitmap
    const longest = Math.max(width, height)
    if (longest <= maxSide) {
      bitmap.close?.()
      return file
    }
    const ratio = maxSide / longest
    const targetW = Math.round(width * ratio)
    const targetH = Math.round(height * ratio)

    const canvas = document.createElement('canvas')
    canvas.width = targetW
    canvas.height = targetH
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      bitmap.close?.()
      return file
    }
    ctx.drawImage(bitmap, 0, 0, targetW, targetH)
    bitmap.close?.()

    const blob: Blob | null = await new Promise((resolve) =>
      canvas.toBlob((b) => resolve(b), 'image/jpeg', 0.85)
    )
    if (!blob) return file
    // 同名 + .jpg 扩展名
    const newName = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([blob], newName, { type: 'image/jpeg' })
  } catch {
    return file
  }
}

/** van-uploader 读取完毕后立即执行压缩（in-place 替换 fileList 中的原始文件） */
async function afterRead(file: UploaderFileListItem | UploaderFileListItem[]) {
  // 单文件场景才需要压缩
  const item = Array.isArray(file) ? file[0] : file
  if (!item || !item.file) return
  if (item.file.size <= MAX_IMAGE_SIZE / 2) {
    // 已经足够小，无需压缩（避免无意义的 canvas 处理）
    return
  }
  compressing.value = true
  try {
    const compressed = await compressImage(item.file)
    item.file = compressed
  } catch {
    // 压缩失败保留原图
  } finally {
    compressing.value = false
  }
}

async function onSubmit() {
  const item = fileList.value[0]
  if (!item || !item.file) {
    showToast('请先上传一张自拍照')
    return
  }
  submitting.value = true
  try {
    const vo = await createMakeover(
      item.file,
      form.scene,
      form.description,
      () => {
        // 上传进度回调：当前 UI 已在 van-button loading 上，无需另开进度条
      }
    )
    makeoverStore.setRecent(vo.recordId)
    showToast('已提交，正在分析中…')
    router.replace({ name: 'MakeoverProgress', params: { recordId: String(vo.recordId) } })
  } catch {
    // 拦截器已显示后端 message，无需再 toast
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.makeover-home {
  min-height: 100vh;
  background: $bg-color;
}
.makeover-home :deep(.van-nav-bar__right) {
  padding: 0 12px;
  .van-icon {
    cursor: pointer;
  }
}
.home-content {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card {
  background: #fff;
  border-radius: $radius-md;
  padding: 14px;
}
.upload-card {
  :deep(.van-uploader) {
    width: 100%;
  }
  :deep(.van-uploader__wrapper) {
    width: 100%;
  }
  :deep(.van-uploader__upload) {
    width: 100%;
    margin: 0;
    background: $bg-color;
    border-radius: $radius-md;
  }
  :deep(.van-uploader__preview-image) {
    width: 100%;
    height: 200px;
    object-fit: cover;
  }
}
.upload-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 24px 0;
  color: $text-secondary;
  p {
    margin: 0;
    font-size: 14px;
  }
  .sub {
    font-size: 12px;
    color: $text-tertiary;
  }
}
.compress-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: $text-tertiary;
  font-size: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 10px;
}
.desc-section {
  padding: 6px 14px;
}
.submit-wrap {
  margin-top: 8px;
}
.submit-hint {
  margin: 8px 0 0;
  text-align: center;
  font-size: 12px;
  color: $text-tertiary;
}

.quota-line {
  margin: 10px 0 0;
  text-align: center;
  font-size: 13px;
  color: $text-secondary;
  &.is-empty {
    color: #ff6b6b;
  }
}

.upgrade-line {
  margin: 6px 0 0;
  text-align: center;
  font-size: 13px;
  color: #ff6b6b;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
}
</style>
