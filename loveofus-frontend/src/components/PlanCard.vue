<template>
  <div class="plan-card" :class="{ cancelled: plan.status === 2, done: plan.status === 1 }">
    <!-- 头部：标题 + 时间 -->
    <div class="plan-header">
      <div class="plan-title">
        <van-icon v-if="plan.status === 1" name="passed" class="status-icon done" />
        <van-icon v-else-if="plan.status === 2" name="cross" class="status-icon cancelled" />
        <span>{{ plan.title }}</span>
      </div>
      <div class="plan-date">
        <van-icon name="calendar-o" />
        <span>{{ plan.date }}</span>
        <van-tag plain type="primary" class="slot-tag">{{ TIME_SLOT_LABEL[plan.timeSlot] }}</van-tag>
      </div>
    </div>

    <!-- 场景标签 -->
    <div class="plan-scenes">
      <span v-for="s in plan.scenes" :key="s" class="scene-tag">{{ s }}</span>
      <van-tag plain type="success" class="budget-tag">{{ BUDGET_LABEL[plan.budget] }}</van-tag>
    </div>

    <!-- 地点建议（无内容时不展示） -->
    <div v-if="plan.locationSuggestion" class="plan-row">
      <van-icon name="location-o" class="row-icon" />
      <div class="row-content">
        <div class="row-label">推荐地点</div>
        <div class="row-text">{{ plan.locationSuggestion }}</div>
      </div>
    </div>

    <!-- AI 推荐理由（无内容时不展示） -->
    <div v-if="plan.reason" class="reason-box">
      <div class="reason-title">
        <van-icon name="gem-o" />
        <span>AI 推荐理由</span>
      </div>
      <div class="reason-content">{{ plan.reason }}</div>
    </div>

    <!-- 小贴士 -->
    <div v-if="plan.tips.length > 0" class="tips">
      <div class="tips-title">
        <van-icon name="bulb-o" />
        <span>贴心提示</span>
      </div>
      <ul class="tips-list">
        <li v-for="(t, i) in plan.tips" :key="i">{{ t }}</li>
      </ul>
    </div>

    <!-- 地点 / 备注：AI 推荐方案由用户填写，已保存的计划只读展示 -->
    <div v-if="!showActions" class="plan-edit">
      <van-field
        v-model="plan.location"
        label="地点"
        maxlength="50"
        placeholder="填一个具体地点（可选）"
      />
      <van-field
        v-model="plan.remark"
        label="备注"
        type="textarea"
        rows="2"
        autosize
        maxlength="200"
        placeholder="补充备注（可选）"
      />
    </div>
    <template v-else>
      <div v-if="plan.location" class="plan-row">
        <van-icon name="location-o" class="row-icon" />
        <div class="row-content">
          <div class="row-label">地点</div>
          <div class="row-text">{{ plan.location }}</div>
        </div>
      </div>
      <div v-if="plan.remark" class="plan-row">
        <van-icon name="notes-o" class="row-icon" />
        <div class="row-content">
          <div class="row-label">备注</div>
          <div class="row-text">{{ plan.remark }}</div>
        </div>
      </div>
    </template>

    <!-- 操作区 -->
    <div v-if="showActions" class="plan-actions">
      <slot name="actions">
        <van-button
          v-if="plan.status === 0"
          size="small"
          plain
          round
          type="success"
          @click="$emit('done', plan.id)"
        >
          <van-icon name="passed" /> 完成
        </van-button>
        <van-button
          v-if="plan.status === 0"
          size="small"
          plain
          round
          @click="$emit('edit', plan)"
        >
          <van-icon name="edit" /> 修改
        </van-button>
        <van-button
          v-if="plan.status === 0"
          size="small"
          plain
          round
          @click="$emit('cancel', plan.id)"
        >
          取消
        </van-button>
        <van-button
          v-if="plan.status === 1"
          size="small"
          plain
          round
          type="primary"
          @click="$emit('detail', plan)"
        >
          <van-icon name="eye-o" /> 查看详情
        </van-button>
        <van-button
          size="small"
          plain
          round
          type="danger"
          @click="$emit('delete', plan.id)"
        >
          删除
        </van-button>
      </slot>
    </div>
    <div v-else class="plan-actions single">
      <van-button size="small" round type="primary" @click="$emit('save', plan)">
        <van-icon name="plus" /> 加入我的计划
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DatePlan } from '@/api/datePlan'

