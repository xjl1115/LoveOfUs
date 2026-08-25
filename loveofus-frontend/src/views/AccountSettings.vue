<template>
  <div class="account-settings-page">
    <van-nav-bar title="账户设置" left-arrow @click-left="$router.back()" fixed placeholder />

    <div class="settings-content">
      <!-- 头像设置 -->
      <div class="avatar-section" @click="changeAvatar">
        <span class="label">头像</span>
        <div class="avatar-value">
          <van-image round width="60" height="60" :src="form.avatarUrl || defaultAvatar" />
          <van-icon name="arrow" class="arrow-icon" />
        </div>
        <input
          ref="fileInputRef"
          type="file"
          accept="image/*"
          style="display: none"
          @change="onAvatarFileChange"
        />
      </div>

      <!-- 基本信息表单 -->
      <van-form @submit="onSubmit" ref="formRef">
        <van-cell-group inset>
          <van-field
            v-model="form.nickname"
            name="nickname"
            label="昵称"
            :placeholder="form.nickname ? '当前：' + form.nickname : '请输入昵称'"
            :rules="[{ required: true, message: '昵称不能为空' }, { validator: validateNickname, message: '2-20个字符' }]"
          />
          <van-field
            v-model="form.phone"
            name="phone"
            label="手机号"
            :placeholder="form.phone ? '当前：' + form.phone : '请输入手机号'"
            :rules="[{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }]"
          />
          <van-field
            v-model="form.email"
            name="email"
            label="邮箱"
            :placeholder="form.email ? '当前：' + form.email : '请输入邮箱'"
            :rules="[{ pattern: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/, message: '请输入正确的邮箱' }]"
          />
        </van-cell-group>

        <!-- 修改密码区域 -->
        <div class="section-title">修改密码（可选）</div>
        <van-cell-group inset>
          <van-field
            v-model="passwordForm.oldPassword"
            :type="showOldPassword ? 'text' : 'password'"
            name="oldPassword"
            label="当前密码"
            placeholder="输入当前密码以修改"
          >
            <template #right-icon>
              <van-icon
                :name="showOldPassword ? 'eye-o' : 'closed-eye'"
                @click="showOldPassword = !showOldPassword"
              />
            </template>
          </van-field>
          <van-field
            v-model="passwordForm.newPassword"
            :type="showNewPassword ? 'text' : 'password'"
            name="newPassword"
            label="新密码"
            placeholder="8-20位，需包含字母和数字"
            :rules="[{ validator: validatePassword }]"
          >
            <template #right-icon>
              <van-icon
                :name="showNewPassword ? 'eye-o' : 'closed-eye'"
                @click="showNewPassword = !showNewPassword"
              />
            </template>
          </van-field>
          <van-field
            v-model="passwordForm.confirmPassword"
            :type="showConfirmPassword ? 'text' : 'password'"
            name="confirmPassword"
            label="确认新密码"
            placeholder="再次输入新密码"
            :rules="[{ validator: validateConfirmPassword }]"
          >
            <template #right-icon>
              <van-icon
                :name="showConfirmPassword ? 'eye-o' : 'closed-eye'"
                @click="showConfirmPassword = !showConfirmPassword"
              />
            </template>
          </van-field>
        </van-cell-group>

        <div style="margin: 24px 16px">
          <van-button round block type="primary" native-type="submit" :loading="submitting">
            保存设置
          </van-button>
        </div>
      </van-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { showToast } from 'vant'
import { getUserInfo, updateUserInfo } from '@/api/user'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const defaultAvatar = 'https://img.yzcdn.cn/vant/cat.jpeg'
const fileInputRef = ref<HTMLInputElement>()
const formRef = ref()
const submitting = ref(false)

// 密码显示/隐藏
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

const form = reactive({
  nickname: '',
  phone: '',
  email: '',
  avatarUrl: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

onMounted(async () => {
  try {
    const data = await getUserInfo()
    form.nickname = data.nickname || ''
    form.phone = data.phone || ''
    form.email = data.email || ''
    form.avatarUrl = data.avatarUrl || ''
  } catch (error) {
    showToast('加载用户信息失败')
  }
})

function validateNickname(val: string) {
  return val.length >= 2 && val.length <= 20
}

function validatePassword(val: string) {
  if (!val) return true // 可选
  return /^(?=.*[a-zA-Z])(?=.*\d).{8,20}$/.test(val)
}

function validateConfirmPassword(val: string) {
  if (!passwordForm.newPassword && !val) return true
  return val === passwordForm.newPassword
}

function changeAvatar() {
  fileInputRef.value?.click()
}

function onAvatarFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 预览
  const reader = new FileReader()
  reader.onload = (e) => {
    form.avatarUrl = e.target?.result as string
  }
  reader.readAsDataURL(file)
}

async function onSubmit() {
  submitting.value = true
  try {
    // 1. 保存基本资料
    const profileData: any = {
      nickname: form.nickname,
      phone: form.phone,
      email: form.email
    }
    if (form.avatarUrl && form.avatarUrl.startsWith('data:')) {
      profileData.avatar = form.avatarUrl // base64 传给后端处理
    }
    await updateUserInfo(profileData)

    // 2. 如果需要修改密码
    if (passwordForm.oldPassword && passwordForm.newPassword) {
      await changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword,
        confirmPassword: passwordForm.confirmPassword
      })
      // 清空密码表单
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    }

    showToast('保存成功')
    // 刷新 store 中的用户信息
    const fresh = await getUserInfo()
    userStore.setUserInfo(fresh)
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || '保存失败'
    showToast(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.account-settings-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 40px;
}

.settings-content {
  padding: 16px;
}

.avatar-section {
  background: #fff;
  border-radius: $radius-lg;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  cursor: pointer;

  .label {
    font-size: 16px;
    color: $text-primary;
  }

  .avatar-value {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .arrow-icon {
    color: $text-tertiary;
  }
}

.section-title {
  font-size: 14px;
  color: $text-secondary;
  padding: 16px 16px 8px;
}
</style>
