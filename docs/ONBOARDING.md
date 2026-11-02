# 项目上手手册（AI 编程助手）

> 面向第一次接触本仓库的开发者。读完这一篇能回答三个问题：**这堆代码分几块、一次请求怎么走、我要改的东西该落在哪个文件**。
> 生成时间：2026-11-02；基于 `main` 分支 `b47cefb`。

---

## 0. 一句话定位

企业内部「AI 编程助手」：Monorepo，四个可运行体 + 一个桌面壳。

| 运行体 | 目录 | 栈 | 端口 | 角色 |
| --- | --- | --- | --- | --- |
| 用户端 | `web/` | Vue3 + Vite + Element Plus + hook-fetch(SSE) | 5173 | 聊天、知识库、我的课程、媒体工作台、应用市场 |
| 管理端 | `admin/` | Vue Vben Admin 5.5.9 + Ant Design Vue + Turbo | 5666 | 模型/Prompt/知识库/课程/问答记录/工作流/MCP/监控 |
| Java 后端 | `backend/` | Java 17 + Spring Boot 3.5 + MyBatis-Plus + Sa-Token | **6039** | 鉴权、会话、RAG、Agent、Coding Harness、工作流 |
| Python AI 服务 | `ai-service/` | FastAPI + Celery | 8001 | **目前是骨架**（RAG 返回空数组，见 §8） |
| 桌面壳 | `desktop/` | Electron | — | 加载 `web/` 页面，仅 3 个源文件 |

外部依赖：MySQL 8（分库 `aicoding-assistant` + SnailJob）、Redis、MinIO/OSS、可选的 Milvus/Qdrant/Weaviate（向量库）、Docker（Coding Harness 沙箱）。

规模参考：Java 1571 个文件（1441 main / 130 test，约 20 万行），`aicoding-chat` 单模块 699 文件 / 6.8 万行，其中 `service/coding/harness` 就有 **254 文件 / 2.95 万行**——这是本项目最需要单独理解的一块。

---

## 1. 目录地图（改代码先看这张表）

```
AI-coding/
├── web/                    # 用户端（Vue3）
│   ├── src/api/            # 每个业务一个目录：index.ts 请求 + types.ts 类型
│   ├── src/pages/          # chat / knowledge / learning / media / app-market
│   ├── src/stores/modules/ # chat / session / agent / model / user / files ...
│   ├── src/utils/request.ts# 统一注入 Bearer + ClientID，401 登出、403 跳转、SSE 解码
│   ├── scripts/*.mjs       # 浏览器回归脚本（CDP 驱动，见 §7）
│   └── CLAUDE.md           # 前端约定（pnpm 11.0.9 / Node>=22.13 / antfu eslint）
├── admin/                  # 管理端（Vben monorepo）
│   └── apps/web-antd/src/{views,api}/...   # views/knowledge、views/learning、views/chat…
├── backend/                # Java 多模块
│   ├── aicoding-admin/     # 启动模块：RuoYiAIApplication + application*.yml
│   ├── aicoding-common/    # 基础设施：satoken、mybatis、chat 领域、向量库适配
│   ├── aicoding-extend/    # 扩展服务
│   ├── aicoding-modules/
│   │   ├── aicoding-chat/          # ★ 主战场：chat/knowledge/learning/agent/mcp/shortdrama/coding
│   │   ├── aicoding-system/        # 用户、角色、菜单、租户、OSS、监控
│   │   ├── aicoding-workflow/      # WarmFlow 流程引擎（定义/实例/任务）
│   │   ├── aicoding-assistantflow/ # AI 节点编排（aiflow）
│   │   └── aicoding-generator/     # 代码生成器
│   ├── docs/script/sql/            # 全量 ruoyi-ai.sql + update/ 增量迁移 + tests/ 校验脚本
│   └── scripts/sandbox/            # Coding Harness 的可信沙箱镜像构建脚本
├── ai-service/app/         # main.py / model.py / rag.py / tasks.py（骨架）
├── docs/                   # 阶段验收记录（phase1 / 课程资料 / 知识库状态）
└── docker-compose.yml      # postgres+pgvector / redis / ai-service / ai-worker
```

