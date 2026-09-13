<template>
  <div class="empty-state">
    <div class="empty-icon">💭</div>
    <div class="empty-text">{{ text }}</div>
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    text?: string
  }>(),
  { text: '暂无数据' }
)
</script>

<style scoped lang="scss">
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  color: $text-tertiary;
  font-size: 13px;

  .empty-icon {
    font-size: 40px;
    margin-bottom: 8px;
    opacity: 0.6;
  }
}
</style>