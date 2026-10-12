# 客户端草稿持久化（`client/edit/draft`）

> 事实来源：`client/edit/draft/{DraftStore, DraftJournal, PersistedDraft, DraftConflict}.java`；接线点 `client/ui/screen/RuleEditorModel.java`。
> 草稿与输入缓冲的关系见 [editor-input-state.md](editor-input-state.md)；服务端覆盖层的权威落盘是另一条链路，见 [../backend/modules/edit-protocol.md](../backend/modules/edit-protocol.md)。

## 1. 定位与边界

草稿持久化只解决一件事：**未应用的编辑草稿在重启 / 断线后不丢**。

| 属于本主题 | 不属于本主题 |
|---|---|
| 本机中间态落盘、恢复、冲突判定、写盘节流 | 服务端覆盖层写入（`RuleOverlayWriter`） |
| 草稿文件格式与文件命名 | 编辑会话与独占锁（`EditSessionManager`） |
| 恢复时的目标级冲突提示 | 提交变更集与乐观并发（见 [edit-save-protocol.md](../backend/flows/edit-save-protocol.md)） |

三条硬边界：

- **纯客户端状态**：草稿目录由客户端读写，**绝不写入服务端目录、覆盖层目录或数据包目录**；编辑锁与覆盖层是服务端的事。
- **可失败即跳过**：目录不存在、单个文件损坏、解析失败都不抛异常，只记 WARN 并跳过该文件。
- **不做逐字段三方合并**：冲突判定只到**整条规则**粒度，字段级合并对玩家无法解释，界面上也给不出可信结果。

## 2. 类清单

### 2.1 `DraftStore`：文件仓库

草稿目录常量 `DraftStore.DIRECTORY = "config/itemdespawntowhat/client/editor-drafts"`，**相对游戏根目录**（Minecraft 工作目录即游戏根目录），因此不依赖任何加载器 API，common 侧可直接使用。一个编辑目标一份 JSON 文件。

| 成员 | 行为 |
|---|---|
| `save(PersistedDraft)` | 写同目录临时文件后原子替换；失败记 WARN 并返回 `false` |
| `loadAll()` | 按文件名排序读取全部 `*.json`；目录缺失返回空表；损坏文件跳过 |
| `delete(String)` | 删除该目标草稿；文件不存在视为成功 |
| `clear()` | 清空目录中本模组写出的 `*.json` 与 `*.json.tmp` |
| `fileName(String)` | 目标 id → 文件名（见 §4） |
| `directory()` | 草稿目录 |

原子替换由私有 `moveInto` 完成：优先 `ATOMIC_MOVE`，文件系统不支持时退化为普通替换。私有 `migrate` 负责旧命名迁移——按新文件名重写成功后删除旧文件，内容里带 `target_id`，迁移不丢数据。

### 2.2 `DraftJournal`：落盘节流器

在 `DraftStore` 之上维护「哪些目标待写盘」，按时间窗口合并写盘请求。

| 常量 / 成员 | 行为 |
|---|---|
| `SAVE_DELAY_MS = 2000L` | 节流窗口：连续编辑只在停顿后写一次盘 |
| `restore()` | 从磁盘恢复全部草稿；**同 id 保留 `saved_at` 较新的一份** |
| `markDirty(PersistedDraft)` | 记录内容并安排 `now + SAVE_DELAY_MS` 写盘 |
| `flushDue(long)` | 写入所有到期条目，有写入返回 `true` |
| `flushAll()` | 无视窗口立即写盘全部待写条目（界面关闭、会话失效等关键时刻） |
| `writeNow(PersistedDraft)` | 立即写单个目标（冲突处理、应用失败后保留草稿） |
| `drop(String)` / `dropAll()` | 丢弃草稿并删除对应文件 |
| `consumeFailures()` | 取走并清零写盘失败事件计数 |

失败处理：`persist` 失败时把该目标重新排入待写队列（下个窗口重试）；**一次成功之前的连续失败只累计一次事件**，避免界面反复刷同一条提示。

### 2.3 `PersistedDraft`：落盘载体

一个编辑目标一份，字段全部可缺省（缺省按 `false` / `0` / `null` 处理）。除草稿内容外还记录建立草稿时的**服务端基线与修订号**，用于恢复时的整条规则比较。

### 2.4 `DraftConflict`：冲突描述

`Reason` 三态：`TARGET_MISSING`（原始目标在服务端已不存在）、`TARGET_EXISTS`（草稿按新建保存，服务端已有同名规则）、`REMOTE_CHANGED`（服务端整条内容与基线不同）。`messageSuffix()` 映射到文案键 `gui.itemdespawntowhat.edit.conflict.message.<suffix>`。

## 3. 落盘文件格式

### 3.1 JSON 字段

`PersistedDraft.toJson()` 写出，`fromJson(JsonObject)` 解析：

