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
        <template v-else>
          <!-- 流式输出时光标闪烁 -->
          <span v-if="message.streaming" class="stream-content">{{ message.content }}<span class="caret">▍</span></span>
          <span v-else-if="message.error" class="error-text">{{ message.content || '发送失败' }}</span>
          <span v-else style="white-space: pre-wrap">{{ message.content }}</span>
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
</style>