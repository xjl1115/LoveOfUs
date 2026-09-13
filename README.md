# LoveOfUs · 恋爱管家

> 情侣专属的私密回忆空间 —— 照片时间线 · 纪念日 · 情侣聊天 · AI 恋爱管家 · AI 妆容建议 · 心动 Hub

[中文](README.md) | [English](README.en.md)

LoveOfUs 是一款面向情侣、移动端优先（H5）的 Web 应用：把两个人共同的照片、纪念日、心愿与日常对话收拢进一个私密空间，
并在其上叠加一整层 AI 能力（恋爱管家对话、工具调用、AI 妆容建议与改造图生成）。

前端是 Vue 3 单页应用，后端是 Spring Boot 单体服务，AI 能力通过 LangChain4j 接入阿里云通义千问（DashScope），
生产环境由 Nginx + Docker Compose 承载。

> 命名说明：仓库名 `LoveOfUs`、后端 Maven 模块名 `LoveMap`、数据库名 `lovemap`，三者指同一个项目。

## 功能特性

| 模块 | 能力 | 主要页面 |
|------|------|----------|
| 账号与情侣绑定 | 邮箱/手机号注册登录、验证码、JWT 双 Token 自动续期、绑定码生成与绑定/解绑、账号注销 | `/`、`/account-settings` |
| 照片与时间线 | 批量上传、EXIF 与 GPS 解析、按年月分组瀑布流、虚拟滚动、大图详情与相邻导航 | `/home`、`/upload`、`/photo/:id` |
| 相册 | 手动相册与 AI 智能相册、照片批量加入/移出、名称与封面管理 | `/albums`、`/albums/:id` |
| 纪念日 | 纪念日增删改查、倒数与年度重复、提前 N 天提醒（邮件）、按月份检索 | 个人中心内 |
| 情侣聊天 | WebSocket 一对一实时聊天、在线状态、未读数、消息撤回、逐用户删除、卡片消息 | `/chat` |
| AI 恋爱管家 | SSE 流式对话、会话历史、短期记忆、业务工具调用、写操作两步骤确认 | `/ai-chat`、`/ai-history` |
| AI 妆容建议 | 多模态分析脸型/肤色/五官 → 妆造建议 + 改造图生成、异步任务进度、每月配额、一键分享 | `/makeover` |
| 心动 Hub | 约会策划（AI 推荐）、情侣心愿单（进度/达成照片/心愿卡海报）、必做 100 件小事 | `/love-hub`、`/date-plan`、`/wishlist` |
| 通知中心 | SSE 实时推送、未读数、在线人数、全部已读/清空 | 全局浮标 |
| 心情与报告 | 每日心情打卡、周报/月报/年报、情侣里程碑 | AI 管家内 |
| 导出 | 按时间范围导出 ZIP/PDF、异步任务进度、取消与下载、导出历史 | `/export` |
| VIP 会员 | 周/月/季/年/永久 5 档、情侣组共享权益、顾问收款后由专属接口开通、妆容额度加成 | `/vip`、`/vip/order` |

## 技术栈

| 层次 | 技术选型 |
|------|----------|
| 后端 | Spring Boot 4.0.6 · Java 25 · Spring Security + JWT（jjwt 0.12.5）· MyBatis 4.0.1 + PageHelper · WebSocket · Spring Mail · Actuator · springdoc-openapi 2.2.0 · iText 7.2.6 |
| AI | LangChain4j 1.18.0 · langchain4j-community-dashscope 1.19.0-beta29 · dashscope-sdk-java 2.22.31 · 通义千问 `qwen3.7-flash`（对话/多模态）· `wanx2.1-imageedit`（图像编辑） |
| 前端 | Vue 3.4 · TypeScript 5.4 · Vite 5 · Pinia · Vue Router 4 · Vant 4 · ECharts 5 · Sass · axios · dayjs · vue-virtual-scroller · html2canvas · qrcode · amfe-flexible + postcss-pxtorem |
| 数据与存储 | MySQL 8/9 · Redis 7 · 阿里云 OSS（生产）/ 本地磁盘（`file.storage=local`） |
| 部署 | Nginx 1.28（反向代理 + 静态托管 + SSE 免缓冲 + WebSocket 升级）· Docker Compose（MySQL + Redis + Backend + Frontend） |

