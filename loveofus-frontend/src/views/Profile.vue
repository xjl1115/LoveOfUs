<template>
  <div class="profile-page">
    <van-nav-bar title="个人中心" fixed placeholder />

    <div class="profile-content">
      <!-- 用户信息卡片 -->
      <div class="user-card">
        <div class="user-avatar">
          <van-image round width="80" height="80" :src="userInfo?.avatarUrl || defaultAvatar" />
          <!-- 头像右下角性别装饰（无障碍可读） -->
          <button
            v-if="userInfo && 'gender' in userInfo"
            class="gender-decor"
            :class="['g-' + genderBadgeClass(userInfo.gender), { 'is-unknown': !userInfo.gender }]"
            :aria-label="'性别：' + genderLabel(userInfo.gender)"
            @click="onClickMyGender"
          >
            <span class="gender-decor-icon" v-html="genderIcon(userInfo.gender)"></span>
            <span class="gender-decor-pulse"></span>
          </button>
        </div>
        <div class="user-info">
          <h3>
            {{ userInfo?.nickname || '未设置昵称' }}
            <span class="vip-level-tag" :class="vipTagClass(userVipLevel)">
              <span v-if="userVipLevel > 0" class="vip-crown">👑</span>{{ vipTagText(vipUserInfo?.vipLevelName) }}
            </span>
            <span
              v-if="userInfo && 'gender' in userInfo"
              class="gender-chip"
              :class="['chip-' + genderBadgeClass(userInfo.gender), { 'chip-pulse': !!userInfo.gender }]"
              role="button"
              tabindex="0"
              @click="onClickMyGender"
              @keydown.enter="onClickMyGender"
            >
              <span class="chip-icon" v-html="genderIcon(userInfo.gender)"></span>
              {{ genderLabel(userInfo.gender) }}
            </span>
          </h3>
          <p v-if="userInfo?.partner" class="partner-info">
            您的伴侣：{{ userInfo.partner.nickname }}
          </p>
          <p v-else class="partner-info unbound">未绑定伴侣</p>
          <div class="days-together" v-if="daysTogether > 0">
            <span class="heart">❤️</span>
            <span>已在一起 {{ daysTogether }} 天</span>
          </div>
        </div>
      </div>

      <!-- 升级 VIP 入口 -->
      <div class="vip-entry" @click="$router.push('/vip')">
        <span class="vip-entry-crown">👑</span>
        <div class="vip-entry-info">
          <div class="vip-entry-title">{{ vipEntryTitle }}</div>
          <div class="vip-entry-sub">{{ vipEntrySubText }}</div>
        </div>
        <span class="vip-entry-action">{{ userVipLevel > 0 ? '续费' : '立即升级' }}</span>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-card">
        <div class="stats-item">
          <span class="stats-value">{{ stats?.photoCount || 0 }}</span>
          <span class="stats-label">照片</span>
        </div>
        <div class="stats-item">
          <span class="stats-value">{{ stats?.cityCount || 0 }}</span>
          <span class="stats-label">地点</span>
        </div>
        <div class="stats-item">
          <span class="stats-value">{{ stats?.albumCount || 0 }}</span>
          <span class="stats-label">相册</span>
        </div>
        <div class="stats-item">
          <span class="stats-value">{{ daysTogether }}</span>
          <span class="stats-label">天数</span>
        </div>
      </div>

      <!-- 纪念日区域 -->
      <div class="anniversary-section" v-if="userInfo?.partner">
        <div class="anniversary-header">
          <span class="anniversary-title">💕 我们的纪念日</span>
          <van-button size="small" type="primary" plain round @click="showAnniversarySettings">
            设置纪念日
          </van-button>
        </div>
        <div class="anniversary-list" v-if="anniversaries.length > 0">
          <div v-for="item in anniversaries" :key="item.id" class="anniversary-item">
            <div class="anniversary-info" @click="showAnniversaryDetail(item)">
              <span class="anniversary-name">{{ item.name }}</span>
              <span class="anniversary-date">{{ item.anniversaryDate }}</span>
              <span v-if="item.description" class="anniversary-desc">{{ item.description }}</span>
            </div>
            <div class="anniversary-right">
              <div class="anniversary-countdown" @click="showAnniversaryDetail(item)">
                <span class="countdown-days">{{ item.daysUntil === 0 ? '今天' : item.daysUntil }}</span>
                <span v-if="item.daysUntil !== 0" class="countdown-text">天后</span>
              </div>
              <van-icon name="ellipsis" class="anniversary-more" @click="onAnniversaryMore(item)" />
            </div>
          </div>
        </div>
        <div class="anniversary-empty" v-else>
          <span>还没有设置纪念日，快去添加吧~</span>
        </div>
      </div>

      <!-- 已绑定伴侣信息 -->
      <div class="bind-code-section" v-if="userInfo?.partner">
        <div class="bind-code-card bound">
          <div class="bind-code-header">
            <van-icon name="success" />
            <span>已绑定伴侣</span>
          </div>
          <div class="partner-detail">
            <van-image round width="56" height="56" :src="userInfo.partner.avatarUrl || defaultAvatar" />
            <div class="partner-detail-info">
              <div class="partner-detail-name">
                {{ userInfo.partner.nickname }}
                <span class="vip-level-tag" :class="vipTagClass(userInfo.partner.vipLevel)">
                  <span v-if="(userInfo.partner.vipLevel ?? 0) > 0" class="vip-crown">👑</span>{{ vipTagText(userInfo.partner.vipLevelName) }}
                </span>
                <span
                  v-if="'gender' in userInfo.partner"
                  class="gender-chip"
                  :class="['chip-' + genderBadgeClass(userInfo.partner.gender), { 'chip-pulse': !!userInfo.partner.gender }]"
                  role="button"
                  tabindex="0"
                  @click="onClickPartnerGender"
                  @keydown.enter="onClickPartnerGender"
                >
                  <span class="chip-icon" v-html="genderIcon(userInfo.partner.gender)"></span>
                  {{ genderLabel(userInfo.partner.gender) }}
                </span>
              </div>
              <div class="partner-detail-item">
                <van-icon name="phone-o" />
                <span>{{ userInfo.partner.phone || '用户暂未设置' }}</span>
              </div>
              <div class="partner-detail-item">
                <van-icon name="envelop-o" />
                <span>{{ userInfo.partner.email }}</span>
              </div>
              <!-- 性别描述行 -->
              <div
                v-if="'gender' in userInfo.partner"
                class="partner-detail-item partner-gender-row"
                @click="onClickPartnerGender"
              >
                <van-icon name="friends-o" />
                <span>{{ partnerGenderDesc(userInfo.partner.gender) }}</span>
              </div>
            </div>
          </div>
          <van-button block round size="small" @click="showUnbindConfirm">解除绑定</van-button>
        </div>
      </div>

      <!-- 伴侣照片展示（单图全屏 + 左右切换） -->
      <div class="photo-hero-section" v-if="userInfo?.partner" ref="photoSectionRef">
        <div class="photo-hero-container">
          <div class="photo-hero-image" @click="handlePhotoClick">
            <van-image
              :src="currentPhotoSrc"
              fit="cover"
              width="100%"
              height="100%"
            >
              <template #loading>
                <div class="photo-placeholder">
                  <van-loading type="spinner" size="24" />
                </div>
              </template>
              <template #error>
                <div class="photo-placeholder photo-error">
                  <van-icon name="photo-o" size="48" color="#ccc" />
                  <span>暂无照片</span>
                </div>
              </template>
            </van-image>
            <!-- 左侧导航按钮 - 悬浮于图片之上 -->
            <div class="photo-nav-btn photo-nav-prev" @click.stop="prevPhoto" v-if="validPartnerPhotos.length > 1">
              <van-icon name="arrow-left" />
            </div>
            <!-- 右侧导航按钮 - 悬浮于图片之上 -->
            <div class="photo-nav-btn photo-nav-next" @click.stop="nextPhoto" v-if="validPartnerPhotos.length > 1">
              <van-icon name="arrow" />
            </div>
            <!-- 地点水印 -->
            <div class="photo-hero-location" v-if="currentPhoto?.locationName">
              <van-icon name="location-o" />
              <span>{{ currentPhoto.locationName }}</span>
            </div>
            <!-- 指示器 -->
            <div class="photo-hero-indicator" v-if="validPartnerPhotos.length > 1">
              {{ currentPhotoIndex + 1 }}/{{ validPartnerPhotos.length }}
            </div>
            <div class="photo-hero-empty-hint" v-else-if="validPartnerPhotos.length === 0">
              <span>暂无伴侣照片</span>
            </div>
          </div>
        </div>
      </div>
      <!-- 未绑定 - 绑定码卡 -->
      <div class="bind-code-section" v-else>
        <div class="bind-code-card">
          <div class="bind-code-header">
            <van-icon name="friends-o" />
            <span>情侣绑定码</span>
          </div>
          <p class="bind-code-desc" v-if="!bindCode">生成绑定码分享给伴侣，绑定后可共同记录美好时光</p>
          <p class="bind-code-desc" v-else>请将绑定码分享给伴侣，绑定码永久有效</p>
          <div class="bind-code-display" v-if="bindCode">
            <div class="code-value">{{ bindCode }}</div>
            <van-button size="small" type="primary" @click="copyBindCode">复制</van-button>
          </div>
          <van-button
            v-else
            block
            round
            type="primary"
            :loading="generatingCode"
            @click="generateBindCode"
          >
            生成绑定码
          </van-button>
          <van-button
            v-if="bindCode"
            class="invite-btn"
            block
            round
            plain
            type="primary"
            icon="share-o"
            @click="invitePartner"
          >
            邀请伴侣共建相册
          </van-button>
          <div class="bind-code-actions">
            <span class="link" @click="showBindInput = true">已有伴侣的绑定码？点击输入</span>
          </div>
        </div>
      </div>

      <!-- 足迹地图入口 -->
      <div class="map-entry" @click="goToMap">
        <van-icon name="map-marked" />
        <span>查看完整足迹地图</span>
        <van-icon name="arrow" />
      </div>

      <!-- 设置列表 -->
      <van-cell-group inset class="settings-group">
        <van-cell title="VIP 会员" icon="gem-o" is-link @click="$router.push('/vip')">
          <template #value>
            <span class="vip-tag" :class="{ 'vip-tag-active': userVipLevel > 0 }">
              {{ userVipLevel > 0 ? vipUserInfo?.vipLevelName : '未开通' }}
            </span>
          </template>
        </van-cell>
        <van-cell title="账户设置" icon="setting-o" is-link @click="$router.push('/account-settings')" />
        <van-cell title="消息通知" icon="bell-o" is-link @click="showNotificationSettings" />

        <van-cell title="AI 约会策划" icon="like-o" is-link @click="$router.push('/date-plan')" />
        <van-cell title="AI 化妆建议" icon="like-o" is-link @click="$router.push('/makeover')" />
        <van-cell title="情侣心愿清单" icon="star-o" is-link @click="$router.push('/wishlist')" />
        <van-cell title="数据备份与导出" icon="backup-o" is-link @click="$router.push('/export')" />
        <van-cell title="分享 LoveOfUs" icon="share-o" is-link @click="showShare" />
        <van-cell title="关于 LoveOfUs" icon="info-o" is-link @click="showAbout" />
      </van-cell-group>

      <!-- 退出登录 -->
      <div class="logout-btn">
        <van-button block round @click="logout">退出登录</van-button>
      </div>

      <!-- 注销账户 -->
      <div class="delete-account-btn">
        <span @click="showDeleteAccountConfirm">注销账户</span>
      </div>
    </div>

    <!-- 输入绑定码弹窗 -->
    <van-popup v-model:show="showBindInput" round closeable :style="{ width: '85%', maxWidth: '400px' }">
      <div class="bind-input-popup">
        <div class="popup-header">
          <h3>输入伴侣绑定码</h3>
        </div>
        <van-field
          v-model="bindInputCode"
          placeholder="请输入伴侣的绑定码"
          maxlength="20"
          clearable
          autocomplete="off"
        >
          <template #left-icon>
            <van-icon name="friends-o" />
          </template>
        </van-field>
        <div class="popup-actions">
          <van-button
            round
            block
            type="primary"
            :loading="bindingPartner"
            @click="confirmBind"
          >
            确认绑定
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 纪念日设置弹窗 -->
    <van-popup v-model:show="showAnniversaryPopup" round closeable position="bottom" :style="{ height: '75%' }">
      <div class="anniversary-popup">
        <div class="popup-header">
          <h3>设置纪念日</h3>
        </div>
        <div class="anniversary-form">
          <van-field
            v-model="newAnniversary.name"
            label="纪念日名称"
            placeholder="例如：恋爱纪念日、结婚纪念日"
            maxlength="20"
          />
          <van-field
            v-model="newAnniversary.date"
            label="日期"
            type="date"
          />
          <!-- 是否每年重复 -->
          <van-cell center title="每年重复提醒">
            <template #right-icon>
              <van-switch v-model="newAnniversary.isRecurring" size="20" />
            </template>
          </van-cell>
          <!-- 提前提醒天数 -->
          <van-field
            v-model="newAnniversary.remindDays"
            label="提前提醒"
            type="number"
            placeholder="提前几天提醒"
          >
            <template #right-icon>
              <span class="field-suffix">天</span>
            </template>
          </van-field>
          <!-- 备注描述 -->
          <van-field
            v-model="newAnniversary.description"
            label="备注"
            type="textarea"
            placeholder="添加备注描述（可选）"
            maxlength="100"
            show-word-limit
            rows="2"
          />
          <van-button round block type="primary" @click="addAnniversary">
            添加纪念日
          </van-button>
        </div>
        <div class="anniversary-list-edit" v-if="anniversaries.length > 0">
          <van-divider>已设置的纪念日</van-divider>
          <van-swipe-cell v-for="(item, index) in anniversaries" :key="item.id">
            <van-cell>
              <template #title>
                <div class="anniversary-edit-title">
                  <span>{{ item.name }}</span>
                  <van-tag v-if="item.isRecurring" type="primary">每年</van-tag>
                  <van-tag v-else type="default">单次</van-tag>
                </div>
              </template>
              <template #label>
                <div class="anniversary-edit-label">
                  <div>{{ item.anniversaryDate }}</div>
                  <div v-if="item.description" class="description-text">{{ item.description }}</div>
                  <div v-if="item.remindDays" class="remind-text">提前{{ item.remindDays }}天提醒</div>
                </div>
              </template>
              <template #value>
                <span :class="{ 'expired': (item.daysUntil ?? 0) < 0 }">
                  {{ countdownText(item.daysUntil) }}
                </span>
              </template>
            </van-cell>
            <template #right>
              <van-button square type="danger" text="删除" @click="removeAnniversary(index)" />
            </template>
          </van-swipe-cell>
        </div>
      </div>
    </van-popup>

    <!-- 消息通知设置弹窗 -->
    <van-popup v-model:show="showNotificationPopup" round closeable :style="{ width: '85%', maxWidth: '400px' }">
      <div class="notification-popup">
        <div class="popup-header">
          <h3>消息通知设置</h3>
        </div>
        <div class="notification-list">
          <van-cell center title="接收新消息通知">
            <template #right-icon>
              <van-switch v-model="notificationSettings.enablePush" size="20" @change="onNotificationSettingChange" />
            </template>
          </van-cell>
          <van-cell center title="伴侣上传照片提醒">
            <template #right-icon>
              <van-switch v-model="notificationSettings.photoUpload" size="20" @change="onNotificationSettingChange" />
            </template>
          </van-cell>
          <van-cell center title="纪念日提醒">
            <template #right-icon>
              <van-switch v-model="notificationSettings.anniversary" size="20" @change="onNotificationSettingChange" />
            </template>
          </van-cell>
          <van-cell center title="邮箱通知">
            <template #right-icon>
              <van-switch v-model="notificationSettings.email" size="20" @change="onNotificationSettingChange" />
            </template>
          </van-cell>
          <van-cell center title="系统公告">
            <template #right-icon>
              <van-switch v-model="notificationSettings.system" size="20" @change="onNotificationSettingChange" />
            </template>
          </van-cell>
        </div>
      </div>
    </van-popup>

    <!-- 分享弹窗 -->
    <van-popup v-model:show="showSharePopup" round closeable :style="{ width: '85%', maxWidth: '420px' }">
      <div class="share-popup">
        <div class="share-slogan">记录每一刻，珍藏一辈子 ❤️</div>

        <div class="share-qr">
          <img v-if="qrCodeDataUrl" :src="qrCodeDataUrl" alt="二维码" />
          <van-loading v-else size="24" />
        </div>

        <div class="share-url-wrap">
          <div class="share-url">{{ shareUrl }}</div>
          <van-button size="small" type="primary" plain @click="copyShareUrl">复制</van-button>
        </div>
        <p class="share-url-hint">点击复制链接，分享给好友</p>
      </div>
    </van-popup>

    <!-- 纪念日操作 ActionSheet -->
    <van-action-sheet v-model:show="showAnniversaryActions" :actions="anniversaryActions" @select="onAnniversaryActionSelect" cancel-text="取消" close-on-click-action />

    <!-- 自己的性别 ActionSheet -->
    <van-action-sheet
      v-model:show="showMyGenderSheet"
      :actions="myGenderActions"
      cancel-text="取消"
      close-on-click-action
      :description="myGenderSheetDesc"
      @select="onMyGenderSelect"
    />

    <!-- 伴侣的性别 ActionSheet -->
    <van-action-sheet
      v-model:show="showPartnerGenderSheet"
      :actions="partnerGenderActions"
      cancel-text="关闭"
      :description="partnerGenderSheetDesc"
    />

    <!-- 纪念日详情弹窗 -->
    <van-popup v-model:show="showAnniversaryDetailPopup" round closeable position="bottom" :style="{ height: '65%' }">
      <div class="anniversary-detail-popup" v-if="selectedAnniversary">
        <div class="popup-header">
          <h3>纪念日详情</h3>
        </div>
        <div class="detail-content">
          <!-- 顶部倒计时卡片 -->
          <div class="detail-countdown-card">
            <div class="countdown-icon">💕</div>
            <div class="countdown-name">{{ selectedAnniversary.name }}</div>
            <div class="countdown-number">
              <span class="countdown-value" :class="{ today: selectedAnniversary.daysUntil === 0 }">
                {{ selectedAnniversary.daysUntil === 0 ? '今天' : (selectedAnniversary.daysUntil ?? 0) }}
              </span>
              <span v-if="selectedAnniversary.daysUntil !== 0" class="countdown-unit">天后</span>
            </div>
            <div class="countdown-date">{{ selectedAnniversary.anniversaryDate }}</div>
          </div>

          <!-- 信息列表 -->
          <div class="detail-info-list">
            <div class="detail-info-row">
              <van-icon name="clock-o" class="detail-info-icon" />
              <span class="detail-info-label">每年重复</span>
              <span class="detail-info-value">
                <van-tag v-if="selectedAnniversary.isRecurring" type="primary" round>每年重复</van-tag>
                <van-tag v-else type="default" round>单次</van-tag>
              </span>
            </div>
            <div class="detail-info-row">
              <van-icon name="bell" class="detail-info-icon" />
              <span class="detail-info-label">提前提醒</span>
              <span class="detail-info-value">{{ selectedAnniversary.remindDays ? selectedAnniversary.remindDays + '天' : '未设置' }}</span>
            </div>
            <div class="detail-info-row" v-if="selectedAnniversary.description">
              <van-icon name="notes-o" class="detail-info-icon" />
              <span class="detail-info-label">备注</span>
              <span class="detail-info-value description-value">{{ selectedAnniversary.description }}</span>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="detail-actions">
            <van-button round block type="primary" icon="edit" @click="editFromDetail">更新纪念日</van-button>
            <van-button round block type="danger" plain icon="delete-o" @click="deleteFromDetail">删除纪念日</van-button>
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 更新纪念日弹窗 -->
    <van-popup v-model:show="showUpdateAnniversaryPopup" round closeable position="bottom" :style="{ height: '75%' }">
      <div class="anniversary-popup">
        <div class="popup-header">
          <h3>更新纪念日</h3>
        </div>
        <div class="anniversary-form">
          <van-field
            v-model="editAnniversary.name"
            label="纪念日名称"
            placeholder="例如：恋爱纪念日、结婚纪念日"
            maxlength="20"
          />
          <van-field
            v-model="editAnniversary.anniversaryDate"
            label="日期"
            type="date"
          />
          <van-cell center title="每年重复提醒">
            <template #right-icon>
              <van-switch v-model="editAnniversary.isRecurring" size="20" />
            </template>
          </van-cell>
          <van-field
            v-model="editAnniversary.remindDays"
            label="提前提醒"
            type="number"
            placeholder="提前几天提醒"
          >
            <template #right-icon>
              <span class="field-suffix">天</span>
            </template>
          </van-field>
          <van-field
            v-model="editAnniversary.description"
            label="备注"
            type="textarea"
            placeholder="添加备注描述（可选）"
            maxlength="100"
            show-word-limit
            rows="2"
          />
          <van-button round block type="primary" @click="confirmUpdateAnniversary">
            保存修改
          </van-button>
        </div>
      </div>
    </van-popup>

    <BottomTab />
    <!-- 隐藏的 van-dialog，触发组件自动导入，供 showDialog/showConfirmDialog 使用 -->
    <van-dialog v-model:show="dummyDialogVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showDialog, showToast, showImagePreview } from 'vant'
