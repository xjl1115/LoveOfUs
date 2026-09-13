# LoveMap 后端项目安全边界表

**审计日期:** 2026-06-11  
**项目路径:** `LoveMap/`  
**技术栈:** Spring Boot + Spring Security + JWT + MyBatis + MySQL + Redis

---

## 一、接口安全边界总览

| 接口分类 | 基础路径 | 登录状态 | 权限设计 | 数据归属 |
|---------|---------|---------|---------|---------|
| 认证管理 | `/auth/**` | 部分需登录 | 公开/用户 | 用户级 |
| 用户管理 | `/user/**` | 需登录 | ROLE_USER | 用户级 |
| 相册管理 | `/albums/**` | 需登录 | ROLE_USER | 用户/组级 |
| 照片管理 | `/photos/**` | 需登录 | ROLE_USER | 用户/组级 |
| 导出管理 | `/exports/**` | 需登录 | ROLE_USER | 用户级 |

---

## 二、详细接口安全边界表

### 2.1 认证管理接口 (/auth)

| 接口 | 方法 | 登录状态 | 权限要求 | 密码规则 | 数据归属 | 风险类型 | 处理逻辑 | 验证方式 | SQL注入风险 |
|-----|------|---------|---------|---------|---------|---------|---------|---------|------------|
| `/auth/captcha/send` | POST | 无需登录 | 公开 | - | - | 频率限制 | 滑动窗口限流(10分钟5次) | Redis计数器 | **无** (参数使用`#{}`预编译) |
| `/auth/captcha/verify` | POST | 无需登录 | 公开 | - | - | 验证码暴力破解 | 一次性使用，验证后删除 | Redis存储比对 | **无** (参数使用`#{}`预编译) |
| `/auth/register` | POST | 无需登录 | 公开 | 长度≥6位 | 用户创建 | 重复注册/密码弱 | 邮箱唯一性校验+BCrypt加密 | 数据库唯一索引 | **无** (MyBatis `#{}`绑定) |
| `/auth/login` | POST | 无需登录 | 公开 | 密码比对 | 用户验证 | 暴力破解 | 限流+密码错误提示模糊化 | BCrypt.matches() | **无** (参数预编译) |
| `/auth/logout` | POST | **需登录** | ROLE_USER | - | 当前用户 | Token伪造 | Token黑名单机制 | Redis黑名单存储 | **无** |
| `/auth/token/refresh` | POST | 需登录(旧Token) | 公开 | - | Token续期 | Token刷新滥用 | 旧Token加入黑名单 | JWT验证+Redis | **无** |
| `/auth/bind-code` | GET | **需登录** | ROLE_USER | - | 当前用户 | - | 从Redis获取绑定码 | `@RequestAttribute`获取userId | **无** |
| `/auth/bind-code/generate` | POST | **需登录** | ROLE_USER | - | 当前用户 | - | 生成新绑定码存入Redis | `@RequestAttribute`获取userId | **无** |
| `/auth/password/reset` | POST | 无需登录 | 公开 | 新密码≥6位 | 目标用户 | 验证码绕过 | 验证码校验+密码加密 | 验证码验证+BCrypt | **无** |
| `/auth/bind` | POST | **需登录** | ROLE_USER | - | 当前用户 | 绑定码伪造 | 绑定码有效性校验 | `@RequestAttribute`获取userId | **无** |
| `/auth/unbind` | POST | **需登录** | ROLE_USER | - | 当前用户 | 未授权解绑 | 验证用户绑定状态 | `@RequestAttribute`获取userId | **无** |
| `/auth/account/delete` | POST | **需登录** | ROLE_USER | 需密码确认 | 当前用户 | 误删除/恶意删除 | 密码验证+软删除/清理 | 密码校验+UserId匹配 | **无** |

### 2.2 用户管理接口 (/user)

| 接口 | 方法 | 登录状态 | 权限要求 | 密码规则 | 数据归属 | 风险类型 | 处理逻辑 | 验证方式 | SQL注入风险 |
|-----|------|---------|---------|---------|---------|---------|---------|---------|------------|
| `/user/profile` | GET | **需登录** | ROLE_USER | - | 当前用户 | 越权访问 | 仅返回当前用户信息 | `@RequestAttribute`获取userId | **无** |
| `/user/profile` | PUT | **需登录** | ROLE_USER | 新密码≥6位(可选) | 当前用户 | 信息篡改/密码修改 | 字段长度校验+密码加密 | `@RequestAttribute`获取userId | **无** |
| `/user/stats` | GET | **需登录** | ROLE_USER | - | 当前用户 | 统计数据泄露 | 仅返回当前用户统计 | `@RequestAttribute`获取userId | **无** |
| `/user/avatar` | POST | **需登录** | ROLE_USER | - | 当前用户 | 文件上传风险 | 文件类型/大小校验 | `@RequestAttribute`获取userId | **无** |

