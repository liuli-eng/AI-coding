# AI 编程助手

企业内部 AI 编程助手，包含用户端、管理端和 Java 业务后端。

## 目录

- `web/`：Vue 3 + TypeScript + Vite 用户端，已将产品品牌改为“AI 编程助手”。
- `admin/`：Vue 3 + TypeScript + Vite 管理后台。
- `backend/`：Java/Spring Boot 业务后端源码。
- `ai-service/`：Python FastAPI 模型、RAG 和异步任务服务。
- `desktop/`：Electron Windows/macOS 桌面客户端壳，复用 `web/` 页面。

## 产品范围

用户端支持多轮编程问答、代码分析、会话历史、知识库问答和流式回答；管理端用于模型、Prompt、知识库、文档、用户权限、调用日志与异常反馈管理。

## 技术栈

本项目采用 Monorepo 组织方式，由用户端、管理端、Java 核心业务后端、Python AI 服务和桌面客户端组成。

### 用户端

- Vue 3、TypeScript、Vite
- Element Plus、Vue Element Plus X
- Pinia、Vue Router、VueUse
- ECharts、UnoCSS、SCSS
- pnpm

### 管理端

- Vue 3、TypeScript、Vite
- Vue Vben Admin、Ant Design Vue
- Pinia、Vue Router、Vue Flow
- ECharts、AntV G6、TinyMCE
- pnpm workspace、Turbo

### Java 业务后端

- Java 17、Spring Boot 3.5
- Maven 多模块工程
- MyBatis Plus、动态数据源、MySQL
- Sa-Token、JWT
- Redis、Redisson、Lock4j
- SpringDoc、Knife4j
- SnailJob、WarmFlow、Spring Boot Admin
- SSE、WebSocket

### AI 与 RAG

- LangChain4j、LangGraph4j
- OpenAI、Anthropic、Ollama、通义千问、智谱 AI 等模型适配
- MCP、Agent、Skills、文档解析和 RAG 检索
- Milvus、Weaviate、Qdrant 等向量数据库适配
- Python、FastAPI、Uvicorn
- Celery 异步任务、Redis 消息与缓存
- PostgreSQL 16、pgvector、asyncpg

### 桌面端与部署

- Electron、electron-builder，支持 Windows 和 macOS
- Docker、Docker Compose
- PostgreSQL、MySQL、Redis

## 运行方式

请分别进入 `backend`、`admin` 和 `web`，按各目录 README 安装依赖并启动。Node 项目使用 pnpm；后端使用项目提供的 Maven/Gradle 配置。

桌面端复用 `web` 页面与业务逻辑，使用 Electron + electron-builder 支持 Windows/macOS 打包、签名和自动更新配置。

根目录 `docker-compose.yml` 已提供 PostgreSQL + pgvector、Redis、FastAPI 和 Celery worker 的开发环境编排。
