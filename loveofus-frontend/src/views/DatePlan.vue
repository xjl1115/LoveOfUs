<template>
  <div class="date-plan-page">
    <van-nav-bar title="AI 约会策划师" left-arrow fixed placeholder @click-left="onBack" />
    <DatePlanContent />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import DatePlanContent from '@/components/DatePlanContent.vue'

const router = useRouter()
function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}
</script>

<style scoped lang="scss">
.date-plan-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 24px;
}
</style>