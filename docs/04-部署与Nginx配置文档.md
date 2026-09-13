# LoveOfUs 部署与 Nginx 配置文档

## 一、整体部署架构

```
                        ┌─────────────┐
                        │   用户浏览器   │
                        └──────┬──────┘
                               │ HTTPS :443
                               ▼
                     ┌─────────────────┐
                     │    Nginx (LB)    │
                     │  反向代理/负载均衡  │
                     └────┬────┬────┬───┘
                          │    │    │
                    ┌─────┘    │    └──────┐
                    ▼          ▼           ▼
             ┌──────────┐ ┌──────────┐ ┌──────────┐
             │ Vue3 FE  │ │Vue3 FE   │ │    ...   │
             │ Instance1│ │Instance2 │ │  (更多)  │
             └────┬─────┘ └────┬─────┘ └──────────┘
                  │            │
                  ▼            ▼
             ┌──────────────────────────────┐
             │        Nginx (API Proxy)      │
             │     /api → Spring Boot        │
             └────┬────────────┬─────────────┘
                  │            │
                  ▼            ▼
        ┌────────────────┐  ┌────────────────┐
        │ Spring Boot    │  │   FastAPI AI   │
        │ :8080 (集群 N) │  │   :8000        │
        └────┬───────────┘  └───────┬────────┘
             │                      │
             ▼                      │
        ┌────────────┐              │
        │   MySQL    │              │
        │   :3306    │              │
        └────────────┘              │
             │                      │
             ▼                      │
        ┌────────────┐              │
        │   Redis    │              │
        │   :6379    │              │
        └────────────┘              │
             │                      │
             ▼                      ▼
        ┌──────────────────────────────────┐
        │           MinIO :9000            │
        │          (对象存储集群)            │
        └──────────────────────────────────┘
```

---

## 二、Nginx 配置

### 2.1 主配置文件 `nginx.conf`

```nginx
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
    multi_accept on;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    # 日志格式
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for" '
                    '$upstream_addr $upstream_response_time';

    access_log /var/log/nginx/access.log main;

    # 基础优化
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    keepalive_requests 1000;
    client_max_body_size 200M;  # 支持大文件上传
    client_body_buffer_size 128k;

    # Gzip 压缩
    gzip on;
    gzip_min_length 1k;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript
               image/svg+xml text/xml application/xml application/xml+rss
               text/javascript;
    gzip_vary on;
    gzip_proxied any;
    gzip_disable "msie6";

    # 静态资源缓存
    open_file_cache max=1000 inactive=20s;
    open_file_cache_valid 30s;
    open_file_cache_min_uses 2;
    open_file_cache_errors on;

    include /etc/nginx/conf.d/*.conf;
}
```

### 2.2 前端站点配置 `loveofus.conf`

```nginx
# 前端站点 - 负载均衡
upstream frontend_cluster {
    least_conn;  # 最少连接数策略
    server 127.0.0.1:3001 max_fails=3 fail_timeout=30s;
    server 127.0.0.1:3002 max_fails=3 fail_timeout=30s;
    # 可扩展更多前端节点
}

server {
    listen 80;
    server_name loveofus.com www.loveofus.com;

    # HTTP → HTTPS 重定向
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name loveofus.com www.loveofus.com;

    # SSL 配置
    ssl_certificate /etc/nginx/ssl/loveofus.crt;
    ssl_certificate_key /etc/nginx/ssl/loveofus.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # HSTS
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # 前端静态文件
    root /var/www/loveofus/dist;
    index index.html;

    # Gzip 上传文件类型
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
        access_log off;
        # 如果文件不存在，返回 404 而不是 fallback 到 index.html
        try_files $uri =404;
    }

    # SPA 路由 - 所有非文件请求返回 index.html
    location / {
        try_files $uri $uri/ /index.html;
        expires -1;
        add_header Cache-Control "no-store, no-cache, must-revalidate";
    }

    # API 反向代理到 Spring Boot 集群
    location /api/ {
        proxy_pass http://backend_cluster;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        # 超时配置
        proxy_connect_timeout 60s;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;

        # 大文件上传支持
        client_max_body_size 200M;
        client_body_buffer_size 128k;
        proxy_request_buffering on;
        proxy_buffering on;
        proxy_buffer_size 4k;
        proxy_buffers 8 4k;

        # 缓存静态 API 响应（如缩略图）
        location /api/photos/.*/image {
            proxy_cache PHOTO_CACHE;
            proxy_cache_valid 200 7d;
            proxy_cache_key "$host$request_uri";
            add_header X-Cache-Status $upstream_cache_status;
            proxy_pass http://backend_cluster;
        }
    }

    # AI 层代理
    location /ai/ {
        rewrite ^/ai/(.*) /api/ai/$1 break;
        proxy_pass http://ai_cluster;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
    }

    # 代理 WebSocket（用于导出进度推送）
    location /ws/ {
        proxy_pass http://backend_cluster;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
    }

    # 配置图片缓存
    proxy_cache_path /var/cache/nginx/photo_cache levels=1:2 keys_zone=PHOTO_CACHE:100m
                     max_size=10g inactive=30d use_temp_path=off;
}
```

