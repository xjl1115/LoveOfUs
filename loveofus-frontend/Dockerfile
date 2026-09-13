# 阶段1：Node 构建 dist
FROM node:22-alpine AS build
WORKDIR /app
# 项目 .npmrc 是 Windows 缓存路径，已在 .dockerignore 排除；
# 如构建拉包慢，取消下行注释改用国内镜像
# RUN npm config set registry https://registry.npmmirror.com
ENV NPM_CONFIG_CACHE=/tmp/npm-cache
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# 阶段2：nginx 托管静态文件（站点配置由 compose 挂载）
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
