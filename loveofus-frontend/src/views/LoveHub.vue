<template>
  <div class="love-hub-page">
    <van-nav-bar title="心动" fixed placeholder />

    <div class="hub-header">
      <div class="hub-title">💕 一起做点浪漫的事</div>
      <div class="hub-sub">AI 约会 / 情侣心愿 / 必做 100 件小事</div>
    </div>

    <van-tabs
      v-model:active="activeTab"
      :sticky="true"
      :offset-top="46"
      line-width="28px"
      title-active-color="#FF6B6B"
      color="#FF6B6B"
    >
      <van-tab title="🌹 约会" name="date">
        <DatePlanContent />
      </van-tab>
      <van-tab title="⭐ 心愿" name="wish">
        <WishlistContent />
      </van-tab>
      <van-tab title="💯 必做" name="must">
        <MustDoContent />
      </van-tab>
    </van-tabs>

    <BottomTab />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomTab from '@/components/BottomTab.vue'
import DatePlanContent from '@/components/DatePlanContent.vue'
import WishlistContent from '@/components/WishlistContent.vue'
import MustDoContent from '@/components/MustDoContent.vue'

const route = useRoute()
const router = useRouter()

type TabName = 'date' | 'wish' | 'must'

// 支持通过 ?tab=date|wish|must 直接定位（纪念日 push 等场景使用）
function parseTab(q: unknown): TabName {
  return q === 'wish' || q === 'must' ? (q as TabName) : 'date'
}

const activeTab = ref<TabName>(parseTab(route.query.tab))

// 切换 tab 时同步 URL，方便分享/回链
watch(activeTab, (v) => {
  if (route.query.tab !== v) {
    router.replace({ query: { ...route.query, tab: v } })
  }
})

// 外部 URL 变化（如纪念日 push 跳转）同步回 tab
watch(() => route.query.tab, (v) => {
  activeTab.value = parseTab(v)
})
</script>

<style scoped lang="scss">
.love-hub-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 80px; // BottomTab 占位
}

.hub-header {
  margin: 16px;
  padding: 16px 18px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe7e7 100%);
  border-radius: $radius-lg;

  .hub-title {
    font-size: 17px;
    font-weight: 600;
    color: $primary-color;
    margin-bottom: 4px;
  }

  .hub-sub {
    font-size: 12px;
    color: $text-secondary;
  }
}
</style>