import BottomTab from '@/components/BottomTab.vue'
import { useUserStore } from '@/stores/user'
import { getUserInfo, getUserStats, generateBindCode as apiGenerateBindCode, getBindCode, bindPartner, unbindPartner, deleteAccount, getNotificationSettings, updateNotificationSettings } from '@/api/user'
import { getTimelinePhotos } from '@/api/photo'
import { getAnniversaryList, createAnniversary, deleteAnniversary, getAnniversary, updateAnniversary, type Anniversary } from '@/api/anniversary'
import { logout as authLogout } from '@/api/auth'
import { scheduleDailyRefresh } from '@/utils/date'
import type { UserInfo, UserStats, Photo } from '@/types'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 跨天（0 点）自动刷新纪念日倒计时
let cancelDailyRefresh: (() => void) | null = null

const dummyDialogVisible = ref(false)

const defaultAvatar = 'https://img.yzcdn.cn/vant/cat.jpeg'
const userInfo = ref<UserInfo | null>(null)
const stats = ref<UserStats | null>(null)
const bindCode = ref<string>('')
const generatingCode = ref(false)

const showBindInput = ref(false)
const bindInputCode = ref('')
const bindingPartner = ref(false)

const partnerPhotos = ref<Photo[]>([])
const currentPhotoIndex = ref(0)

