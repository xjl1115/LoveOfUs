# LoveMap

情侣专属的相册与纪念日管理应用。Spring Boot + Vue3 全栈，支持照片时间线、AI 智能整理、纪念日提醒、伴侣绑定等能力。

## 技术栈

### 后端（LoveMap/）
- **Spring Boot 4.0.6** + Java 25
- **MyBatis** + MySQL + Redis
- **JWT** 鉴权 + **Aliyun OSS** 对象存储
- **WebSocket**（伴侣聊天）
- **LangChain4j 1.18** + 通义千问（DashScope）— LLM 接入

### 前端（loveofus-frontend/）
- **Vue 3** + **TypeScript** + **Vite 5**
- **Pinia** + **Vue Router**
- **Vant 4**（移动端组件库）
- **ECharts**（可视化）
- **Sass** + **axios**

### 部署（nginx-1.28.0/）
- Nginx 反向代理 + 静态资源托管

## 目录结构

```
LoveOfUs/
├── LoveMap/                       # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/example/lovemap/
│       ├── ai/                    # AI 模块（LangChain4j + DashScope）
│       │   ├── config/            # LangChain4j Bean 装配
│       │   ├── controller/        # /api/ai/chat, /api/ai/chat/stream
│       │   ├── dto/               # 请求/响应体
│       │   ├── exception/         # AI 关闭异常
│       │   └── service/           # 业务封装
│       ├── chat/                  # WebSocket 聊天
│       ├── common/                # 通用工具与返回体
│       ├── config/                # Spring 配置类
│       ├── controller/            # REST 控制器
│       ├── mapper/                # MyBatis Mapper
│       ├── model/                 # DTO / Entity / VO
│       ├── service/               # 业务 Service
│       └── utils/                 # 工具类
│
├── loveofus-frontend/             # Vue3 前端
│   ├── package.json
│   └── src/
│       ├── api/                   # API 封装（含 aiChat.ts）
│       ├── components/            # 通用组件（含 AiFloatBtn.vue）
│       ├── composables/           # 组合式函数
│       ├── data/                  # 静态数据
│       ├── directives/            # 自定义指令
│       ├── router/                # 路由
│       ├── stores/                # Pinia 状态
│       ├── styles/                # 全局样式
│       ├── types/                 # 类型定义
│       ├── utils/                 # 工具（含 sse.ts）
│       └── views/                 # 页面（含 AIChat.vue）
│
└── nginx-1.28.0/                  # Nginx 部署
    ├── conf/nginx.conf
    └── html/                      # 静态资源（含打包后的 dist/）
```

## 本地开发

### 后端启动

```bash
cd LoveMap

# 1. 配置 application.yml 或环境变量
#    - spring.datasource.* (MySQL)
#    - spring.redis.* (Redis)
#    - aliyun.oss.* (OSS)
#    - ai.dashscope.api-key (DASHSCOPE_API_KEY)

# 2. 初始化数据库
mysql -u root -p < sql/chat_message.sql   # 其他建表语句按 sql/ 目录执行

# 3. 启动
mvn spring-boot:run
```

默认监听 `http://localhost:8080`。

### 前端启动

```bash
cd loveofus-frontend
npm install
npm run dev
```

默认监听 `http://localhost:5173`。

### 生产构建

```bash
cd loveofus-frontend
npm run build          # 产物在 dist/

# 复制到 nginx
rm -rf ../nginx-1.28.0/html/assets/*
rm -f ../nginx-1.28.0/html/index.html ../nginx-1.28.0/html/favicon.svg
cp -r dist/* ../nginx-1.28.0/html/
```

## AI 模块说明

当前阶段仅接入 LLM（通义千问），不含 Tools。

### 配置

```yaml
ai:
  enabled: true                 # 总开关
  dashscope:
    api-key: sk-xxxxxx          # 通义千问 API Key
    model-name: qwen-plus       # 默认模型
    temperature: 0.7
    max-tokens: 1500
```

### 接口

| 接口 | 说明 |
|------|------|
| `POST /api/ai/chat` | 非流式，返回完整 AI 回复 |
| `POST /api/ai/chat/stream` | SSE 流式，逐 chunk 返回 |

SSE 帧格式：

```
event: chunk
data: {"text":"..."}

event: done
data: {}

event: error
data: {"message":"..."}
```

### 关闭 AI

`ai.enabled=false` 时所有 AI 接口返回 503，应用其他功能不受影响。

## 路由权限

未登录用户访问任意页面均重定向到 `/login`。除登录/注册外的接口需要 JWT。

## License

MIT