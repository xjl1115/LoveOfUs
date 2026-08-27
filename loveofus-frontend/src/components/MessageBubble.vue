<template>
  <div :class="['bubble-row', `role-${message.role}`]">
    <!-- 头像 -->
    <div v-if="showAvatar" class="avatar" :class="avatarClass">
      <van-icon :name="avatarIcon" size="20" color="#fff" />
    </div>

    <!-- 消息主体 -->
    <div class="bubble-stack">
      <!-- 工具调用徽章 -->
      <div v-if="message.role === 'tool' && message.toolName" class="tool-badge">
        <van-icon name="tool-o" size="12" />
        <span>{{ message.toolName }}</span>
      </div>

      <!-- 消息文本 -->
      <div :class="['bubble', `bubble-${message.role}`]">
        <template v-if="message.role === 'tool'">
          <span class="tool-prefix">🔧</span>
          <span>{{ message.content }}</span>
        </template>
        <template v-else-if="message.role === 'system' && message.export">
          <!-- 导出完成卡片 -->
          <div class="export-card" :class="{ 'is-failed': message.export.status === 'failed' }">
            <div class="export-card-icon">
              <van-icon :name="message.export.status === 'failed' ? 'warning-o' : 'down-circle'" size="22" />
            </div>
            <div class="export-card-body">
              <div class="export-card-title">
                {{ message.export.status === 'failed' ? '导出失败' : '导出完成' }}
              </div>
              <div class="export-card-meta">
                <template v-if="message.export.status === 'failed'">
                  {{ message.export.error || '导出过程中发生错误' }}
                </template>
                <template v-else>
                  <span>{{ (message.export.format || 'zip').toUpperCase() }}</span>
                  <span v-if="message.export.photoCount != null"> · {{ message.export.photoCount }} 张照片</span>
                  <span v-if="message.export.fileSize"> · {{ formatSize(message.export.fileSize) }}</span>
                </template>
              </div>
              <a
                v-if="message.export.status !== 'failed'"
                class="export-card-btn"
                :href="message.export.downloadUrl || '#'"
                :download="message.export.fileName"
                target="_self"
              >
                <van-icon name="down" size="14" />
                <span>下载到本机</span>
              </a>
            </div>
          </div>
        </template>
        <template v-else>
          <!-- 流式输出时光标闪烁 -->
          <span v-if="message.streaming" class="stream-content">{{ message.content }}<span class="caret">▍</span></span>
          <span v-else-if="message.error" class="error-text">{{ message.content || '发送失败' }}</span>
          <span v-else style="white-space: pre-wrap">{{ message.content }}</span>
          <!-- 图片网格：工具返回的照片以缩略图展示 -->
          <div v-if="message.images && message.images.length" class="image-grid">
            <a
              v-for="(img, i) in message.images"
              :key="i"
              class="image-grid-item"
              :href="img.imageUrl"
              target="_blank"
              rel="noopener"
            >
              <img
                :src="img.imageUrl"
                :alt="img.description || img.locationName || '照片'"
                loading="lazy"
                @error="onImgError($event)"
              />
            </a>
          </div>
        </template>
      </div>

      <!-- 时间 -->
      <div v-if="showTime" class="time">{{ formatTime(message.createdAt) }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '@/api/aiChat'

const props = defineProps<{
  message: ChatMessage
  showAvatar?: boolean
  showTime?: boolean
}>()

/** 头像图标：系统/工具/AI/用户 */
const avatarIcon = computed(() => {
  switch (props.message.role) {
    case 'user':
      return 'user-o'
    case 'tool':
      return 'tool-o'
    case 'system':
      return 'info-o'
    default:
      return 'flower-o'
  }
})

const avatarClass = computed(() => `avatar-${props.message.role}`)

function formatTime(ts: number): string {
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

/** 字节 → 人读格式（KB / MB） */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 图片加载失败：替换为占位图（保留点击打开原图） */
function onImgError(ev: Event) {
  const el = ev.target as HTMLImageElement
  el.src =
    'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120" viewBox="0 0 120 120"><rect width="120" height="120" fill="%23f2f3f5"/><text x="50%" y="50%" font-size="14" text-anchor="middle" dominant-baseline="middle" fill="%23999">图片加载失败</text></svg>'
}
</script>

<style scoped lang="scss">
.bubble-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  align-items: flex-start;

  &.role-user {
    flex-direction: row-reverse;

    .bubble-stack {
      align-items: flex-end;
    }
  }

  &.role-tool,
  &.role-system {
    justify-content: center;

    .avatar {
      display: none;
    }

    .bubble-stack {
      align-items: center;
      max-width: 80%;
    }
  }
}

.avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;

  &.avatar-ai {
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  }
  &.avatar-user {
    background: linear-gradient(135deg, #5b8def 0%, #8aaefb 100%);
  }
  &.avatar-tool {
    background: #909399;
  }
}

.bubble-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 75%;
}

.tool-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 10px;
  background: $primary-light-bg;
  color: $primary-color;
  font-size: 11px;
  align-self: flex-start;
}

.bubble {
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  position: relative;

  &.bubble-ai {
    background: #fff;
    color: $text-primary;
    border-top-left-radius: 4px;
    box-shadow: $shadow-sm;
  }

  &.bubble-user {
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    color: #fff;
    border-top-right-radius: 4px;
  }

  &.bubble-tool {
    background: #f5f5f5;
    color: $text-secondary;
    font-size: 13px;
    border-radius: 8px;
  }

  &.bubble-system {
    background: transparent;
    color: $text-tertiary;
    font-size: 12px;
    text-align: center;
  }
}

.tool-prefix {
  margin-right: 6px;
}

.stream-content {
  white-space: pre-wrap;
}

.caret {
  display: inline-block;
  margin-left: 2px;
  color: $primary-color;
  animation: caret-blink 1s steps(1) infinite;
}

.error-text {
  color: #ee0a24;
}

.time {
  font-size: 11px;
  color: $text-tertiary;
  padding: 0 4px;
}

@keyframes caret-blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}

/* 图片网格：工具返回的照片缩略图 */
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 6px;
  margin-top: 8px;
  max-width: 100%;
}

.image-grid-item {
  display: block;
  position: relative;
  width: 100%;
  padding-bottom: 100%; /* 1:1 方形缩略图 */
  border-radius: 8px;
  overflow: hidden;
  background: #f2f3f5;

  img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.18s;
  }

  &:hover img {
    transform: scale(1.04);
  }

  &:active img {
    transform: scale(0.98);
  }
}

/* 导出完成卡片（system + export） */
.export-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: linear-gradient(135deg, #fff5f8 0%, #f3eaff 100%);
  border: 1px solid rgba(232, 113, 154, 0.18);
  text-align: left;
  min-width: 240px;
  max-width: 320px;

  &.is-failed {
    background: linear-gradient(135deg, #fff1f0 0%, #ffece6 100%);
    border-color: rgba(238, 10, 36, 0.18);
  }
}

.export-card-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: $primary-color;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;

  .is-failed & {
    background: #ee0a24;
  }
}

.export-card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.export-card-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;

  .is-failed & {
    color: #ee0a24;
  }
}

.export-card-meta {
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
  word-break: break-word;
}

.export-card-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  padding: 5px 12px;
  border-radius: 999px;
  background: $primary-color;
  color: #fff;
  font-size: 13px;
  text-decoration: none;
  margin-top: 2px;
  transition: opacity 0.18s;

  &:hover {
    opacity: 0.88;
  }

  &:active {
    opacity: 0.7;
  }
}
</style>