# 💬 Chat-System — 企业级实时聊天系统

基于 **Spring Boot 3.5 + Vue 3 + Docker** 的全栈即时通讯平台，支持高并发实时消息、AI 智能摘要、文件存储等企业级功能，采用微服务架构设计，全容器化一键部署。

## 🧩 技术架构

```
┌────────────────────────────────────────────────────┐
│                     Nginx (前端)                     │
│                   Vue 3 + Element Plus              │
│              Pinia · WebSocket · IndexedDB           │
└───────────────────────┬────────────────────────────┘
                        │ HTTP / WebSocket
┌───────────────────────▼────────────────────────────┐
│               Spring Boot 3.5 (2实例)                │
│        Spring Security · JWT · STOMP WebSocket       │
│         RabbitMQ 消息队列 · 分布式在线状态管理         │
└───┬──────────┬──────────┬──────────┬───────────────┘
    │          │          │          │
┌───▼──┐ ┌────▼───┐ ┌───▼───┐ ┌───▼────┐
│MySQL │ │  Redis │ │MongoDB│ │ MinIO  │
│8.0   │ │  会话/  │ │ 消息  │ │ S3存储 │
│ 关系  │ │  缓存   │ │ 持久化│ │  文件  │
└──────┘ └────────┘ └───────┘ └────────┘
                        ┌──────────┐
                        │  Ollama  │
                        │ AI智能助手 │
                        └──────────┘
```

## ✨ 核心特性

| 模块 | 特性 |
|------|------|
| 💬 **实时通讯** | WebSocket (STOMP) 长连接，支持私聊/群聊，在线状态实时同步 |
| 📁 **文件存储** | MinIO (S3兼容) 分布式文件存储，预签名URL直传，图片自动压缩缩略图 |
| 🤖 **AI 集成** | Ollama 接入，支持聊天记录自动摘要、关键信息提取、智能回复建议 |
| 🔐 **安全认证** | Spring Security + JWT 无状态认证，WebSocket 握手鉴权 |
| ☁️ **消息可靠性** | RabbitMQ 消息队列解耦，消息异步持久化到 MongoDB，重试机制 |
| 📱 **离线消息** | 前端 IndexedDB (Dexie.js) 本地缓存，消息不丢失 |
| ⚡ **性能优化** | HikariCP 连接池、JPA 批量操作、Redis 缓存、G1GC 调优 |
| 🐳 **容器化部署** | Docker Compose 12个服务编排，多阶段构建，一键启动 |

## 🚀 快速开始

### 前置要求

- **Docker** & **Docker Compose** ≥ v2.0
- 可用内存 ≥ 16GB（推荐 32GB）
- 磁盘空间 ≥ 10GB

### 1. 克隆项目

```bash
git clone https://github.com/loong-hai/chat-system.git
cd chat-system
```

### 2. 拉取基础镜像（首次）

```bash
docker pull mysql:8.0
docker pull redis:7-alpine
docker pull mongo:7
docker pull rabbitmq:4-management-alpine
docker pull minio/minio:latest
docker pull nginx:alpine
docker pull ollama/ollama:latest
docker pull maven:3.9.14-eclipse-temurin-21
docker pull eclipse-temurin:21-jre-ubi9-minimal
docker pull node:20-alpine
```

### 3. 启动全部服务

```bash
cd docker
docker compose up -d
```

首次启动会自动构建前后端镜像（后端需约3-5分钟编译），之后启动只需约30秒。

### 4. 访问

| 服务 | 地址 | 说明 |
|------|------|------|
| 聊天系统 | http://localhost | 主前端页面 |
| MinIO 控制台 | http://localhost:9001 | 对象存储管理 |
| RabbitMQ 管理 | http://localhost:15672 | 消息队列监控 |
| Portainer | http://localhost:9000 | 容器管理面板 |
| DbGate | http://localhost:3000 | 数据库客户端 |

### 5. 注册使用

访问 http://localhost，注册账号后即可开始聊天。

## 📁 项目结构

```
chat-system/
├── backend/                    # Spring Boot 3.5 后端
│   ├── src/main/java/...
│   │   ├── ai/                 # AI 服务（Ollama集成）
│   │   ├── config/             # Spring配置（Security/WebSocket/Redis/S3）
│   │   ├── controller/         # REST API 控制器
│   │   ├── model/              # 实体/DTO/VO
│   │   ├── repository/         # JPA + MongoDB Repository
│   │   ├── security/           # JWT 认证过滤
│   │   ├── service/            # 业务逻辑层
│   │   │   ├── cache/          # 用户会话/在线状态
│   │   │   ├── message/        # 消息路由/持久化/分发
│   │   │   ├── storage/        # S3文件存储
│   │   │   └── websocket/      # WebSocket管理
│   │   └── utils/              # 工具类
│   └── Dockerfile
├── frontend/                   # Vue 3 + TypeScript 前端
│   ├── src/
│   │   ├── api/                # API 接口层
│   │   ├── components/         # 通用组件（气泡/窗口/面板）
│   │   ├── db/                 # IndexedDB 离线存储
│   │   ├── router/             # 路由 + 权限守卫
│   │   ├── services/           # WebSocket 服务
│   │   ├── stores/             # Pinia 状态管理
│   │   └── views/              # 页面（登录/注册/聊天）
│   └── Dockerfile
├── docker/                     # 容器编排
│   ├── docker-compose.yml      # 12服务编排文件
│   ├── nginx/nginx.conf        # 前端反向代理
│   ├── mysql/init/             # 数据库DDL
│   └── minio/init/             # MinIO初始化
└── tests/                      # JMeter 压力测试
```

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.5.9, Java 21 |
| **即时通讯** | WebSocket (STOMP), SockJS, RabbitMQ |
| **数据库** | MySQL 8.0 (关系型), MongoDB 7 (消息), Redis 7 (缓存/会话) |
| **对象存储** | MinIO (S3 API 兼容) |
| **安全** | Spring Security, JWT 无状态认证 |
| **AI** | Ollama (本地大模型) |
| **前端** | Vue 3.5, TypeScript, Vite 7, Pinia, Element Plus |
| **离线存储** | IndexedDB (Dexie.js) |
| **容器化** | Docker Compose, 多阶段构建 |
| **监控** | Portainer, RabbitMQ Dashboard |

## 📊 容器服务清单

| 服务 | 副本数 | 端口 | 用途 |
|------|--------|------|------|
| chat-system-backend-1 | 1 | 8080 | 后端实例1 |
| chat-system-backend-2 | 1 | 8081 | 后端实例2（高可用） |
| chat-front | 1 | 80 | Nginx + Vue 前端 |
| mysql | 1 | 3306 | 用户/好友/文件元数据 |
| mongodb | 1 | 27017 | 聊天消息持久化 |
| redis | 1 | 6379 | 会话Token/在线状态 |
| rabbitmq | 1 | 5672/15672 | 消息队列 |
| minio | 1 | 9000/9001 | 文件对象存储 |
| ollama | 1 | 11434 | AI 模型服务 |
| portainer | 1 | 9000 | Docker 可视化管理 |
| dbgate | 1 | 3000 | 数据库Web客户端 |

## 🔧 本地开发

```bash
# 后端
cd backend
./mvnw spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```

本地开发需确保 MySQL、Redis、MongoDB、RabbitMQ、MinIO 等中间件可用（可使用 `docker compose up -d mysql redis mongodb rabbitmq minio` 仅启动基础中间件）。