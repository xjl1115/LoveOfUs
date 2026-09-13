<template>
  <!-- 心愿记录卡片：纯 HTML/CSS 排版，导出图片时由 html2canvas 截图 -->
  <div class="wish-poster">
    <div class="poster-label">🎉 心愿达成</div>
    <div class="poster-date">{{ achievedDate }} 达成</div>

    <div class="poster-icon">{{ wish.icon }}</div>
    <div class="poster-title">{{ wish.title }}</div>
    <div class="poster-meta">{{ metaText }}</div>

    <div class="poster-rule"></div>

    <!-- 纪念照片：用 url 铺满固定区域 -->
    <div
      v-if="photoUrl"
      class="poster-photo"
      :style="{ backgroundImage: `url(${photoUrl})` }"
    ></div>

    <div v-if="noteText" class="poster-note">{{ noteText }}</div>

    <div class="poster-brand">LoveOfUs</div>
    <div class="poster-slogan">记录我们的每一次心动</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Wish } from '@/api/wishlist'
import { formatDate } from '@/utils/date'

const props = defineProps<{
  wish: Wish
  /** 纪念照片的同域 blob URL（为空则不展示照片区域） */
  photoUrl?: string
}>()

const achievedDate = computed(
  () => formatDate(props.wish.achievedAt) || formatDate(new Date())
)

const metaText = computed(() => {
  const unit = props.wish.unit ? ` ${props.wish.unit}` : ''
  const parts = [`完成 ${props.wish.currentValue}/${props.wish.targetValue}${unit}`]
  const days = durationDays(props.wish)
  parts.push(days > 0 ? `历时 ${days} 天` : '当天达成')
  return parts.join(' · ')
})

const noteText = computed(() => (props.wish.achievedNote || '').trim())

function durationDays(wish: Wish): number {
  if (!wish.createdAt || !wish.achievedAt) return 0
  const start = new Date(wish.createdAt).getTime()
  const end = new Date(wish.achievedAt).getTime()
  if (Number.isNaN(start) || Number.isNaN(end) || end <= start) return 0
  return Math.floor((end - start) / 86400000)
}
</script>

<style scoped lang="scss">
/* 注意：卡片要交给 html2canvas 截图。html2canvas 会克隆节点并按浏览器真实布局取几何信息，
   所以这里只用最简单的纵向 flex 列布局（照片区占满剩余高度），不使用 gap / justify-content。 */
.wish-poster {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 375px;
  // 9:16 竖版卡片
  aspect-ratio: 9 / 16;
  margin: 0 auto;
  padding: 22px 20px 18px;
  // 内容超出时裁切，保证导出图始终是 9:16
  overflow: hidden;
  border-radius: 18px;
  background: linear-gradient(160deg, #fff3f1 0%, #ffffff 55%);
  border: 1px solid rgba(255, 107, 107, 0.16);
  font-family: "PingFang SC", "Microsoft YaHei", -apple-system, sans-serif;
  color: #333333;
  text-align: center;
}

.poster-label {
  font-size: 12px;
  font-weight: 600;
  color: #ff6b6b;
  letter-spacing: 1px;
}

.poster-date {
  margin-top: 4px;
  font-size: 11px;
  color: #bbbbbb;
}

.poster-icon {
  width: 64px;
  height: 64px;
  margin: 16px auto 0;
  border-radius: 32px;
  background: #fff0f0;
  font-size: 32px;
  line-height: 64px;
}

.poster-title {
  margin-top: 12px;
  font-size: 19px;
  font-weight: 600;
  line-height: 1.4;
  word-break: break-word;
}

.poster-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #999999;
}

.poster-rule {
  width: 48px;
  height: 3px;
  margin: 14px auto 0;
  border-radius: 2px;
  background: linear-gradient(90deg, #ff6b6b 0%, #ffc1a1 100%);
}

.poster-photo {
  flex: 1;
  min-height: 120px;
  margin-top: 16px;
  border-radius: 12px;
  background-color: #f5f5f5;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.poster-note {
  margin-top: 14px;
  padding: 10px 12px;
  border-left: 3px solid #ff6b6b;
  border-radius: 10px;
  background: #fff8f8;
  font-size: 13px;
  line-height: 1.6;
  color: #666666;
  text-align: left;
  word-break: break-word;
}

.poster-brand {
  // 没有照片时把品牌信息压到卡片底部；有照片时剩余高度由照片占满
  margin-top: auto;
  padding-top: 20px;
  font-size: 14px;
  font-weight: 600;
  color: #ff6b6b;
}

.poster-slogan {
  margin-top: 4px;
  font-size: 11px;
  color: #b8b8b8;
}
</style>
