# LoveOfUs · Love Butler

> A private memory space for couples — photo timeline, anniversaries, couple chat, AI love butler, AI makeover advice, and the Love Hub.

[中文](README.md) | [English](README.en.md)

LoveOfUs is a mobile-first (H5) web application for couples. It gathers the photos, anniversaries, wishes and daily conversations you
share into one private space, and layers a full set of AI capabilities on top of it (butler-style chat, tool calling, AI makeover
advice and generated restyled portraits).

The frontend is a Vue 3 single-page app, the backend a Spring Boot monolith, and the AI capabilities are wired to Alibaba Cloud
Tongyi Qianwen (DashScope) through LangChain4j. Production runs on Nginx + Docker Compose.

> Naming: the repository is `LoveOfUs`, the backend Maven module is `LoveMap`, and the database is `lovemap` — all three refer to the
> same project.

## Features

| Module | Capabilities | Main pages |
|--------|--------------|-----------|
| Account and partner binding | Email/phone sign-up and sign-in, verification codes, JWT access + refresh token auto-renewal, bind-code generation/binding/unbinding, account deletion | `/`, `/account-settings` |
| Photos and timeline | Batch upload, EXIF and GPS parsing, waterfall timeline grouped by year and month, virtual scrolling, photo detail with neighbour navigation | `/home`, `/upload`, `/photo/:id` |
| Albums | Manual and AI-generated albums, adding/removing photos in bulk, name and cover management | `/albums`, `/albums/:id` |
| Anniversaries | Full CRUD, countdowns and yearly recurrence, reminders N days ahead (email), lookup by month | Inside the profile page |
| Couple chat | One-to-one real-time chat over WebSocket, presence, unread counts, message recall, per-user deletion, card messages | `/chat` |
| AI love butler | Streaming SSE chat, session history, short-term memory, business tool calling, two-step confirmation for writes | `/ai-chat`, `/ai-history` |
| AI makeover advice | Multimodal analysis of face shape/skin tone/features → styling advice plus a generated restyled image, async progress, monthly quota, one-tap sharing | `/makeover` |
| Love Hub | Date planning (AI recommendations), couple wishlist (progress, achievement photos, wish-card posters), 100 must-do things | `/love-hub`, `/date-plan`, `/wishlist` |
| Notification centre | Real-time SSE push, unread counts, online headcount, mark-all-read and clear | Global floating button |
| Mood and reports | Daily mood check-in, weekly/monthly/yearly reports, couple milestones | Inside the AI butler |
| Export | Time-range export to ZIP/PDF, async task progress, cancellation and download, export history | `/export` |
| VIP membership | Five tiers (week/month/quarter/year/lifetime), benefits shared by the couple group, activated by an advisor after payment, bonus makeover quota | `/vip`, `/vip/order` |

## Tech Stack

| Layer | Choices |
|-------|---------|
| Backend | Spring Boot 4.0.6 · Java 25 · Spring Security + JWT (jjwt 0.12.5) · MyBatis 4.0.1 + PageHelper · WebSocket · Spring Mail · Actuator · springdoc-openapi 2.2.0 · iText 7.2.6 |
| AI | LangChain4j 1.18.0 · langchain4j-community-dashscope 1.19.0-beta29 · dashscope-sdk-java 2.22.31 · Tongyi Qianwen `qwen3.7-flash` (chat/multimodal) · `wanx2.1-imageedit` (image editing) |
| Frontend | Vue 3.4 · TypeScript 5.4 · Vite 5 · Pinia · Vue Router 4 · Vant 4 · ECharts 5 · Sass · axios · dayjs · vue-virtual-scroller · html2canvas · qrcode · amfe-flexible + postcss-pxtorem |
| Data and storage | MySQL 8/9 · Redis 7 · Alibaba Cloud OSS in production, local disk when `file.storage=local` |
| Deployment | Nginx 1.28 (reverse proxy, static hosting, unbuffered SSE, WebSocket upgrade) · Docker Compose (MySQL + Redis + backend + frontend) |

## Architecture

```text
Browser / H5
   │
   ├── /                             → Nginx static assets (Vue build output)
   ├── /api/**                       → Spring Boot (server.servlet.context-path=/api)
   ├── /ws/chat                      → WebSocket couple chat (JWT check on handshake)
   ├── /api/notifications/subscribe  → SSE notification push
   ├── /api/ai/chat/stream           → SSE streaming chat
   └── /api/makeover/{id}/stream     → SSE makeover progress
                     │
                     └── Backend dependencies: MySQL (business data and sessions) · Redis (cache and short-term
                         memory) · Alibaba Cloud OSS (photos and artifacts) · DashScope Qwen (LLM / multimodal /
                         image editing)
```

