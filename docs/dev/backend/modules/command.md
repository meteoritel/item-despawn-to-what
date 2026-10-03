# 功能模块：命令系统（`core/command` + `core/debug` 命令注册）

> 事实来源：`core/command/**`（5 个文件）、`core/debug/{RuleDebugCommands, DebugPipelineCommands, DebugMode, DebugScenarioCatalog, DebugPipelineCatalog}.java`。
> `/idtw` 的唯一命令入口；配置与查询在 `command`，诊断入口在 `core/debug`。
> **`convert` 子命令已退役（2026-10-03，P8 结论）**：`/idtw config convert` 与 `RuleConvertService` 已从代码中删除，旧 v1.2.1 配置不再加载，改用 `/idtw config edit` 重建。见 [更新说明](../../../guide/update-notes.md) 与 [迁移评估 §5](../../../plan/v1.2.1-migration-evaluation.md)。

## 1. 类清单

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleCommandTree` | `/idtw` 唯一入口：根节点装配、权限口径、反馈出口 | `ROOT="idtw"`、`register(dispatcher, context)`、`hasAccess(source)`、`reply` / `replyAll` / `replyFailure` / `notReady` |
| `RuleCommandContext` | 窄接口：向命令层暴露运行时能力（由两端 `RuleRuntimeHost` 实现） | `runtime()`、`serverConfig()`、`editContext()`、`overlayNamespace()`、`overlayVersion()`、`activeSessionCount()`、`reloadRules(server)` |
| `RuleCommandText` | i18n 反馈组件构造器（**唯一出口，禁止中文字面量**） | `of` / `kv` / `build`、`issue` / `issues` / `issueTexts` / `summary` |
| `RuleConfigCommands` | `config` 分支：reload / edit / validate / list / edit-lock（`convert` 已退役） | `build(context)` |
| `RuleQueryCommands` | `rule` 分支：list / show | `build(context)` |
| `RuleDebugCommands` | `debug` 分支装配：`help` / `examples` / `run` / `bench` / `pipeline` / `stop` / `status` / `mark` | `build(context)`（场景名由 `DebugScenarioCatalog` 声明驱动） |
| `DebugPipelineCommands` | `debug pipeline` 子分支：`p0`～`p4` 与 `help` / `status` / `stop` | `build(context)`（阶段名由 `DebugPipelineCatalog` 声明驱动） |
| ~~`RuleConvertService`~~【已删除，2026-10-03】 | 旧链路 JSON → 新覆盖层规则一次性转换；P8 结论后整类删除 | 历史成员 `convert(...)`、映射表 `TYPE_TO_EFFECT`、`CONDITIONS` |

## 2. 命令树与权限

```text
/idtw
├── config
│   ├── reload              (requires hasAccess)
│   ├── edit                (requires hasAccess)
│   ├── validate            (requires hasAccess)
│   ├── list                (requires hasAccess)
│   └── edit-lock
│       ├── status          (无权限要求：任何玩家可查看自身状态)
│       └── release         (requires 权限等级 ≥2)
├── rule                    (requires hasAccess)
│   ├── list
│   └── show <id>
└── debug                   (requires DebugMode.ENABLED && hasAccess，仅开发环境挂载)
    ├── (无参) / help       帮助
    ├── examples           列出全部已注册场景
    ├── run <场景> [seconds]
    ├── bench <场景> [entities] [seconds]
    ├── pipeline
    │   ├── (无参) / help   帮助
    │   ├── status
    │   ├── stop
    │   └── p0|p1|p2|p3|p4  [entities|max_entities] [gap_seconds]
    ├── stop
    ├── status
    └── mark <描述>
