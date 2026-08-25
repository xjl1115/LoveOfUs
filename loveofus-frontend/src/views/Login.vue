<template>
  <div class="login-page">
    <div class="login-container">
      <!-- Logo 区域 -->
      <div class="logo-section">
        <div class="logo-icon">❤️</div>
        <h1 class="logo-title">LoveMap</h1>
        <p class="logo-subtitle">记录我们的每一刻</p>
        <p class="logo-desc">时间会走，爱会停留</p>
      </div>

      <!-- 登录表单 -->
      <van-form class="login-form" @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="form.account"
            name="account"
            placeholder="手机号 / 邮箱"
            :rules="[
              { required: true, message: '请输入手机号或邮箱' },
              { validator: (val: string) => validateAccount(val.trim()).valid, message: '请输入有效的手机号或邮箱' }
            ]"
          >
            <template #left-icon>
              <van-icon name="user-o" />
            </template>
          </van-field>
          <!-- 密码输入区 / 验证码输入区 -->
          <van-field
            v-if="!isCodeLogin"
            v-model="form.password"
            :type="showLoginPassword ? 'text' : 'password'"
            name="password"
            placeholder="密码"
            :rules="[
              { required: true, message: '请输入密码' },
              { validator: validatePassword, message: '密码至少8位，包含大小写字母和数字' }
            ]"
          >
            <template #left-icon>
              <van-icon name="lock" />
            </template>
            <template #right-icon>
              <van-icon
                :name="showLoginPassword ? 'eye-o' : 'closed-eye'"
                @click="showLoginPassword = !showLoginPassword"
              />
            </template>
          </van-field>
          <!-- 验证码输入区 -->
          <van-field
            v-else
            v-model="form.code"
            name="code"
            placeholder="验证码"
            :rules="[{ required: true, message: '请输入验证码' }]"
          >
            <template #left-icon>
              <van-icon name="shield-o" />
            </template>
            <template #button>
              <van-button
                size="small"
                type="primary"
                :disabled="codeSending || codeCountdown > 0"
                @click="sendCode"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
              </van-button>
            </template>
          </van-field>
        </van-cell-group>

        <!-- 隐私协议复选框 -->
        <div class="agreement-section">
          <van-checkbox v-model="agreementChecked" shape="square" icon-size="16px">
            <template #default>
              <span class="agreement-text">
                我已阅读并同意
                <span class="agreement-link" @click.stop="showPrivacy = true">《隐私协议》</span>
                和
                <span class="agreement-link" @click.stop="showTerms = true">《用户协议》</span>
              </span>
            </template>
          </van-checkbox>
        </div>

        <div class="form-actions">
          <van-button
            round
            block
            type="primary"
            native-type="submit"
            :loading="loading"
            class="login-btn"
          >
            登 录
          </van-button>
        </div>

        <div class="form-links">
          <span class="link" @click="showRegister = true">没有账号？立即注册</span>
          <span class="link-divider">|</span>
          <span class="link" @click="showForgotPassword = true">忘记密码？</span>
        </div>
        <div class="switch-login-mode">
          <span v-if="!isCodeLogin" class="link" @click="isCodeLogin = true">使用验证码登录</span>
          <span v-else class="link" @click="isCodeLogin = false">使用密码登录</span>
        </div>
      </van-form>
    </div>

    <!-- 注册弹窗 -->
    <van-popup v-model:show="showRegister" round position="bottom" :style="{ height: '70%' }">
      <div class="register-popup">
        <div class="popup-header">
          <h3>注册账号</h3>
          <van-icon name="cross" @click="showRegister = false" />
        </div>
        <van-form @submit="onRegister">
          <van-cell-group inset>
            <van-field
            v-model="registerForm.nickname"
            name="nickname"
            placeholder="昵称"
            :rules="[
              { required: true, message: '请输入昵称' },
              { validator: validateNickname, message: '昵称2-20位，支持中文、字母、数字、下划线' }
            ]"
          />
            <van-field
              v-model="registerForm.account"
              name="account"
              placeholder="手机号 / 邮箱"
              :rules="[{ required: true, message: '请输入手机号或邮箱' }]"
            />
            <van-field
              v-model="registerForm.code"
              name="code"
              placeholder="验证码"
              :rules="[{ required: true, message: '请输入验证码' }]"
            >
              <template #button>
                <van-button
                  size="small"
                  type="primary"
                  :disabled="registerCodeSending || registerCodeCountdown > 0"
                  @click="sendRegisterCode"
                >
                  {{ registerCodeCountdown > 0 ? `${registerCodeCountdown}s` : '发送验证码' }}
                </van-button>
              </template>
            </van-field>
            <van-field
              v-model="registerForm.password"
              :type="showRegisterPassword ? 'text' : 'password'"
              name="password"
              placeholder="密码"
              :rules="[
                { required: true, message: '请输入密码' },
                { validator: validatePassword, message: '密码长度不能少于6位' }
              ]"
            >
              <template #right-icon>
                <van-icon
                  :name="showRegisterPassword ? 'eye-o' : 'closed-eye'"
                  @click="showRegisterPassword = !showRegisterPassword"
                />
              </template>
            </van-field>
            <van-field
              v-model="registerForm.confirmPassword"
              :type="showRegisterConfirmPassword ? 'text' : 'password'"
              name="confirmPassword"
              placeholder="确认密码"
              :rules="[
                { required: true, message: '请确认密码' },
                { validator: validateConfirmPassword, message: '两次密码不一致' }
              ]"
            >
              <template #right-icon>
                <van-icon
                  :name="showRegisterConfirmPassword ? 'eye-o' : 'closed-eye'"
                  @click="showRegisterConfirmPassword = !showRegisterConfirmPassword"
                />
              </template>
            </van-field>
            <van-field
              v-model="registerForm.bindCode"
              name="bindCode"
              placeholder="情侣绑定码（可选）"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="registerLoading">
              注 册
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 忘记密码弹窗 -->
    <van-popup v-model:show="showForgotPassword" round :style="{ width: '85%', maxWidth: '400px' }">
      <div class="forgot-password-popup">
        <div class="popup-header">
          <h3>忘记密码</h3>
          <van-icon name="cross" @click="showForgotPassword = false" />
        </div>
        <div class="forgot-password-options">
          <div
            class="option-item"
            :class="{ active: forgotPasswordType === 'code' }"
            @click="forgotPasswordType = 'code'"
          >
            <van-icon name="shield-o" />
            <span>验证码登录</span>
          </div>
          <div
            class="option-item"
            :class="{ active: forgotPasswordType === 'reset' }"
            @click="forgotPasswordType = 'reset'"
          >
            <van-icon name="lock" />
            <span>密码找回</span>
          </div>
        </div>
        <div class="popup-content">
          <p v-if="forgotPasswordType === 'code'" class="option-desc">
            选择验证码登录，将使用邮箱验证码直接登录
          </p>
          <p v-else class="option-desc">
            选择密码找回，将通过邮箱验证码重置您的密码
          </p>
        </div>
        <div class="popup-actions">
          <van-button round block type="primary" @click="handleForgotPasswordAction">
            {{ forgotPasswordType === 'code' ? '使用验证码登录' : '重置密码' }}
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 重置密码弹窗 -->
    <van-popup v-model:show="showResetPassword" round :style="{ width: '85%', maxWidth: '400px' }">
      <div class="reset-password-popup">
        <div class="popup-header">
          <h3>重置密码</h3>
          <van-icon name="cross" @click="showResetPassword = false" />
        </div>
        <van-form @submit="onResetPassword">
          <van-cell-group inset>
            <van-field
              v-model="resetForm.account"
              name="account"
              placeholder="手机号 / 邮箱"
              :rules="[{ required: true, message: '请输入手机号或邮箱' }]"
            >
              <template #left-icon>
                <van-icon name="user-o" />
              </template>
            </van-field>
            <van-field
              v-model="resetForm.code"
              name="code"
              placeholder="验证码"
              :rules="[{ required: true, message: '请输入验证码' }]"
            >
              <template #left-icon>
                <van-icon name="shield-o" />
              </template>
              <template #button>
                <van-button
                  size="small"
                  type="primary"
                  :disabled="resetCodeSending || resetCodeCountdown > 0"
                  @click="sendResetCode"
                >
                  {{ resetCodeCountdown > 0 ? `${resetCodeCountdown}s` : '发送验证码' }}
                </van-button>
              </template>
            </van-field>
            <van-field
              v-model="resetForm.newPassword"
              :type="showResetNewPassword ? 'text' : 'password'"
              name="newPassword"
              placeholder="新密码"
              :rules="[
                { required: true, message: '请输入新密码' },
                { validator: validatePassword, message: '密码长度不能少于6位' }
              ]"
            >
              <template #left-icon>
                <van-icon name="lock" />
              </template>
              <template #right-icon>
                <van-icon
                  :name="showResetNewPassword ? 'eye-o' : 'closed-eye'"
                  @click="showResetNewPassword = !showResetNewPassword"
                />
              </template>
            </van-field>
            <van-field
              v-model="resetForm.confirmPassword"
              :type="showResetConfirmPassword ? 'text' : 'password'"
              name="confirmPassword"
              placeholder="确认新密码"
              :rules="[
                { required: true, message: '请确认新密码' },
                { validator: validateResetConfirmPassword, message: '两次密码不一致' }
              ]"
            >
              <template #left-icon>
                <van-icon name="lock" />
              </template>
              <template #right-icon>
                <van-icon
                  :name="showResetConfirmPassword ? 'eye-o' : 'closed-eye'"
                  @click="showResetConfirmPassword = !showResetConfirmPassword"
                />
              </template>
            </van-field>
          </van-cell-group>
          <div class="popup-actions">
            <van-button round block type="primary" native-type="submit" :loading="resetLoading">
              确认重置
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 隐私协议弹窗 -->
    <van-popup v-model:show="showPrivacy" round :style="{ width: '90%', height: '70%' }">
      <div class="agreement-popup">
        <div class="popup-header">
          <h3>隐私协议</h3>
          <van-icon name="cross" @click="showPrivacy = false" />
        </div>
        <div class="agreement-content">
          <p>这里是隐私协议内容...</p>
        </div>
      </div>
    </van-popup>

    <!-- 用户协议弹窗 -->
    <van-popup v-model:show="showTerms" round :style="{ width: '90%', height: '70%' }">
      <div class="agreement-popup">
        <div class="popup-header">
          <h3>用户协议</h3>
          <van-icon name="cross" @click="showTerms = false" />
        </div>
        <div class="agreement-content">
          <p>这里是用户协议内容...</p>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { login, register } from '@/api/user'
