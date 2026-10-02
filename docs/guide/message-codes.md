# 消息码约定

> 权威来源：[契约 §3.4 / §3.5](../plan/plan-frontend-rewrite-contract.md)（状态码、RuleIssue）、源码 `core/network/protocol/RuleSaveStatus.java` 与 `RuleIssue.java`。
> 读者：新增协议回执、校验问题或界面文案的开发者。

## 1. 两套前缀，不要混用

| 前缀 | 用途 | 例子 |
|---|---|---|
| `gui.itemdespawntowhat.edit.*` | **界面文本**：标题、按钮、字段标签、枚举值、类型显示名 | `gui.itemdespawntowhat.edit.field.y_level.min` |
| `itemdespawntowhat.edit.*` | **协议回执与校验问题的 messageCode**（服务端生成、客户端翻译） | `itemdespawntowhat.edit.status.lock_busy` |

字段标签是 `gui.itemdespawntowhat.edit.field.<类型 path>.<字段>`、枚举值是 `gui.itemdespawntowhat.edit.enum.<组>.<值>`、类型显示名是 `gui.itemdespawntowhat.edit.<condition|effect>.<类型>`（见 [client-editor-spi.md](client-editor-spi.md) 与契约 §5.1）。

## 2. 回执状态码 RuleSaveStatus（12 个）

`RuleSaveStatus` 是**唯一**的回执分类；客户端**只按 statusCode 分支，禁止解析文案**。

| 枚举 | `id()` | messageCode | 场景 |
|---|---|---|---|
| `SUCCESS` | `success` | `itemdespawntowhat.edit.status.success` | 保存成功：已写盘且已重载 |
| `NO_CHANGES` | `no_changes` | `itemdespawntowhat.edit.status.no_changes` | 变更集为空，未做任何写盘 |
| `NO_PERMISSION` | `no_permission` | `itemdespawntowhat.edit.status.no_permission` | 权限不足（单人世界或权限等级 ≥2 才可编辑） |
| `LOCK_NOT_OWNED` | `lock_not_owned` | `itemdespawntowhat.edit.status.lock_not_owned` | 会话不属于当前玩家（锁已易主） |
| `LOCK_BUSY` | `lock_busy` | `itemdespawntowhat.edit.status.lock_busy` | 编辑目标被他人持有；`OpenRuleEditorPayload` 同样下发本码 |
| `SESSION_EXPIRED` | `session_expired` | `itemdespawntowhat.edit.status.session_expired` | 会话过期（心跳超时或被释放） |
| `VERSION_CONFLICT` | `version_conflict` | `itemdespawntowhat.edit.status.version_conflict` | 客户端 `expected_version` 与服务端版本不一致，整批拒绝 |
| `VALIDATION_FAILED` | `validation_failed` | `itemdespawntowhat.edit.status.validation_failed` | 校验未通过，细节在 `issues` 里 |
| `WRITE_FAILED` | `write_failed` | `itemdespawntowhat.edit.status.write_failed` | 落盘失败（IO / 备份 / 原子提交） |
| `SAVED_NOT_RELOADED` | `saved_not_reloaded` | `itemdespawntowhat.edit.status.saved_not_reloaded` | 已写盘但重载失败，规则索引仍是旧的 |
| `INVALID_REQUEST` | `invalid_request` | `itemdespawntowhat.edit.status.invalid_request` | 载荷非法（字段缺失、超长、分片不齐） |
| `UNAVAILABLE` | `unavailable` | `itemdespawntowhat.edit.status.unavailable` | 运行时未就绪（overlayRoot / typeRegistries 缺失） |

规则：`id()` 为小写下划线（`name().toLowerCase(Locale.ROOT)`）；`messageKey() = "itemdespawntowhat.edit.status." + id()`；`fromId(String)` 做宽松解析，未知取值返回 `null`。

## 3. 其它协议 messageCode（16 个）

下列键用于 `RuleSaveResultPayload.messageCode`、`OpenRuleEditorPayload.statusCode` 之外的提示，以及 `RuleIssue.messageCode` 的通用兜底：

- `itemdespawntowhat.edit.no_permission`
- `itemdespawntowhat.edit.runtime_not_ready`
- `itemdespawntowhat.edit.parse_failed`
- `itemdespawntowhat.edit.session_expired`
- `itemdespawntowhat.edit.nothing_to_save`
- `itemdespawntowhat.edit.version_conflict`
- `itemdespawntowhat.edit.snapshot_too_large`
- `itemdespawntowhat.edit.save_success`
- `itemdespawntowhat.edit.validation_failed`
- `itemdespawntowhat.edit.save_failed`
- `itemdespawntowhat.edit.saved_reload_failed`
- `itemdespawntowhat.edit.snapshot_failed`
- `itemdespawntowhat.edit.protocol_mismatch`
- `itemdespawntowhat.edit.issue.load`
- `itemdespawntowhat.edit.issue.generic`
- `itemdespawntowhat.edit.issue.decode`

