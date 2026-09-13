<template>
  <div class="chip-list">
    <span v-for="(item, idx) in items" :key="idx" class="chip">
      <span class="chip-dot" />
      {{ item }}
    </span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  items: string[]
}>()
</script>

<style scoped lang="scss">
.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  background: $primary-light-bg;
  color: $primary-color;
  font-size: 12px;
  border-radius: 14px;
  border: 1px solid rgba(255, 107, 107, 0.18);
  line-height: 1.2;
}
.chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: $primary-color;
}
</style>