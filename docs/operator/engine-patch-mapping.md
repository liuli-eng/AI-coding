# purge 的 82 条补丁 → 本项目等价物

> 结论先说：**82 条里没有任何一条值得照抄到本项目。**
> purge 打补丁是因为它改的是别人打包的 JS（`@deepseek-ai/dsh/lib/index.js` 里的字符串常量），
> 一升级锚点就漂移，所以必须带 markers / skip / 备份 / 迁移。
> 本项目是宿主本身——同样的效果用代码里的策略点实现，零漂移。

---

## 一、82 条的构成（实测）

| 文件 | 条数 | 层 | 改什么 |
| --- | --- | --- | --- |
| `prompt.js` | 10 | 提示词 | system prompt 常量、persona、preset YAML 里的 prefix |
| `engine.js` | 8 | 代码 | 审批门、沙箱升级阶梯、never 策略 |
| `tool.js` | 61 | 代码/工具 | 工具描述与能力声明、流式处理、重试 |
| `code.js` | 3 | 代码 | 杂项行为 |
| 合计 | **82** | — | 按层统计：代码 46 / 提示词 25 / 工具 8 / 兼容 3 |

---

## 二、逐项映射

### 提示词层（25 条）—— **已实现，且比它干净**

| purge 补丁 | 做法 | 本项目对应 | 状态 |
| --- | --- | --- | --- |
| `WORKSPACE_CONTEXT_INTRO` / `AGENT_INSTRUCTIONS_INTRO` 强句替换 | 把 "do not override system" 改成 "ACTIVE and MANDATORY" | `OperatorPromptOverlay.strongIntro()` + Harness 3 锚点定点替换 | ✅ |
| `SYSTEM_PROMPT_PERSONA` / `WEB_PRESET_IDENTITY_STRIP`（4 条） | 清空 personaPrefix / prefix | `replace-base-prompt=true` → 丢弃宿主模式人设 | ✅ |
| `APPROVAL_NEVER_SENTENCE` / `ASK_SENTENCE_BYPASS` | 把「审批已禁用→会被拒绝」改成「自动放行」 | 注入段直接描述执行上下文，不再出现拒绝性描述 | ✅ |
| `PRESET_SHELL_NETWORK`（去「无网络」） | 删宿主预设里的网络禁令 | 本项目无此约束（execute_process 走 Docker 沙箱） | — 不适用 |
| `LIANGSHEN_IDENTITY_LINE` 等 | 第三方预设的身份句 | 无第三方预设 | — 不适用 |

### 引擎层（8 条）—— **已实现，走枚举而不是删代码**

| purge 补丁 | 本项目对应 | 状态 |
| --- | --- | --- |
| `APPROVAL_AUTO_GRANT`（`return "allowed-once"`） | `HarnessApprovalPolicy.NEVER` → `ToolPolicyEngine` 直接 `allow()`（原代码就有这个分支） | ✅ 无需改 |
| `APPROVAL_CONFIG_DEFAULT_NEVER` | `CodingHarnessController.resolveApprovalPolicy()` 按 profile 默认 NEVER | ✅ |
| `ESCALATION_WIDENING_EXEMPT` / `ESCALATION_GRANT_UNCONDITIONAL` | `forcePermissionMode` / `bypassPermissionChecks`（服务端决定上限） | ✅ |
| never/ask 提示句改写 | 注入段 + `operatorTail()` | ✅ |

### 工具层（61 条）—— **大部分不适用**

purge 的 61 条集中在：工具描述文案、工具能力声明（`read_file` 等）、流式增量处理、
以及 `refusal-retry` 相关路径。本项目的对应物：

| purge 做的事 | 本项目 | 状态 |
| --- | --- | --- |
| 放宽工具描述里的自我限制 | `BuiltinToolDescriptors` 本来就声明全量能力（read/write/execute/search），没有「为安全自我阉割」的文案 | — 无需改 |
| 流式增量拼接 / 拒绝中途丢弃 | `OperatorRefusalGate`（扣留 600 字 → 判定 → 丢弃重试） | ✅ |
| 重试路径 | `attemptGuardedStream` + `reinforceForRetry`（剥离历史拒绝 + 强化段） | ✅ 已比它细 |
| 宿主工具数量上限 / 预算 | `HarnessBudget`、`coding.harness.budget.*` | — 已有 |

### 兼容层（3 条）—— **不适用**

`0.1.5 兼容` / `0.2.0 兼容` 是给老版本 DSH 的锚点迁移，本项目没有字符串锚点，无需迁移。

---

## 三、那"82 条"到底还剩什么是我没有的

只有三件，且都不需要照抄：

1. **体量**：purge 的注入文本 32KB（305 行），我现在给的是 2KB / 10KB 两档。
   → 已在 `full-ruleset.md` 补到 10KB，覆盖它的 6 类判据 + 意图映射。
   它的 32KB 里约 60% 是中英双写与关键词变体，不产生额外效果。

2. **特定模型**：purge 的效果高度依赖模型。同一套注入在 A 模型 0% 拒绝、在 B 模型 90% 拒绝。
   → 本项目已具备量化手段（`docs/operator/refusal-bench.mjs`），但没有跑过模型矩阵。
   **这是唯一真正缺的一环，而且只能靠实测，不能靠补丁。**

3. **引擎级流式过滤的粒度**：purge 在 token 流上做拦截；本项目在「首 600 字」上做拦截。
   → 对本项目足够（拒绝几乎都在开头），若要更细可把 `HOLD_LIMIT` 调小。

---

## 四、如果你仍想要"82 条"那种形式

可以做，但不建议：

- 把 `OperatorPromptOverlay` 的 3 个锚点替换扩成一张 `List<Replacement>` 表（幂等 + 备份 + markers），
  形式上对齐 purge 的 patch table。
- 代价：本项目改文案不需要"找锚点"，编译器会直接报错——加这层只会把编译期错误推迟到运行期。

**建议**：保持现在的策略层实现，把精力投在「模型矩阵 + 注入体量」上，这两项才决定实际效果。
