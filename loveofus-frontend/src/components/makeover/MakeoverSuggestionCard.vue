<template>
  <div class="suggestion-card">
    <div class="card-head">
      <span class="head-icon">{{ icon }}</span>
      <span class="head-title">{{ title }}</span>
    </div>
    <div class="card-body">
      <slot>{{ content }}</slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  icon: string
  title: string
  content?: string
}>()
</script>

<style scoped lang="scss">
.suggestion-card {
  background: #fff;
  border-radius: $radius-md;
  padding: 14px 16px;
  box-shadow: $shadow-sm;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  .head-icon {
    font-size: 16px;
    line-height: 1;
  }
  .head-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }
}
.card-body {
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.65;
  word-break: break-word;
}
</style>