## 系统架构

```text
浏览器 / H5
   │
   ├── /                             → Nginx 静态资源（Vue 构建产物）
   ├── /api/**                       → Spring Boot（server.servlet.context-path=/api）
   ├── /ws/chat                      → WebSocket 情侣聊天（握手校验 JWT）
   ├── /api/notifications/subscribe  → SSE 通知推送
   ├── /api/ai/chat/stream           → SSE 流式对话
   └── /api/makeover/{id}/stream     → SSE 妆容任务进度
                     │
                     └── 后端依赖：MySQL（业务数据与会话）· Redis（缓存与短期记忆）
                         · 阿里云 OSS（照片与产物）· DashScope 通义千问（LLM / 多模态 / 图像编辑）
```

## 目录结构

```text
LoveOfUs/
├── LoveMap/                     # 后端（Spring Boot，Maven 模块名 LoveMap）
│   ├── Dockerfile               # 多阶段构建（maven:3.9-temurin-25 → temurin-25-jre）
│   ├── pom.xml
│   ├── db/things_init.sql       # “必做 100 件小事”种子数据
│   ├── sql/
│   │   ├── chat_message.sql     # 聊天表初始化脚本
│   │   └── migrate/             # 增量迁移脚本 V1 ~ V17（按文件名顺序执行）
│   └── src/main/
│       ├── config/ThingsSeedRunner.java
│       ├── java/com/example/lovemap/
│       │   ├── ai/              # LangChain4j 配置 · 会话 · 短期记忆 · SSE · tool/ 工具实现
│       │   ├── chat/            # WebSocket 聊天（握手鉴权、在线状态、会话注册表）
│       │   ├── makeover/        # AI 妆容建议（多模态分析 → 图像编辑 → 任务状态机 → SSE 事件总线）
│       │   ├── common/          # 统一返回体、常量（VipConstant 等）、异常
│       │   ├── config/          # Spring 配置（Security、CORS、Redis、Web 等）
│       │   ├── controller/      # REST 控制器
│       │   ├── mapper/          # MyBatis Mapper 接口
│       │   ├── model/           # entity / dto / vo
│       │   ├── service/         # 业务服务
│       │   └── utils/           # 工具类
│       └── resources/
│           ├── application.yml
│           └── mapper/*.xml     # MyBatis SQL 映射
│
├── loveofus-frontend/           # 前端（Vue 3 + Vite）
│   ├── Dockerfile               # node:22 构建 → nginx:1.28-alpine 托管
│   ├── vite.config.ts           # 别名 @、pxtorem、Vant 按需加载、/api 与 /ai 开发代理
│   └── src/
│       ├── api/                 # 按模块封装的请求函数
│       ├── components/          # 通用组件（+ components/makeover/）
│       ├── composables/         # 组合式函数
│       ├── constants/ · data/   # 常量与静态数据
│       ├── directives/          # 懒加载等自定义指令
│       ├── router/              # 路由表与登录守卫
│       ├── stores/              # Pinia（user / chatUnread / makeover / photo / theme）
│       ├── styles/ · types/     # 全局样式与类型定义
│       ├── utils/               # request.ts（拦截器 + Token 刷新）、sse.ts、crypto.ts 等
│       └── views/               # 页面组件
│
├── deploy/
│   ├── mysql/init/01_lovemap.sql   # 全量建库 + 种子数据（容器首次启动自动执行）
│   └── nginx/default.conf          # 容器版 Nginx 站点配置
├── nginx-1.28.0/                # 本机 Windows 版 Nginx（conf/nginx.conf + html/ 静态目录）
├── docker-compose.yml           # MySQL + Redis + Backend + Frontend 一键编排
└── .env.example                 # 环境变量模板
```

## 快速开始

