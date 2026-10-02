# 功能模块：命令系统（`core/command`）

> 事实来源：`core/command/**`（6 个文件）。
> `/idtw` 的唯一命令入口；配置与查询在 `command`，诊断入口在 `core/debug`。

## 1. 类清单

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleCommandTree` | `/idtw` 唯一入口：权限门、反馈出口、子分支装配 | `ROOT="idtw"`、`register(dispatcher, context)`、`hasAccess(source)`、`reply` / `replyAll` / `replyFailure` / `notReady` |
| `RuleCommandContext` | 窄接口：向命令层暴露运行时能力（由两端 `RuleRuntimeHost` 实现） | `runtime()`、`serverConfig()`、`editContext()`、`overlayNamespace()`、`overlayVersion()`、`activeSessionCount()`、`reloadRules(server)` |
| `RuleCommandText` | i18n 反馈组件构造器（**唯一出口，禁止中文字面量**） | 各命令文案 key |
| `RuleConfigCommands` | `config` 分支：reload / edit / validate / list / convert | `build(context)` |
| `RuleQueryCommands` | `rule` 分支：list / show | `build(context)` |
| `RuleConvertService` | 旧链路 JSON → 新覆盖层规则一次性转换（备份 + 映射 + 校验 + 落盘） | `convert(...)`、映射表 `TYPE_TO_EFFECT`、`CONDITIONS` |

## 2. 命令树与权限

```text
/idtw (requires hasAccess)
├── config  → reload | edit | validate | list | convert
├── rule    → list | show <id>
└── debug   → 仅 DebugMode.ENABLED 时挂载（见 debug.md）
```

- **权限口径** `hasAccess`：单人世界放行，否则要求权限等级 ≥2（与网络编辑会话一致）。
- **统一反馈**：`reply`（sendSuccess，返回 1）/ `replyAll`（逐行，空清单返回 0）/ `replyFailure`（sendFailure，返回 0）/ `notReady`（未就绪提示）。全部用 `Component.translatable`，不写中文字面量；每个 key 参数个数固定，条件分支用空串占位。
- `rule show` 用 `ResourceLocationArgument.id()` 作参数类型。

## 3. 子命令 → 后端调用

| 命令 | 实现 | 后端调用 |
|---|---|---|
| `/idtw config reload` | `RuleConfigCommands.reload` | `RuleCommandContext.reloadRules(server)`（实现走 `RuleRuntimeHost.reload`） |
| `/idtw config edit` | `RuleConfigCommands.edit` | `context.editContext().sendTo(player, new OpenRuleEditorPayload())` |
| `/idtw config validate` | `RuleConfigCommands.validate` | `context.editContext().loadMerged(server)`（只读，不重建索引） |
| `/idtw config list` | `RuleConfigCommands.list` | `loadMerged`；逐条列出 id/layer/priority/trigger/effects/enabled/origin |
| `/idtw config convert` | `RuleConfigCommands.convert` | `RuleConvertService.convert(...)`；成功条数 >0 时再 `reloadRules(server)` |
| `/idtw rule list` | `RuleQueryCommands.list` | `loadMerged` |
| `/idtw rule show <id>` | `RuleQueryCommands.show` | `loadMerged` + `RuleSnapshotAssembler.assemble(...)` 取原始 JSON |

## 4. 旧配置转换（`RuleConvertService`）

| 项 | 行为 |
|---|---|
| 输入 | `config/<overlay_directory>/` 下旧 `*.json`（跳过 `rules/`、`_old_chain_backup/`、`server.json`），按文件名识别旧类型，只处理 9 个内置类型 |
| 输出 | 新覆盖层 `rules/**`（经 `RuleOverlayWriter` 按 id upsert） |
| 新 id | `<namespace>:legacy/<旧相对路径去扩展名>_<对象序号>` |
| 备份 | 命中旧文件复制到 `_old_chain_backup/<相对路径>`（保留首次备份） |
| 报告 | `converted` / `backedUp` / `writtenFiles` / `unmapped`（无法映射）/ `notes`（字段级损失） |
| 原则 | **能无歧义映射就转换，否则显式报告，绝不静默丢弃**；v1 扁平字段明确拒绝自动转换 |
| 幂等 | 已存在同 id 的迁移规则跳过，保留用户后续编辑；旧文件不删除、运行时不再读取 |

`RuleConvertService` 与旧链路 `config/` **零耦合**：只按字段名读旧 JSON 文本，不引用任何旧类。旧的"读时写回"（`schema_version`）正是被淘汰的做法，因此不提供自动迁移。

## 5. 扩展点

- **新增子命令**：在 `RuleConfigCommands`/`RuleQueryCommands` 加分支，文案进 `RuleCommandText` + 双语 lang；需要新运行时能力时扩 `RuleCommandContext` 并在两端 `RuleRuntimeHost` 的 `COMMAND_CONTEXT` 实现。
- **改权限口径**：改 `RuleCommandTree.hasAccess`（与 `RuleEditServerHandler.canEdit` 保持一致）。

## 6. 相关

- 平台实现：[platform.md](platform.md)（`RuleRuntimeHost.COMMAND_CONTEXT`）
- 编辑协议：[edit-protocol.md](edit-protocol.md)
- 调试命令：[debug.md](debug.md)
- i18n 约束：所有命令文案必须同步 `en_us.json` 与 `zh_cn.json`