## Project Layout

```text
LoveOfUs/
├── LoveMap/                     # Backend (Spring Boot, Maven module LoveMap)
│   ├── Dockerfile               # Multi-stage build (maven:3.9-temurin-25 → temurin-25-jre)
│   ├── pom.xml
│   ├── db/things_init.sql       # Seed data for the “100 must-do things” list
│   ├── sql/
│   │   ├── chat_message.sql     # Chat table bootstrap script
│   │   └── migrate/             # Incremental migrations V1 ~ V17 (run in filename order)
│   └── src/main/
│       ├── config/ThingsSeedRunner.java
│       ├── java/com/example/lovemap/
│       │   ├── ai/              # LangChain4j config, sessions, short-term memory, SSE, tool/ implementations
│       │   ├── chat/            # WebSocket chat (handshake auth, presence, session registry)
│       │   ├── makeover/        # AI makeover (multimodal analysis → image editing → task state machine → SSE event bus)
│       │   ├── common/          # Unified response wrapper, constants (VipConstant, …), exceptions
│       │   ├── config/          # Spring configuration (Security, CORS, Redis, web, …)
│       │   ├── controller/      # REST controllers
│       │   ├── mapper/          # MyBatis mapper interfaces
│       │   ├── model/           # entity / dto / vo
│       │   ├── service/         # Business services
│       │   └── utils/           # Utilities
│       └── resources/
│           ├── application.yml
│           └── mapper/*.xml     # MyBatis SQL mappings
│
├── loveofus-frontend/           # Frontend (Vue 3 + Vite)
│   ├── Dockerfile               # node:22 build → nginx:1.28-alpine runtime
│   ├── vite.config.ts           # @ alias, pxtorem, Vant auto-import, /api and /ai dev proxies
│   └── src/
│       ├── api/                 # Per-module request functions
│       ├── components/          # Shared components (plus components/makeover/)
│       ├── composables/         # Composable functions
│       ├── constants/ · data/   # Constants and static data
│       ├── directives/          # Custom directives such as lazy loading
│       ├── router/              # Route table and auth guard
│       ├── stores/              # Pinia (user / chatUnread / makeover / photo / theme)
│       ├── styles/ · types/     # Global styles and type definitions
│       ├── utils/               # request.ts (interceptors + token refresh), sse.ts, crypto.ts, …
│       └── views/               # Page components
│
├── deploy/
│   ├── mysql/init/01_lovemap.sql   # Full schema + seed data (auto-run on first container start)
│   └── nginx/default.conf          # Containerised Nginx site config
├── nginx-1.28.0/                # Local Windows Nginx distribution (conf/nginx.conf + html/ static dir)
├── docker-compose.yml           # One-command MySQL + Redis + backend + frontend orchestration
└── .env.example                 # Environment variable template
```

## Getting Started

### Prerequisites

| Dependency | Version | Notes |
|------------|---------|-------|
| JDK | 25 | Backend compile and runtime (`java.version=25`) |
| Maven | 3.9+ | Or the bundled Maven of your IDE |
| Node.js | 22+ | Frontend build (the Dockerfile uses node:22) |
| MySQL | 8/9 | Database `lovemap`, charset `utf8mb4` |
| Redis | 7 | Cache, verification codes, AI short-term memory, token blacklist |
| Alibaba Cloud OSS | — | Photo/avatar/artifact storage; AK/SK required when `file.storage=oss` |
| DashScope | — | Tongyi Qianwen API key, needed for AI features only |

### Option 1 — Docker Compose

```bash
cp .env.example .env      # edit as needed
docker compose up -d --build
```

- `OSS_ACCESS_KEY_ID`, `OSS_ACCESS_KEY_SECRET` and `VIP_ADMIN_TOKEN` are **required** in `.env`; Compose fails fast without them.
- On the first start with an empty MySQL volume, `deploy/mysql/init/01_lovemap.sql` is executed automatically.
- Entry point `http://localhost` (`HTTP_PORT`, default 80); backend debug port `http://localhost:8081`.