### 前置要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 25 | 后端编译与运行（`java.version=25`） |
| Maven | 3.9+ | 或使用 IDE 内置 Maven |
| Node.js | 22+ | 前端构建（Dockerfile 使用 node:22） |
| MySQL | 8/9 | 库名 `lovemap`，字符集 `utf8mb4` |
| Redis | 7 | 缓存、验证码、AI 短期记忆、Token 黑名单 |
| 阿里云 OSS | — | 照片/头像/产物存储，`file.storage=oss` 时需要 AK/SK |
| DashScope | — | 通义千问 API Key，仅 AI 功能需要 |

### 方式一 Docker Compose 一键部署

```bash
cp .env.example .env      # 按需修改
docker compose up -d --build
```

- `.env` 中 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`VIP_ADMIN_TOKEN` 为**必填**，缺失时 compose 直接报错退出。
- MySQL 数据卷为空时，首次启动会自动执行 `deploy/mysql/init/01_lovemap.sql` 建库建表。
- 访问入口 `http://localhost`（`HTTP_PORT`，默认 80）；后端调试端口 `http://localhost:8081`。

| 服务 | 镜像 | 宿主机端口 | 说明 |
|------|------|-----------|------|
| frontend | nginx:1.28-alpine（自建） | `HTTP_PORT`=80 | 静态资源 + 反向代理 |
| backend | temurin-25-jre（自建） | `BACKEND_PORT`=8081 → 容器 8080 | Spring Boot |
| mysql | mysql:9.2 | `MYSQL_PORT`=13306 → 3306 | 避开本机 3306，健康检查后才启动后端 |
| redis | redis:7-alpine | `REDIS_PORT`=6379 | 密码为 `REDIS_PASSWORD`，开启 AOF |

### 方式二 本地开发

```bash
# 1) 建库并导入初始化脚本
mysql -uroot -p -e "CREATE DATABASE lovemap DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p lovemap < deploy/mysql/init/01_lovemap.sql

# 2) 启动依赖（MySQL 3306 / Redis 6379）
docker compose up -d mysql redis

# 3) 启动后端（默认 http://localhost:8080/api）
cd LoveMap
mvn spring-boot:run

# 4) 启动前端（默认 http://localhost:3000，/api 代理到 8080）
cd loveofus-frontend
npm install
npm run dev
```

`application.yml` 内置了本机开发默认值（MySQL `root/1115`、Redis 密码 `1115`、`file.storage=local`）。
生产环境请全部改用环境变量覆盖，不要把真实凭据提交进仓库。

### 生产构建与 Nginx 托管

```bash
# 前端构建（vue-tsc 类型检查 + vite build）
cd loveofus-frontend
npm run build            # 产物在 dist/

# 本机 Windows Nginx：把 dist/ 内容复制到 nginx-1.28.0/html/
# 容器部署：docker compose up -d --build frontend
```

## 环境变量

`docker-compose.yml` 与后端 `application.yml` 读取同一批变量，`.env.example` 已给出模板。

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_ROOT_PASSWORD` | `1115` | MySQL root 密码（compose 与后端共用） |
| `MYSQL_PORT` | `13306` | MySQL 映射到宿主机的端口 |
| `REDIS_PASSWORD` | `1115` | Redis 密码 |
| `REDIS_PORT` | `6379` | Redis 映射到宿主机的端口 |
| `HTTP_PORT` / `BACKEND_PORT` | `80` / `8081` | 前端入口端口与后端调试端口 |
| `FILE_STORAGE` | `oss` | `oss` 或 `local`；照片上传强依赖 OSS |
| `FILE_LOCAL_BASE_DIR` / `FILE_LOCAL_URL_PREFIX` | `/app/uploads` / `http://localhost/uploads` | `local` 模式的落盘目录与访问前缀 |
| `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_REGION` | `oss-cn-beijing.aliyuncs.com` / `allenxjl` / `cn-beijing` | OSS 配置 |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 无 | 对应 OSS SDK 标准变量，**必填** |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | `smtp.qq.com` / `465` / 空 | 邮件通知（验证码、纪念日提醒） |
| `AI_ENABLED` | `true` | AI 总开关；`false` 时 AI 接口返回“功能未开启” |
| `DASHSCOPE_API_KEY` | 空 | 通义千问 API Key，AI 功能必填 |
| `DASHSCOPE_MODEL` | `qwen3.7-flash-2026-07-15` | 对话与工具调用模型 |
| `DASHSCOPE_MAKEOVER_API_KEY` / `DASHSCOPE_MAKEOVER_MODEL` | 回落到 `DASHSCOPE_API_KEY` | 妆容建议专用模型覆盖 |
| `DASHSCOPE_MAKEOVER_IMAGE_MODEL` | `wanx2.1-imageedit` | 改造图生成模型 |
| `MAKEOVER_MONTHLY_FREE_QUOTA` | `3` | 每账户每月免费妆容建议次数 |
| `VIP_ADMIN_TOKEN` | 空 | 顾问开通接口密钥（请求头 `X-Vip-Admin-Token`），未配置时接口返回 403 |
| `VIP_PUBLIC_BASE_URL` | `http://localhost:8080` | 对外地址，用于生成顾问通知邮件中的开通命令 |
| `VITE_API_BASE_URL` | `/api` | 前端请求前缀，仅前端构建期生效 |

