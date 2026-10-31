# Phase 1 本地验收

本阶段补齐问答、Prompt、课程及会话快照结构、管理员菜单权限，并增加普通用户知识库入口与会话知识库绑定。到此停止，等待用户检查；没有执行 Phase 2、提交、部署、真实数据库迁移或已有服务重启。

## 先完成安装或升级

以下命令供实际安装时使用。本次验证只在独立临时数据库中运行，没有对正在使用的主库执行这些命令。

全新安装，从仓库根目录执行：

```sh
mysql -uroot -p < backend/docs/script/sql/ruoyi-ai.sql
```

全量脚本已嵌入本阶段迁移，在切换到 `snail_job` 前执行。它会初始化主库和调度库；已有数据的环境不要执行全量脚本。其他历史功能迁移的适用条件见 [SQL 安装说明](../backend/docs/script/sql/README.md)。

已有安装只执行增量升级：

```sh
mysql -uroot -p --database=aicoding-assistant < backend/docs/script/sql/update/2026-10-28-phase1-foundation-rag.sql
```

本机没有独立 `mysql` CLI，使用现有 Docker MySQL 容器时，可从仓库根目录改用以下命令。命令只在容器内读取已有密码，不打印密码；本次仅提供操作说明，没有执行：

```sh
docker exec -i aicoding-assistant-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --database=aicoding-assistant --default-character-set=utf8mb4' < backend/docs/script/sql/update/2026-10-28-phase1-foundation-rag.sql
```

迁移增加四张实体表，以及 `chat_session` 的模式、Prompt/学习快照、可空字符串 `knowledge_id` 等字段；保留已有业务记录及角色授权。新增管理菜单只自动授予已有且启用的 `superadmin` / `admin` 角色。自定义管理员角色需由现有管理员按需授权；普通用户不会获得新增管理权限。菜单保留 ID 冲突会在修改持久结构前报错，解决冲突后再执行，不要使用 `mysql --force`。

SQL 验证命令：

```sh
python3 backend/docs/script/sql/tests/test_phase1_foundation_rag.py
```

本次在 Docker `aicoding-assistant-mysql` 的 MySQL 8.4.11 上通过 7 项测试，包括全新安装、旧库与重复升级、实体字段、数据/自定义权限保留、管理员授权、报名唯一性、菜单 ID 冲突及嵌入迁移同步。测试使用唯一的 `aicoding_phase1_test_` 前缀临时数据库，结束后清理；仅在容器内读取密码，不输出密码。迁移的 CHECK 约束要求 MySQL 8.0.16 以上；没有另行验证 MySQL 8.0 镜像。

## 本地运行与入口

本阶段实际使用 **Java 17、Node.js 22.23.2、pnpm 11.0.9**。后端其他旧说明中的 Java 21 文案不代表本阶段测试环境。

