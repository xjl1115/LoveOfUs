# LoveMap 前端项目安全审计报告

**审计日期:** 2026-06-11  
**项目路径:** `loveofus-frontend/`  
**技术栈:** Vue 3 + TypeScript + Vite + Pinia + Vant

---

## 一、严重级别问题 (Critical)

### 1.1 localStorage 存储敏感 Token

**严重程度:** 🔴 **CRITICAL**  
**位置:** `src/stores/user.ts` 第 6-13 行

**问题代码:**
```typescript
const token = ref<string | null>(localStorage.getItem('token'))
const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
const tokenExpireAt = ref<number | null>(
  (() => {
    const stored = localStorage.getItem('tokenExpireAt')
    return stored ? Number(stored) : null
  })()
)
```

**风险分析:**
- **XSS 攻击风险**: localStorage 可被 JavaScript 读取，若存在 XSS 漏洞，攻击者可窃取 Token
- **无 HttpOnly 保护**: 无法防止 JavaScript 访问
- **持久化存储**: Token 长期存储在浏览器中

**修复建议:**
```typescript
// 方案1: 使用 sessionStorage (会话级存储)
const token = ref<string | null>(sessionStorage.getItem('token'))

// 方案2: 使用内存存储 (页面刷新后需重新登录)
const token = ref<string | null>(null)

// 方案3: 配合后端使用 httpOnly Cookie (推荐)
// 后端设置: Set-Cookie: token=xxx; HttpOnly; Secure; SameSite=Strict
```

---

### 1.2 调试日志泄露敏感信息

**严重程度:** 🔴 **CRITICAL**  
**位置:** `src/router/index.ts` 第 95-103 行

**问题代码:**
```typescript
console.log('[Router] 导航到:', to.path)
console.log('[Router] isLoggedIn:', userStore.isLoggedIn)
console.log('[Router] store token:', userStore.token ? '存在' : '不存在')
console.log('[Router] storage token:', storageToken.token ? '存在' : '不存在')
console.log('[Router] tokenExpireAt:', userStore.tokenExpireAt)
console.log('[Router] 当前时间:', Date.now())
console.log('[Router] 是否过期:', userStore.tokenExpireAt ? Date.now() >= userStore.tokenExpireAt : '无过期时间')
```

**风险分析:**
- 生产环境 console.log 会暴露用户认证状态
- 攻击者可通过浏览器 DevTools 查看敏感信息
- 可能泄露 Token 存在性和过期时间

**修复建议:**
```typescript
// 使用环境变量控制日志输出
const isDev = import.meta.env.DEV

if (isDev) {
  console.log('[Router] 导航到:', to.path)
  // ... 其他日志
}

// 或使用日志级别控制
const logger = {
  debug: (...args: any[]) => import.meta.env.DEV && console.log(...args),
  info: (...args: any[]) => import.meta.env.DEV && console.info(...args),
  warn: console.warn,
  error: console.error
}
```

---

## 二、高危级别问题 (High)

### 2.1 Token 刷新竞态条件

**严重程度:** 🟠 **HIGH**  
**位置:** `src/stores/user.ts` 第 84-105 行

**问题代码:**
```typescript
async function doRefreshToken(): Promise<boolean> {
  if (!refreshToken.value) return false
  try {
    const response = await fetch('/api/auth/token/refresh', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token.value}`,
        'X-Internal-Skip-Refresh': 'true'
      }
    })
    // ...
  }
}
```

**风险分析:**
- 多个并发请求可能同时触发 Token 刷新
- 第一个请求消耗 Refresh Token 后，后续请求会失败
- 可能导致用户被强制登出

**修复建议:**
```typescript
// src/utils/request.ts
let refreshPromise: Promise<boolean> | null = null

axiosInstance.interceptors.request.use(
  async (config) => {
    const userStore = useUserStore()

    if (config.headers?.get(HEADER_SKIP_REFRESH)) {
      return config
    }

    if (userStore.shouldAutoRefresh) {
      // 使用单例模式确保只有一个刷新请求
      if (!refreshPromise) {
        refreshPromise = userStore.doRefreshToken().finally(() => { 
          refreshPromise = null 
        })
      }
      const ok = await refreshPromise
      if (!ok) {
        return config
      }
    }

    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }

    return config
  },
  (error) => Promise.reject(error)
)
```

---

### 2.2 密码哈希算法安全性不足

**严重程度:** 🟠 **HIGH**  
**位置:** `src/utils/crypto.ts`

**问题分析:**
- 使用纯 JavaScript 实现的 SHA-256
- 缺乏盐值 (Salt)，易受彩虹表攻击
- 单次哈希，无法抵御 GPU 暴力破解

**修复建议:**
```typescript
// 使用 bcryptjs 替代自定义 SHA-256
import bcrypt from 'bcryptjs'

