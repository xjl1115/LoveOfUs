<template>
  <div class="makeover-result">
    <van-nav-bar title="改造建议" left-arrow fixed placeholder @click-left="onBack">
      <template v-if="isOwner" #right>
        <van-icon name="delete-o" size="18" @click="onAskDelete" aria-label="删除" />
      </template>
    </van-nav-bar>

    <!-- 顶部黄色提示：出图失败但保留文本建议 -->
    <div v-if="detail && !detail.afterUrl" class="image-failed-banner">
      <van-icon name="warning-o" />
      <span>改造图生成失败，以下为文字建议</span>
    </div>

    <div class="result-content">
      <!-- 加载骨架 -->
      <template v-if="loading">
        <van-skeleton :row="3" :loading="true" />
        <van-skeleton title :row="2" :loading="true" style="margin-top:12px" />
      </template>

      <template v-else-if="detail">
        <!-- 1. Before / After 对比 -->
        <section v-if="detail.afterUrl" class="card compare-card">
          <MakeoverCompareSlider :before="detail.originalUrl" :after="detail.afterUrl" />
        </section>
        <section v-else class="card single-image">
          <img :src="detail.originalUrl" alt="原图" />
          <p class="sub">原图（改造图未生成）</p>
        </section>

        <!-- 改造总结：图片正下方，整体思路 + 化妆步骤详解 -->
        <section
          v-if="detail.summary && (detail.summary.overall || (detail.summary.steps && detail.summary.steps.length))"
          class="card summary-card"
        >
          <div class="summary-head">
            <h3 class="section-title">✨ 改造总结</h3>
            <span class="summary-tag">AI 量身定制</span>
          </div>
          <p v-if="detail.summary.overall" class="summary-overall">
            {{ detail.summary.overall }}
          </p>
          <div
            v-if="detail.summary.steps && detail.summary.steps.length"
            class="summary-steps"
          >
            <h4 class="steps-title">💄 化妆步骤详解</h4>
            <ol class="steps-list">
              <li
                v-for="(step, idx) in detail.summary.steps"
                :key="idx"
                class="step-item"
              >
                <span class="step-no">{{ idx + 1 }}</span>
                <span class="step-text">{{ step }}</span>
              </li>
            </ol>
          </div>
        </section>

        <!-- 2. 脸部特征 chips -->
        <section v-if="detail.faceFeatures" class="card features-card">
          <h3 class="section-title">你的脸型特征</h3>
          <MakeoverChipList :items="featureItems" />
        </section>

        <!-- 3. 妆容建议 -->
        <section v-if="detail.suggestions" class="card">
          <h3 class="section-title">💄 妆容建议</h3>
          <div class="suggestion-grid">
            <MakeoverSuggestionCard
              icon="🪞"
              title="底妆"
              :content="detail.suggestions.makeup.base"
            />
            <MakeoverSuggestionCard
              icon="👁️"
              title="眼妆"
              :content="detail.suggestions.makeup.eye"
            />
            <MakeoverSuggestionCard
              icon="💋"
              title="唇妆"
              :content="detail.suggestions.makeup.lip"
            />
            <MakeoverSuggestionCard
              icon="🪄"
              title="眉妆"
              :content="detail.suggestions.makeup.brow"
            />
          </div>
        </section>

        <!-- 4. 发型 / 配饰 -->
        <section v-if="detail.suggestions" class="card">
          <MakeoverSuggestionCard icon="💇" title="发型">
            <div>风格：{{ detail.suggestions.hair.style }}</div>
            <div>发色：{{ detail.suggestions.hair.color }}</div>
          </MakeoverSuggestionCard>
          <MakeoverSuggestionCard
            v-if="detail.suggestions.accessory.length"
            icon="💍"
            title="配饰"
            style="margin-top: 10px"
          >
            <MakeoverChipList :items="detail.suggestions.accessory" />
          </MakeoverSuggestionCard>
        </section>

        <!-- 4.5 香水 -->
        <section
          v-if="detail.suggestions && detail.suggestions.perfume && detail.suggestions.perfume.family"
          class="card perfume-card"
        >
          <MakeoverSuggestionCard icon="🌸" title="香水推荐">
            <div class="perfume-family">
              <span class="family-chip">{{ perfumeFamilyLabel }}</span>
            </div>
            <div v-if="detail.suggestions.perfume.topNote" class="perfume-line">
              <span class="line-tag">前调</span>
              <span>{{ detail.suggestions.perfume.topNote }}</span>
            </div>
            <div v-if="detail.suggestions.perfume.heartNote" class="perfume-line">
              <span class="line-tag">中调</span>
              <span>{{ detail.suggestions.perfume.heartNote }}</span>
            </div>
            <div v-if="detail.suggestions.perfume.baseNote" class="perfume-line">
              <span class="line-tag">后调</span>
              <span>{{ detail.suggestions.perfume.baseNote }}</span>
            </div>
            <div v-if="detail.suggestions.perfume.product" class="perfume-product">
              推荐：{{ detail.suggestions.perfume.product }}
            </div>
            <div v-if="detail.suggestions.perfume.occasion" class="perfume-occasion">
              适合：{{ detail.suggestions.perfume.occasion }}
            </div>
            <div v-if="detail.suggestions.perfume.tips" class="perfume-tips">
              💡 {{ detail.suggestions.perfume.tips }}
            </div>
          </MakeoverSuggestionCard>
        </section>

        <!-- 5. 服装 -->
        <section v-if="detail.suggestions" class="card">
          <MakeoverSuggestionCard icon="👗" title="服装">
            <div>风格：{{ detail.suggestions.outfit.style }}</div>
            <div v-if="detail.suggestions.outfit.items.length" class="outfit-items">
              推荐单品：
              <MakeoverChipList :items="detail.suggestions.outfit.items" />
            </div>
            <div v-if="detail.suggestions.outfit.colorTips" class="outfit-tip">
              配色建议：{{ detail.suggestions.outfit.colorTips }}
            </div>
          </MakeoverSuggestionCard>
        </section>

        <!-- 6. Tips 列表 -->
        <section
          v-if="detail.suggestions && detail.suggestions.tips.length"
          class="card tips-card"
        >
          <h3 class="section-title">💡 小贴士</h3>
          <ul class="tips-list">
            <li v-for="(t, idx) in detail.suggestions.tips" :key="idx">
              <span class="tip-dot">•</span>
              <span>{{ t }}</span>
            </li>
          </ul>
        </section>

        <!-- 7. 场景补充说明 -->
        <section v-if="detail.sceneText" class="card">
          <h3 class="section-title">📝 你提供的补充</h3>
          <p class="scene-text">{{ detail.sceneText }}</p>
        </section>

        <!-- 8. 操作区 -->
        <div v-if="isOwner" class="actions">
          <van-button round block plain @click="onAgain">再来一次</van-button>
          <van-button round block type="primary" @click="onShare">分享给 TA</van-button>
        </div>
        <div v-else class="actions partner-tip">
          <van-icon name="eye-o" />
          <span>伴侣视角只读，仅可查看效果</span>
        </div>
      </template>

      <template v-else>
        <EmptyState text="记录不存在或已被删除" />
      </template>
    </div>

    <MakeoverDeleteSheet v-model="deleteSheet.show" @delete="onConfirmDelete" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showFailToast } from 'vant'