后端包结构（`aicoding-chat`）：

```
org.ruoyi
├── controller/{chat,knowledge,learning,agent,mcp,coding,shortdrama,media}
├── service/{chat,knowledge,learning,retrieval,vector,embed,rerank,coding,agent,media,...}
├── websocket/chat/         # 小程序端 WebSocket 通道（MpChatWebSocketHandler）
├── domain/{bo,vo,entity}/  # 入参 BO / 出参 VO / 表实体
├── mapper/                 # MyBatis-Plus Mapper
└── observability/argtrace  # 链路追踪（trace 表、payload 截断）
```

---

## 2. 三条主业务链路

### 链路 A：普通聊天 / 知识库问答（用户端主流程）

```
web: chat store → POST /chat/send (SSE)
  → ChatController#send
  → ChatServiceFacade#sseChat                      @ service/chat/impl/ChatServiceFacade.java:1097行
      ├─ handleWorkflowChat   应用市场工作流
      ├─ handleAgentChat      智能体（注入 agent 配置、skill）
      └─ handleModelChat      直连模型
           ├─ buildModelChatMessages  组装 system prompt（含 Prompt 模板快照）+ 历史 + 当前问题
           ├─ collectKnowledgeIds     取知识库：会话级绑定优先，其次智能体/请求
           ├─ buildMultiKnowledgeAugmentor  → RetrievalAugmentor（多库检索 + rerank）
           └─ StreamingChatResponseHandler → SSE 推给前端，落库 chat_message
```

- 会话知识库绑定：`ChatSession.knowledgeId` / `tenantId` / `courseMaterialIds`；权限守卫在 `service/chat/ChatSessionOwnershipGuard.java`，越权/跨租户/已删除库统一报「该知识库暂不可用」。
- 检索链路目录：`service/retrieval`（检索）、`service/vector`（向量库适配）、`service/embed`（Embedding）、`service/rerank`（重排）、`service/knowledge`（切分、附件、状态）。
- 知识库状态（`knowledge_info.status`）在检索缓存读取前和检索完成后各校验一次，停用不能降级成普通问答。
- 小程序端走 `websocket/chat/MpChatWebSocketHandler`，与 HTTP 共用同一套知识库授权。

### 链路 B：学习 / 课程资料

```
admin: views/learning/course → /system/course (+ /materials 上传)
web:   pages/learning → MyLearningController（加入课程、章节完成度）
问答:  ChatSessionOwnershipGuard 在学习模式下把课程最新附件全文注入上下文
```

课程资料（`learning_course_material`）存在 OSS/MinIO，下载需 `learning:course:query`；后台保存后，已加入的学习记录和新提问都读最新附件。

### 链路 C：Coding Harness（自研编程智能体，本项目技术含量最高处）

```
POST /coding/harness/sessions                        建会话（模型、工作区、权限上限、审批策略）
POST /coding/harness/sessions/{id}/runs              提交需求 → 建 run
GET  .../runs/{runId}/events/stream                  订阅事件流（SSE，Last-Event-ID 断点续传）
POST .../runs/{runId}/approvals/{approvalId}/resolve 人工批准工具调用
POST .../runs/{runId}/plan/approve                   人工批准执行计划
POST .../runs/{runId}/cancel | resume | inputs       取消 / 恢复 / 补充输入
GET  .../sessions/{id}/messages                      读会话转录
```

包职责（`service/coding/harness/`，254 文件）：

