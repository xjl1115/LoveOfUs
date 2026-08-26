<template>
  <div class="ai-history-detail">
    <van-nav-bar
      :title="detail?.title || '会话详情'"
      left-arrow
      fixed
      placeholder
      @click-left="onBack"
    >
      <template #right>
        <span class="nav-icon" @click="openRename" title="重命名">
          <van-icon name="edit" size="18" />
        </span>
      </template>
    </van-nav-bar>

    <div class="loading-row" v-if="loading">
      <van-loading type="spinner" size="20" />
      <span>加载中…</span>
    </div>

    <div class="meta-row" v-else-if="detail">
      <span>{{ detail.messages?.length || 0 }} 条消息</span>
    </div>

    <div class="message-list" ref="scrollRef" v-if="!loading && detail">
      <MessageBubble
        v-for="(msg, idx) in detail.messages"
        :key="msg.id ?? idx"
        :message="toUiMessage(msg)"
        :show-avatar="shouldShowAvatar(idx)"
        :show-time="shouldShowTime(idx)"
      />
    </div>

    <!-- 重命名对话框 -->
    <van-dialog
      v-model:show="renameDialog.show"
      title="重命名会话"
      show-cancel-button
      confirm-button-text="保存"
      cancel-button-text="取消"
      @confirm="confirmRename"
      @cancel="renameDialog.show = false"
    >
      <div class="rename-input-wrap">
        <van-field
          v-model="renameDialog.value"
          placeholder="请输入新标题"
          maxlength="100"
          show-word-limit
          :border="false"
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import MessageBubble from '@/components/MessageBubble.vue'
import {
  getAiSessionDetail,
  renameAiSession,
  type AiSessionDetail,
  type AiSessionMessage
} from '@/api/aiChat'

const route = useRoute()
const router = useRouter()

const sessionId = computed(() => String(route.params.sessionId || ''))
const loading = ref(false)
const detail = ref<AiSessionDetail | null>(null)
const scrollRef = ref<HTMLElement | null>(null)

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/ai-history')
}

function toUiMessage(msg: AiSessionMessage) {
  // 后端 createdAt 是 ISO 字符串（'yyyy-MM-ddTHH:mm:ss'），前端期望毫秒
  const createdMs = msg.createdAt ? new Date(msg.createdAt.replace(' ', 'T')).getTime() : Date.now()
  return {
    id: String(msg.id ?? Math.random().toString(36).slice(2)),
    role: msg.role,
    content: msg.content,
    toolName: msg.toolName,
    createdAt: Number.isFinite(createdMs) ? createdMs : Date.now()
  } as any
}

function shouldShowAvatar(idx: number): boolean {
  const list = detail.value?.messages || []
  const m = list[idx]
  if (!m || m.role === 'system' || m.role === 'tool') return false
  if (idx === 0) return true
  return list[idx - 1].role !== m.role
}

function shouldShowTime(idx: number): boolean {
  const list = detail.value?.messages || []
  const m = list[idx]
  if (!m) return false
  if (idx === 0) return true
  const prev = list[idx - 1]
  return new Date(m.createdAt).getTime() - new Date(prev.createdAt).getTime() > 5 * 60 * 1000
}

async function refresh() {
  if (!sessionId.value) return
  loading.value = true
  try {
    detail.value = await getAiSessionDetail(sessionId.value)
    nextTick(scrollToBottom)
  } catch (e: any) {
    showToast(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function scrollToBottom() {
  if (!scrollRef.value) return
  scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}

// 重命名输入对话框的状态
const renameDialog = ref<{ show: boolean; value: string }>({ show: false, value: '' })

function openRename() {
  if (!detail.value) return
  renameDialog.value = { show: true, value: detail.value.title || '' }
}

async function confirmRename() {
  if (!detail.value) return
  const value = renameDialog.value.value.trim()
  if (!value) return
  try {
    const updated = await renameAiSession(detail.value.sessionId, value)
    detail.value.title = updated.title
    showToast('已重命名')
  } catch (e: any) {
    showToast(e?.message || '重命名失败')
  }
}

onMounted(refresh)
</script>

<style scoped lang="scss">
.ai-history-detail {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 16px;
}
.loading-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 0;
  color: #969799;
  font-size: 14px;
}
.meta-row {
  padding: 8px 16px;
  font-size: 12px;
  color: #969799;
}
.message-list {
  padding: 8px 12px;
}
.nav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  cursor: pointer;
  color: inherit;
}
.rename-input-wrap {
  padding: 8px 16px 16px;
}
</style>