# SQL 脚本说明

## 全新安装

在本目录执行 [ruoyi-ai.sql](ruoyi-ai.sql)，即可创建 `aicoding-assistant` 主库和 `snail_job` 调度库，并导入初始化数据。数据库名仍为 `aicoding-assistant`，脚本文件名为 `ruoyi-ai.sql`。

```sh
mysql -uroot -p < ruoyi-ai.sql
```

主 SQL 已合并下表列出的基础更新，以及原始想法、技能绑定、剧本打磨字段、模型优先级和 PC 登录超时结构。2026-10-28 Phase 1 迁移也已完整嵌入主脚本，在切换到调度库之前执行；新安装无需另跑该迁移。其他功能仍按下面“后续功能更新”的说明补充。[snail_job_mysql.sql](snail_job_mysql.sql) 仅用于单独初始化调度库，执行主 SQL 后无需再执行它。Docker MySQL 初始化入口同样只复制并执行 `ruoyi-ai.sql` 一次。

全量初始化包含 `DROP TABLE` 和调度库的 `DROP DATABASE`，已有数据库升级应使用对应的增量脚本。

## 已有数据库升级

连接目标主库后，按文件名升序执行尚未应用的更新脚本；不要根据文件重命名重复执行已应用的更新。

- `2026-06-15-trace.sql` 原名为 `update-0615-trace.sql`，内容未变；其中菜单插入没有判重，不应重复执行。
- `2026-08-30-mcp-market-tool-tenant.sql` 是一次性迁移，执行前检查脚本内的预检要求；部分执行失败后应按实际表结构续跑。
- 其余脚本包含重复执行保护，具体适用条件见各脚本注释。

### Phase 1 基础结构升级

已初始化的主库执行以下增量脚本；此命令不会切换数据库或初始化调度库：

```sh
mysql -uroot -p --database=aicoding-assistant < update/2026-10-28-phase1-foundation-rag.sql
```

迁移增加 `chat_question`、`chat_prompt`、`learning_course`、`course_enrollment`，补齐会话模式、Prompt/学习快照及可空 `knowledge_id VARCHAR(64)`，并增加问答、异常、Prompt、课程和业务总览菜单。新表遵循 `TenantEntity` 的字符串租户编号、审计和版本字段约定；原会话表租户列保持原样。已有会话不会凭空回填 Prompt 或知识库快照。

菜单 ID 使用独立的 `2099102800000000xxx` 段，迁移先检查 ID 冲突；若报 `phase1_reserved_menu_id_collision` 检查约束错误，应检查并解决该 ID 冲突后重试，不要使用 `mysql --force`。只向已有且启用的 `superadmin` / `admin` 角色补充管理权限及父菜单，保留普通用户、自定义角色和已有授权。自定义管理员角色需要由现有管理员按需授权。重复运行保留已存在菜单的显示、组件等自定义设置；DDL 逐项生效，建议先备份并安排维护窗口。

若旧库已有另一套问答、异常、Prompt、课程和总览菜单，本迁移会合并已确认的旧菜单及重复按钮，每项只留一个入口。合并前核对旧 ID、父菜单、组件和权限；旧授权迁移到保留项，自定义子项也会保留。其他菜单、角色和系统配置不会删除。已执行过旧版 Phase 1 SQL 的数据库，可重新执行此增量脚本完成清理，随后退出后台重新登录。

### 课程资料升级

先执行上面的 Phase 1 基础迁移，再在已有主库执行：

```sh
mysql -uroot -p --database=aicoding-assistant < update/2026-10-28-course-materials.sql
```

新增不可变资料表 `learning_course_material`，并给课程、报名和会话添加可空的资料 ID JSON 数组快照列。历史 NULL 按空列表处理，不回填、不修改课程、报名、会话正文或权限。脚本可重复执行，已完整嵌入全量 SQL，在 `USE snail_job` 之前运行。新安装无需单独执行。对象文件继续使用系统默认对象存储配置；资料解析不依赖向量库。

### 知识库启用／停用

已有主库执行：

```sh
mysql -uroot -p --database=aicoding-assistant < update/2026-10-29-knowledge-status.sql
```

新增 `knowledge_info.status`，`0` 启用、`1` 停用。已有和新建知识库默认启用，重复执行保留已设置的停用状态。停用不删除文档或向量；普通设置保存不会修改启停状态。全量 SQL 已包含此升级，新安装无需单独执行。后台开关沿用 `system:info:edit` 权限。

## 命名约定

更新脚本统一采用 `YYYY-MM-DD-功能描述.sql`，功能描述使用小写英文和连字符，例如 `2026-09-01-sys-url.sql`。保留已有更新日期，同日脚本以依赖顺序为准；2026-10-28 必须先执行 `phase1-foundation-rag` 再执行 `course-materials`。

全量入口为仓库中的 `ruoyi-ai.sql`，独立调度库入口为 `snail_job_mysql.sql`。

## 主 SQL 合并清单

截至 2026-09-01，`update` 目录的 9 个脚本均已体现在主 SQL 的最终表结构和初始化数据中。结构变更直接并入建表语句，重复配置按最终值保留一份；旧库回填及兼容逻辑保留在增量脚本中。