| 包 | 职责 |
| --- | --- |
| `app/` | 应用服务：`CodingHarnessApplicationService` 聚合所有用例（建会话/建 run/审批/计划/取消/恢复） |
| `runtime/`, `store/` | 调度与持久化：`HarnessScheduler`、`HarnessRunProcessor`、`DurableHarnessRunProcessor`、`FileHarnessStore`（文件即真相源） |
| `loop/` | 模型回合循环：流式适配、工具批次执行、协议校验、消息映射 |
| `tool/` | 工具注册与策略：`ToolPolicyEngine`、`ToolCapability`、`builtin/`（read_file、read_source、list_files、glob_files、search_text、git_status、git_diff、write_file、replace_text）、`command/`（execute_process + Docker 沙箱） |
| `approval/` | 工具调用审批状态机：过期、越权、参数 sha256 绑定、乐观锁 revision |
| `plan/` | 计划模式：任务/步骤/验收标准、规范哈希、计划审批与修订 |
| `context/` | 上下文窗口与压缩：token 预算、`ContextCompaction`、固定桩 |
| `event/` | 事件中心 + outbox：`HarnessEventHub` 订阅、持久化重放 |
| `journal/` | 运行日志投影（默认关闭，`coding.harness.journal.enabled=false`） |
| `recovery/` | 崩溃恢复：工具「不确定副作用」账本对账，防止重复执行写操作 |
| `artifact/` | 产物仓库：分页清单、防篡改校验 |
| `model/` | 全部领域对象：`HarnessRunState`、`HarnessRunStatus`、`HarnessBudget`、`HarnessPermissionMode`… |
| `modelruntime/` | 模型注册/路由/策略（`HarnessModelRegistry`、`HarnessModelRouter`、`HarnessChatModelFactory`） |
| `prompt/`, `skill/` | 提示词装配（`DefaultHarnessPromptAssembler`）、项目指令加载、技能目录 |

run 状态机（`HarnessRunStatus`）：

```
QUEUED → RUNNING → {WAITING_FOR_APPROVAL | WAITING_FOR_INPUT | SUSPENDED} → QUEUED → …
RUNNING → COMPLETED | FAILED | CANCELLED   （终态不可再转）
```

安全边界（改这块前必须知道）：

- 全类 `@SaCheckPermission("coding:harness:use")`；写权限码 `coding:harness:write`，审批 `coding:harness:approve`。`permissionMode` 只是**请求上限**，不能由客户端提权，`READ_ONLY` 之外一律再校验 `write`。
- `execute_process` 默认 **关闭**，开启后只能经固定镜像的 Docker 沙箱执行，且必须显式配齐 `docker-executable` 及其 sha256、空 `docker-config-directory`、本地 `docker-host`、`sha256:` 镜像 ID——缺一项就 fail closed（`application.yml` 第 23–50 行）。
- 旧接口 `CodingController`（`/coding/chat`、`/coding/command` 等）默认 `coding.legacy.enabled=false`，调用直接 410，正路只有 `/coding/harness`。
- XSS 过滤器对 harness 提交正文做了豁免（`xss.excludeUrls` 含 `/coding/harness/sessions/*/runs`），否则代码里的 `<T>` 会被转义。

---

## 3. 本地起环境

### 3.1 后端（最长路径，先做这个）

```bash
# 1) 依赖版本：Java 17（README 里写的 21 不作为验收口径，见 docs/phase1-verification-record.md:41）
java -version

# 2) 数据库：第一次从全量脚本初始化（会同时建主库和 snail_job 调度库）
mysql -uroot -p < backend/docs/script/sql/ruoyi-ai.sql

# 已有库只跑增量
mysql -uroot -p --database=aicoding-assistant < backend/docs/script/sql/update/<最新增量>.sql

# 3) 编译启动
mvn -f backend/pom.xml -DskipTests package
java -jar backend/aicoding-admin/target/aicoding-admin.jar      # 端口 6039
# 或在 IDEA 里跑 backend/aicoding-admin/src/main/java/org/ruoyi/RuoYiAIApplication.java
```

- 配置分层：`application.yml`（公共）+ `application-dev.yml` / `application-prod.yml`（`SPRING_PROFILES_ACTIVE` 决定，默认 dev）。
- **dev 默认连远端共享库**：`application-dev.yml` 里 datasource 与 Redis 指向 `39.107.104.165`，且账号密码以 `${ENV:默认值}` 形式内联。要连本机就在启动参数/环境变量里覆盖 `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL`、`SPRING_DATA_REDIS_HOST`，别去改文件。
- 文档中心：`http://localhost:6039/doc.html`（Knife4j）；健康检查 `http://localhost:6039/actuator/health`。

