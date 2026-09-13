<template>
  <div class="stage-steps" role="status" aria-live="polite">
    <van-steps
      :active="activeIndex"
      active-color="#FF6B6B"
      inactive-color="#ccc"
      direction="vertical"
    >
      <van-step>
        <h4>提交成功</h4>
        <p>记录已创建，排队等待分析</p>
      </van-step>
      <van-step>
        <h4>AI 文本分析</h4>
        <p>读取脸部特征，判断适合的妆造风格</p>
      </van-step>
      <van-step>
        <h4>改造出图</h4>
        <p>基于分析结论生成改造效果参考图</p>
      </van-step>
      <van-step>
        <h4>完成</h4>
        <p>生成完整妆造建议</p>
      </van-step>
    </van-steps>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 进度阶段：
 *   pending  -> 仅"提交成功"高亮
 *   analyze  -> 0~2
 *   edit     -> 0~3
 *   done     -> 全部完成
 *   failed   -> 标记为失败态
 */
const props = defineProps<{
  stage: 'pending' | 'analyze' | 'edit' | 'done' | 'failed'
}>()

const activeIndex = computed(() => {
  switch (props.stage) {
    case 'pending':
      return 0
    case 'analyze':
      return 1
    case 'edit':
      return 2
    case 'done':
      return 3
    default:
      return 1
  }
})
</script>

<style scoped lang="scss">
.stage-steps {
  padding: 12px 4px 0;

  :deep(.van-step__title) {
    font-size: 14px;
    color: $text-primary;
    font-weight: 500;
    margin-bottom: 2px;
  }
  :deep(.van-step__desc),
  :deep(.van-step p) {
    font-size: 12px;
    color: $text-tertiary;
    line-height: 1.5;
  }
}
</style>