## 配置项说明

除环境变量外，`LoveMap/src/main/resources/application.yml` 中还有若干需要按环境调整的配置：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `server.port` / `server.servlet.context-path` | `8080` / `/api` | 所有接口以 `/api` 开头 |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `20MB` / `200MB` | 上传限制，需与 Nginx `client_max_body_size` 保持一致 |
| `jwt.ttl` / `jwt.refresh-ttl` | `86400000` / `2592000000` | 访问令牌 24 小时、刷新令牌 30 天 |
| `verify.code.length` / `expire` | `6` / `60` | 验证码位数与有效期（秒） |
| `sse.timeout` | `1800000` | SSE 长连接超时（30 分钟） |
| `ai.dashscope.system-prompt` | 见文件 | 恋爱管家系统提示词与工具调用规则，可直接在此调整 AI 行为 |
| `makeover.max-image-size` | `8MB` | 妆容建议单图体积上限 |
| `makeover.user-concurrent-limit` | `3` | 单用户并发任务上限（防刷） |
| `vip.admin-token` / `vip.public-base-url` | 取环境变量 | VIP 顾问开通密钥与对外地址 |

## API 概览

统一前缀 `/api`（由 `server.servlet.context-path` 提供）。除 `/auth/**` 公开接口外，均需请求头
`Authorization: Bearer <token>`；未登录访问受保护接口返回 401，Token 过期由前端 `src/utils/request.ts`
使用刷新令牌自动续期并重放请求。