export async function hashPassword(password: string): Promise<string> {
  // 使用 10-12 轮 salt
  const salt = await bcrypt.genSalt(10)
  return bcrypt.hash(password, salt)
}

// 注意: 如果后端使用 bcrypt，前端不应再哈希，直接传输明文
// 使用 HTTPS 保护传输安全
```

---

### 2.3 路由守卫逻辑缺陷

**严重程度:** 🟠 **HIGH**  
**位置:** `src/router/index.ts` 第 109-159 行

**问题代码:**
```typescript
// 有 Token 但无用户信息时，向后端验证 Token 有效性
if (!userStore.userInfo) {
  try {
    const userInfo = await getUserInfo()
    if (userInfo) {
      userStore.setUserInfo(userInfo)
    }
  } catch {
    // 网络波动或服务暂不可用时不清空 Token
    console.warn('获取用户信息失败，Token 仍存在')
  }
}
```

**风险分析:**
- 获取用户信息失败时不清理 Token
- 可能导致使用无效 Token 继续访问
- 错误处理不完善

**修复建议:**
```typescript
if (!userStore.userInfo) {
  try {
    const userInfo = await getUserInfo()
    if (userInfo) {
      userStore.setUserInfo(userInfo)
    }
  } catch (error: any) {
    // 区分网络错误和认证错误
    if (error.response?.status === 401) {
      // Token 确实无效，执行登出
      userStore.logout()
      return { name: 'Login' }
    }
    // 网络错误，允许继续但标记状态
    console.warn('网络波动，使用缓存状态')
  }
}
```

---

## 三、中等级别问题 (Medium)

### 3.1 输入验证不充分

**严重程度:** 🟡 **MEDIUM**  
**位置:** `src/views/Login.vue` 等表单页面

**问题分析:**
- 前端验证仅检查长度，缺乏格式验证
- 手机号/邮箱格式未严格校验
- 密码复杂度要求较低（仅 6 位）

**修复建议:**
```typescript
// 添加严格的输入验证
const validateEmail = (email: string): boolean => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

const validatePhone = (phone: string): boolean => {
  const phoneRegex = /^1[3-9]\d{9}$/
  return phoneRegex.test(phone)
}

const validatePassword = (password: string): boolean => {
  // 至少 8 位，包含大小写字母和数字
  const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,}$/
  return passwordRegex.test(password)
}
```

---

### 3.2 文件上传限制不足

**严重程度:** 🟡 **MEDIUM**  
**位置:** `src/views/Upload.vue` 第 10-16 行

**问题代码:**
```vue
<van-uploader
  v-model="fileList"
  multiple
  :max-count="20"
  :max-size="10 * 1024 * 1024"
  :preview-image="true"
  @oversize="onOversize"
/>
```

**风险分析:**
- 仅限制文件大小，未限制文件类型
- 可能上传恶意文件（如包含脚本的图片）
- 缺乏文件名安全检查

**修复建议:**
```typescript
// 添加文件类型白名单
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

const beforeRead = (file: File): boolean => {
  // 验证文件类型
  if (!ALLOWED_TYPES.includes(file.type)) {
    showToast('仅支持 JPG、PNG、GIF、WebP 格式')
    return false
  }
  
  // 验证文件大小
  if (file.size > MAX_FILE_SIZE) {
    showToast('文件大小不能超过 10MB')
    return false
  }
  
  // 验证文件名（防止路径遍历）
  const sanitizedName = file.name.replace(/[^a-zA-Z0-9.-]/g, '_')
  if (sanitizedName !== file.name) {
    showToast('文件名包含非法字符')
    return false
  }
  
  return true
}
```

---

### 3.3 敏感信息硬编码风险

**严重程度:** 🟡 **MEDIUM**  
**位置:** `vite.config.ts` 第 36-44 行

**问题代码:**
```typescript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    },
    '/ai': {
      target: 'http://localhost:8000',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/ai/, '')
    }
  }
}
```

**风险分析:**
- 开发环境代理配置暴露后端地址
- 生产环境配置可能残留敏感信息

**修复建议:**
```typescript
// 使用环境变量
server: {
  port: Number(import.meta.env.VITE_DEV_PORT) || 3000,
  proxy: {
    '/api': {
      target: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
      changeOrigin: true
    }
  }
}

// .env.development
VITE_API_BASE_URL=http://localhost:8080
VITE_AI_BASE_URL=http://localhost:8000

