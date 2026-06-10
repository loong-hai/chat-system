# 第一阶段：构建前端
FROM node:20-alpine AS frontend-builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY index.html .
COPY vite.config.ts .
COPY tsconfig*.json ./
COPY public ./public
COPY src ./src
COPY .env.production ./

RUN npm run build

# 第二阶段：Nginx 托管
FROM nginx:alpine

COPY --from=frontend-builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]