| 更新脚本 | 主 SQL 中对应内容 |
| --- | --- |
| [2026-06-15-trace.sql](update/2026-06-15-trace.sql) | `trace_run`、`trace_node` 表、租户字段和索引，以及链路追踪菜单 |
| [2026-07-20-knowledge-fragment-fid.sql](update/2026-07-20-knowledge-fragment-fid.sql) | 附件 `file_hash`、片段 `fid`、可空的 `doc_id` 和知识库租户索引 |
| [2026-07-21-chat-provider-icon-length.sql](update/2026-07-21-chat-provider-icon-length.sql) | `chat_provider.provider_icon` 扩展为 `varchar(1000)` |
| [2026-07-21-sys-config-node-template.sql](update/2026-07-21-sys-config-node-template.sql) | 7 个工作流节点消息模板配置 |
| [2026-07-24-register-default-role-and-missing-menus.sql](update/2026-07-24-register-default-role-and-missing-menus.sql) | 默认注册角色、角色菜单关联、默认角色配置，以及会话、附件和片段权限 |
| [2026-07-29-sys-config-node-template-all.sql](update/2026-07-29-sys-config-node-template-all.sql) | 工作流全部 8 个模板，前 7 个配置与上一批模板合并去重 |
| [2026-07-29-zhipu-web-search-node.sql](update/2026-07-29-zhipu-web-search-node.sql) | 智谱网络搜索节点及其消息模板；内部组件名保留 `Google` 以兼容已有流程 |
| [2026-08-30-mcp-market-tool-tenant.sql](update/2026-08-30-mcp-market-tool-tenant.sql) | `mcp_market_tool` 租户、审计字段和 `idx_tenant_market` 索引 |
| [2026-09-01-sys-url.sql](update/2026-09-01-sys-url.sql) | `sys_url` 表、5 个菜单权限和 2 条公开链接 |
| [2026-10-29-knowledge-status.sql](update/2026-10-29-knowledge-status.sql) | 知识库启停状态；旧记录默认启用，重复升级保留管理员选择；全量脚本已嵌入 |
| [2026-10-28-course-materials.sql](update/2026-10-28-course-materials.sql) | 不可变课程资料及课程、报名、会话资料快照；全量脚本已嵌入 |
| [2026-10-28-phase1-foundation-rag.sql](update/2026-10-28-phase1-foundation-rag.sql) | 主库选中时嵌入完整幂等迁移：问答、Prompt、课程、学习进度及会话快照字段；管理菜单只授予现有管理员 |

后续增加更新脚本时，同步修改主 SQL 中对应的表结构或初始化数据，并更新本清单及主 SQL 文件头。

## 后续功能更新

以下脚本统一位于 `update/`。已有数据库按日期升级，保留原始输入、已批准媒体和提供商密钥；不要执行全量初始化。

| 脚本 | 内容与新安装要求 |
| --- | --- |
| [知识库检索节点](update/2026-09-11-knowledge-retrieval-node.sql) | 补充节点注册，新安装也需执行 |
| [短剧视觉资产](update/2026-09-30-short-drama-visual-asset.sql) | 补充异步视觉资产表，新安装也需执行 |
| [剧本打磨与 Seedance 2.5](update/2026-09-30-script-refinement-seedance-25.sql) | 打磨字段已并入全量结构；新安装执行以补充视频模型记录 |
| [技能绑定](update/2026-10-01-short-drama-skill-bindings.sql) | 项目审美与导演绑定，幂等；全量结构已包含 |
| [模型优先级](update/2026-10-02-chat-model-sort-order.sql) | 幂等增加优先级列，保留已自定义优先级；全量结构已包含 |
| [PC 登录空闲时间](update/2026-10-02-pc-login-idle-timeout.sql) | 仅调整原 30 分钟配置；全量默认已更新，已签发令牌需重新登录 |
| [文字模型默认优先级](update/2026-10-03-short-drama-flash-default.sql) | 先应用模型优先级迁移，再调整已有 Flash / Seed 模型排序，不新增模型或密钥 |
| [原始创作输入](update/2026-10-03-short-drama-original-idea.sql) | 幂等增加独立原文列，不用生成概要回填；全量结构已包含 |
| [Phase 1 基础结构](update/2026-10-28-phase1-foundation-rag.sql) | 幂等补齐问答、Prompt、学习课程、会话快照与管理菜单；全量脚本已嵌入，新安装无需单独执行 |

## MySQL 8 回归验证

运行中的 Docker 容器 `aicoding-assistant-mysql` 需要设置 `MYSQL_ROOT_PASSWORD`，本机需要 Python 3 和 Docker CLI。从仓库根目录运行：

```sh
python3 backend/docs/script/sql/tests/test_phase1_foundation_rag.py
```

可通过 `MYSQL_TEST_CONTAINER` 指定其他 MySQL 8 容器。测试只在容器内部读取密码，创建唯一的 `aicoding_phase1_test_` 前缀数据库；全量脚本中的两个库名均重写为临时库，测试结束自动清理，不操作现有主库或调度库。验证覆盖全新安装、旧结构升级、重复升级、全部实体字段、已有记录和自定义授权保留、管理员权限、跨租户报名唯一性、菜单 ID 冲突时提前停止，以及重复菜单合并、授权迁移和自定义子项保留。

课程资料回归同样只操作测试临时库，覆盖全新安装、Phase 1 升级、重复执行、历史 NULL/业务记录/权限保留、完整嵌入和精确 JSON 字符串 ID 匹配：

```sh
python3 backend/docs/script/sql/tests/test_course_materials.py
```

知识库启停回归验证升级默认值、重复执行保留停用状态、全新安装及现有权限保留：

```sh
python3 backend/docs/script/sql/tests/test_knowledge_status.py
```