### 3.2 前端

```bash
# 用户端（pnpm 11.0.9，Node >= 22.13）
pnpm --dir web install --frozen-lockfile
pnpm --dir web dev                 # 5173，接口直连 VITE_API_URL=http://127.0.0.1:6039

# 管理端（pnpm 10.14.0，Vben monorepo，用 turbo）
pnpm --dir admin install
pnpm --dir admin dev:antd          # 5666，代理默认 http://127.0.0.1:6039
```

浏览器回归脚本会自己拉 Vite、自己起 fixture API，别和开发服务抢端口：

```bash
node web/scripts/phase1-browser.mjs
node web/scripts/course-materials-browser.mjs
```

### 3.3 Python / 桌面（可选）

```bash
cd ai-service && cp .env.example .env
python -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001

# 或一键：根目录 docker compose up -d（pgvector + redis + ai-service + celery worker）
cd desktop && pnpm install && pnpm dev
```

---

## 4. 数据与迁移约定

- **一切结构变更都进 `backend/docs/script/sql/update/YYYY-MM-DD-<主题>.sql`**，同时把改动同步进全量 `ruoyi-ai.sql`；给每个迁移配一个 `docs/script/sql/tests/test_*.py` 校验脚本（已有 5 个：phase1、knowledge_status、course_materials、settings_documents、document_mapper）。
- 迁移必须**幂等**（重复执行保留原状态）、保留既有数据与角色授权、菜单 ID 冲突要报错而不是 `--force`。
- 新增管理菜单默认只授给已启用的 `superadmin` / `admin`，其它角色由管理员自授权。
- 多租户：`tenant_id` 广泛存在，跨租户访问必须被服务端拒绝；前端隐藏选项不算授权。

---

## 5. 测试与验收

```bash
# 后端单测（Java 17；reuseForks=false 是为了隔离静态 Spring/MapStruct fixture）
mvn -f backend/pom.xml -Pdev -pl aicoding-modules/aicoding-chat -am \
    -Dsurefire.failIfNoSpecifiedTests=false -Dtest='*Test' -DreuseForks=false test

# 全量
mvn -f backend/pom.xml -Pdev test

# SQL 层
python3 backend/docs/script/sql/tests/test_phase1_foundation_rag.py

# 前端
pnpm --dir web build          # vue-tsc + vite build
pnpm --dir web lint
```

参考基线：`aicoding-chat` 阶段验收记录 555–621 项测试全绿（`docs/phase1-verification-record.md`、`docs/knowledge-status-results.md`、`docs/course-materials-results.md`），浏览器场景 27 项通过。注意这些数字是各阶段快照，以你自己跑出来的为准。

**这些验收记录里的浏览器测试都是 mock API**：证明控件行为和发出的 JSON 载荷，不证明真实权限/检索质量/模型回答质量。

---

## 6. 权限与接口速查

- 权限码共 202 个，格式 `模块:实体:动作`。Harness 相关只有 4 个：`coding:harness:use`、`:write`、`:approve`、`:legacy-command`。
- 登录态：Sa-Token（PC）+ JWT；`ClientID` 头由前端统一注入；401 触发登出、403 跳 403 页。
- 主要接口根：

| 前缀 | 归属 |
| --- | --- |
| `/chat`、`/system/session`、`/system/message` | 聊天与会话 |
| `/knowledge/*`（`bases`、`info`、`attach`、`fragment`、`settings`、`status`） | 知识库 |
| `/system/course`、`/system/course/materials` | 课程与资料 |
| `/agent/agent`、`/workflow/*`、`/admin/workflow/*` | 智能体、工作流、应用市场 |
| `/coding/harness/*` | 编程智能体（唯一正路） |
| `/system/*`、`/monitor/*` | 系统管理与监控 |

---

## 7. 十个会踩的坑

