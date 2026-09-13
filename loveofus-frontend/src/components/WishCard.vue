<template>
  <div class="wish-card" :class="{ achieved: wish.status === 1 }">
    <div class="wish-head">
      <div class="wish-icon">{{ wish.icon }}</div>

      <div class="wish-main">
        <div class="wish-title-row">
          <span class="wish-title">{{ wish.title }}</span>
          <van-tag v-if="wish.needBothConfirm" plain type="primary" class="confirm-tag">双方确认</van-tag>
          <van-tag v-if="wish.deadline" plain class="deadline-tag">{{ wish.deadline }}</van-tag>
        </div>

        <div class="progress-row">
          <div class="progress-bar">
            <div
              class="progress-fill"
              :style="{ width: progressPercent + '%', background: progressColor }"
            ></div>
          </div>
          <div class="progress-text">
            <span :class="{ full: wish.status === 1 }">
              {{ wish.currentValue }}/{{ wish.targetValue }} {{ wish.unit }}
            </span>
          </div>
        </div>

        <div v-if="wish.status === 1 && wish.achievedNote" class="achieve-note">
          <van-icon name="notes-o" />
          <span>{{ wish.achievedNote }}</span>
        </div>
      </div>
    </div>

    <!-- 操作区：左侧编辑/删除，右侧进度调整与完成 -->
    <div class="wish-actions">
      <div class="action-links">
        <span class="link" @click="$emit('edit', wish)">编辑</span>
        <span class="divider">|</span>
        <span class="link danger" @click="$emit('delete', wish.id)">删除</span>
      </div>

      <div class="action-buttons">
        <template v-if="wish.status !== 1">
          <div class="stepper">
            <van-button
              class="step-btn"
              size="mini"
              plain
              round
              icon="minus"
              :disabled="wish.currentValue <= 0"
              @click="$emit('minus', wish.id)"
            />
            <van-button
              class="step-btn"
              size="mini"
              plain
              round
              icon="plus"
              :disabled="wish.currentValue >= wish.targetValue"
              @click="$emit('plus', wish.id)"
            />
          </div>
          <van-button
            class="achieve-btn"
            size="mini"
            round
            type="success"
            @click="$emit('achieve', wish)"
          >
            完成
          </van-button>
        </template>
        <template v-else>
          <span class="achieved-badge">✓ 已达成</span>
          <van-button
            class="card-btn"
            size="mini"
            round
            plain
            type="primary"
            icon="photo-o"
            @click="$emit('card', wish)"
          >
            记录卡片
          </van-button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Wish } from '@/api/wishlist'

const props = defineProps<{
  wish: Wish
}>()

defineEmits<{
  plus: [id: Wish['id']]
  minus: [id: Wish['id']]
  achieve: [wish: Wish]
  card: [wish: Wish]
  edit: [wish: Wish]
  delete: [id: Wish['id']]
}>()

const progressPercent = computed(() => {
  if (props.wish.targetValue <= 0) return 0
  return Math.min(100, Math.round((props.wish.currentValue / props.wish.targetValue) * 100))
})

const progressColor = computed(() => {
  if (props.wish.status === 1) return 'linear-gradient(90deg, #07c160 0%, #00d966 100%)'
  // 注意：#ff6b6b 即主题色 $primary-color。SCSS 变量在 script 的字符串里不会被编译，写进去会生成非法 CSS
  if (progressPercent.value >= 70) return 'linear-gradient(90deg, #ff9a3c 0%, #ff6b6b 100%)'
  return 'linear-gradient(90deg, #ffb1b1 0%, #ff6b6b 100%)'
})
</script>

<style scoped lang="scss">
.wish-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  margin-bottom: 12px;
  background: #fff;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;

  &.achieved {
    background: linear-gradient(135deg, #f6fff6 0%, #e7f9e7 100%);

    .wish-title {
      text-decoration: line-through;
      color: $text-tertiary;
    }
  }

  .wish-head {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .wish-icon {
    width: 48px;
    height: 48px;
    border-radius: 24px;
    background: $primary-light-bg;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 26px;
    flex-shrink: 0;
  }

  .wish-main {
    flex: 1;
    min-width: 0;

    .wish-title-row {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 8px;
      flex-wrap: wrap;
    }

    .wish-title {
      font-size: 15px;
      font-weight: 500;
      color: $text-primary;
    }

    .confirm-tag,
    .deadline-tag {
      transform: scale(0.8);
      transform-origin: left center;
    }

    .deadline-tag {
      background: #fff7e6;
      color: #b8821b;
    }

    .progress-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .progress-bar {
      flex: 1;
      height: 6px;
      background: $bg-color;
      border-radius: 3px;
      overflow: hidden;
    }

    .progress-fill {
      height: 100%;
      transition: width 0.4s ease;
    }

    .progress-text {
      font-size: 12px;
      color: $text-tertiary;
      flex-shrink: 0;

      .full {
        color: $primary-color;
        font-weight: 600;
      }
    }

    .achieve-note {
      margin-top: 8px;
      padding: 6px 8px;
      background: rgba(7, 193, 96, 0.08);
      border-radius: $radius-sm;
      font-size: 12px;
      color: $text-secondary;
      display: flex;
      align-items: flex-start;
      gap: 4px;
      line-height: 1.4;

      .van-icon { font-size: 12px; flex-shrink: 0; margin-top: 1px; }
    }
  }

  .wish-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding-top: 10px;
    border-top: 1px solid $border-color;

    .achieved-badge {
      display: inline-flex;
      align-items: center;
      padding: 4px 10px;
      background: #e7f9e7;
      color: #07c160;
      font-size: 12px;
      border-radius: 12px;
      font-weight: 500;
    }
  }

  .action-links {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;

    .link {
      color: $text-tertiary;
      cursor: pointer;

      &:active {
        opacity: 0.7;
      }

      &.danger:active {
        color: #ee0a24;
      }
    }

    .divider {
      color: $border-color;
    }
  }

  .action-buttons {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .stepper {
    display: flex;
    align-items: center;
    gap: 6px;

    :deep(.van-button--mini) {
      width: 26px;
      height: 26px;
      padding: 0;
      font-size: 12px;
    }
  }

  .achieve-btn {
    height: 26px;
    min-width: 0;
    padding: 0 14px;
    font-size: 12px;
  }

  .card-btn {
    height: 26px;
    min-width: 0;
    padding: 0 12px;
    font-size: 12px;
  }
}
</style>