| Service | Image | Host port | Notes |
|---------|-------|-----------|-------|
| frontend | nginx:1.28-alpine (local build) | `HTTP_PORT`=80 | Static assets plus reverse proxy |
| backend | temurin-25-jre (local build) | `BACKEND_PORT`=8081 → 8080 | Spring Boot |
| mysql | mysql:9.2 | `MYSQL_PORT`=13306 → 3306 | Avoids clashing with a local 3306; backend waits for its health check |
| redis | redis:7-alpine | `REDIS_PORT`=6379 | Password from `REDIS_PASSWORD`, AOF enabled |

### Option 2 — Local development

```bash
# 1) Create the database and import the bootstrap script
mysql -uroot -p -e "CREATE DATABASE lovemap DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p lovemap < deploy/mysql/init/01_lovemap.sql

# 2) Start the dependencies (MySQL 3306 / Redis 6379)
docker compose up -d mysql redis

# 3) Start the backend (default http://localhost:8080/api)
cd LoveMap
mvn spring-boot:run

# 4) Start the frontend (default http://localhost:3000, /api proxied to 8080)
cd loveofus-frontend
npm install
npm run dev
```

`application.yml` ships with development defaults (MySQL `root/1115`, Redis password `1115`, `file.storage=local`).
Override everything with environment variables in production, and never commit real credentials.

### Production build and Nginx hosting

```bash
# Frontend build (vue-tsc type check + vite build)
cd loveofus-frontend
npm run build            # output in dist/

# Local Windows Nginx: copy the contents of dist/ into nginx-1.28.0/html/
# Container deployment: docker compose up -d --build frontend
```

## Environment Variables

`docker-compose.yml` and the backend `application.yml` read the same variables; `.env.example` provides the template.

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_ROOT_PASSWORD` | `1115` | MySQL root password (shared by Compose and the backend) |
| `MYSQL_PORT` | `13306` | Host port mapped to MySQL |
| `REDIS_PASSWORD` | `1115` | Redis password |
| `REDIS_PORT` | `6379` | Host port mapped to Redis |
| `HTTP_PORT` / `BACKEND_PORT` | `80` / `8081` | Frontend entry port and backend debug port |
| `FILE_STORAGE` | `oss` | `oss` or `local`; photo upload depends on OSS |
| `FILE_LOCAL_BASE_DIR` / `FILE_LOCAL_URL_PREFIX` | `/app/uploads` / `http://localhost/uploads` | Local storage directory and URL prefix when `file.storage=local` |
| `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_REGION` | `oss-cn-beijing.aliyuncs.com` / `allenxjl` / `cn-beijing` | OSS configuration |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | none | Mapped to the OSS SDK standard variables, **required** |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | `smtp.qq.com` / `465` / empty | Email notifications (verification codes, anniversary reminders) |
| `AI_ENABLED` | `true` | Master switch for AI; when `false` the AI endpoints answer with “feature disabled” |
| `DASHSCOPE_API_KEY` | empty | Tongyi Qianwen API key, required for AI features |
| `DASHSCOPE_MODEL` | `qwen3.7-flash-2026-07-15` | Chat and tool-calling model |
| `DASHSCOPE_MAKEOVER_API_KEY` / `DASHSCOPE_MAKEOVER_MODEL` | falls back to `DASHSCOPE_API_KEY` | Model overrides for makeover advice |
| `DASHSCOPE_MAKEOVER_IMAGE_MODEL` | `wanx2.1-imageedit` | Model used to generate the restyled image |
| `MAKEOVER_MONTHLY_FREE_QUOTA` | `3` | Free makeover requests per account per month |
| `VIP_ADMIN_TOKEN` | empty | Advisor activation key (header `X-Vip-Admin-Token`); the endpoint returns 403 when unset |
| `VIP_PUBLIC_BASE_URL` | `http://localhost:8080` | Public base URL used to build the activation command inside advisor emails |
| `VITE_API_BASE_URL` | `/api` | Request prefix for the frontend, applied at build time only |

## Configuration Reference

Beyond the environment variables, `LoveMap/src/main/resources/application.yml` holds settings worth tuning per environment:

| Setting | Default | Description |
|---------|---------|-------------|
| `server.port` / `server.servlet.context-path` | `8080` / `/api` | Every endpoint is served under `/api` |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `20MB` / `200MB` | Upload limits, must match Nginx `client_max_body_size` |
| `jwt.ttl` / `jwt.refresh-ttl` | `86400000` / `2592000000` | Access token 24 hours, refresh token 30 days |
| `verify.code.length` / `expire` | `6` / `60` | Verification code length and lifetime in seconds |
| `sse.timeout` | `1800000` | SSE connection timeout (30 minutes) |
| `ai.dashscope.system-prompt` | see file | Love-butler system prompt and tool-calling rules; tune AI behaviour here first |
| `makeover.max-image-size` | `8MB` | Maximum size of a single makeover image |
| `makeover.user-concurrent-limit` | `3` | Per-user concurrent task limit (anti-abuse) |
| `vip.admin-token` / `vip.public-base-url` | from environment | VIP advisor activation key and public base URL |