1. **端口不是 8080**。老文档写 8080，实际 `server.port=6039`；老文档还说 Java 21，实际验收用 Java 17。
2. **`ai-service` 是骨架**。`rag.retrieve()` 直接返回 `[]`，模型走裸 HTTP SSE；真正可用的 RAG 在 Java 的 `service/retrieval`。别以为改 Python 就能改检索效果。
3. **Harness 前端还没接**。全仓 `grep -r "coding/harness"` 只有后端代码和 xss 白名单，`web/` 没有任何调用。要做 UI 就从 `src/api/` 新建模块开始。
4. **`CodingController` 是死路**。`legacy.enabled=false` 时全部 410，别在这上面加功能。
5. **`execute_process` 默认关**，且沙箱镜像不自动 pull/build，只认本地 `sha256:` ID；`backend/scripts/sandbox/README.md` 写清了构建与校验流程。
6. **dev 配置里有真实凭据与远端库地址**。改环境用环境变量覆盖，不要把值写进代码或文档。
7. **前端两个 pnpm 大版本**：`web` 用 11.0.9，`admin` 用 10.14.0，别跨目录混装；`web` 只维护 `pnpm-lock.yaml`。
8. **终端里中文乱码是控制台编码问题**（GBK），文件本身是 UTF-8，读文件用编辑器/`read`，别按乱码判断内容。
9. **知识库停用不能降级**：检索前后各查一次状态，任何一处停用都必须报错，不能悄悄当普通问答回答。
10. **工具副作用有账本**：`recovery/` 会拦截「不确定是否已执行」的写操作，改工具实现时不要绕过 `ToolEffectLedger`。

---

## 8. 上手路线

**第 1 天**：跑通三件套（后端 6039 + web 5173 + admin 5666），登录 admin 看菜单，发一条普通聊天消息，在「问答记录」里找到它。

**第 2 天**：读三条链路的关键类（§2 的类名），对照 `docs/phase1-acceptance.md` 的 8 项检查单手动走一遍；读 `ChatServiceFacade` 的 `handleModelChat`。

**第 3–4 天**：专攻 Harness。顺序：`HarnessRunStatus` → `CodingHarnessApplicationService` → `HarnessRunProcessor`/`DurableHarnessRunProcessor` → `ToolPolicyEngine` + `approval/` → `context/`。这一块占全仓最复杂的 3 万行，值得单独排时间。

**第一周后**：接一个真实小需求（推荐起步点：给 Harness 做最小的 Web 端会话页，或给知识库列表加一个筛选列）。

### 常见改动落点

| 想做的事 | 改哪里 |
| --- | --- |
| 加一个 REST 接口 | `controller/<域>/*Controller.java` + `service/<域>/impl/` + 需要时 `domain/{bo,vo}` |
| 加一个前端页面 | `web/src/pages/<域>/index.vue` + `web/src/api/<域>/` + `routers/modules/staticRouter.ts` |
| 加后台管理页 | `admin/apps/web-antd/src/views/<域>/` + `src/api/<域>/` + 菜单 SQL |
| 加 Harness 工具 | `harness/tool/builtin/BuiltinToolDescriptors` + `BuiltinCodingTools` + `ToolCapability` 声明 + 审批策略 |
| 换模型 / 加供应商 | `harness/modelruntime/HarnessModelRegistry`（Harness）、`service/chat/impl/provider/`（普通对话）、后台「模型管理」 |
| 改表结构 | `backend/docs/script/sql/update/` 新增文件 + 同步全量 SQL + 加 `tests/test_*.py` |
| 改检索效果 | `service/retrieval`、`service/rerank`、`service/embed`、`service/knowledge/impl/split` |

---

## 9. 已知状态（接手时先确认）

- 分支：`main`（含 `dev-gzl` 合并），工作区干净。最近一次大改动是「知识库访问控制 + 会话级知识绑定 + 课程资料」。
- 待人工验收项见 `docs/phase1-acceptance.md`、`docs/knowledge-status-acceptance.md`、`docs/course-materials-acceptance.md`、`docs/settings-documents-acceptance.md`。
- 管理端全量类型检查仍有约 118 条历史诊断，前端构建有 KaTeX 字体与 chunk 体积告警——都是既有问题，不是新引入的。