`itemdespawntowhat.edit.no_permission` / `runtime_not_ready` / `parse_failed` / `session_expired` / `nothing_to_save` / `version_conflict` / `snapshot_too_large` / `save_success` / `validation_failed` / `save_failed` / `saved_reload_failed` / `snapshot_failed` 是命令与回执的自然语言提示；`protocol_mismatch` 用于协议版本不一致；`issue.load` / `issue.generic` / `issue.decode` 是加载、通用、解码三类问题的兜底码。

## 4. RuleIssue：结构化问题

```java
public record RuleIssue(String severity, @Nullable String ruleId, String origin, String fieldPath,
                        String messageCode, List<String> messageArgs, String fallbackMessage)
```

| 字段 | 取值 / 约定 |
|---|---|
| `severity` | `error` / `warning` / `info`；缺省 `error` |
| `origin` | `overlay` / `datapack` / `runtime` / `network`；缺省 `runtime` |
| `ruleId` | 相关规则 id，可为 `null`（全局问题） |
| `fieldPath` | JSON 字段路径，如 `conditions.terms[0].condition.min`；与后端 `ConditionTrees.forEachLeaf` 的路径规则一致 |
| `messageCode` | 全限定翻译 key（两语言文件都要有） |
| `messageArgs` | 传给 `Component.translatable` 的参数，按 `%s` 顺序 |
| `fallbackMessage` | 缺失翻译时的兜底文本（英文） |

线上 JSON 键固定为 `severity` / `rule_id` / `origin` / `field_path` / `message_code` / `message_args` / `fallback_message`。

承载位置：`RuleSaveResultPayload(sessionId, operationId, statusCode, messageCode, messageArgs, resultVersion, writtenToDisk, reloaded, issues)`；`RuleSnapshot(version, contextRevision, entries, issues)` 与 `RuleSnapshotEntry(..., issues)` 也带问题清单。

## 5. 新增一个消息码

1. 在 `en_us.json` 与 `zh_cn.json` **同时**添加同名键——两个文件的键集合必须完全一致（当前各 362 个键）；
2. 命名：回执状态用 `status.<小写下划线>`，问题用 `issue.<类别>`，其余用动词短语（`save_failed` 等）；
3. 参数用 `%s`（或 `%1$s`），顺序必须与 `messageArgs` 一致；
4. 服务端生成时必须同时给出 `fallbackMessage`（英文），不要只在客户端拼字符串；
5. 客户端展示统一走 `Component.translatable(messageCode, messageArgs)`，缺失翻译时回退 `fallbackMessage`；
6. 新增 `RuleSaveStatus` 枚举值时，en_us/zh_cn 的 `status.<id>` 键要一起补，否则回执会显示原始 key。

## 6. 当前已落盘键清单（`itemdespawntowhat.edit.*`，28 个）

12 个 `status.*`（见第 2 节） + 第 3 节 16 个。

## 7. 已知缺口（记录于 2026-10-03）

以下键被代码引用但语言文件尚未落盘，属于界面/资源侧待补项（非协议键）：

| 缺失键 | 引用点 |
|---|---|
| `gui.itemdespawntowhat.edit.list.empty` / `.input_hint` / `.accessible_name` | `client/ui/widget/UiListEditor.java` |
| `gui.itemdespawntowhat.edit.preset.baby` / `.adult` | `client/edit/BuiltinEditorDescriptors.java` |
| `gui.itemdespawntowhat.edit.undo.*`（13 个，`EditSession.OP_*`） | `client/edit/EditSession.java` |
| `gui.itemdespawntowhat.edit.enum.<组>.<值>`（5 组共 13 个） | `BuiltinEditorDescriptors` 的 `enumIn(...)`；且当前 `client/ui` 尚未读取 `EditorField.enumGroup()` |

## 8. 相关

- 类型编辑器与字段描述符：[client-editor-spi.md](client-editor-spi.md)
- 后端类型注册：[custom-types.md](custom-types.md)
- 契约 §3.4 / §3.5 / §5.1：[plan-frontend-rewrite-contract.md](../plan/plan-frontend-rewrite-contract.md)