```

- **根节点不统一鉴权**：`register` 只装配 `config` / `rule` / `debug` 三个分支；权限**下放到各子分支**（`RuleConfigCommands.build` / `RuleQueryCommands.build` 内部逐个 `.requires(RuleCommandTree::hasAccess)`）。
- **权限口径** `hasAccess`：单人世界放行，否则要求权限等级 ≥2（与网络编辑会话 `RuleEditServerHandler.canEdit` 一致）。
- **`edit-lock status` 例外**：不加 `hasAccess`——任何玩家都能查看自身会话；他人持有时有权限显示持有者名与状态，无权限只提示已被占用（不泄露 sessionId）。`edit-lock release` 要求权限 ≥2。
- **`debug` 挂载条件**：`DebugMode.ENABLED`（加载器 development 环境）**且** `hasAccess`；发布环境不注册任何 debug 入口。
- **统一反馈**：`reply`（sendSuccess，返回 1）/ `replyAll`（逐行，空清单返回 0）/ `replyFailure`（sendFailure，返回 0）/ `notReady`（未就绪提示）。全部用 `Component.translatable`，不写中文字面量；每个 key 参数个数固定，条件分支用空串占位。
- `rule show` 用 `ResourceLocationArgument.id()` 作参数类型。`debug run`/`bench` 的场景名由目录 manifest 声明驱动，新增场景无需改命令树（见 [debug.md](debug.md)）。

## 3. 子命令 → 后端调用

| 命令 | 实现 | 后端调用 |
|---|---|---|
| `/idtw config reload` | `RuleConfigCommands.reload` | `RuleCommandContext.reloadRules(server)`（实现走 `RuleRuntimeHost.reload`） |
| `/idtw config edit` | `RuleConfigCommands.edit` | 先 `RuleEditServerHandler.openEditor(player, editContext)` 原子取锁，成功才下发 `OpenRuleEditorPayload` |
| `/idtw config validate` | `RuleConfigCommands.validate` | `context.editContext().loadMerged(server)`（只读，不重建索引） |
| `/idtw config list` | `RuleConfigCommands.list` | `loadMerged`；逐条列出 id/layer/priority/trigger_seconds/effects/enabled/origin |
| `/idtw config edit-lock status` | `RuleConfigCommands.editLockStatus` | `RuleEditServerHandler.sessionManager(...)`；空闲/自身持有/他人持有三种口径 |
| `/idtw config edit-lock release` | `RuleConfigCommands.editLockRelease` | `EditSessionManager.release()`；原持有者在线时立刻收 `SESSION_EXPIRED` |
| `/idtw config convert`【已退役，2026-10-03】 | 历史：`RuleConfigCommands.convert`（分支已删除） | 历史：`RuleConvertService.convert(...)`（已整类删除） |
| `/idtw rule list` | `RuleQueryCommands.list` | `loadMerged` |
| `/idtw rule show <id>` | `RuleQueryCommands.show` | `loadMerged` + `RuleSnapshotAssembler.assemble(...)` 取 `effective`（覆盖层原文 / 数据包规则的编码结果） |
| `/idtw debug run\|bench <场景>` | `RuleDebugCommands` → `DebugScenarioManager.start` | 场景目录声明驱动；见 [debug.md](debug.md) |
| `/idtw debug pipeline p0..p4` | `DebugPipelineCommands` → `DebugPipelineManager.start` | 串行阶段计划；见 [debug.md](debug.md) |

## 4. 旧配置转换（`RuleConvertService`）【历史记录，2026-10-03 起失效】

> **本章描述的实现已被删除（P8 结论）**：不要再据此排查或使用转换入口；当前迁移路径见 [更新说明](../../../guide/update-notes.md)。以下内容仅作为历史记录保留。

| 项 | 行为 |
|---|---|
| 输入 | `config/<overlay_directory>/` 下旧 `*.json`（跳过 `rules/`、`_old_chain_backup/`、`server.json`），按文件名识别旧类型，只处理 9 个内置类型 |
| 输出 | 新覆盖层 `rules/**`（经 `RuleOverlayWriter` 按 id upsert） |
| 新 id | `<namespace>:legacy/<旧相对路径去扩展名>_<对象序号>` |
| 备份 | 命中旧文件复制到 `_old_chain_backup/<相对路径>`（保留首次备份） |
| 报告 | `converted` / `backedUp` / `writtenFiles` / `unmapped`（无法映射）/ `notes`（字段级损失） |
| 原则 | **能无歧义映射就转换，否则显式报告，绝不静默丢弃**；v1 扁平字段明确拒绝自动转换 |
| 幂等 | 已存在同 id 的迁移规则跳过，保留用户后续编辑；旧文件不删除、运行时不再读取 |

## 5. 扩展点

- **新增子命令**：在 `RuleConfigCommands`/`RuleQueryCommands` 加分支，文案进 `RuleCommandText` + 双语 lang；需要新运行时能力时扩 `RuleCommandContext` 并在两端 `RuleRuntimeHost` 的 `COMMAND_CONTEXT` 实现。
- **新增 debug 场景/阶段**：只加 `idtw-debug/` 下的 JSON 声明与 manifest 条目，不改命令树（见 [debug.md](debug.md)）。
- **改权限口径**：改 `RuleCommandTree.hasAccess`（与 `RuleEditServerHandler.canEdit` 保持一致）。

## 6. 相关

- 平台实现：[platform.md](platform.md)（`RuleRuntimeHost.COMMAND_CONTEXT`）
- 编辑协议：[edit-protocol.md](edit-protocol.md)
- 调试命令：[debug.md](debug.md)
- i18n 约束：所有命令文案必须同步 `en_us.json` 与 `zh_cn.json`