| 模块 | 主要接口 |
|------|----------|
| 认证 `/auth` | `POST /captcha/send`、`POST /captcha/verify`、`POST /register`、`POST /login`、`POST /logout`、`POST /token/refresh`、`GET /bind-code`、`POST /bind-code/generate`、`POST /password/reset`、`POST /bind`、`POST /unbind`、`POST /account/delete` |
| 用户 `/user` | `GET /profile`、`PUT /profile`、`GET /stats`、`POST /avatar`、`GET /notification-settings`、`PUT /notification-settings` |
| 照片 `/photos` | `POST /upload`、`GET /timeline`、`GET /{id}`、`DELETE /{id}` |
| 相册 `/albums` | `POST /`、`GET /`、`GET /{id}`、`GET /{albumId}/photos`、`PUT /{id}`、`DELETE /{id}`、`POST /{albumId}/photos`、`DELETE /{albumId}/photos/{photoId}` |
| 纪念日 `/anniversaries` | `GET /`、`GET /{id}`、`POST /`、`PUT /{id}`、`DELETE /{id}` |
| 聊天 `/chat` | `GET /history`、`GET /unread-count`、`POST /read-all`、`POST /in-chat/enter`、`POST /in-chat/heartbeat`、`POST /in-chat/leave`、`GET /online-status`、`POST /message/{id}`、`POST /messages/delete-batch`、`POST /message/{id}/recall`、`POST /clear-local` |
| 通知 `/notifications` | `GET /subscribe`（SSE）、`GET /`、`GET /unread-count`、`PUT /{id}/read`、`PUT /read-all`、`DELETE /{id}`、`DELETE /clear`、`GET /online-count` |
| 约会策划 `/date-plans` | `GET /`、`GET /{id}`、`POST /`、`PUT /{id}`、`PUT /{id}/status`、`POST /{id}/photos`、`DELETE /{id}`、`POST /recommend` |
| 心愿单 `/wishlist-items` | `GET /`、`GET /{id}`、`POST /`、`PUT /{id}`、`PUT /{id}/status`、`PUT /{id}/progress`、`POST /{id}/photo`、`GET /{id}/photo/raw`、`POST /{id}/card`、`DELETE /{id}`、`POST /recommend` |
| 必做小事 `/things` | `GET /`、`GET /stats`、`POST /achieve`、`GET /{thingId}/photos`、`POST /photos` |
| 导出 `/exports` | `POST /`、`GET /{id}/status`、`GET /`、`GET /{id}/download`、`POST /{id}/cancel` |
| VIP `/vip` | `GET /tiers`、`POST /orders`、`GET /orders`、`POST /orders/{orderNo}/cancel`、`GET /admin/orders/pending`、`POST /admin/orders/{orderNo}/activate`、`POST /admin/orders/batch-activate` |
| AI 对话 `/ai` | `POST /chat`（非流式）、`POST /chat/stream`（SSE 流式） |
| AI 会话 `/ai/sessions` | `GET /`、`GET /{sessionId}`、`PUT /{sessionId}/title`、`DELETE /{sessionId}` |
| 文案反馈 `/ai/copy-feedback` | `POST /`、`GET /acceptance-stats` |
| 妆容建议 `/makeover` | `POST /`（multipart 上传）、`GET /{id}`、`GET /`、`GET /quota`、`DELETE /{id}`、`POST /{id}/cancel`、`POST /{id}/share`、`GET /{id}/stream`（SSE 进度） |
| 实时通道 | `WS /api/ws/chat`（握手时通过 `?token=` 或 `Authorization` 校验 JWT，支持多端登录） |

`/vip/admin/**` 为专属顾问接口，请求头 `X-Vip-Admin-Token` 必须与配置 `vip.admin-token` 一致，否则返回 403。

## AI 能力

### 恋爱管家对话

- **模型接入**：DashScope 通义千问（默认 `qwen3.7-flash-2026-07-15`），由 `ai/config/LangChain4jConfig` 装配。
- **调用入口**：`POST /api/ai/chat` 一次性返回；`POST /api/ai/chat/stream` 以 SSE 逐块返回。
- **会话与记忆**：历史落在 `ai_chat_session` / `ai_chat_message`，前端 `/ai-history` 支持回看、重命名、删除；
  `AiShortTermMemoryService` 维护多轮上下文，`AiSessionService` 负责会话生命周期。
- **提示词**：系统提示词集中在 `application.yml` 的 `ai.dashscope.system-prompt`，约束角色设定、时间计算、
  性别/伴侣称呼与工具调用流程，改 AI 行为优先改这里。
- **工具调用**：`ai/tool/` 下每个 Tool 暴露若干函数，LLM 通过 Function Calling 先取真实数据再作答（禁止编造数字）。
- **写操作安全**：修改昵称/手机号/邮箱、新建纪念日、设置提醒等一律走「`prepare*` 预演 → 用户确认 → `confirm*` 落库」，
  未确认不会写数据库。

### 工具清单

