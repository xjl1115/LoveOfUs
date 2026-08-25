<template>
  <div class="quick-suggestions">
    <div class="title">试试这样问我 👇</div>
    <div class="card-list">
      <div
        v-for="(item, idx) in questions"
        :key="idx"
        class="card"
        @click="onPick(item)"
      >
        <span class="icon">{{ item.icon }}</span>
        <div class="text-wrap">
          <div class="text">{{ item.text }}</div>
          <div class="tag">{{ item.tag }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
export interface QuickQuestion {
  icon: string
  text: string
  tag: string
}

defineProps<{
  questions: QuickQuestion[]
}>()

const emit = defineEmits<{
  (e: 'pick', item: QuickQuestion): void
}>()

function onPick(item: QuickQuestion) {
  emit('pick', item)
}
</script>

<style scoped lang="scss">
.quick-suggestions {
  padding: 12px 16px;

  .title {
    font-size: 13px;
    color: $text-tertiary;
    margin-bottom: 10px;
    text-align: center;
  }
}

.card-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.card {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: transform 0.15s;

  &:active {
    transform: scale(0.97);
  }

  .icon {
    font-size: 22px;
    flex-shrink: 0;
  }

  .text-wrap {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .text {
    font-size: 13px;
    color: $text-primary;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .tag {
    font-size: 11px;
    color: $primary-color;
    background: $primary-light-bg;
    padding: 1px 6px;
    border-radius: 8px;
    align-self: flex-start;
  }
}
</style>