### 2.3 相册管理接口 (/albums)

| 接口 | 方法 | 登录状态 | 权限要求 | 密码规则 | 数据归属 | 风险类型 | 处理逻辑 | 验证方式 | SQL注入风险 |
|-----|------|---------|---------|---------|---------|---------|---------|---------|------------|
| `/albums` | POST | **需登录** | ROLE_USER | - | 用户/组级 | - | 创建相册关联当前用户 | `@RequestAttribute`获取userId | **无** |
| `/albums` | GET | **需登录** | ROLE_USER | - | 用户/组级 | 越权查看 | 仅返回用户/组内相册 | `@RequestAttribute`获取userId | **无** |
| `/albums/{id}` | GET | **需登录** | ROLE_USER | - | 用户/组级 | IDOR越权 | 相册归属校验 | `@RequestAttribute`获取userId | **无** |
| `/albums/{albumId}/photos` | GET | **需登录** | ROLE_USER | - | 用户/组级 | IDOR越权 | 相册归属校验 | `@RequestAttribute`获取userId | **无** |
| `/albums/{id}` | PUT | **需登录** | ROLE_USER | - | 用户/组级 | IDOR越权/篡改 | 相册存在性+归属校验 | `@RequestAttribute`获取userId | **无** |
| `/albums/{id}` | DELETE | **需登录** | ROLE_USER | - | 用户/组级 | IDOR越权/误删除 | 相册归属校验+级联删除 | `@RequestAttribute`获取userId | **无** |
| `/albums/{albumId}/photos` | POST | **需登录** | ROLE_USER | - | 用户/组级 | IDOR越权 | 相册归属校验 | `@RequestAttribute`获取userId | **无** |
| `/albums/{albumId}/photos/{photoId}` | DELETE | **需登录** | ROLE_USER | - | 用户/组级 | IDOR越权 | 相册归属校验 | `@RequestAttribute`获取userId | **无** |

### 2.4 照片管理接口 (/photos)

| 接口 | 方法 | 登录状态 | 权限要求 | 密码规则 | 数据归属 | 风险类型 | 处理逻辑 | 验证方式 | SQL注入风险 |
|-----|------|---------|---------|---------|---------|---------|---------|---------|------------|
| `/photos/upload` | POST | **需登录** | ROLE_USER | - | 用户/组级 | 文件上传风险/越权 | 文件校验+相册归属校验 | `@RequestAttribute`获取userId | **无** |
| `/photos/timeline` | GET | **需登录** | ROLE_USER | - | 用户/组级 | 越权查看 | 仅返回用户/组内照片 | `@RequestAttribute`获取userId | **无** (参数使用`#{}`) |
| `/photos/{id}` | DELETE | **需登录** | ROLE_USER | - | 用户/组级 | IDOR越权/误删除 | 照片归属校验 | `@RequestAttribute`获取userId | **无** |
| `/photos/{id}` | GET | **需登录** | ROLE_USER | - | 用户/组级 | IDOR越权 | 照片归属校验 | `@RequestAttribute`获取userId | **无** |

### 2.5 导出管理接口 (/exports)

| 接口 | 方法 | 登录状态 | 权限要求 | 密码规则 | 数据归属 | 风险类型 | 处理逻辑 | 验证方式 | SQL注入风险 |
|-----|------|---------|---------|---------|---------|---------|---------|---------|------------|
| `/exports` | POST | **需登录** | ROLE_USER | - | 用户级 | 资源耗尽 | 分布式锁防并发+异步处理 | `@RequestAttribute`获取userId | **无** |
| `/exports/{id}/status` | GET | **需登录** | ROLE_USER | - | 用户级 | IDOR越权 | 任务归属校验 | `@RequestAttribute`获取userId+任务ID匹配 | **无** |
| `/exports` | GET | **需登录** | ROLE_USER | - | 用户级 | 越权查看 | 仅返回当前用户导出历史 | `@RequestAttribute`获取userId | **无** |
| `/exports/{id}/download` | GET | **需登录** | ROLE_USER | - | 用户级 | IDOR越权/文件泄露 | 任务归属校验+文件路径校验 | `@RequestAttribute`获取userId+任务ID匹配 | **无** |
| `/exports/{id}/cancel` | POST | **需登录** | ROLE_USER | - | 用户级 | IDOR越权 | 任务归属校验 | `@RequestAttribute`获取userId+任务ID匹配 | **无** |

---

## 三、安全机制详细说明

### 3.1 登录状态验证机制