| 字段 | 类型 | 含义 |
|---|---|---|
| `format` | int | 格式版本，当前 `PersistedDraft.FORMAT_VERSION = 1`；将来结构变更时据此迁移或丢弃 |
| `target_id` | string | 编辑目标标识；**必填** |
| `draft` | object | 草稿内容（动态值快照）；**必填** |
| `created` | bool | 草稿是按「新建规则」建立的 |
| `restore` | bool | 草稿是「恢复原始版本」标记 |
| `deleted` | bool | 草稿带删除标记 |
| `base_version` | int | 建立草稿时的覆盖层修订号 |
| `base_context_revision` | int | 建立草稿时的上下文修订号 |
| `saved_at` | long | 写盘时刻，恢复时的择新依据 |
| `baseline` | object | 建立草稿时的服务端基线（动态值快照）；可缺省 |

缺 `target_id` 或 `draft`、或 `draft` 不是对象时 `fromJson` 返回 `null`（由调用方跳过）。非布尔 / 非数字字段一律按缺省值处理，用于兼容旧文件。

### 3.2 文件命名

`DraftStore.fileName(targetId)` = **可读前缀 + 稳定短哈希 + `.json`**：

- 前缀由 `sanitize` 清洗：只保留 ASCII 字母、数字与 `.` `_` `-`，其余替换为 `_`；结果为空则用 `_` 占位。
- 短哈希是 **FNV-1a 32 位**，固定 8 位十六进制，与 JVM / 平台无关。
- 短哈希用于区分清洗后前缀相同的 id（如 `idtw:foo/bar` 与 `idtw:foo_bar`）。**id 本身仍存在于文件内容里，不从文件名反向还原**。

## 4. 恢复与冲突判定

接线点是 `RuleEditorModel`：构造时调 `journal.restore()` 把磁盘草稿读进 `restored`；**真正绑定要等服务端快照**，发生在 `refresh()`。

`refresh()` 对每条恢复的草稿按以下顺序判定（只比整条规则，不比字段）：

| 条件 | 结论 |
|---|---|
| `created == true` 且服务端已有内容 | `TARGET_EXISTS` |
| `created == false` 且服务端无生效内容 | `TARGET_MISSING` |
| 服务端有内容，且基线缺失或与当前内容不等 | `REMOTE_CHANGED` |

- 服务端当前内容取快照条目的 `effective` 视图；**`effective` 为 null 即视为目标不存在**。
- **基线缺失（旧版草稿）保守判为 `REMOTE_CHANGED`**，把选择权交给玩家而不是静默采用。
- 判定后草稿仍会被装载为 `EditSession`（`RuleDraft.replaceWith(draftValue())` + `setDeleted(...)`），冲突只是提示，不阻止编辑。
- 恢复的草稿会立刻 `markDirty` 一次；此后每条会话历史变化都经 `bindSession` 挂上的监听器触发布草稿。

冲突文案键：`gui.itemdespawntowhat.edit.conflict.title` 与三条 `...conflict.message.<suffix>`，玩家可见选项为保留草稿 / 采用服务端版本 / 稍后决定。

## 5. 与相邻链路的关系

- **草稿 vs 输入缓冲**：草稿落盘与 `EditSession.snapshot()` 只读 JSON，**未完成的输入文本不落盘**；退出并重新打开编辑器不保证恢复文本。见 [editor-input-state.md](editor-input-state.md)。
- **草稿 vs 提交**：提交走工作区的变更集上行，服务端校验拒绝**不清除草稿**；草稿只在保存成功、冲突选择「采用服务端版本」或显式丢弃时移除。
- **草稿 vs 覆盖层**：客户端永远不直接写覆盖层；草稿是本机中间态，落盘成功不代表规则已被服务端接受。

## 6. 扩展点与约束

| 要做什么 | 落点 | 约束 |
|---|---|---|
| 改落盘格式 | `PersistedDraft` | 提升 `FORMAT_VERSION` 并写出迁移或丢弃策略；保持缺省值容忍 |
| 改节流窗口 / 重试策略 | `DraftJournal` | 保持「关键时刻 `flushAll`」不丢草稿；连续失败只计一次事件 |
| 增删冲突类型 | `DraftConflict.Reason` + 三处：`messageSuffix`、判定点、**中英双语 lang** | 文案键必须同时进 `en_us.json` 与 `zh_cn.json` |
| 改文件命名 | `DraftStore.fileName` | 变更即需要迁移，保证旧文件仍能读出；短哈希不得改用平台相关实现 |

## 7. 相关

- 输入缓冲与历史：[editor-input-state.md](editor-input-state.md)
- 编辑器四页契约：[editor-pages-contract.md](editor-pages-contract.md)
- 服务端编辑协议与权威落盘：[../backend/modules/edit-protocol.md](../backend/modules/edit-protocol.md)
- 端到端保存链路：[../backend/flows/edit-save-protocol.md](../backend/flows/edit-save-protocol.md)