// .env.production (不提交到版本控制)
VITE_API_BASE_URL=https://api.production.com
```

---

## 四、低级别问题 (Low)

### 4.1 依赖版本管理

**严重程度:** 🟢 **LOW**  
**位置:** `package.json`

**问题分析:**
- 部分依赖使用 `^` 版本范围
- 可能引入不兼容的次要版本更新

**修复建议:**
```json
{
  "dependencies": {
    "axios": "1.6.8",
    "vue": "3.4.21"
  },
  "devDependencies": {
    "typescript": "5.4.5"
  }
}

// 或使用 package-lock.json 锁定版本
// 定期运行 npm audit 检查漏洞
```

---

### 4.2 缺乏 Content Security Policy

**严重程度:** 🟢 **LOW**  
**位置:** `index.html`

**修复建议:**
```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <!-- 添加 CSP 头 -->
    <meta http-equiv="Content-Security-Policy" 
          content="default-src 'self'; 
                   script-src 'self' 'unsafe-inline'; 
                   style-src 'self' 'unsafe-inline';
                   img-src 'self' data: https:;
                   connect-src 'self' https://api.yourdomain.com;
                   font-src 'self';
                   object-src 'none';
                   frame-ancestors 'none';
                   base-uri 'self';
                   form-action 'self';" />
    <title>LoveMap</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

---

## 五、安全加固建议

### 5.1 立即执行 (Critical)

1. **移除生产环境调试日志**
   ```bash
   # 添加构建时日志清理
   npm install terser-webpack-plugin --save-dev
   ```

2. **实现请求签名机制**
   ```typescript
   // 添加时间戳和签名防止重放攻击
   const generateSignature = (data: object, timestamp: number): string => {
     const secret = import.meta.env.VITE_API_SECRET
     const payload = JSON.stringify(data) + timestamp + secret
     return CryptoJS.HmacSHA256(payload, secret).toString()
   }
   ```

3. **添加请求频率限制**
   ```typescript
   // 实现简单的客户端限流
   class RateLimiter {
     private requests: Map<string, number[]> = new Map()
     
     canMakeRequest(key: string, maxRequests: number = 10, windowMs: number = 60000): boolean {
       const now = Date.now()
       const timestamps = this.requests.get(key) || []
       
       // 清理过期请求
       const validTimestamps = timestamps.filter(t => now - t < windowMs)
       
       if (validTimestamps.length >= maxRequests) {
         return false
       }
       
       validTimestamps.push(now)
       this.requests.set(key, validTimestamps)
       return true
     }
   }
   ```

---

### 5.2 短期执行 (High Priority)

1. **实现自动登出机制**
   ```typescript
   // 添加空闲检测
   let idleTimer: NodeJS.Timeout
   
   const resetIdleTimer = () => {
     clearTimeout(idleTimer)
     idleTimer = setTimeout(() => {
       userStore.logout()
       router.push('/login')
       showToast('长时间未操作，已自动登出')
     }, 30 * 60 * 1000) // 30分钟
   }
   
   window.addEventListener('mousemove', resetIdleTimer)
   window.addEventListener('keydown', resetIdleTimer)
   ```

2. **添加操作确认机制**
   ```typescript
   // 敏感操作二次确认
   const confirmSensitiveAction = async (action: string): Promise<boolean> => {
     return await showConfirmDialog({
       title: '确认操作',
       message: `您确定要执行 "${action}" 吗？此操作不可撤销。`,
       confirmButtonText: '确认',
       cancelButtonText: '取消'
     })
   }
   ```

---

### 5.3 长期执行 (Medium Priority)

1. **实现端到端加密**
   - 对敏感数据传输使用 AES-256 加密
   - 密钥定期轮换

2. **添加安全监控**
   - 集成 Sentry 等错误监控
   - 记录安全事件日志

3. **定期安全审计**
   - 每季度运行 `npm audit`
   - 使用 Snyk 等工具扫描依赖漏洞

---

## 六、总结

| 级别 | 数量 | 主要问题 |
|------|------|----------|
| 🔴 Critical | 2 | localStorage 存储 Token、调试日志泄露 |
| 🟠 High | 3 | Token 刷新竞态、密码哈希、路由守卫缺陷 |
| 🟡 Medium | 3 | 输入验证、文件上传、硬编码配置 |
| 🟢 Low | 2 | 依赖版本、CSP 缺失 |

**建议优先级:**
1. 立即修复 Critical 级别问题
2. 1周内修复 High 级别问题
3. 1个月内完成 Medium 级别改进
4. 下个版本迭代处理 Low 级别问题

---

**审计人员:** AI Security Assistant  
**报告版本:** 1.0  
**下次审计:** 建议 3 个月后进行复审计