| 分类 | 代表工具 |
|------|----------|
| 时间 | `today`、`recentDays`、`monthRange`、`getCurrentTime` |
| 照片 | `searchPhotos`、`describePhoto`、`getPhotoTimeline`、`getRecentPhotos`、`getMostVisitedCity`、`getPhotoInsight` |
| 相册 | `listAlbums`、`searchAlbumByName` |
| 纪念日 | `queryAnniversaries`、`searchAnniversaryByName`、`getCountdownByName`、`searchAnniversariesByMonth`、`getAnniversaryStats`、`collectAnniversaryField`、`checkAnniversaryDraft`、`prepareCreateAnniversary`、`confirmCreateAnniversary` |
| 提醒 | `listReminders`、`prepareSetReminder`、`confirmSetReminder` |
| 用户与伴侣 | `getUserStats`、`getMyProfile`、`getPartnerInfo`、`prepareUpdateNickname`、`prepareUpdatePhone`、`prepareUpdateEmail` 及对应 `confirm*` |
| 心情与报告 | `getTodayMood`、`recordMood`、`generateWeeklyReport`、`generateMonthlyReport`、`generateYearlyReport`、`getCoupleMilestones` |
| 导出 | `listExportHistory`、`getExportStatus`、`prepareCreateExport`、`confirmCreateExport` |
| 礼物与设置 | 礼物推荐、通知设置（`GiftRecommendTool`、`NotificationSettingsTool`） |

> 工具随版本演进，完整清单以 `LoveMap/src/main/java/com/example/lovemap/ai/tool/` 下的实现为准。

### SSE 事件格式

流式对话（`POST /api/ai/chat/stream`）返回 `text/event-stream`：

```text
event: chunk
data: {"text":"..."}

event: done
data: {}

event: error
data: {"message":"..."}
```

通知推送 `/api/notifications/subscribe` 与妆容进度 `/api/makeover/{id}/stream` 使用同一套 SSE 约定。
Nginx 已对这三个路径关闭 `proxy_buffering`，否则流式输出会被缓冲成一次性返回。

### AI 妆容建议

两阶段异步流程，任务状态机为 `0 待处理 / 1 分析中 / 2 出图中 / 3 完成 / 4 失败 / 5 已取消`：

1. **分析**：多模态模型读取人像照片，输出脸型、肤色、五官特征，以及场景化的妆造建议（妆容、发型、穿搭、香水、配饰，
   外加整体总结与上妆步骤）。
2. **出图**：`wanx2.1-imageedit` 依据建议生成改造图，结果与建议一并持久化到 `makeover_record`，
   子任务进度记在 `makeover_task`，前端通过 SSE 实时展示。
3. **配额**：普通用户每月 `makeover.monthly-free-quota` 次（默认 3），VIP 按档位叠加
   （周卡 +2 / 月卡 +6 / 季卡 +9 / 年卡 +13 / 永久不限量）。
4. **分享**：`POST /api/makeover/{id}/share` 以卡片消息（`msg_type=5`）发送到情侣聊天。

## 数据库

- 库名 `lovemap`，字符集 `utf8mb4`；MyBatis 开启下划线转驼峰，映射文件位于 `LoveMap/src/main/resources/mapper/`。
- 初始化：容器首次启动自动执行 `deploy/mysql/init/01_lovemap.sql`（全量导出快照），本地手工导入同一文件。
- 增量脚本：`LoveMap/sql/migrate/V1 ~ V17` 按文件名顺序执行，只含 `ALTER` / `CREATE`，依赖基础表已存在。

| 表 | 说明 |
|----|------|
| `user` | 用户（伴侣绑定字段、性别、`vip_level` / `vip_expire_at`） |
| `group` | 情侣组（组 UUID + 双方用户 ID） |
| `photo` / `album` / `photo_album` | 照片、相册与照片-相册关联 |
| `anniversary` / `anniversary_reminder` | 纪念日主表与提醒计划（提前 N 天、是否已发送） |
| `mood_log` | 心情打卡（组 + 用户 + 日期唯一） |
| `chat_message` / `chat_message_delete` | 聊天消息与「仅自己删除」记录 |
| `notification` | 站内通知 |
| `export_record` | 导出任务记录 |
| `date_plans` | 约会计划（含预算档位、状态、关联照片） |
| `wishlist_items` | 心愿清单（分类、优先级、进度、达成照片、心愿卡） |
| `things` / `things_completion` / `things_url` / `things_url_rel` | 必做 100 件小事、完成记录与配图资源 |
| `provence` | 省份字典（足迹/城市统计用） |
| `ai_chat_session` / `ai_chat_message` | AI 会话与消息 |
| `makeover_record` / `makeover_task` | 妆容建议记录与异步子任务 |
| `ai_copy_feedback` | AI 文案采纳/拒绝反馈（迁移 V6 引入） |
| `vip_order` | VIP 订单（迁移 V17 引入） |