import MakeoverCompareSlider from '@/components/makeover/MakeoverCompareSlider.vue'
import MakeoverSuggestionCard from '@/components/makeover/MakeoverSuggestionCard.vue'
import MakeoverChipList from '@/components/makeover/MakeoverChipList.vue'
import MakeoverDeleteSheet from '@/components/makeover/MakeoverDeleteSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  getMakeoverDetail,
  deleteMakeover,
  shareMakeover,
  type MakeoverDetailVO
} from '@/api/makeover'

const PERFUME_FAMILY_LABEL: Record<string, string> = {
  floral: '花香调',
  citrus: '柑橘调',
  woody: '木质调',
  oriental: '东方调',
  fresh: '清新调',
  gourmand: '美食调',
  chypre: '西普调'
}

const route = useRoute()
const router = useRouter()

const recordId = Number(route.params.recordId)
const loading = ref(true)
const detail = ref<MakeoverDetailVO | null>(null)

const deleteSheet = ref<{ show: boolean }>({ show: false })

const featureItems = computed(() => {
  const f = detail.value?.faceFeatures
  if (!f) return []
  // 顺序展示关键特征
  return [f.faceShape, f.skinTone, f.eyeShape, f.lipShape, f.hairLength].filter(Boolean)
})

const isOwner = computed(() => detail.value?.ownedByCurrent !== false)

const perfumeFamilyLabel = computed(() => {
  const code = detail.value?.suggestions?.perfume?.family || ''
  return PERFUME_FAMILY_LABEL[code] || code
})

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/makeover')
}

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getMakeoverDetail(recordId)
  } catch {
    // 拦截器已显示后端 message（如「记录不存在」），无需再 toast
    detail.value = null
  } finally {
    loading.value = false
  }
}

function onAskDelete() {
  deleteSheet.value.show = true
}