const validPartnerPhotos = computed(() => partnerPhotos.value.filter(p => p.storagePath))

const currentPhoto = computed(() => validPartnerPhotos.value[currentPhotoIndex.value] || null)

const currentPhotoSrc = computed(() => {
  if (validPartnerPhotos.value.length > 0) {
    return validPartnerPhotos.value[currentPhotoIndex.value]?.storagePath
  }
  // 无照片时显示伴侣头像
  return userInfo.value?.partner?.avatarUrl || defaultAvatar
})

const daysTogether = computed(() => userStore.daysTogether)

/**
 * VIP 信息读取 store 而非本页 userInfo：
 * Profile 被 keep-alive 缓存，本地 userInfo 不会随会员页的下单结果刷新
 */
const vipUserInfo = computed(() => userStore.userInfo)
/** VIP 等级：0-普通用户，>0 为已开通 */
const userVipLevel = computed(() => vipUserInfo.value?.vipLevel ?? 0)

/** 等级标签文案：未设置时按普通展示 */
function vipTagText(name?: string) {
  return name || '普通'
}

/** 等级标签配色：普通为灰色，会员为金色 */
function vipTagClass(level?: number) {
  return (level ?? 0) > 0 ? 'vip-tag-gold' : 'vip-tag-gray'
}