## API Overview

All endpoints share the `/api` prefix (from `server.servlet.context-path`). Except for the public `/auth/**` endpoints, every request
needs an `Authorization: Bearer <token>` header. Unauthenticated calls to protected endpoints return 401, and an expired access token
is refreshed and the request replayed by `src/utils/request.ts`.

| Module | Main endpoints |
|--------|----------------|
| Auth `/auth` | `POST /captcha/send`, `POST /captcha/verify`, `POST /register`, `POST /login`, `POST /logout`, `POST /token/refresh`, `GET /bind-code`, `POST /bind-code/generate`, `POST /password/reset`, `POST /bind`, `POST /unbind`, `POST /account/delete` |
| User `/user` | `GET /profile`, `PUT /profile`, `GET /stats`, `POST /avatar`, `GET /notification-settings`, `PUT /notification-settings` |
| Photos `/photos` | `POST /upload`, `GET /timeline`, `GET /{id}`, `DELETE /{id}` |
| Albums `/albums` | `POST /`, `GET /`, `GET /{id}`, `GET /{albumId}/photos`, `PUT /{id}`, `DELETE /{id}`, `POST /{albumId}/photos`, `DELETE /{albumId}/photos/{photoId}` |
| Anniversaries `/anniversaries` | `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `DELETE /{id}` |
| Chat `/chat` | `GET /history`, `GET /unread-count`, `POST /read-all`, `POST /in-chat/enter`, `POST /in-chat/heartbeat`, `POST /in-chat/leave`, `GET /online-status`, `POST /message/{id}`, `POST /messages/delete-batch`, `POST /message/{id}/recall`, `POST /clear-local` |
| Notifications `/notifications` | `GET /subscribe` (SSE), `GET /`, `GET /unread-count`, `PUT /{id}/read`, `PUT /read-all`, `DELETE /{id}`, `DELETE /clear`, `GET /online-count` |
| Date plans `/date-plans` | `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/status`, `POST /{id}/photos`, `DELETE /{id}`, `POST /recommend` |
| Wishlist `/wishlist-items` | `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `PUT /{id}/status`, `PUT /{id}/progress`, `POST /{id}/photo`, `GET /{id}/photo/raw`, `POST /{id}/card`, `DELETE /{id}`, `POST /recommend` |
| Must-do things `/things` | `GET /`, `GET /stats`, `POST /achieve`, `GET /{thingId}/photos`, `POST /photos` |
| Export `/exports` | `POST /`, `GET /{id}/status`, `GET /`, `GET /{id}/download`, `POST /{id}/cancel` |
| VIP `/vip` | `GET /tiers`, `POST /orders`, `GET /orders`, `POST /orders/{orderNo}/cancel`, `GET /admin/orders/pending`, `POST /admin/orders/{orderNo}/activate`, `POST /admin/orders/batch-activate` |
| AI chat `/ai` | `POST /chat` (non-streaming), `POST /chat/stream` (streaming SSE) |
| AI sessions `/ai/sessions` | `GET /`, `GET /{sessionId}`, `PUT /{sessionId}/title`, `DELETE /{sessionId}` |
| Copy feedback `/ai/copy-feedback` | `POST /`, `GET /acceptance-stats` |
| Makeover `/makeover` | `POST /` (multipart upload), `GET /{id}`, `GET /`, `GET /quota`, `DELETE /{id}`, `POST /{id}/cancel`, `POST /{id}/share`, `GET /{id}/stream` (SSE progress) |
| Realtime | `WS /api/ws/chat` (JWT verified on handshake via `?token=` or the `Authorization` header, multi-device sign-in supported) |

`/vip/admin/**` endpoints are advisor-only: the `X-Vip-Admin-Token` header must match the `vip.admin-token` setting, otherwise they
return 403.

## AI Capabilities

### Love butler chat

- **Model wiring**: DashScope Tongyi Qianwen (default `qwen3.7-flash-2026-07-15`), assembled by `ai/config/LangChain4jConfig`.
- **Entry points**: `POST /api/ai/chat` returns a single response; `POST /api/ai/chat/stream` streams chunks over SSE.
- **Sessions and memory**: history lives in `ai_chat_session` / `ai_chat_message`, and `/ai-history` lets users review, rename and
  delete sessions. `AiShortTermMemoryService` keeps multi-turn context while `AiSessionService` owns the session lifecycle.
