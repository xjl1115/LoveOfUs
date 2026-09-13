# 后端（Spring Boot）生产级优化分析报告

> 生成时间：2026-06-28
> 项目：LoveOfUs - LoveMap 后端服务
> 技术栈：Spring Boot 4.0.6 + MyBatis + Redis + MySQL + JWT + 阿里云 OSS + SSE

---

## 一、安全问题（P0 - 紧急）

### 1.1 JWT 密钥硬编码
- **问题**：pplication.yml 中 jwt.secret 值为明文 bcdefghijklmnopqrstuvwxyz0123456789，未使用环境变量
- **风险**：密钥泄露可伪造任意用户 Token
- **建议**：
  - 改用环境变量 ${JWT_SECRET} 注入
  - 密钥长度至少 256 位（当前为 32 字符，仅满足最低要求）
  - 考虑使用 HS512 或 RS256 算法

### 1.2 数据库密码硬编码
- **问题**：pplication.yml 中 spring.datasource.password: 1115 明文存储
- **风险**：代码仓库泄露即暴露数据库
- **建议**：改用环境变量 ${DB_PASSWORD}，生产环境使用 Vault 或 K8s Secrets

### 1.3 邮件密码硬编码
- **问题**：pplication.yml 中 MAIL_PASSWORD 默认值明文存储
- **风险**：SMTP 凭证泄露可被滥用发垃圾邮件
- **建议**：完全依赖环境变量，不设默认值

### 1.4 Token 从 URL 参数获取
- **问题**：JwtAuthFilter.java:62 支持从 URL 参数 	oken 获取认证令牌
- **风险**：URL 中的 Token 会出现在浏览器历史、代理日志、Referer 头中
- **建议**：移除 URL 参数 Token 支持，仅保留 Authorization 头部

### 1.5 缺少速率限制中间件
- **问题**：验证码发送有速率限制，但登录、注册等接口无全局限流
- **风险**：暴力破解密码、注册轰炸
- **建议**：
  - 登录接口：同一 IP 每分钟最多 5 次
  - 注册接口：同一 IP 每小时最多 10 次
  - 使用 Sentinel 或 Redis 滑动窗口实现

### 1.6 缺少请求签名/防重放攻击
- **问题**：所有 API 调用仅依赖 JWT，无请求签名机制
- **风险**：Token 被截获后可重放
- **建议**：关键操作（修改密码、注销账号）增加二次验证

---

## 二、架构与代码质量（P1 - 高优先级）

### 2.1 Controller 层直接调用 Service，缺少 DTO 校验
- **问题**：部分 Controller 方法参数直接使用实体类而非 DTO，且缺少 @Validated 注解
- **建议**：统一使用 DTO + @Valid 校验，如 LoginDTO 已使用但部分接口未覆盖

### 2.2 密码修改接口缺失
- **问题**：前端 pi/auth.ts 定义了 changePassword 接口，但后端 AuthService 中无对应实现
- **影响**：前端调用将返回 404
- **建议**：补全 POST /auth/password/change 接口

### 2.3 用户注销接口路径不一致
- **问题**：前端 pi/user.ts 调用 /user/delete，后端 AuthController 注销接口在 /auth/account/delete
- **影响**：前端无法正确调用注销接口
- **建议**：统一接口路径

### 2.4 通知设置接口返回逻辑问题
- **问题**：UserServiceImpl.getNotificationSettings() 无论用户是否存在都返回成功，新建用户直接返回默认值而不写入数据库
- **建议**：首次访问时初始化通知设置为数据库记录

### 2.5 缓存策略不统一
- **问题**：
  - ServiceHelper.putToCache() 写入缓存时**不设置过期时间**
  - 部分缓存 key 永久存储，导致 Redis 内存无限增长
  - 删除照片时清理缓存使用 scan + unlink，但其他场景未保持一致性