/** 入口标题：会员显示「XX会员」，非会员显示开通引导 */
const vipEntryTitle = computed(() =>
  userVipLevel.value > 0 ? `${vipTagText(vipUserInfo.value?.vipLevelName)}会员` : '开通 VIP 会员'
)

/** 入口副标题：会员显示到期时间，非会员显示卖点 */
const vipEntrySubText = computed(() => {
  if (userVipLevel.value <= 0) {
    return '双人同享 · 解锁全部情侣专属权益'
  }
  const expireAt = vipUserInfo.value?.vipExpireAt
  return expireAt ? `${String(expireAt).slice(0, 10)} 到期` : '永久有效'
})

// 分享相关
const showSharePopup = ref(false)
const shareUrl = window.location.origin
const qrCodeDataUrl = ref('')

// 消息通知设置
const showNotificationPopup = ref(false)
const notificationSettings = ref({
  enablePush: true,
  photoUpload: true,
  anniversary: true,
  system: true,
  email: true
})

// 纪念日设置
const showAnniversaryPopup = ref(false)
const anniversaries = ref<Anniversary[]>([])
const newAnniversary = ref({
  name: '',
  date: '',
  isRecurring: true,
  remindDays: 1,
  description: ''
})
const loadingAnniversaries = ref(false)

/** 纪念日倒计时文案：负数=已过去 N 天，0=今天，正数=N 天后 */
function countdownText(days: number | null | undefined): string {
  const value = days ?? 0
  if (value < 0) return `已过去${Math.abs(value)}天`
  if (value === 0) return '今天'
  return `${value}天后`
}

// 加载纪念日列表
async function loadAnniversaries() {
  if (!userInfo.value?.partner) return
  
  loadingAnniversaries.value = true
  try {
    const list = await getAnniversaryList()
    anniversaries.value = list.sort((a, b) => (a.daysUntil ?? 999) - (b.daysUntil ?? 999))
  } catch (error) {
    console.error('加载纪念日失败:', error)
    showToast('加载纪念日失败')
  } finally {
    loadingAnniversaries.value = false
  }
}

// 显示纪念日设置弹窗
async function showAnniversarySettings() {
  showAnniversaryPopup.value = true
  await loadAnniversaries()
}

// 添加纪念日
async function addAnniversary() {
  if (!newAnniversary.value.name.trim() || !newAnniversary.value.date) {
    showToast('请填写完整信息')
    return
  }

  try {
    await createAnniversary({
      name: newAnniversary.value.name.trim(),
      anniversaryDate: newAnniversary.value.date,
      isRecurring: newAnniversary.value.isRecurring,
      remindDays: parseInt(newAnniversary.value.remindDays?.toString() || '1'),
      description: newAnniversary.value.description?.trim() || ''
    })

    newAnniversary.value = { name: '', date: '', isRecurring: true, remindDays: 1, description: '' }
    await loadAnniversaries()
    showToast('添加成功')
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || '添加失败'
    showToast(msg)
  }
}

// 删除纪念日
async function removeAnniversary(index: number) {
  const item = anniversaries.value[index]
  if (!item) return

  try {
    await deleteAnniversary(item.id)
    await loadAnniversaries()
    showToast('已删除')
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || '删除失败'
    showToast(msg)
  }
}

// ---- 纪念日详情 / 操作 ActionSheet / 更新 ----

const showAnniversaryActions = ref(false)
const showAnniversaryDetailPopup = ref(false)
const showUpdateAnniversaryPopup = ref(false)
const selectedAnniversary = ref<Anniversary | null>(null)
const selectedAnniversaryIndex = ref<number>(-1)

const anniversaryActions = [
  { name: '更新', value: 'update' },
  { name: '删除', value: 'delete' }
]

// 点击纪念日项 -> 调用 API 获取详情 -> 弹出详情
async function showAnniversaryDetail(item: Anniversary) {
  try {
    const detail = await getAnniversary(item.id)
    selectedAnniversary.value = detail
    selectedAnniversaryIndex.value = anniversaries.value.findIndex(a => a.id === item.id)
    showAnniversaryDetailPopup.value = true
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || '获取详情失败'
    showToast(msg)
  }
}

// 详情弹窗 -> 更新按钮
function editFromDetail() {
  if (!selectedAnniversary.value) return
  showAnniversaryDetailPopup.value = false
  // 预填现有信息到编辑表单
  editAnniversary.value = {
    name: selectedAnniversary.value.name || '',
    anniversaryDate: selectedAnniversary.value.anniversaryDate || '',
    isRecurring: selectedAnniversary.value.isRecurring ?? true,
    remindDays: selectedAnniversary.value.remindDays ?? 1,
    description: selectedAnniversary.value.description || ''
  }
  // 记录正在编辑的 ID
  editingAnniversaryId.value = selectedAnniversary.value.id
  showUpdateAnniversaryPopup.value = true
}

