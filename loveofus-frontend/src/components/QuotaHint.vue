<template>
  <div class="quota-hint" :class="{ 'quota-hint-low': isLow, 'quota-hint-empty': isEmpty }">
    <span class="quota-icon">
      <van-icon :name="isEmpty ? 'warning-o' : 'gem-o'" />
    </span>
    <span class="quota-text" v-if="!quota">加载中…</span>
    <span class="quota-text" v-else-if="quota.unlimited">不限次数</span>
    <span class="quota-text" v-else>
      剩余 <strong>{{ quota.remaining }}</strong> / {{ quota.limit }} 次
    </span>
    <span v-if="showReset" class="quota-reset">每周一刷新</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QuotaItem } from '@/api/vip'

const props = withDefaults(
  defineProps<{
    quota?: QuotaItem | null
    /** 显示"每周一刷新"提示，默认 true（AI 推荐是周维度） */
    showReset?: boolean
  }>(),
  { quota: null, showReset: true }
)

const isEmpty = computed(() => !props.quota?.unlimited && (props.quota?.remaining ?? 0) <= 0)
const isLow = computed(
  () =>
    !props.quota?.unlimited &&
    !isEmpty.value &&
    (props.quota?.remaining ?? 0) <= 1
)
</script>

<style scoped lang="scss">
.quota-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(231, 76, 60, 0.08);
  color: $primary-color;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;

  .quota-icon {
    display: inline-flex;
  }

  strong {
    color: $primary-color;
    font-weight: 700;
  }
}

.quota-hint-low {
  background: rgba(255, 152, 0, 0.12);
  color: #f57c00;

  .quota-icon,
  strong {
    color: #f57c00;
  }
}

.quota-hint-empty {
  background: rgba(158, 158, 158, 0.12);
  color: $text-tertiary;

  .quota-icon,
  strong {
    color: $text-tertiary;
  }
}

.quota-reset {
  margin-left: 4px;
  font-size: 11px;
  color: inherit;
  opacity: 0.7;
  border-left: 1px solid currentColor;
  padding-left: 6px;
}
</style>