- **建议**：
  - 所有缓存设置合理 TTL（如 30 分钟）
  - 使用 Cache-Aside 模式统一管理
  - 考虑引入 Caffeine 本地缓存减少 Redis 压力

### 2.6 事务边界过大
- **问题**：PhotoServiceImpl.uploadPhotos() 中 @Transactional 覆盖了 OSS 上传、异步任务、数据库插入等大量操作
- **风险**：长事务持有数据库连接，高并发下易导致连接池耗尽
- **建议**：将非数据库操作（OSS 上传）移出事务边界，仅数据库操作在事务内执行

### 2.7 分布式锁实现问题
- **问题**：lock_acquire.lua 和 lock_release.lua 脚本在多个 Service 中重复加载
- **建议**：抽取为独立的 RedisLockService，统一管理和复用 Lua 脚本

### 2.8 导出服务内存风险
- **问题**：esolvePhotoIds() 一次性查询所有照片 ID，若用户照片量大可能 OOM
- **建议**：分批查询处理，或使用游标方式逐批导出

---

## 三、性能优化（P1 - 高优先级）

### 3.1 数据库连接池配置不足
- **问题**：未显式配置 HikariCP 参数
- **建议**：
  `yaml
  spring.datasource.hikari:
    maximum-pool-size: 20
    minimum-idle: 5
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
  `

### 3.2 Redis 连接池偏小
- **问题**：max-active: 8, max-idle: 8 在高并发下可能成为瓶颈
- **建议**：调整为 max-active: 16, max-idle: 8, min-idle: 4

### 3.3 照片列表接口缺少索引
- **问题**：按 user_id、group_id、	aken_date 查询但未确认是否有复合索引
- **建议**：
  `sql
  CREATE INDEX idx_photo_user_date ON photo(user_id, taken_date DESC);
  CREATE INDEX idx_photo_group_date ON photo(group_id, taken_date DESC);
  `

### 3.4 Timeline 接口 N+1 查询
- **问题**：分页查询后逐条关联相册、用户信息
- **建议**：使用 JOIN 批量查询，或在 Mapper XML 中使用 <collection> 嵌套结果

### 3.5 SSE 连接无持久化
- **问题**：SseService 使用 ConcurrentHashMap 存储连接，服务重启后所有连接丢失
- **建议**：用户重新连接时自动恢复，或将在线状态持久化到 Redis

### 3.6 异步线程池配置不合理
- **问题**：
  - photoUploadExecutor 队列容量 200，但拒绝策略为 CallerRunsPolicy，会阻塞主线程
  - 导出任务线程池核心线程仅 2，高负载下排队严重
- **建议**：
  - 改为 AbortPolicy + 自定义告警
  - 导出线程池根据 CPU 核数动态调整

### 3.7 图片未做 CDN 加速
- **问题**：照片直接通过 OSS 原始域名访问
- **建议**：绑定 CDN 域名，开启图片裁剪、压缩、水印等处理能力

---

## 四、可观测性与运维（P1 - 高优先级）

### 4.1 Actuator 暴露面过大
- **问题**：management.endpoints.web.exposure.include: startup 仅暴露了启动信息，但未配置健康检查
- **建议**：
  `yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics
    endpoint:
      health:
        show-details: when-authorized
  `

### 4.2 日志级别为 DEBUG
- **问题**：logging.level.com.example.lovemap: DEBUG 在生产环境中会产生海量日志
- **建议**：生产环境改为 INFO，仅在排查问题时临时调至 DEBUG

### 4.3 缺少链路追踪
- **问题**：未集成 Sleuth/Zipkin 或 Micrometer Tracing
- **建议**：引入 Micrometer Tracing + OpenTelemetry，支持分布式追踪

### 4.4 缺少健康检查端点
- **问题**：未实现自定义 HealthIndicator
- **建议**：实现数据库、Redis、OSS 的健康检查

### 4.5 缺少指标监控
- **问题**：未暴露 JVM、HTTP 请求、数据库连接池等指标
- **建议**：集成 Micrometer + Prometheus + Grafana