async function onConfirmDelete() {
  try {
    await deleteMakeover(recordId)
    showToast('已删除')
    router.replace('/makeover/history')
  } catch {
    // 拦截器已显示后端 message，无需再 toast
  }
}

function onAgain() {
  router.replace('/makeover')
}

const sharing = ref(false)
async function onShare() {
  if (sharing.value) return
  sharing.value = true
  try {
    const msgId = await shareMakeover(recordId)
    showToast({ type: 'success', message: '已发送给 TA' })
    // 等动画展示一下再跳到聊天页
    setTimeout(() => router.push('/chat'), 400)
    void msgId
  } catch {
    // 拦截器已显示后端 message，无需再 toast
  } finally {
    sharing.value = false
  }
}

onMounted(() => {
  if (!recordId || Number.isNaN(recordId)) {
    showFailToast('记录不存在')
    router.replace('/makeover/history')
    return
  }
  loadDetail()
})
</script>

<style scoped lang="scss">
.makeover-result {
  min-height: 100vh;
  background: $bg-color;
}
.image-failed-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 16px 0;
  padding: 8px 12px;
  background: #fff7e6;
  color: #b8821b;
  font-size: 13px;
  border-radius: 8px;
}
.result-content {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card {
  background: #fff;
  border-radius: $radius-md;
  padding: 14px 16px;
}
/* 改造总结卡片：渐变背景突出主题，独立区分于普通建议卡片 */
.summary-card {
  background: linear-gradient(160deg, #fff5f3 0%, #ffffff 60%);
  border: 1px solid rgba(255, 107, 107, 0.18);
}
.summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.summary-head .section-title {
  margin: 0;
}
.summary-tag {
  display: inline-block;
  padding: 2px 8px;
  background: linear-gradient(135deg, #ffd6e7 0%, #ffe7c2 100%);
  color: #b5436a;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.summary-overall {
  margin: 0 0 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.7);
  border-left: 3px solid #ff8a8a;
  border-radius: 6px;
  font-size: 13.5px;
  line-height: 1.7;
  color: $text-primary;
  word-break: break-word;
}
.steps-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  margin: 8px 0 10px;
}
.steps-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.step-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  background: rgba(255, 240, 240, 0.55);
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: $text-primary;
  word-break: break-word;
}
.step-no {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff8a8a 0%, #ffb3b3 100%);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}
.step-text {
  flex: 1;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 10px;
}
.single-image {
  text-align: center;
  img {
    width: 100%;
    border-radius: $radius-md;
    display: block;
  }
  .sub {
    margin: 8px 0 0;
    font-size: 12px;
    color: $text-tertiary;
  }
}
.suggestion-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.outfit-items {
  margin-top: 6px;
}
.outfit-tip {
  margin-top: 6px;
  color: $text-secondary;
  font-size: 13px;
}
.tips-list {
  list-style: none;
  margin: 0;
  padding: 0;
  li {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 6px 0;
    color: $text-secondary;
    font-size: 13px;
    line-height: 1.6;
  }
  .tip-dot {
    color: $primary-color;
    flex-shrink: 0;
    line-height: 1.6;
  }
}
.scene-text {
  margin: 0;
  color: $text-secondary;
  font-size: 13px;
  line-height: 1.6;
  background: $bg-color;
  padding: 10px 12px;
  border-radius: $radius-sm;
}
.actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  .van-button {
    flex: 1;
  }
}
.actions.partner-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 14px 16px;
  background: #fff;
  border-radius: $radius-md;
  color: $text-tertiary;
  font-size: 13px;
  .van-icon {
    color: $primary-color;
    font-size: 16px;
  }
}
.perfume-card {
  .perfume-family {
    margin-bottom: 8px;
  }
  .family-chip {
    display: inline-block;
    padding: 3px 10px;
    background: linear-gradient(90deg, #ffd6e7, #ffe7c2);
    color: #b5436a;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 600;
  }
  .perfume-line {
    display: flex;
    gap: 6px;
    margin-top: 4px;
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.6;
  }
  .line-tag {
    flex-shrink: 0;
    padding: 0 6px;
    height: 18px;
    line-height: 18px;
    border-radius: 4px;
    background: $bg-color;
    color: $text-tertiary;
    font-size: 11px;
    text-align: center;
  }
  .perfume-product {
    margin-top: 8px;
    color: $primary-color;
    font-size: 13px;
    font-weight: 500;
  }
  .perfume-occasion {
    margin-top: 4px;
    color: $text-secondary;
    font-size: 12px;
  }
  .perfume-tips {
    margin-top: 6px;
    color: $text-tertiary;
    font-size: 12px;
    line-height: 1.55;
  }
}
</style>