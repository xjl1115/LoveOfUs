<template>
  <van-action-sheet
    v-model:show="show"
    :actions="actions"
    cancel-text="取消"
    close-on-click-action
    :description="description || '确认删除这条化妆建议？'"
    @select="onSelect"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: boolean
  description?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'delete'): void
}>()

/** v-model:show 双向绑定 */
const show = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const actions = computed(() => [
  { name: '删除记录', subname: '软删后无法在历史中找到', color: '#ee0a24' }
])

function onSelect(action: { name: string }) {
  if (action.name === '删除记录') emit('delete')
}
</script>