<template>
  <div class="ai-history-page">
    <van-nav-bar title="AI 历史会话" left-arrow fixed placeholder @click-left="onBack">
      <template #right>
        <van-icon v-if="!loading && sessions.length > 0" name="plus" size="18" @click="onNewSession" />
      </template>
    </van-nav-bar>

    <div class="loading-row" v-if="loading">
      <van-loading type="spinner" size="20" />
      <span>加载中…</span>
    </div>

    <van-empty v-else-if="!loading && sessions.length === 0" description="暂无历史会话">
      <template #default>
        <van-button round type="primary" size="small" @click="onNewSession">开始第一次对话</van-button>
      </template>
    </van-empty>

    <van-cell-group v-else inset>
      <van-swipe-cell v-for="s in sessions" :key="s.sessionId">
        <van-cell
          clickable
          :title="s.title || '未命名会话'"
          :label="formatLabel(s)"
          is-link
          @click="onOpen(s)"
        >
          <template #icon>
            <van-icon name="chat-o" class-prefix="ai" size="20" class="cell-icon" />
          </template>
        </van-cell>
        <template #right>
          <van-button square type="primary" text="重命名" class="swipe-btn" @click="openRename(s)" />
          <van-button square type="danger" text="删除" class="swipe-btn" @click="onDelete(s)" />
        </template>
      </van-swipe-cell>
    </van-cell-group>

    <!-- 重命名对话框 -->
    <van-dialog
      v-model:show="renameDialog.show"
      title="重命名会话"
      show-cancel-button
      confirm-button-text="保存"
      cancel-button-text="取消"
      @confirm="confirmRename"
      @cancel="closeRename"
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import {
  listAiSessions,
  deleteAiSession,
  renameAiSession,
  type AiSessionSummary,
  getSessionId as getOrCreateSessionId
} from '@/api/aiChat'

const router = useRouter()
const loading = ref(false)
const sessions = ref<AiSessionSummary[]>([])

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

function onNewSession() {
  // 创建新会话 id 后跳转到 AIChat
  getOrCreateSessionId()
  router.push('/ai-chat')
}

async function refresh() {
  loading.value = true
  try {
    sessions.value = (await listAiSessions()) || []
  } catch (e: any) {
    showToast(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onOpen(s: AiSessionSummary) {
  // 切到该会话：把 sessionId 写回 localStorage，再进入 AIChat
  localStorage.setItem('ai_chat_session_id', s.sessionId)
  // 标记"历史会话已选中"：让 AIChat onMounted 时直接拉详情而不是读 localStorage 历史
  localStorage.setItem('ai_chat_load_from_history', '1')
  router.push('/ai-chat')
}

function formatLabel(s: AiSessionSummary): string {
  const last = s.lastActiveAt ? s.lastActiveAt.replace('T', ' ').substring(0, 16) : ''
  return `${s.messageCount || 0} 条消息 · ${last}`
}

// 重命名输入对话框的状态
const renameDialog = ref<{ show: boolean; session: AiSessionSummary | null; value: string }>({
  show: false,
  session: null,
  value: ''
})

function openRename(s: AiSessionSummary) {
  renameDialog.value = { show: true, session: s, value: s.title || '' }
}

function closeRename() {
  renameDialog.value.show = false
}

async function confirmRename() {
  const s = renameDialog.value.session
  const value = renameDialog.value.value.trim()
  renameDialog.value.show = false
  if (!s || !value) return
  try {
    const updated = await renameAiSession(s.sessionId, value)
    showToast('已重命名')
    const idx = sessions.value.findIndex(v => v.sessionId === s.sessionId)
    if (idx >= 0) sessions.value[idx] = updated
  } catch (e: any) {
    showToast(e?.message || '重命名失败')
  }
}

async function onDelete(s: AiSessionSummary) {
  try {
    await showConfirmDialog({
      title: '删除会话',
      message: `确定删除"${s.title || '未命名'}"吗？该会话下的所有消息将被清除。`
    })
    await deleteAiSession(s.sessionId)
    sessions.value = sessions.value.filter(v => v.sessionId !== s.sessionId)
    showToast('已删除')
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    showToast(e?.message || '删除失败')
  }
}

onMounted(refresh)
</script>

<style scoped lang="scss">
.ai-history-page {
  min-height: 100vh;
  background: #f7f8fa;
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
.cell-icon {
  margin-right: 8px;
  color: #6c8cff;
}
.swipe-btn {
  height: 100%;
}
.rename-input-wrap {
  padding: 8px 16px 16px;
}
</style>