- **Prompt**: the system prompt sits in `application.yml` under `ai.dashscope.system-prompt`, constraining persona, date handling,
  how partners are addressed, and the tool-calling flow. Tune AI behaviour there first.
- **Tool calling**: every Tool under `ai/tool/` exposes functions that the LLM calls through function calling, so answers are backed
  by real data (numbers are never invented).
- **Write safety**: changing nickname/phone/email, creating anniversaries and setting reminders all follow
  “`prepare*` preview → user confirms → `confirm*` persists”. Nothing is written to the database before confirmation.

### Tool inventory

| Category | Representative tools |
|----------|----------------------|
| Time | `today`, `recentDays`, `monthRange`, `getCurrentTime` |
| Photos | `searchPhotos`, `describePhoto`, `getPhotoTimeline`, `getRecentPhotos`, `getMostVisitedCity`, `getPhotoInsight` |
| Albums | `listAlbums`, `searchAlbumByName` |
| Anniversaries | `queryAnniversaries`, `searchAnniversaryByName`, `getCountdownByName`, `searchAnniversariesByMonth`, `getAnniversaryStats`, `collectAnniversaryField`, `checkAnniversaryDraft`, `prepareCreateAnniversary`, `confirmCreateAnniversary` |
| Reminders | `listReminders`, `prepareSetReminder`, `confirmSetReminder` |
| User and partner | `getUserStats`, `getMyProfile`, `getPartnerInfo`, `prepareUpdateNickname`, `prepareUpdatePhone`, `prepareUpdateEmail` plus their `confirm*` counterparts |
| Mood and reports | `getTodayMood`, `recordMood`, `generateWeeklyReport`, `generateMonthlyReport`, `generateYearlyReport`, `getCoupleMilestones` |
| Export | `listExportHistory`, `getExportStatus`, `prepareCreateExport`, `confirmCreateExport` |
| Gifts and settings | Gift recommendations, notification settings (`GiftRecommendTool`, `NotificationSettingsTool`) |

> Tools evolve between releases; the implementations under `LoveMap/src/main/java/com/example/lovemap/ai/tool/` are the source of
> truth.

### SSE event format

Streaming chat (`POST /api/ai/chat/stream`) returns `text/event-stream`:

```text
event: chunk
data: {"text":"..."}

event: done
data: {}

event: error
data: {"message":"..."}
```

Notification push (`/api/notifications/subscribe`) and makeover progress (`/api/makeover/{id}/stream`) follow the same SSE
convention. Nginx disables `proxy_buffering` for all three paths, otherwise streamed output would arrive in one burst.

### AI makeover advice

A two-stage asynchronous pipeline whose task state machine is `0 pending / 1 analysing / 2 rendering / 3 done / 4 failed / 5 cancelled`:

1. **Analysis**: a multimodal model reads the portrait and returns face shape, skin tone and facial features together with
   scene-specific styling advice (makeup, hair, outfit, perfume, accessories, plus an overall summary and application steps).
2. **Rendering**: `wanx2.1-imageedit` produces a restyled portrait from that advice. Advice and image are persisted in
   `makeover_record`, sub-task progress in `makeover_task`, and the frontend follows along over SSE.
3. **Quota**: regular users get `makeover.monthly-free-quota` requests per month (3 by default); VIP tiers add more
   (week +2 / month +6 / quarter +9 / year +13 / lifetime unlimited).
4. **Sharing**: `POST /api/makeover/{id}/share` sends the result to the couple chat as a card message (`msg_type=5`).

## Database

- Database `lovemap`, charset `utf8mb4`. MyBatis maps snake_case columns to camelCase; the SQL mappings live in
  `LoveMap/src/main/resources/mapper/`.
- Bootstrap: containers run `deploy/mysql/init/01_lovemap.sql` (a full dump snapshot) on first start; import the same file locally.
- Incremental scripts: `LoveMap/sql/migrate/V1 ~ V17` run in filename order. They contain only `ALTER` / `CREATE` statements and
  assume the base tables already exist.