> **注意**：`deploy/mysql/init/01_lovemap.sql` 是某个时间点的全量导出快照。
> 若快照中缺少 `user.vip_level`、`user.vip_expire_at`、`vip_order`、`ai_copy_feedback`
> （即迁移 V6 / V16 / V17 引入的结构），请在建库后按文件名顺序补跑缺失的迁移脚本，
> 否则 VIP 与文案反馈相关功能不可用。

## 部署

### Docker Compose（推荐）

```bash
cp .env.example .env
docker compose up -d --build
docker compose logs -f backend
```

编排细节：

- MySQL 与 Redis 均配置健康检查，`backend` 等待两者就绪后才启动（`depends_on: condition: service_healthy`）。
- 容器内时区统一为 `Asia/Shanghai`。
- `uploads-data` 与 `exports-data` 数据卷分别承载本地上传文件与导出产物，容器重建不丢数据。

### Nginx 反向代理

容器版 `deploy/nginx/default.conf` 与本机版 `nginx-1.28.0/conf/nginx.conf` 语义一致，核心配置如下：

| 路径 | 处理方式 |
|------|----------|
| `/` | SPA 静态资源，`try_files ... /index.html` 兜底，`index.html` 不缓存，带 hash 的 chunk 长缓存 |
| `/api/` | 反向代理到后端集群（`least_conn` + keepalive），开启 Gzip 与代理缓存，`client_max_body_size 200M` |
| `/api/notifications/subscribe`、`/api/ai/chat/stream`、`/api/makeover/*/stream` | 关闭 `proxy_buffering` 与缓存，`proxy_read_timeout 1800s` |
| `/ws/` | WebSocket 升级（`Upgrade` / `Connection` 头），`proxy_read_timeout 3600s` |
| `/uploads/` | 代理到后端 `/api/uploads/`（`file.storage=local` 时使用），缓存 7 天 |

> 本机版配置中保留的 `ai_cluster`（FastAPI）upstream 属于历史遗留，当前 AI 能力已集成在 Spring Boot 内，不参与转发。

## 常见问题

| 现象 | 排查方向 |
|------|----------|
| 照片/头像上传失败 | 检查 `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET`；`file.storage=oss` 时上传强依赖 OSS |
| AI 接口返回「功能未开启」 | 确认 `AI_ENABLED=true` 且 `DASHSCOPE_API_KEY` 已配置 |
| VIP 开通接口 403 | `VIP_ADMIN_TOKEN` 未配置，或请求头 `X-Vip-Admin-Token` 与之一致性校验失败 |
| 流式输出变成一次性返回 | Nginx 未对 SSE 路径关闭 `proxy_buffering` |
| 接口 404 | 后端 context-path 为 `/api`，前端需以 `/api` 前缀访问 |
| 上传大图返回 413 | 同时调大 `spring.servlet.multipart.max-*` 与 Nginx `client_max_body_size` |
| 新建库缺少 VIP 表 | 按「数据库」一节说明补跑 `sql/migrate` 中缺失的迁移脚本 |
| 本地前端请求跨域 | 使用 `npm run dev` 的 `/api` 代理，或通过 `VITE_API_BASE_URL` 指向后端 |

## 文档与约定

- 更详细的前后端开发文档、AI 层设计、部署说明与优化记录位于本地 `docs/` 目录（已在 `.gitignore` 中，不入仓）。
  其中部分文档写于 AI 由 FastAPI 承载的阶段，与现状不一致处以后端代码为准。
- 后端遵循 `controller → service → mapper` 分层；AI 工具统一放在 `ai/tool/`；前端请求统一走 `src/utils/request.ts`。
- 提交前建议至少确认：后端 `mvn -q compile` 通过、前端 `npm run build`（含 `vue-tsc` 类型检查）通过。

## 开源协议

MIT