// 详情弹窗 -> 删除按钮
async function deleteFromDetail() {
  if (!selectedAnniversary.value) return
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除「${selectedAnniversary.value.name}」吗？`
    })
    await deleteAnniversary(selectedAnniversary.value.id)
    showAnniversaryDetailPopup.value = false
    selectedAnniversary.value = null
    await loadAnniversaries()
    showToast('已删除')
  } catch {
    // 用户取消
  }
}

// 更新弹窗表单
const editingAnniversaryId = ref<number | null>(null)
const editAnniversary = ref({
  name: '',
  anniversaryDate: '',
  isRecurring: true,
  remindDays: 1,
  description: ''
})

// 确认更新
async function confirmUpdateAnniversary() {
  if (!editingAnniversaryId.value) return
  if (!editAnniversary.value.name.trim() || !editAnniversary.value.anniversaryDate) {
    showToast('请填写完整信息')
    return
  }

  try {
    await updateAnniversary(editingAnniversaryId.value, {
      name: editAnniversary.value.name.trim(),
      anniversaryDate: editAnniversary.value.anniversaryDate,
      isRecurring: editAnniversary.value.isRecurring,
      remindDays: parseInt(editAnniversary.value.remindDays?.toString() || '1'),
      description: editAnniversary.value.description?.trim() || ''
    })
    showUpdateAnniversaryPopup.value = false
    editingAnniversaryId.value = null
    await loadAnniversaries()
    showToast('更新成功')
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || '更新失败'
    showToast(msg)
  }
}

// 纪念日列表项选择（更新/删除）
function onAnniversaryMore(item: Anniversary) {
  selectedAnniversary.value = item
  selectedAnniversaryIndex.value = anniversaries.value.findIndex(a => a.id === item.id)
  showAnniversaryActions.value = true
}

function onAnniversaryActionSelect(action: { name: string; value: string }) {
  if (action.value === 'update') {
    editFromDetail()
  } else if (action.value === 'delete') {
    deleteFromDetail()
  }
}

// 显示消息通知设置弹窗 - 从后端加载
async function showNotificationSettings() {
  showNotificationPopup.value = true
  try {
    const data = await getNotificationSettings()
    notificationSettings.value = data
  } catch {
    // 加载失败使用默认值
  }
}

// 任何一个开关变化，立即将完整状态发送到后端
let notificationSaveTimer: ReturnType<typeof setTimeout> | null = null
async function onNotificationSettingChange() {
  if (notificationSaveTimer) {
    clearTimeout(notificationSaveTimer)
  }
  // 防抖，避免频繁请求
  notificationSaveTimer = setTimeout(async () => {
    try {
      await updateNotificationSettings(notificationSettings.value)
      showToast('设置已保存')
    } catch (error: any) {
      const msg = error?.response?.data?.message || error?.message || '保存失败'
      showToast(msg)
    }
  }, 300)
}

async function generateQRCode() {
  try {
    qrCodeDataUrl.value = await QRCode.toDataURL(shareUrl, {
      width: 200,
      margin: 2,
      color: { dark: '#333', light: '#fff' }
    })
  } catch {
    showToast('生成二维码失败')
  }
}

function showShare() {
  showSharePopup.value = true
  if (!qrCodeDataUrl.value) {
    generateQRCode()
  }
}

async function copyShareUrl() {
  try {
    await navigator.clipboard.writeText(shareUrl)
    showToast('链接已复制')
  } catch {
    showToast('复制失败，请手动复制')
  }
}

onMounted(() => {
  loadUserInfo()
  loadStats()
  cancelDailyRefresh = scheduleDailyRefresh(loadAnniversaries)
})

onBeforeUnmount(() => {
  cancelDailyRefresh?.()
})

// 支持从首页「纪念日提醒」跳转过来时直接打开纪念日入口（/profile?open=anniversary）
// Profile 在 App.vue 的 keep-alive 缓存中，onActivated 在首次挂载与每次回到该页时都会触发
onActivated(() => {
  refreshVipInfo()
  handleAnniversaryEntry()
})

/**
 * VIP 由顾问收款后异步开通，回到本页时刷新一次，
 * 避免 keep-alive 缓存导致「VIP 会员」入口状态滞后
 */
async function refreshVipInfo() {
  try {
    const data = await getUserInfo()
    userStore.setUserInfo(data)
  } catch (error) {
    console.error('刷新用户信息失败:', error)
  }
}

async function handleAnniversaryEntry() {
  if (route.query.open !== 'anniversary') return
  const id = Number(route.query.id)
  // 清掉 query，避免下次进入个人页时重复弹窗
  router.replace({ path: '/profile' })
  // 首页提醒卡片会带上对应纪念日 id：直接展示该条详情；无 id 时仍打开纪念日列表
  if (id > 0) {
    try {
      selectedAnniversary.value = await getAnniversary(id)
      showAnniversaryDetailPopup.value = true
      return
    } catch (error: any) {
      showToast(error?.response?.data?.message || error?.message || '获取详情失败')
    }
  }
  await showAnniversarySettings()
}

async function loadUserInfo() {
  try {
    const data = await getUserInfo()
    userInfo.value = data
    userStore.setUserInfo(data)
    // 已绑定时加载伴侣照片和纪念日
    if (data.isBound) {
      loadPartnerPhotos()
      loadAnniversaries()
    }
    // 加载绑定码
    loadBindCode()
  } catch (error) {
    console.error('加载用户信息失败:', error)
  }
}

async function loadStats() {
  try {
    stats.value = await getUserStats()
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

// 加载绑定码
async function loadBindCode() {
  try {
    const res = await getBindCode()
    bindCode.value = res.bindCode || ''
  } catch (error) {
    console.error('加载绑定码失败:', error)
  }
}

function goToMap() {
  router.push({ path: '/home', hash: '#map' })
}

// 生成绑定码（只能生成一次）
async function generateBindCode() {
  // 如果已经生成过，直接返回
  if (bindCode.value) {
    showToast('您已生成过绑定码')
    return
  }

  generatingCode.value = true
  try {
    const res = await apiGenerateBindCode()
    bindCode.value = res.bindCode

    showToast('绑定码已生成')
  } catch (error: any) {
    console.error('生成绑定码失败:', error)
    const msg = error?.response?.data?.message || error?.message || '生成失败，请重试'
    showToast(msg)
  } finally {
    generatingCode.value = false
  }
}

// 复制绑定码
function copyBindCode() {
  if (!bindCode.value) return
  navigator.clipboard.writeText(bindCode.value).then(() => {
    showToast('绑定码已复制')
  }).catch(() => {
    showToast('复制失败，请手动复制')
  })
}

// 复制邀请文案（已生成绑定码且未绑定时可用）
function invitePartner() {
  if (!bindCode.value) return
  const nickname = userInfo.value?.nickname?.trim() || '您的好友'
  const text = [
    `【LoveOfUs】${nickname}邀请您共建情侣相册`,
    `邀请码：${bindCode.value}（永久有效）`,
    '',
    '打开链接注册或绑定伴侣时输入邀请码，即可与 TA 一起记录每一刻，珍藏一辈子 ❤️',
    shareUrl
  ].join('\n')
  navigator.clipboard.writeText(text).then(() => {
    showToast('邀请内容已复制，快分享给伴侣吧')
  }).catch(() => {
    showToast('复制失败，请手动复制')
  })
}

// 加载伴侣照片（用于单图展示）
async function loadPartnerPhotos() {
  try {
    const data = await getTimelinePhotos({ page: 1, size: 20 })
    partnerPhotos.value = data.list || []
    currentPhotoIndex.value = 0
    console.log('伴侣照片加载完成，共', partnerPhotos.value.length, '张')
  } catch (error) {
    console.error('加载伴侣照片失败:', error)
    partnerPhotos.value = []
  }
}

// 上一张/下一张
function nextPhoto() {
  if (validPartnerPhotos.value.length <= 1) return
  currentPhotoIndex.value = (currentPhotoIndex.value + 1) % validPartnerPhotos.value.length
}

function prevPhoto() {
  if (validPartnerPhotos.value.length <= 1) return
  currentPhotoIndex.value = (currentPhotoIndex.value - 1 + validPartnerPhotos.value.length) % validPartnerPhotos.value.length
}

// 点击预览照片
function handlePhotoClick() {
  if (validPartnerPhotos.value.length > 0) {
    const photo = validPartnerPhotos.value[currentPhotoIndex.value]
    if (!photo) return
    const images = validPartnerPhotos.value.map(p => p.storagePath!).filter(Boolean) as string[]
    const startIndex = validPartnerPhotos.value.indexOf(photo)
    showImagePreview({
      images,
      startPosition: startIndex >= 0 ? startIndex : 0,
      closeable: true
    })
  } else {
    // 无照片时预览伴侣头像
    const avatarUrl = userInfo.value?.partner?.avatarUrl
    if (avatarUrl) {
      showImagePreview({
        images: [avatarUrl],
        closeable: true
      })
    }
  }
}

// 显示解除绑定确认
function showUnbindConfirm() {
  showConfirmDialog({
    title: '解除绑定',
    message: '确定要解除与伴侣的绑定关系吗？此操作不可恢复。'
  }).then(async () => {
    try {
      await unbindPartner()
      bindCode.value = ''
      showToast('已解除绑定')
      await loadUserInfo()
    } catch (error: any) {
      const msg = error?.response?.data?.message || error?.message || '解除绑定失败'
      showToast(msg)
    }
  }).catch(() => {
    // 取消
  })
}

// 确认输入伴侣绑定码
async function confirmBind() {
  const code = bindInputCode.value.trim()
  if (!code) {
    showToast('请输入绑定码')
    return
  }
  bindingPartner.value = true
  try {
    await bindPartner(code)
    showToast('绑定成功')
    showBindInput.value = false
    bindInputCode.value = ''
    // 重新加载用户信息
    await loadUserInfo()
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || '绑定失败'
    showToast(msg)
  } finally {
    bindingPartner.value = false
  }
}

// 确认注销账户
function showDeleteAccountConfirm() {
  showConfirmDialog({
    title: '注销账户',
    message: '确定要注销账户吗？此操作将删除所有数据且不可恢复！',
    confirmButtonText: '确认注销',
    confirmButtonColor: '#ee0a24'
  }).then(() => {
    performDeleteAccount()
  }).catch(() => {
    // 取消
  })
}

async function performDeleteAccount() {
  try {
    await deleteAccount()
    showToast('账户已注销')
    userStore.logout()
    router.push('/')
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || '注销失败'
    showToast(msg)
  }
}

// 显示关于 LoveOfUs 信息
function showAbout() {
  showDialog({
    title: '关于 LoveOfUs',
    message: `LoveOfUs v1.0.0

一款专为情侣和夫妻打造的甜蜜回忆地图，记录你们的每一次出行、每一张照片和每一个美好瞬间。

主要功能
• 照片上传与自动定位
• AI 智能分析与标签
• 足迹地图展示
• 时光相册整理
• 数据备份与导出

愿你们的爱情，如地图上的每一个坐标，被永远铭记。`,
    confirmButtonText: '知道了'
  })
}

// 性别显示标签
function genderLabel(gender?: number | string) {
  const map: Record<string, string> = {
    '0': '保密',
    '1': '男',
    '2': '女',
    'male': '男',
    'female': '女',
    'other': '保密'
  }
  return map[String(gender)] || '保密'
}

// 性别徽标样式类
function genderBadgeClass(gender?: number | string) {
  const map: Record<string, string> = {
    '1': 'male',
    '2': 'female',
    'male': 'male',
    'female': 'female'
  }
  return map[String(gender)] || 'other'
}

// 性别图标（SVG 字符串，避免额外依赖）
function genderIcon(gender?: number | string) {
  const g = String(gender)
  if (g === '1' || g === 'male') {
    // 男性 ♂
    return '<svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor"><path d="M14.5 3.5l5 5-1.4 1.4-2.1-2.1V14h-2V7.8L11.9 9.9a5 5 0 1 1-1.4-1.4L14.5 4.5zM9 12a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/></svg>'
  }
  if (g === '2' || g === 'female') {
    // 女性 ♀
    return '<svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor"><path d="M12 3a5 5 0 0 0-4 8.2V14H6v2h2v3h2v-3h2v3h2v-3h2v-2h-2v-2.8A5 5 0 0 0 12 3zm-3 5a3 3 0 1 1 6 0 3 3 0 0 1-6 0z"/></svg>'
  }
  // 保密 / 未知 / 未设置
  return '<svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16zm-1-13h2v6h-2V7zm0 8h2v2h-2v-2z"/></svg>'
}

// 性别描述（用于伴侣行）
function partnerGenderDesc(gender?: number | string) {
  const g = String(gender)
  if (g === '1' || g === 'male') return '他 / 男朋友'
  if (g === '2' || g === 'female') return '她 / 女朋友'
  return 'TA / 性别保密'
}

// 性别 ActionSheet 控制
const showMyGenderSheet = ref(false)
const myGenderActions = ref<{ name: string; subname?: string; disabled?: boolean }[]>([])
const myGenderSheetDesc = ref('')

const showPartnerGenderSheet = ref(false)
const partnerGenderActions = ref<{ name: string; subname?: string; disabled?: boolean }[]>([])
const partnerGenderSheetDesc = ref('')

// 点击自己的性别
function onClickMyGender() {
  const g = userInfo.value?.gender
  if (!g) {
    showConfirmDialog({
      title: '性别未设置',
      message: '你还没设置性别，去「账户设置」完善一下吧～',
      confirmButtonText: '去设置',
      cancelButtonText: '稍后'
    }).then(() => router.push('/account-settings'))
      .catch(() => {})
    return
  }
  myGenderSheetDesc.value = `${genderLabel(g)} · ${genderSubDesc(g, false)}`
  myGenderActions.value = [
    { name: `当前性别：${genderLabel(g)}`, subname: genderSubDesc(g, false), disabled: true },
    { name: '去修改性别', subname: '账户设置中可修改' }
  ]
  showMyGenderSheet.value = true
}

// 自己的 ActionSheet 选项点击
function onMyGenderSelect(_action: { name: string }, index: number) {
  if (index === 1) {
    showMyGenderSheet.value = false
    router.push('/account-settings')
  }
}

// 点击伴侣的性别
function onClickPartnerGender() {
  const partner = userInfo.value?.partner
  if (!partner) return
  const g = partner.gender
  partnerGenderSheetDesc.value = `${genderLabel(g)} · ${genderSubDesc(g, true)}`
  partnerGenderActions.value = [
    { name: `${partner.nickname}：${genderLabel(g)}`, subname: genderSubDesc(g, true), disabled: true }
  ]
  showPartnerGenderSheet.value = true
}

// 性别的副描述
function genderSubDesc(g: any, isPartner: boolean) {
  const who = isPartner ? 'TA' : '你'
  const map: Record<string, string> = {
    '1': `${who}是男生 / 男朋友`,
    '2': `${who}是女生 / 女朋友`,
    'male': `${who}是男生 / 男朋友`,
    'female': `${who}是女生 / 女朋友`
  }
  return map[String(g)] || `${who}暂未设置性别 / 保密`
}

function logout() {
  showConfirmDialog({
    title: '确认退出',
    message: '确定要退出登录吗？'
  }).then(async () => {
    try {
      await authLogout()
    } catch (error) {
      console.error('退出登录接口调用失败:', error)
    }
    // 无论接口成功或失败，都清除本地状态
    userStore.logout()
    router.push('/')
  })
}
</script>

<style scoped lang="scss">
.profile-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 140px;
}

.profile-content {
  padding: 16px;
  padding-bottom: 30px;
}

.user-card {
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  border-radius: $radius-lg;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #fff;
  margin-bottom: 16px;

  .user-info {
    h3 {
      font-size: 20px;
      margin: 0 0 4px;
    }

    .partner-info {
      font-size: 14px;
      opacity: 0.9;
      margin: 0 0 8px;

      &.unbound {
        opacity: 0.6;
      }
    }

    .days-together {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 14px;

      .heart {
        animation: heartbeat 1.5s ease-in-out infinite;
      }
    }
  }
}

@keyframes heartbeat {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.2); }
}

// ============ 性别视觉与交互 ============

// 头像右下角的圆形性别装饰（高亮、动效）
.gender-decor {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.18);
  cursor: pointer;
  outline: none;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  font-size: 16px;
  line-height: 1;
  z-index: 2;

  &:hover { transform: scale(1.08); }
  &:active { transform: scale(0.94); }

  // 内部 SVG 颜色由背景决定，文字图标用纯色
  .gender-decor-icon {
    display: inline-flex;
    color: #fff;
    align-items: center;
    justify-content: center;
  }

  // 脉冲光环（保密时不显示）
  .gender-decor-pulse {
    position: absolute;
    inset: -4px;
    border-radius: 50%;
    pointer-events: none;
    opacity: 0;
    animation: genderPulse 2.2s ease-out infinite;
  }

  &.g-male {
    background: linear-gradient(135deg, #4f9bff 0%, #2b6cff 100%);
    .gender-decor-pulse { box-shadow: 0 0 0 0 rgba(43, 108, 255, 0.55); }
  }
  &.g-female {
    background: linear-gradient(135deg, #ff6fa5 0%, #ee0a6b 100%);
    .gender-decor-pulse { box-shadow: 0 0 0 0 rgba(238, 10, 107, 0.55); }
  }
  &.g-other {
    background: linear-gradient(135deg, #b8bec6 0%, #8e94a1 100%);
    color: #fff;
    .gender-decor-pulse { display: none; }
  }
  &.is-unknown { animation: genderShake 0.45s ease-out 1; }
}

@keyframes genderPulse {
  0% { box-shadow: 0 0 0 0 rgba(43, 108, 255, 0.55); opacity: 1; }
  70% { box-shadow: 0 0 0 12px rgba(43, 108, 255, 0); opacity: 0; }
  100% { box-shadow: 0 0 0 0 rgba(43, 108, 255, 0); opacity: 0; }
}
.gender-decor.g-female .gender-decor-pulse {
  animation-name: genderPulseFemale;
}
@keyframes genderPulseFemale {
  0% { box-shadow: 0 0 0 0 rgba(238, 10, 107, 0.55); opacity: 1; }
  70% { box-shadow: 0 0 0 12px rgba(238, 10, 107, 0); opacity: 0; }
  100% { box-shadow: 0 0 0 0 rgba(238, 10, 107, 0); opacity: 0; }
}
@keyframes genderShake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  50% { transform: translateX(3px); }
  75% { transform: translateX(-2px); }
}

// 昵称旁的胶囊式 chip（柔和、磨砂）
.gender-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  border-radius: 10px;
  vertical-align: middle;
  border: 1px solid transparent;
  cursor: pointer;
  user-select: none;
  outline: none;
  transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.2s ease;

  .chip-icon {
    display: inline-flex;
    align-items: center;
    font-size: 12px;
    line-height: 1;
  }

  &:hover { transform: translateY(-1px); box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08); }
  &:active { transform: scale(0.96); }
  &:focus-visible { box-shadow: 0 0 0 2px rgba(25, 137, 250, 0.35); }

  &.chip-male {
    color: #2b6cff;
    background: linear-gradient(135deg, #f0f6ff 0%, #dceaff 100%);
    border-color: rgba(43, 108, 255, 0.25);
  }
  &.chip-female {
    color: #ee0a6b;
    background: linear-gradient(135deg, #fff0f6 0%, #ffd9e7 100%);
    border-color: rgba(238, 10, 107, 0.25);
  }
  &.chip-other {
    color: #6b7280;
    background: linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%);
    border-color: rgba(107, 114, 128, 0.25);
  }
  &.chip-pulse {
    animation: chipBreathe 3.6s ease-in-out infinite;
  }
}

@keyframes chipBreathe {
  0%, 100% { box-shadow: 0 0 0 0 rgba(43, 108, 255, 0); }
  50% { box-shadow: 0 0 0 4px rgba(43, 108, 255, 0.08); }
}
.gender-chip.chip-female.chip-pulse {
  animation-name: chipBreatheFemale;
}
@keyframes chipBreatheFemale {
  0%, 100% { box-shadow: 0 0 0 0 rgba(238, 10, 107, 0); }
  50% { box-shadow: 0 0 0 4px rgba(238, 10, 107, 0.1); }
}

// 伴侣详情名旁的 chip 小一号
.partner-detail-name .gender-chip {
  font-size: 10px;
  padding: 1px 8px;
  line-height: 16px;
  .chip-icon { font-size: 10px; }
}

// 伴侣卡片里的性别描述行（带悬停）
.partner-detail-item.partner-gender-row {
  cursor: pointer;
  padding: 4px 6px;
  margin-left: -6px;
  border-radius: 6px;
  transition: background 0.15s ease;
  &:hover { background: rgba(0, 0, 0, 0.04); }
  &:active { background: rgba(0, 0, 0, 0.08); }
}

// 头像容器需要 relative 以便装饰定位
.user-avatar {
  position: relative;
}

.stats-card {
  background: #fff;
  border-radius: $radius-lg;
  padding: 20px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
  box-shadow: $shadow-sm;

  .stats-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;

    .stats-value {
      font-size: 24px;
      font-weight: 600;
      color: $primary-color;
    }

    .stats-label {
      font-size: 12px;
      color: $text-secondary;
    }
  }
}

.bind-code-section {
  margin-bottom: 16px;

  .bind-code-card {
    background: #fff;
    border-radius: $radius-lg;
    padding: 20px;
    box-shadow: $shadow-sm;

    &.bound {
      background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
    }

    .bind-code-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;

      .van-icon {
        font-size: 20px;
        color: $primary-color;
      }

      span {
        font-size: 16px;
        font-weight: 600;
        color: $text-primary;
      }
    }

    .bind-code-desc {
      font-size: 13px;
      color: $text-secondary;
      margin: 0 0 16px;
      line-height: 1.5;
    }

    .bind-code-display {
      display: flex;
      align-items: center;
      gap: 12px;

      .code-value {
        flex: 1;
        background: $bg-color;
        border-radius: $radius-md;
        padding: 12px 16px;
        font-size: 24px;
        font-weight: 600;
        color: $primary-color;
        text-align: center;
        letter-spacing: 4px;
        font-family: 'Courier New', monospace;
      }
    }

    .invite-btn {
      margin-top: 12px;
    }

    .partner-detail {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 12px 0 16px;
    }

    .partner-detail-info {
      flex: 1;
      min-width: 0;
    }

    .partner-detail-name {
      font-size: 16px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 6px;
    }

    .partner-detail-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: $text-secondary;
      margin-top: 4px;

      .van-icon {
        font-size: 14px;
        color: $text-tertiary;
      }

      span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }
}

.map-entry {
  background: #fff;
  border-radius: $radius-lg;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  box-shadow: $shadow-sm;
  cursor: pointer;

  .van-icon:first-child {
    font-size: 24px;
    color: $primary-color;
  }

  span {
    flex: 1;
    font-size: 16px;
    color: $text-primary;
  }

  .van-icon:last-child {
    color: $text-tertiary;
  }

  &:active {
    opacity: 0.8;
  }
}

// 一起做 双入口（约会 / 心愿）
.together-do-card {
  margin-top: 12px;
  background: linear-gradient(135deg, #fff5f5 0%, #fff0e6 100%);
  border-radius: $radius-lg;
  padding: 14px 16px;
  box-shadow: $shadow-sm;

  .td-title {
    font-size: 14px;
    font-weight: 600;
    color: $primary-color;
    margin-bottom: 10px;
  }

  .td-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  .td-item {
    display: flex;
    align-items: center;
    gap: 8px;
    background: #fff;
    border-radius: $radius-md;
    padding: 10px 12px;
    cursor: pointer;
    transition: transform 0.18s;

    &:active { transform: scale(0.98); }

    .td-icon {
      font-size: 20px;
      flex-shrink: 0;
    }

    .td-info {
      flex: 1;
      min-width: 0;

      .td-name {
        font-size: 13px;
        font-weight: 600;
        color: $text-primary;
        line-height: 1.2;
      }

      .td-desc {
        font-size: 11px;
        color: $text-tertiary;
        line-height: 1.3;
        margin-top: 2px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .van-icon {
      font-size: 14px;
      color: $text-tertiary;
    }
  }
}

.settings-group {
  margin-bottom: 16px;
}

.logout-btn {
  padding: 0 16px;

  .van-button {
    color: $primary-color;
    border-color: $primary-color;
  }
}

  .photo-hero-section {
    margin-bottom: 16px;
    border-radius: $radius-lg;
    overflow: hidden;
    box-shadow: $shadow-sm;
    background: #fff;

    .photo-hero-container {
      position: relative;
      width: 100%;
      height: 260px;
      background: #f5f5f5;
      display: flex;
      align-items: center;
    }

    .photo-hero-image {
      width: 100%;
      height: 100%;
      cursor: pointer;
      position: relative;

      .van-image {
        width: 100%;
        height: 100%;
      }
    }

    .photo-placeholder {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f5f5;

      &.photo-error {
        flex-direction: column;
        gap: 8px;
      }
    }

    // 导航按钮 - 圆形边框悬浮于图片之上
    .photo-nav-btn {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.9);
      border: 1px solid rgba(0, 0, 0, 0.1);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
      z-index: 10;

      &:active {
        transform: translateY(-50%) scale(0.95);
        background: rgba(255, 255, 255, 1);
      }

      .van-icon {
        color: $text-primary;
        font-size: 16px;
        font-weight: bold;
      }

      &.photo-nav-prev {
        left: 12px;
      }

      &.photo-nav-next {
        right: 12px;
      }
    }

    .photo-hero-indicator {
      position: absolute;
      bottom: 12px;
      right: 12px;
      padding: 3px 10px;
      border-radius: 10px;
      background: rgba(0, 0, 0, 0.4);
      color: #fff;
      font-size: 12px;
      line-height: 1.5;
    }

    .photo-hero-location {
      position: absolute;
      bottom: 12px;
      left: 12px;
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 3px 10px;
      border-radius: 10px;
      background: rgba(0, 0, 0, 0.4);
      color: #fff;
      font-size: 12px;
      line-height: 1.5;

      .van-icon {
        font-size: 12px;
      }
    }

    .photo-hero-empty-hint {
      position: absolute;
      bottom: 12px;
      left: 50%;
      transform: translateX(-50%);
      padding: 3px 12px;
      border-radius: 10px;
      background: rgba(0, 0, 0, 0.4);
      color: #fff;
      font-size: 12px;
      line-height: 1.5;
    }
  }

.delete-account-btn {
  text-align: center;
  padding: 16px 16px 0;

  span {
    font-size: 13px;
    color: $text-tertiary;
    cursor: pointer;

    &:active {
      color: #ee0a24;
    }
  }
}

.bind-code-actions {
  margin-top: 12px;
  text-align: center;

  .link {
    font-size: 13px;
    color: $primary-color;
    cursor: pointer;

    &:active {
      opacity: 0.7;
    }
  }
}

.bind-input-popup {
  padding: 24px 20px 20px;

  .popup-header {
    text-align: center;
    margin-bottom: 16px;

    h3 {
      margin: 0;
      font-size: 18px;
      color: $text-primary;
    }
  }

  .popup-actions {
    margin-top: 16px;
  }
}

.notification-popup {
  padding: 24px 0 20px;

  .popup-header {
    text-align: center;
    margin-bottom: 16px;
    padding: 0 20px;

    h3 {
      margin: 0;
      font-size: 18px;
      color: $text-primary;
    }
  }

  .notification-list {
    padding: 0 8px;

    .van-cell {
      padding: 12px 16px;

      .van-cell__title {
        font-size: 14px;
        color: $text-primary;
      }
    }
  }

  .popup-actions {
    padding: 16px 20px 0;
  }
}

// 纪念日区域样式
.anniversary-section {
  background: #fff;
  border-radius: $radius-lg;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: $shadow-sm;

  .anniversary-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;

    .anniversary-title {
      font-size: 16px;
      font-weight: 600;
      color: $text-primary;
    }
  }

  .anniversary-list {
    .anniversary-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 0;
      border-bottom: 1px solid $border-color;

      &:last-child {
        border-bottom: none;
      }

      .anniversary-info {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 4px;
        cursor: pointer;

        .anniversary-name {
          font-size: 15px;
          color: $text-primary;
          font-weight: 500;
        }

        .anniversary-date {
          font-size: 13px;
          color: $text-secondary;
        }

        .anniversary-desc {
          font-size: 12px;
          color: $text-tertiary;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          max-width: 180px;
        }
      }

      .anniversary-right {
        display: flex;
        align-items: center;
        gap: 8px;

        .anniversary-countdown {
          display: flex;
          align-items: baseline;
          gap: 4px;
          cursor: pointer;

          .countdown-days {
            font-size: 24px;
            font-weight: 600;
            color: $primary-color;
          }

          .countdown-text {
            font-size: 13px;
            color: $text-secondary;
          }
        }

        .anniversary-more {
          font-size: 18px;
          color: $text-tertiary;
          padding: 4px;
          cursor: pointer;

          &:active {
            color: $primary-color;
          }
        }
      }
    }
  }

  .anniversary-empty {
    text-align: center;
    padding: 20px 0;
    color: $text-tertiary;
    font-size: 14px;
  }
}

// 纪念日弹窗样式
.anniversary-popup {
  height: 100%;
  display: flex;
  flex-direction: column;

  .popup-header {
    text-align: center;
    padding: 16px 20px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
      color: $text-primary;
    }
  }

  .anniversary-form {
    padding: 16px 20px;

    .van-field {
      margin-bottom: 12px;
    }

    .van-cell {
      margin-bottom: 12px;
    }

    .van-button {
      margin-top: 8px;
    }

    .field-suffix {
      color: $text-secondary;
      font-size: 14px;
      margin-left: 4px;
    }
  }

  .anniversary-list-edit {
    flex: 1;
    overflow-y: auto;
    padding: 0 20px 20px;

    .van-divider {
      margin: 16px 0;
    }

    .anniversary-edit-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 15px;
      color: $text-primary;
      font-weight: 500;
    }

    .anniversary-edit-label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin-top: 4px;

      .description-text {
        font-size: 12px;
        color: $text-tertiary;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 200px;
      }

      .remind-text {
        font-size: 11px;
        color: $primary-color;
      }
    }

    .expired {
      color: $text-tertiary;
    }
  }
}

// 纪念日详情弹窗
.anniversary-detail-popup {
  height: 100%;
  display: flex;
  flex-direction: column;

  .popup-header {
    text-align: center;
    padding: 16px 20px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
      color: $text-primary;
      font-weight: 600;
    }
  }

  .detail-content {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 20px;

    // 倒计时卡片
    .detail-countdown-card {
      background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
      border-radius: $radius-lg;
      padding: 24px;
      text-align: center;
      color: #fff;
      box-shadow: 0 4px 12px rgba($primary-color, 0.3);

      .countdown-icon {
        font-size: 32px;
        margin-bottom: 8px;
      }

      .countdown-name {
        font-size: 18px;
        font-weight: 600;
        margin-bottom: 12px;
      }

      .countdown-number {
        margin-bottom: 8px;

        .countdown-value {
          font-size: 48px;
          font-weight: 700;
          line-height: 1;

          // 当天显示「今天」时缩小字号，避免文字过长
          &.today {
            font-size: 32px;
          }
        }

        .countdown-unit {
          font-size: 16px;
          margin-left: 4px;
          opacity: 0.9;
        }
      }

      .countdown-date {
        font-size: 14px;
        opacity: 0.85;
      }
    }

    // 信息列表
    .detail-info-list {
      background: #fff;
      border-radius: $radius-md;
      padding: 16px;
      box-shadow: $shadow-sm;

      .detail-info-row {
        display: flex;
        align-items: center;
        padding: 12px 0;
        border-bottom: 1px solid $border-color;

        &:last-child {
          border-bottom: none;
        }

        .detail-info-icon {
          font-size: 18px;
          color: $primary-color;
          margin-right: 12px;
          flex-shrink: 0;
        }

        .detail-info-label {
          font-size: 14px;
          color: $text-secondary;
          min-width: 80px;
        }

        .detail-info-value {
          flex: 1;
          text-align: right;
          font-size: 14px;
          color: $text-primary;

          &.description-value {
            text-align: left;
            margin-left: 12px;
            color: $text-secondary;
            line-height: 1.5;
          }
        }
      }
    }

    // 操作按钮
    .detail-actions {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding-top: 8px;

      .van-button {
        height: 44px;
        font-size: 15px;
        font-weight: 500;
        transition: all 0.2s;

        &:active {
          transform: scale(0.98);
        }
      }
    }
  }
}

.share-popup {
  padding: 28px 20px 24px;
  text-align: center;

  .share-slogan {
    font-size: 18px;
    font-weight: 600;
    color: $primary-color;
    margin-bottom: 20px;
  }

  .share-qr {
    width: 180px;
    height: 180px;
    margin: 0 auto 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid $border-color;
    border-radius: $radius-md;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      display: block;
    }
  }

  .share-url-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    background: $bg-color;
    border-radius: $radius-md;
    padding: 8px 12px;
    margin-bottom: 8px;

    .share-url {
      flex: 1;
      font-size: 13px;
      color: $text-primary;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .share-url-hint {
    font-size: 12px;
    color: $text-tertiary;
    margin: 0;
  }
}

.vip-tag {
  font-size: 12px;
  color: $text-tertiary;
}

.vip-tag-active {
  color: #c9a227;
  font-weight: 600;
}

.vip-level-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 8px;
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  border-radius: 10px;
  vertical-align: middle;
  border: 1px solid transparent;

  .vip-crown {
    font-size: 12px;
    line-height: 1;
  }

  &.vip-tag-gold {
    color: #8a6a12;
    background: linear-gradient(135deg, #fff8e1 0%, #ffe9a8 100%);
    border-color: rgba(201, 162, 39, 0.4);
  }

  &.vip-tag-gray {
    color: $text-tertiary;
    background: linear-gradient(135deg, #f5f5f5 0%, #ececec 100%);
    border-color: rgba(0, 0, 0, 0.06);
  }
}

// 伴侣卡片里的等级标签小一号
.partner-detail-name .vip-level-tag {
  font-size: 10px;
  padding: 1px 8px;
  line-height: 16px;

  .vip-crown {
    font-size: 10px;
  }
}

.vip-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 14px;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, #3d3226 0%, #6b5636 100%);
  color: $text-white;
  box-shadow: $shadow-md;
  cursor: pointer;
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.99);
  }

  .vip-entry-crown {
    font-size: 22px;
    line-height: 1;
  }

  .vip-entry-info {
    flex: 1;
    min-width: 0;
  }

  .vip-entry-title {
    font-size: 15px;
    font-weight: 600;
  }

  .vip-entry-sub {
    font-size: 11px;
    opacity: 0.8;
    margin-top: 2px;
  }

  .vip-entry-action {
    flex: none;
    font-size: 12px;
    font-weight: 600;
    color: #3d3226;
    background: linear-gradient(135deg, #ffe9a8 0%, #f5d173 100%);
    border-radius: 12px;
    padding: 5px 12px;
  }
}
</style>