| Table | Purpose |
|-------|---------|
| `user` | Users (partner-binding fields, gender, `vip_level` / `vip_expire_at`) |
| `group` | Couple groups (group UUID plus both user IDs) |
| `photo` / `album` / `photo_album` | Photos, albums and the photo-album relation |
| `anniversary` / `anniversary_reminder` | Anniversaries and their reminder schedule (N days ahead, sent flag) |
| `mood_log` | Mood check-ins (unique per group, user and date) |
| `chat_message` / `chat_message_delete` | Chat messages and per-user “delete for me” records |
| `notification` | In-app notifications |
| `export_record` | Export task records |
| `date_plans` | Date plans (budget tier, status, linked photos) |
| `wishlist_items` | Wishlist entries (category, priority, progress, achievement photo, wish card) |
| `things` / `things_completion` / `things_url` / `things_url_rel` | The 100 must-do list, completion records and image assets |
| `provence` | Province dictionary (used by footprint/city statistics) |
| `ai_chat_session` / `ai_chat_message` | AI sessions and messages |
| `makeover_record` / `makeover_task` | Makeover records and async sub-tasks |
| `ai_copy_feedback` | Accepted/rejected AI copy feedback (migration V6) |
| `vip_order` | VIP orders (migration V17) |

> **Note**: `deploy/mysql/init/01_lovemap.sql` is a point-in-time full dump. If the snapshot lacks `user.vip_level`,
> `user.vip_expire_at`, `vip_order` or `ai_copy_feedback` (the structures introduced by migrations V6 / V16 / V17), run the missing
> migration scripts in filename order after importing, otherwise VIP and copy-feedback features will not work.

## Deployment

### Docker Compose (recommended)

```bash
cp .env.example .env
docker compose up -d --build
docker compose logs -f backend
```

Orchestration details:

- MySQL and Redis both define health checks, and `backend` starts only once they are healthy
  (`depends_on: condition: service_healthy`).
- Every container runs with `TZ=Asia/Shanghai`.
- The `uploads-data` and `exports-data` volumes hold local uploads and export artifacts, so data survives container rebuilds.

### Nginx reverse proxy

The containerised `deploy/nginx/default.conf` matches the semantics of the local `nginx-1.28.0/conf/nginx.conf`. Key rules:

| Path | Handling |
|------|----------|
| `/` | SPA static assets with a `try_files ... /index.html` fallback; `index.html` is never cached while hashed chunks are cached long-term |
| `/api/` | Reverse proxy to the backend cluster (`least_conn` + keepalive) with Gzip and proxy caching, `client_max_body_size 200M` |
| `/api/notifications/subscribe`, `/api/ai/chat/stream`, `/api/makeover/*/stream` | `proxy_buffering` and caching disabled, `proxy_read_timeout 1800s` |
| `/ws/` | WebSocket upgrade (`Upgrade` / `Connection` headers), `proxy_read_timeout 3600s` |
| `/uploads/` | Proxied to the backend `/api/uploads/` (used when `file.storage=local`), cached for 7 days |

> The `ai_cluster` (FastAPI) upstream still present in the local config is legacy: AI now lives inside Spring Boot and no traffic is
> forwarded to it.

## Troubleshooting

| Symptom | What to check |
|---------|---------------|
| Photo/avatar upload fails | Verify `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET`; with `file.storage=oss` uploads depend entirely on OSS |
| AI endpoints answer “feature disabled” | Make sure `AI_ENABLED=true` and `DASHSCOPE_API_KEY` is set |
| VIP activation returns 403 | `VIP_ADMIN_TOKEN` is unset, or the `X-Vip-Admin-Token` header does not match it |
| Streaming output arrives all at once | Nginx is not disabling `proxy_buffering` for the SSE paths |
| Endpoints return 404 | The backend context path is `/api`; the frontend must call through that prefix |
| Large uploads return 413 | Raise both `spring.servlet.multipart.max-*` and Nginx `client_max_body_size` |
| A fresh database lacks the VIP tables | Run the missing scripts from `sql/migrate` as described in the Database section |
| Cross-origin errors in local development | Use the `/api` dev proxy from `npm run dev`, or point `VITE_API_BASE_URL` at the backend |

## Documentation and Conventions

- More detailed frontend/backend development docs, AI layer design, deployment notes and optimisation records live in the local
  `docs/` directory (it is listed in `.gitignore` and therefore not committed). Some of them were written when AI ran on FastAPI;
  where they disagree with today’s code, the backend code wins.
- The backend follows `controller → service → mapper`; AI tools live under `ai/tool/`; frontend requests all go through
  `src/utils/request.ts`.
- Before committing, at minimum make sure the backend compiles (`mvn -q compile`) and the frontend builds
  (`npm run build`, which includes the `vue-tsc` type check).

## License

MIT