### 2.3 后端负载均衡配置

```nginx
# 后端 Spring Boot 集群
upstream backend_cluster {
    least_conn;
    server 127.0.0.1:8080 max_fails=3 fail_timeout=30s weight=5;
    server 127.0.0.1:8081 max_fails=3 fail_timeout=30s weight=5;
    server 127.0.0.1:8082 max_fails=3 fail_timeout=30s weight=5;
    keepalive 32;
}

# AI 层 FastAPI 集群
upstream ai_cluster {
    least_conn;
    server 127.0.0.1:8000 max_fails=3 fail_timeout=30s;
    server 127.0.0.1:8001 max_fails=3 fail_timeout=30s;
    keepalive 16;
}
```

---

## 三、Docker Compose 完整部署

### 3.1 `docker-compose.yml`

```yaml
version: '3.8'

networks:
  loveofus-net:
    driver: bridge

volumes:
  mysql-data:
  redis-data:
  minio-data:
  nginx-logs:

services:
  # ==== 数据库 ====
  mysql:
    image: mysql:8.0
    container_name: loveofus-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: loveofus
      MYSQL_CHARACTER_SET_SERVER: utf8mb4
      MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - loveofus-net
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: loveofus-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes
    networks:
      - loveofus-net
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ==== 对象存储 ====
  minio:
    image: minio/minio:latest
    container_name: loveofus-minio
    ports:
      - "9000:9000"   # API
      - "9001:9001"   # Console
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    volumes:
      - minio-data:/data
    command: server /data --console-address ":9001"
    networks:
      - loveofus-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 10s
      retries: 3

  # ==== Spring Boot 后端 ====
  loveofus-backend:
    build:
      context: ./loveofus-backend
      dockerfile: Dockerfile
    container_name: loveofus-backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      minio:
        condition: service_healthy
    networks:
      - loveofus-net
    restart: unless-stopped

  # ==== FastAPI AI 层 ====
  loveofus-ai:
    build:
      context: ./loveofus-ai
      dockerfile: Dockerfile
    container_name: loveofus-ai
    ports:
      - "8000:8000"
    environment:
      - MODEL_PATH=/app/models
      - REDIS_URL=redis://:${REDIS_PASSWORD}@redis:6379/0
    volumes:
      - ./loveofus-ai/models:/app/models
    depends_on:
      redis:
        condition: service_healthy
    networks:
      - loveofus-net
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    restart: unless-stopped

  # ==== Vue 前端 ====
  loveofus-frontend:
    build:
      context: ./loveofus-frontend
      dockerfile: Dockerfile
    container_name: loveofus-frontend
    ports:
      - "3000:80"
    networks:
      - loveofus-net
    restart: unless-stopped

  # ==== Nginx 负载均衡 ====
  nginx:
    image: nginx:alpine
    container_name: loveofus-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - nginx-logs:/var/log/nginx
      - ./loveofus-frontend/dist:/var/www/loveofus/dist:ro
    depends_on:
      - loveofus-frontend
      - loveofus-backend
      - loveofus-ai
    networks:
      - loveofus-net
    restart: unless-stopped
```

---

## 四、前端 Dockerfile

```dockerfile
# loveofus-frontend/Dockerfile
# 构建阶段
FROM node:20-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
RUN npm run build

# 生产阶段
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html

# 前端使用轻量 nginx 提供服务
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## 五、后端 Dockerfile

```dockerfile
# loveofus-backend/Dockerfile
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

---

## 六、数据库初始化

