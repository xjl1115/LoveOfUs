# LoveOfUs 项目开发文档总览

## 项目简介

LoveOfUs 是一款情侣游玩照片存储与分享 Web 应用，支持照片按时间线组织管理，可按时间范围导出照片。

### 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                    客户端 (手机/桌面浏览器)                    │
├─────────────────────────────────────────────────────────────┤
│                     Nginx 负载均衡                           │
├──────────────────┬──────────────────┬───────────────────────┤
│    Vue 3 前端     │   Spring Boot    │    FastAPI AI 层      │
│   (H5 适配)       │   后端 API        │    (照片智能分析)      │
├──────────────────┴──────────────────┴───────────────────────┤
│                  MySQL + Redis + MinIO                       │
└─────────────────────────────────────────────────────────────┘
```

| 层 | 技术 | 文档 |
|----|------|------|
| 前端 | Vue 3 + Vite + TypeScript + Vant | [01-前端开发文档](01-前端开发文档.md) |
| 后端 | Spring Boot 4.0 + MyBatis + MySQL | [02-后端开发文档](02-后端开发文档.md) |
| AI 层 | FastAPI + PyTorch + CLIP + FaceNet | [03-AI层开发文档](03-AI层开发文档.md) |
| 部署 | Nginx + Docker Compose | [04-部署与Nginx配置文档](04-部署与Nginx配置文档.md) |

---

## 页面清单（共 7 页）

| # | 页面 | 路径 | 页面功能概要 |
|---|------|------|-------------|
| 1 | 登录/注册 | `/login` | 情侣主题欢迎页，支持手机/邮箱登录注册、情侣绑定码 |
| 2 | 首页时间线 | `/home` | 照片按年月分组瀑布流，下拉刷新、上拉加载、切换视图模式 |
| 3 | 照片详情 | `/photo/:id` | 大图查看、双指缩放、元数据展示、AI 标签、相邻照片导航 |
| 4 | 照片上传 | `/upload` | 批量上传 20 张、EXIF 解析、GPS 定位、标签推荐 |
| 5 | 相册管理 | `/albums` | 手动/AI 智能相册、照片分组、拖拽排序 |
| 6 | 照片导出 | `/export` | 时间范围选择、ZIP/PDF/视频导出、进度跟踪 |
| 7 | 个人中心 | `/profile` | 情侣信息、统计、足迹地图、设置 |

---

## 核心功能模块

| 功能模块 | 前端 | 后端 | AI 层 |
|----------|------|------|-------|
| 用户认证 | 登录/注册表单 | JWT + Spring Security | - |
| 情侣绑定 | 绑定码输入 | 双向绑定逻辑 | - |
| 照片上传 | 批量上传组件 | MinIO 存储 + EXIF 解析 | 场景/标签/情绪分析 |
| 时间线 | 瀑布流 + 时间线分组 | 按年月分页查询 | - |
| 相册管理 | 相册 CRUD | 照片-相册关联 | 智能聚类推荐 |
| 照片导出 | 时间选择器 + 选项 | ZIP/PDF 异步生成 | Slideshow 配乐 |
| 统计地图 | ECharts 地图 | 城市统计 | - |

---

## 数据库表结构（6 张表）

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| `user` | 用户 | phone, email, password_hash, partner_id, bind_code |
| `photo` | 照片 | user_id, taken_date, location, description, ai_tags |
| `album` | 相册 | user_id, name, cover_photo_id, is_ai_generated |
| `photo_album` | 关联 | photo_id, album_id |
| `tag` | 标签 | name, type |
| `photo_tag` | 关联 | photo_id, tag_id, source |
| `export_record` | 导出记录 | user_id, date_range, format, status, file_path |

---

## 开发环境启动

```bash
# 前端
cd loveofus-frontend
npm install
npm run dev

# 后端
cd loveofus-backend
mvn spring-boot:run

# AI 层
cd loveofus-ai
pip install -r requirements.txt
uvicorn app.main:app --reload

# 数据库（Docker）
docker-compose up -d mysql redis minio
```