---

## 五、业务逻辑优化（P2 - 中优先级）

### 5.1 好友绑定码安全性
- **问题**：绑定码为简单数字码，无防暴力破解机制
- **建议**：绑定码尝试 3 次失败后失效，需重新生成

### 5.2 纪念日提醒任务
- **问题**：AnniversaryReminderTask 为定时任务，但未考虑集群部署时的重复执行
- **建议**：使用 Redis 分布式锁或 Quartz 集群模式

### 5.3 导出文件清理
- **问题**：ExportCleanupTask 清理过期文件，但未清理 OSS 上的旧文件
- **建议**：同步清理本地和 OSS 上的导出文件

### 5.4 头像上传未做格式校验
- **问题**：UserController.updateAvatar() 未校验文件类型和大小
- **建议**：限制为 JPEG/PNG/WebP，最大 5MB

### 5.5 照片上传未做内容安全审核
- **问题**：上传的照片直接存入 OSS，未进行敏感内容检测
- **建议**：接入阿里云内容安全 API，过滤违规图片

---

## 六、代码规范与工程化（P2 - 中优先级）

### 6.1 包命名不规范
- **问题**：service/serviceImpl/ 目录使用了小写 serviceImpl，不符合 Java 驼峰命名规范
- **建议**：重命名为 service/impl/

### 6.2 缺少单元测试
- **问题**：仅有一个空的 LoveMapApplicationTests.java
- **建议**：
  - 使用 @WebMvcTest 测试 Controller 层
  - 使用 @DataJpaTest 或 MyBits 测试注解测试 Service 层
  - 目标覆盖率 > 60%

### 6.3 常量类分散
- **问题**：常量定义在多个 Constant 类中，部分硬编码字符串散落在代码中
- **建议**：统一使用常量类，避免魔法值

### 6.4 错误码未国际化
- **问题**：ResultCode 中的消息均为中文硬编码
- **建议**：使用 MessageSource 实现多语言支持

### 6.5 缺少 API 版本控制
- **问题**：所有接口路径为 /api/xxx，无版本号
- **建议**：使用 /api/v1/xxx 路径版本控制

### 6.6 Swagger 在生产环境暴露
- **问题**：Swagger UI 在生产环境仍可访问
- **建议**：通过 springdoc.swagger-ui.enabled 环境变量控制，生产环境关闭

---

## 七、部署与安全加固（P2 - 中优先级）

### 7.1 未启用 HTTPS
- **问题**：Nginx 配置中 SSL 部分被注释
- **建议**：使用 Let's Encrypt 免费证书，强制 HTTP -> HTTPS 重定向

### 7.2 缺少 CORS 配置
- **问题**：SecurityConfig 中未配置 CORS
- **建议**：显式配置允许的源、方法、头部

### 7.3 缺少请求头安全加固
- **问题**：未设置 X-Content-Type-Options、X-Frame-Options、Content-Security-Policy 等安全头
- **建议**：在 Nginx 层面统一添加

### 7.4 敏感信息未脱敏
- **问题**：日志中可能打印用户手机号、邮箱等敏感信息
- **建议**：使用 Logback Masking 或 Logstash Filter 脱敏

### 7.5 备份策略缺失
- **问题**：未配置数据库自动备份
- **建议**：每日全量备份 + 实时 binlog 备份

---

## 八、优化优先级汇总

| 优先级 | 类别 | 数量 | 预估工时 |
|--------|------|------|----------|
| P0 | 安全问题 | 6 | 2-3 天 |
| P1 | 架构与代码质量 | 8 | 5-7 天 |
| P1 | 性能优化 | 7 | 3-5 天 |
| P1 | 可观测性 | 5 | 3-4 天 |
| P2 | 业务逻辑 | 5 | 2-3 天 |
| P2 | 代码规范 | 6 | 2-3 天 |
| P2 | 部署安全 | 5 | 2-3 天 |

**总计**：约 22-28 个工作日