在本机可先确认运行时：

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$HOME/.nvm/versions/node/v22.23.2/bin:$PATH"
java -version
node --version
pnpm --version
```

前端依赖与构建：

```sh
pnpm --dir web install --frozen-lockfile
pnpm --dir web build
pnpm --dir web dev
```

`web` 使用现有环境中的 `VITE_API_URL` 和客户端配置；不要把令牌、数据库密码或模型密钥写入文档。管理端按其现有配置运行。本轮没有改动用户已有环境文件。

已有本地入口：

- 用户端：[聊天](http://localhost:5173/chat)、[知识库](http://localhost:5173/knowledge)。
- 管理端：[管理后台](http://localhost:5666)。
- 后端地址：[localhost:8080](http://localhost:8080)。

**在 2026-10-28 本次开发结束时，8080 后端仍是修改前已启动的代码。真实用户检查前，需要先按环境完成增量迁移，再使用 Java 17 重新构建，并按原有启动机制重启后端。** 这是本轮结束时的状态快照；后续如已升级重启，以实际运行状态为准。现有 5173/5666 页面可打开，但不能据此认为新接口已经在 8080 生效。本次没有重启它们，也没有发送测试问题到真实模型。

迁移和后端重启后，管理端需退出后重新登录，并刷新动态菜单，再检查新增菜单和按钮；权限快照保存在登录上下文中。本轮未清理真实 Redis，也未自动注销用户。

## 功能检查清单

- [ ] 管理员能看到问答记录、异常、Prompt、课程、业务总览入口；普通用户没有新增管理权限。
- [ ] 普通用户知识库只显示当前租户内公开或本人拥有的库；返回内容只有名称、说明、文档数、更新时间及字符串 ID，不含检索或模型配置。
- [ ] 知识库页支持名称搜索、每页 12 条分页，以及加载、空结果、失败后重试；零文档显示 `0`，缺失时间显示 `—`。
- [ ] 点击某个库的“开始对话”，新会话草稿保留该选择；也能在新对话的可选知识库中搜索选择或清除。
- [ ] 创建会话的请求含所选 `knowledgeId`；失败时问题和选择保留，不产生待发的旧 `chatContent`。
- [ ] 创建成功后，普通聊天使用会话保存的 `knowledgeId`；刷新或直接打开 `/chat/{id}` 后先恢复会话和历史记录，不能在恢复完成前发送；延迟历史加载后首问仍只发送一次。
- [ ] 后端返回安全范围内的数字会话 ID 或大整数字符串 ID 时，都能恢复并发送；侧栏选中一致，请求中的字符串 ID 保持准确。
- [ ] 在不同知识库绑定及未绑定的会话之间切换，名称和后续请求不串库；找不到或无权访问的库统一显示“该知识库暂不可用”。
- [ ] 点击新对话、退出登录时清除草稿/待发消息；课程教学会话的延迟响应不会重新写入待发消息或跳回旧会话；从知识库进入新对话时保留本次指定的库。
- [ ] 智能体和工作流创建/发送请求不继承普通聊天的知识库选择；再从侧栏切回已绑定知识库的普通会话后，显示保存的库名，后续请求不带应用参数。
- [ ] 在桌面、平板及 390px 宽度下检查列表、选择器和输入框；知识库页面没有横向滚动。

服务端还应拒绝伪造、跨租户、其他人私有、已删除及失效的知识库 ID，拒绝修改请求中的 ID 来覆盖已保存会话绑定。HTTP/SSE 与 WebSocket 的请求知识源均有服务端授权回归测试；UI 隐藏选项不能替代授权检查。

## 已运行的验证与范围

后端测试使用 Java 17 和 dev 标签，最终 **555 项、104 个测试类全部通过**：

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
/opt/homebrew/bin/mvn -f backend/pom.xml -Pdev \
-pl aicoding-modules/aicoding-chat -am \
-Dsurefire.failIfNoSpecifiedTests=false '-Dtest=*Test' -DreuseForks=false test
```

`reuseForks=false` 隔离已有测试中的静态 Spring/MapStruct fixture 状态，没有排除失败测试。该命令不运行外部 MiniMax 模型集成及 `*IT` Docker 向量库集成。

前端验证：`pnpm --dir web build` 通过；修改的 Vue/TypeScript 文件通过 focused ESLint；新增知识库组件的设计规则源码检查为 0 FAIL / 0 WARN。构建保留已有 KaTeX 字体解析、chunk 大小及旧 `:deep` 写法提示，未为这些旧问题扩大改动。

浏览器回归使用真实 Chrome、真实 Vue/Element Plus 控件和确定的测试 API。复现时需安装已有的 `design-taste-frontend` 技能及 Chrome，并显式指定其零依赖 CDP 驱动：

```sh
export PHASE1_CDP_DRIVER="$HOME/.agents/skills/design-taste-frontend/scripts/lib/cdp.mjs"
node web/scripts/phase1-browser.mjs
```

脚本创建自己的 `127.0.0.1:15173` Vite 进程、独立优化缓存、随机端口 fixture API 和新浏览器 profile，并阻止访问其他 origin；结束后停止自己创建的进程。不要与另一个同端口脚本同时运行。驱动缺失会明确报错，未添加新测试框架或应用依赖。

最终 27 个浏览器场景全部通过，覆盖创建失败保留输入、真实选择器载荷、刷新/切换/延迟详情竞态、恢复失败重试、智能体/工作流隔离及退出登录，并覆盖延迟历史时首问不丢失、课程异步响应在新对话/退出后失效、从应用模式经侧栏切回普通知识库会话，以及数字/大整数字符串会话 ID 的直接恢复、侧栏选中和准确发送；创建夹具也返回数字 ID。1440/768/390px 设计探针均为 0 FAIL。

初轮截图与探针保存在 `/tmp/aicoding-phase1-browser/`；最终修复后的完整 27 场景截图、请求体、检查列表、设计探针结果及测试服务器日志保存在 `/tmp/aicoding-phase1-id-final/`。测试样例“Java 开发规范”“产品使用手册”、12 条开发资料、验收模型/智能体/工作流及回答只在脚本中定义，不是产品中的默认数据。

**这些浏览器结果是 mocked API 验证。** 它们证明控件行为、恢复时序和实际发出的 JSON 载荷，不证明真实数据库权限、向量检索质量或模型回答质量；后者需要迁移/重启之后，用授权的测试账号和实际资料另行验收。服务端权限与 SQL 的证据分别来自上述 Java 单元测试和真实 MySQL 临时库测试。