| 组件 | 实现方式 | 说明 |
|-----|---------|------|
| JWT认证过滤器 | `JwtAuthFilter` | 拦截所有请求，验证Authorization头 |
| Token提取 | `TokenUtils.extractToken()` | 从Bearer Token中提取纯Token |
| Token验证 | `TokenUtils.validateToken()` | 验证Token有效性+黑名单检查 |
| 用户ID注入 | `request.setAttribute("userId", userId)` | 通过`@RequestAttribute`在Controller获取 |
| 公开路径 | `EXCLUDED_PATHS` | `/auth/captcha/**`, `/auth/register`, `/auth/login`, `/auth/password/reset`, `/auth/token/refresh` |

### 3.2 权限设计

| 权限角色 | 说明 | 适用范围 |
|---------|------|---------|
| `ROLE_USER` | 普通用户权限 | 所有业务接口 |
| 公开(permitAll) | 无需认证 | 登录/注册/验证码等 |

### 3.3 密码规则

| 场景 | 规则 | 实现方式 |
|-----|------|---------|
| 注册密码 | 长度≥6位 | `@Size(min = 6)`注解校验 |
| 密码加密 | BCrypt | `BCryptPasswordEncoder.encode()` |
| 密码比对 | 安全比对 | `BCryptPasswordEncoder.matches()` |
| 密码修改 | 需旧密码验证 | 业务逻辑校验 |

### 3.4 数据归属校验机制

| 数据类型 | 归属字段 | 校验方式 |
|---------|---------|---------|
| 用户数据 | `user_id` | `@RequestAttribute`获取当前用户ID比对 |
| 相册数据 | `user_id` / `group_id` | 查询时带用户ID/组ID条件过滤 |
| 照片数据 | `user_id` / `group_id` | 查询时带用户ID/组ID条件过滤 |
| 导出任务 | `user_id` | 任务记录中存储用户ID，查询时比对 |

### 3.5 SQL注入防护分析

| Mapper文件 | 参数绑定方式 | 风险分析 |
|-----------|-------------|---------|
| `UserMapper.xml` | 全部使用`#{}` | **无SQL注入风险** - 预编译参数绑定 |
| `AlbumMapper.xml` | 全部使用`#{}` | **无SQL注入风险** - 预编译参数绑定 |
| `PhotoMapper.xml` | 全部使用`#{}` | **无SQL注入风险** - 预编译参数绑定 |
| `PhotoAlbumMapper.xml` | 全部使用`#{}` | **无SQL注入风险** - 预编译参数绑定 |
| `ExportMapper.xml` | 全部使用`#{}` | **无SQL注入风险** - 预编译参数绑定 |
| `GroupMapper.xml` | 全部使用`#{}` | **无SQL注入风险** - 预编译参数绑定 |

**结论**: 所有MyBatis Mapper文件均使用`#{}`预编译参数绑定，**不存在SQL注入风险**。

---

## 四、风险类型汇总

| 风险类型 | 涉及接口 | 防护措施 | 风险等级 |
|---------|---------|---------|---------|
| **频率限制绕过** | `/auth/captcha/send` | Redis滑动窗口限流 | 低 |
| **验证码暴力破解** | `/auth/captcha/verify` | 一次性使用+过期时间 | 低 |
| **密码暴力破解** | `/auth/login` | 限流+错误提示模糊化 | 中 |
| **Token伪造/盗用** | 所有需登录接口 | JWT签名验证+黑名单机制 | 低 |
| **越权访问(IDOR)** | `/albums/{id}`, `/photos/{id}`等 | 数据归属校验 | 中 |
| **文件上传风险** | `/photos/upload`, `/user/avatar` | 文件类型/大小校验 | 中 |
| **资源耗尽** | `/exports` | 分布式锁+异步处理 | 低 |
| **SQL注入** | 所有数据库操作接口 | MyBatis预编译`#{}` | **无风险** |

---

## 五、安全建议

### 5.1 已实施的良好实践

1. **JWT认证**: 使用无状态Token，支持Token刷新和黑名单机制
2. **密码加密**: 使用BCrypt强哈希算法存储密码
3. **参数校验**: 使用JSR-303注解进行入参校验
4. **SQL注入防护**: 全项目使用MyBatis预编译参数绑定
5. **数据归属校验**: 所有业务操作均校验数据归属权限
6. **限流机制**: 验证码发送使用滑动窗口限流

### 5.2 建议改进项

| 建议 | 优先级 | 说明 |
|-----|-------|------|
| 登录失败次数限制 | 高 | 增加连续登录失败锁定机制 |
| 敏感操作二次验证 | 中 | 修改密码/注销账号需验证码确认 |
| 操作日志审计 | 中 | 记录关键操作的审计日志 |
| 接口响应脱敏 | 中 | 避免返回敏感字段(如密码哈希) |
| 文件上传类型白名单 | 中 | 严格限制上传文件MIME类型 |

---

**报告生成时间:** 2026-06-11  
**审计范围:** LoveMap后端全部Controller接口及数据访问层  
**审计结论:** 整体安全设计良好，无SQL注入风险，权限控制基本完善