```sql
-- sql/init.sql
CREATE DATABASE IF NOT EXISTS loveofus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE loveofus;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `nickname` VARCHAR(50) NOT NULL,
    `avatar_url` VARCHAR(500),
    `phone` VARCHAR(20) UNIQUE,
    `email` VARCHAR(100) UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `partner_id` BIGINT,
    `bind_code` VARCHAR(20) UNIQUE,
    `bind_code_expire` DATETIME,
    `is_bound` TINYINT DEFAULT 0,
    `relationship_start` DATE,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_phone` (`phone`),
    INDEX `idx_email` (`email`),
    INDEX `idx_bind_code` (`bind_code`),
    INDEX `idx_partner_id` (`partner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 照片表
CREATE TABLE IF NOT EXISTS `photo` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `original_name` VARCHAR(255),
    `storage_path` VARCHAR(500) NOT NULL,
    `thumbnail_path` VARCHAR(500),
    `medium_path` VARCHAR(500),
    `file_size` BIGINT,
    `width` INT,
    `height` INT,
    `mime_type` VARCHAR(50),
    `taken_date` DATE,
    `taken_time` TIME,
    `latitude` DECIMAL(10,7),
    `longitude` DECIMAL(10,7),
    `location_name` VARCHAR(200),
    `city` VARCHAR(50),
    `province` VARCHAR(50),
    `country` VARCHAR(50),
    `description` TEXT,
    `ai_tags` JSON,
    `ai_description` TEXT,
    `ai_emotion` VARCHAR(20),
    `is_deleted` TINYINT DEFAULT 0,
    `deleted_at` DATETIME,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_taken_date` (`taken_date`),
    INDEX `idx_user_date` (`user_id`, `taken_date`, `is_deleted`),
    INDEX `idx_group_date` (`group_id`, `taken_date`, `is_deleted`),
    INDEX `idx_location` (`city`, `province`),
    INDEX `idx_user_deleted` (`user_id`, `is_deleted`),
    INDEX `idx_group_deleted` (`group_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 相册表
CREATE TABLE IF NOT EXISTS `album` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `cover_photo_id` BIGINT,
    `description` VARCHAR(500),
    `is_ai_generated` TINYINT DEFAULT 0,
    `sort_order` INT DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 照片-相册关联表
CREATE TABLE IF NOT EXISTS `photo_album` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `photo_id` BIGINT NOT NULL,
    `album_id` BIGINT NOT NULL,
    `sort_order` INT DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_photo_album` (`photo_id`, `album_id`),
    INDEX `idx_album_id` (`album_id`),
    INDEX `idx_photo_id` (`photo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 标签表
CREATE TABLE IF NOT EXISTS `tag` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL UNIQUE,
    `type` VARCHAR(20) DEFAULT 'manual',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 照片-标签关联表
CREATE TABLE IF NOT EXISTS `photo_tag` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `photo_id` BIGINT NOT NULL,
    `tag_id` BIGINT NOT NULL,
    `source` VARCHAR(10) DEFAULT 'manual',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_photo_tag` (`photo_id`, `tag_id`),
    INDEX `idx_photo_id` (`photo_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 导出记录表
CREATE TABLE IF NOT EXISTS `export_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `start_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `photo_count` INT,
    `format` VARCHAR(20),
    `status` VARCHAR(20) DEFAULT 'pending',
    `file_path` VARCHAR(500),
    `file_size` BIGINT,
    `options` JSON,
    `error_message` TEXT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `completed_at` DATETIME,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 七、部署命令

```bash
# 1. 克隆代码
git clone https://github.com/your-org/loveofus.git
cd loveofus

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填写密码和密钥

# 3. 构建前端
cd loveofus-frontend
npm install
npm run build
cd ..

# 4. 构建后端
cd loveofus-backend
mvn clean package -DskipTests
cd ..

# 5. 启动所有服务
docker-compose up -d

# 6. 查看状态
docker-compose ps
docker-compose logs -f nginx

# 7. 初始化 MinIO Bucket
docker exec loveofus-minio mc config host add myminio http://localhost:9000 ${MINIO_ACCESS_KEY} ${MINIO_SECRET_KEY}
docker exec loveofus-minio mc mb myminio/loveofus
docker exec loveofus-minio mc policy set download myminio/loveofus
```

---

## 八、性能优化建议

| 优化项 | 方案 | 说明 |
|--------|------|------|
| Nginx Worker | `worker_processes auto` | 自动匹配 CPU 核心数 |
| 静态资源 | CDN + 30 天缓存 | 减少 Nginx 负载 |
| 图片 CDN | 缩略图走 Nginx 缓存 | `proxy_cache` 7 天有效 |
| 数据库连接池 | HikariCP 默认 + 调优 | 最大连接数根据并发调整 |
| Redis 缓存 | 缓存热门时间线数据 | 减少数据库查询 |
| 图片压缩 | 上传时自动压缩为 WebP | 减少存储和带宽 |
| 前端懒加载 | 路由懒加载 + 图片懒加载 | 减少首屏加载时间 |
| SSL 会话复用 | SSL Session Cache | 减少 SSL 握手开销 |

---

## 九、监控与日志

| 工具 | 用途 |
|------|------|
| ELK / Loki | 日志集中管理 |
| Prometheus + Grafana | 性能监控 |
| Sentry | 前端错误追踪 |
| Spring Boot Actuator | 后端健康检查 |
| Nginx Amplify | Nginx 性能监控 |

---

## 十、环境变量模板 `.env`

```bash
# 数据库
DB_PASSWORD=your_secure_password

# Redis
REDIS_PASSWORD=your_redis_password

# MinIO
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=your_minio_secret_key

# JWT
JWT_SECRET=your_jwt_secret_key_min_32_chars_long

# 域名
DOMAIN=loveofus.com

# AI 层
AI_FASTAPI_URL=http://loveofus-ai:8000
```