const TIME_SLOT_LABEL: Record<DatePlan['timeSlot'], string> = {
  morning: '上午',
  noon: '中午',
  afternoon: '下午',
  evening: '傍晚',
  night: '夜晚'
}

const BUDGET_LABEL: Record<DatePlan['budget'], string> = {
  free: '免费',
  low: '<100 元',
  mid: '100-300 元',
  high: '300-500 元',
  luxury: '>500 元'
}

defineProps<{
  plan: DatePlan
  /** 是否显示状态相关按钮（在我的计划列表中为 true） */
  showActions?: boolean
}>()

defineEmits<{
  save: [plan: DatePlan]
  done: [id: DatePlan['id']]
  edit: [plan: DatePlan]
  detail: [plan: DatePlan]
  cancel: [id: DatePlan['id']]
  delete: [id: DatePlan['id']]
}>()
</script>

<style scoped lang="scss">
.plan-card {
  margin: 12px 16px;
  padding: 16px;
  background: #fff;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  transition: opacity 0.2s;

  &.done {
    opacity: 0.85;
    .plan-title { text-decoration: line-through; color: $text-tertiary; }
  }

  &.cancelled {
    opacity: 0.6;
    .plan-title { text-decoration: line-through; }
  }

  .plan-header {
    margin-bottom: 10px;
  }

  .plan-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 6px;

    .status-icon.done { color: $primary-color; }
    .status-icon.cancelled { color: $text-tertiary; }
  }

  .plan-date {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: $text-secondary;

    .slot-tag {
      margin-left: 4px;
      transform: scale(0.85);
      transform-origin: left center;
    }
  }

  .plan-scenes {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin: 10px 0 14px;

    .scene-tag {
      display: inline-flex;
      align-items: center;
      padding: 3px 10px;
      border-radius: 12px;
      background: $primary-light-bg;
      color: $primary-color;
      font-size: 12px;
    }

    .budget-tag {
      transform: scale(0.85);
      transform-origin: left center;
    }
  }

  .plan-row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 10px;
    background: $bg-color;
    border-radius: $radius-md;
    margin-bottom: 12px;

    .row-icon {
      font-size: 18px;
      color: $primary-color;
      flex-shrink: 0;
      margin-top: 1px;
    }

    .row-content { flex: 1; min-width: 0; }

    .row-label {
      font-size: 12px;
      color: $text-tertiary;
      margin-bottom: 2px;
    }

    .row-text {
      font-size: 14px;
      color: $text-primary;
      line-height: 1.4;
    }
  }

  .reason-box {
    background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
    border-radius: $radius-md;
    padding: 12px;
    margin-bottom: 12px;

    .reason-title {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 13px;
      font-weight: 500;
      color: #b8821b;
      margin-bottom: 6px;
    }

    .reason-content {
      font-size: 13px;
      color: $text-primary;
      line-height: 1.6;
    }
  }

  .tips {
    .tips-title {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 13px;
      color: $text-secondary;
      margin-bottom: 6px;
    }

    .tips-list {
      margin: 0;
      padding: 0 0 0 18px;
      font-size: 13px;
      color: $text-secondary;
      line-height: 1.8;
    }
  }

  .plan-edit {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 12px;

    :deep(.van-field) {
      background: $bg-color;
      border-radius: $radius-md;
      padding: 4px 8px;
    }
  }

  .plan-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px dashed $border-color;

    &.single {
      justify-content: flex-end;
    }

    :deep(.van-button) {
      flex: 1;
      min-width: 0;
    }
  }
}
</style>