import { sendCaptcha, resetPassword } from '@/api/auth'
import { hashPassword } from '@/utils/crypto'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const showRegister = ref(false)
const registerLoading = ref(false)

// 隐私协议相关
const agreementChecked = ref(false)
const showPrivacy = ref(false)
const showTerms = ref(false)

// 验证码登录相关
const isCodeLogin = ref(false)
const codeSending = ref(false)
const codeCountdown = ref(0)

// 忘记密码相关
const showForgotPassword = ref(false)
const forgotPasswordType = ref<'code' | 'reset'>('code')
const showResetPassword = ref(false)
const resetCodeSending = ref(false)
const resetCodeCountdown = ref(0)
const resetLoading = ref(false)

// 密码显示/隐藏
const showLoginPassword = ref(false)
const showRegisterPassword = ref(false)
const showRegisterConfirmPassword = ref(false)
const showResetNewPassword = ref(false)
const showResetConfirmPassword = ref(false)

const form = reactive({
  account: '',
  password: '',
  code: ''
})

const registerForm = reactive({
  nickname: '',
  account: '',
  code: '',
  password: '',
  confirmPassword: '',
  bindCode: ''
})

// 注册验证码相关
const registerCodeSending = ref(false)
const registerCodeCountdown = ref(0)

const resetForm = reactive({
  account: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

// ==================== 输入验证工具函数 ====================

/** 验证邮箱格式 */
const validateEmail = (email: string): boolean => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/** 验证手机号格式（中国大陆） */
const validatePhone = (phone: string): boolean => {
  const phoneRegex = /^1[3-9]\d{9}$/
  return phoneRegex.test(phone)
}

/** 验证账号类型（邮箱或手机号） */
const validateAccount = (account: string): { valid: boolean; type: 'email' | 'phone' | 'invalid' } => {
  if (validateEmail(account)) {
    return { valid: true, type: 'email' }
  }
  if (validatePhone(account)) {
    return { valid: true, type: 'phone' }
  }
  return { valid: false, type: 'invalid' }
}

/** 验证密码复杂度
 * 要求：至少6位
 */
const validatePassword = (val: string): boolean => {
  // 至少6位
  return val.length >= 6
}

/** 验证密码复杂度（带错误信息） */
const validatePasswordWithMessage = (val: string): string | boolean => {
  if (val.length < 6) {
    return '密码长度至少6位'
  }
  return true
}

/** 验证确认密码 */
const validateConfirmPassword = () => {
  return registerForm.password === registerForm.confirmPassword
}

/** 验证重置密码确认 */
const validateResetConfirmPassword = () => {
  return resetForm.newPassword === resetForm.confirmPassword
}

/** 验证昵称 */
const validateNickname = (nickname: string): boolean => {
  // 2-20位，支持中文、字母、数字、下划线
  const nicknameRegex = /^[\u4e00-\u9fa5a-zA-Z0-9_]{2,20}$/
  return nicknameRegex.test(nickname)
}

/** 验证验证码 */
const validateCaptcha = (code: string): boolean => {
  // 6位数字
  const captchaRegex = /^\d{6}$/
  return captchaRegex.test(code)
}

/** 验证绑定码 */
const validateBindCode = (code: string): boolean => {
  if (!code) return true // 可选字段
  // 6-20位字母数字
  const bindCodeRegex = /^[a-zA-Z0-9]{6,20}$/
  return bindCodeRegex.test(code)
}

const onSubmit = async () => {
  if (!agreementChecked.value) {
    showToast('请先同意隐私协议和用户协议')
    return
  }

  // 验证账号格式
  const accountValidation = validateAccount(form.account.trim())
  if (!accountValidation.valid) {
    showToast('请输入有效的手机号或邮箱')
    return
  }

  // 验证码登录时验证验证码格式
  if (isCodeLogin.value && !validateCaptcha(form.code)) {
    showToast('请输入6位数字验证码')
    return
  }

  // 密码登录时验证密码格式
  if (!isCodeLogin.value) {
    const passwordCheck = validatePasswordWithMessage(form.password)
    if (typeof passwordCheck === 'string') {
      showToast(passwordCheck)
      return
    }
  }

  loading.value = true
  try {
    const loginData: any = {
      account: form.account.trim(),
      loginType: isCodeLogin.value ? 'captcha' : 'password'
    }

    if (isCodeLogin.value) {
      loginData.captcha = form.code
    } else {
      // 密码进行 SHA-256 加密后传输
      loginData.password = await hashPassword(form.password)
    }

    const data = await login(loginData)
    userStore.setTokenWithExpire(data.token, data.expiresIn)
    if (data.refreshToken) {
      userStore.setRefreshToken(data.refreshToken)
    }
    userStore.setUserInfo(data.userInfo)
    showToast('登录成功')
    router.push('/home')
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}

const onRegister = async () => {
  // 验证昵称
  if (!validateNickname(registerForm.nickname.trim())) {
    showToast('昵称必须为2-20位，支持中文、字母、数字、下划线')
    return
  }

  // 验证账号格式
  const accountValidation = validateAccount(registerForm.account.trim())
  if (!accountValidation.valid) {
    showToast('请输入有效的手机号或邮箱')
    return
  }

  // 验证验证码格式
  if (!validateCaptcha(registerForm.code)) {
    showToast('请输入6位数字验证码')
    return
  }

  // 验证密码复杂度
  const passwordCheck = validatePasswordWithMessage(registerForm.password)
  if (typeof passwordCheck === 'string') {
    showToast(passwordCheck)
    return
  }

  // 验证确认密码
  if (!validateConfirmPassword()) {
    showToast('两次输入的密码不一致')
    return
  }

  // 验证绑定码（如果填写了）
  if (registerForm.bindCode && !validateBindCode(registerForm.bindCode.trim())) {
    showToast('绑定码必须为6-20位字母数字组合')
    return
  }

  registerLoading.value = true
  try {
    const isEmail = registerForm.account.includes('@')
    // 密码和确认密码均进行 SHA-256 加密后传输
    const encryptedPassword = await hashPassword(registerForm.password)
    const encryptedConfirmPassword = await hashPassword(registerForm.confirmPassword)
    await register({
      nickname: registerForm.nickname.trim(),
      [isEmail ? 'email' : 'phone']: registerForm.account.trim(),
      password: encryptedPassword,
      confirmPassword: encryptedConfirmPassword,
      captcha: registerForm.code,
      partnerCode: registerForm.bindCode?.trim() || undefined
    })
    showToast('注册成功，请登录')
    // 将注册的账号填入登录表单
    form.account = registerForm.account
    showRegister.value = false
  } catch (error) {
    console.error('注册失败:', error)
  } finally {
    registerLoading.value = false
  }
}

// 发送登录验证码
const sendCode = async () => {
  if (!form.account.trim()) {
    showToast('请先输入手机号或邮箱')
    return
  }
  codeSending.value = true
  try {
    const isEmail = form.account.includes('@')
    await sendCaptcha({
      target: form.account.trim(),
      channel: isEmail ? 'email' : 'sms',
      type: 'login'
    })
    showToast('验证码已发送')
    codeCountdown.value = 60
    const timer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    console.error('发送验证码失败:', error)
    showToast('发送验证码失败，请稍后重试')
  } finally {
    codeSending.value = false
  }
}

// 发送注册验证码
const sendRegisterCode = async () => {
  if (!registerForm.account.trim()) {
    showToast('请先输入手机号或邮箱')
    return
  }
  registerCodeSending.value = true
  try {
    const isEmail = registerForm.account.includes('@')
    await sendCaptcha({
      target: registerForm.account.trim(),
      channel: isEmail ? 'email' : 'sms',
      type: 'register'
    })
    showToast('验证码已发送')
    registerCodeCountdown.value = 60
    const timer = setInterval(() => {
      registerCodeCountdown.value--
      if (registerCodeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    console.error('发送验证码失败:', error)
    showToast('发送验证码失败，请稍后重试')
  } finally {
    registerCodeSending.value = false
  }
}

// 处理忘记密码选项
const handleForgotPasswordAction = () => {
  showForgotPassword.value = false
  if (forgotPasswordType.value === 'code') {
    isCodeLogin.value = true
    showToast('已切换至验证码登录模式')
  } else {
    showResetPassword.value = true
  }
}

// 发送重置密码验证码
const sendResetCode = async () => {
  if (!resetForm.account.trim()) {
    showToast('请先输入手机号或邮箱')
    return
  }
  resetCodeSending.value = true
  try {
    const isEmail = resetForm.account.includes('@')
    await sendCaptcha({
      target: resetForm.account.trim(),
      channel: isEmail ? 'email' : 'sms',
      type: 'reset_password'
    })
    showToast('验证码已发送')
    resetCodeCountdown.value = 60
    const timer = setInterval(() => {
      resetCodeCountdown.value--
      if (resetCodeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    console.error('发送验证码失败:', error)
    showToast('发送验证码失败，请稍后重试')
  } finally {
    resetCodeSending.value = false
  }
}

// 重置密码
const onResetPassword = async () => {
  if (!resetForm.newPassword || !resetForm.confirmPassword) {
    showToast('请输入密码')
    return
  }
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    showToast('两次密码不一致')
    return
  }
  if (resetForm.newPassword.length < 6 || resetForm.newPassword.length > 20) {
    showToast('密码长度需在6-20位之间')
    return
  }
  if (!form.account) {
    showToast('请先输入手机号或邮箱')
    return
  }
  resetLoading.value = true
  try {
    const isEmail = form.account.includes('@')
    const encryptedPassword = await hashPassword(resetForm.newPassword)
    const encryptedConfirmPassword = await hashPassword(resetForm.confirmPassword)
    await resetPassword({
      target: form.account,
      channel: isEmail ? 'email' : 'sms',
      captcha: resetForm.code,
      newPassword: encryptedPassword,
      confirmPassword: encryptedConfirmPassword
    })
    showToast('密码重置成功')
    showResetPassword.value = false
    resetForm.code = ''
    resetForm.newPassword = ''
    resetForm.confirmPassword = ''
  } catch (error) {
    console.error('重置密码失败:', error)
  } finally {
    resetLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #FFF7EA 0%, #FFE4D6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-container {
  width: 100%;
  max-width: 400px;
}

.logo-section {
  text-align: center;
  margin-bottom: 40px;

  .logo-icon {
    font-size: 64px;
    margin-bottom: 16px;
    animation: heartbeat 1.5s ease-in-out infinite;
  }

  .logo-title {
    font-size: 28px;
    font-weight: 600;
    color: $primary-color;
    margin-bottom: 8px;
  }

  .logo-subtitle {
    font-size: 18px;
    color: $text-primary;
    margin-bottom: 4px;
  }

  .logo-desc {
    font-size: 14px;
    color: $text-tertiary;
  }
}

@keyframes heartbeat {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.login-form {
  .agreement-section {
    margin: 12px 16px;
    display: flex;
    align-items: flex-start;

    .agreement-text {
      font-size: 12px;
      color: $text-secondary;
      line-height: 1.5;
    }

    .agreement-link {
      color: $primary-color;
      cursor: pointer;
    }
  }

  .form-actions {
    margin: 24px 16px 16px;
  }

  .login-btn {
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    border: none;
  }

  .form-links {
    text-align: center;
    margin-top: 16px;

    .link {
      color: $primary-color;
      font-size: 14px;
      cursor: pointer;
    }

    .link-divider {
      color: $primary-light;
      margin: 0 12px;
      font-weight: 300;
      opacity: 0.6;
      user-select: none;
    }
  }

  .switch-login-mode {
    text-align: center;
    margin-top: 12px;

    .link {
      color: $primary-color;
      font-size: 13px;
      cursor: pointer;
    }
  }
}

.register-popup {
  height: 100%;
  background: $bg-white;

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
      cursor: pointer;
    }
  }

  .form-actions {
    margin: 24px 16px;
  }
}

.forgot-password-popup {
  background: $bg-white;
  padding-bottom: 20px;

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
      cursor: pointer;
    }
  }

  .forgot-password-options {
    display: flex;
    padding: 20px 16px;
    gap: 12px;

    .option-item {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 16px;
      border: 1px solid $border-color;
      border-radius: $radius-md;
      cursor: pointer;
      transition: all 0.3s;

      .van-icon {
        font-size: 24px;
        color: $text-secondary;
      }

      span {
        font-size: 14px;
        color: $text-secondary;
      }

      &.active {
        border-color: $primary-color;
        background: rgba($primary-color, 0.05);

        .van-icon,
        span {
          color: $primary-color;
        }
      }
    }
  }

  .popup-content {
    padding: 0 16px 16px;

    .option-desc {
      font-size: 13px;
      color: $text-tertiary;
      text-align: center;
      margin: 0;
    }
  }

  .popup-actions {
    padding: 0 16px;
  }
}

.reset-password-popup {
  background: $bg-white;
  padding-bottom: 20px;

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
      cursor: pointer;
    }
  }

  .popup-actions {
    padding: 20px 16px 0;
  }
}

.agreement-popup {
  height: 100%;
  background: $bg-white;
  display: flex;
  flex-direction: column;

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid $border-color;
    flex-shrink: 0;

    h3 {
      margin: 0;
      font-size: 18px;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
      cursor: pointer;
    }
  }

  .agreement-content {
    flex: 1;
    padding: 16px;
    overflow-y: auto;

    p {
      font-size: 14px;
      color: $text-primary;
      line-height: 1.6;
    }
  }
}

@media (min-width: 768px) {
  .login-container {
    background: $bg-white;
    padding: 40px;
    border-radius: $radius-xl;
    box-shadow: $shadow-lg;
  }
}
</style>
