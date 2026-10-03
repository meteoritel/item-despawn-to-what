# 功能模块：编辑协议与持久化（`core/network` + `core/service` 写入/会话）

> 事实来源：`core/network/protocol/**`、`core/network/transport/**`、`core/network/catalog/**`、`core/service/{RuleEditService, EditSessionManager, RuleEditSessionLimits, RuleOverlayWriter, RuleSubmissionValidator, RuleSnapshotAssembler}.java`。
> 客户端编辑器已落地，本协议为现行客户端编辑通道。规则契约（新增字段与条件树）见 [rule-model.md](rule-model.md)；决策：[ADR-0016](../../../adr/0016-edit-protocol-changeset-version-stamp.md)、[ADR-0019](../../../adr/0019-exclusive-edit-session-and-target-lock.md)、[ADR-0020](../../../adr/0020-source-catalog-and-overlay-control-entries.md)。

## 1. 类清单

### 1.1 `core/network/protocol`（协议数据模型）

| 类 | 职责 |
|---|---|
| `RuleEditProtocol` | 协议版本常量 `VERSION = 2`；握手不一致即拒绝 |
| `RuleSnapshot` | `{version, context_revision, List<RuleSnapshotEntry> entries, List<RuleIssue> issues}`；`serialize()`/`parse()`。客户端只按 JSON 渲染，不依赖服务端模型类 |
| `RuleSnapshotEntry` | 单条规则的来源三视图：`{id, origin, status, editable, effective, base, overlay, issues}`。`origin ∈ overlay\|datapack\|mixed`，`status ∈ active\|disabled\|masked\|invalid` |
| `RuleEdit` | `{id, action(UPSERT\|DELETE), rule}`；upsert 必带 rule，id 归一为小写下划线 |
| `RuleEditChangeSet` | `{expected_version, List<RuleEdit> edits}`；乐观并发基准，`serialize()`/`parse()` |
| `RuleSaveStatus` | 回执状态码枚举（见 §4.2）；`messageKey()` = `itemdespawntowhat.edit.status.<小写状态码>` |
| `RuleIssue` | 结构化问题：`{severity, rule_id, origin, field_path, message_code, message_args, fallback_message}`；`severity ∈ error\|warning\|info`，`origin ∈ overlay\|datapack\|runtime\|network` |
| `RuleCatalog` | 选择目录分页结果：`{type, revision, entries, last_page}` |
| `RuleCatalogEntry` | 目录条目：`{id, label, sub_label, icon}`（subLabel/icon 允许空串） |
| `RuleCatalogType` | 目录类型枚举：`ITEM / BLOCK / ENTITY / LOOT_TABLE / BIOME / DIMENSION / TAG / FLUID / MOB_EFFECT` |

### 1.2 `core/network/transport`（载荷）

| 载荷 | 方向 | 字段 |
|---|---|---|
| `ConfirmRuleEditorPayload` | C2S | `sessionId` / `protocolVersion` |
| `HeartbeatRuleEditorPayload` | C2S | `sessionId` |
| `CloseRuleEditorPayload` | C2S | `sessionId` / `reasonCode` |
| `RequestRuleSnapshotPayload` | C2S | `sessionId` / `requestId` |
| `RequestRuleCatalogPayload` | C2S | `sessionId` / `requestId` / `catalogType` / `filter` / `page` / `pageSize`（`MAX_PAGE_SIZE = 200`） |
| `SaveRuleChangeSetPayload` | C2S | `sessionId` / `operationId` / `changeSetJson`（直发，≤ `MAX_DIRECT_CHARS`） |
| `SaveRuleChangeSetChunkPayload` | C2S | `sessionId` / `operationId` / `transferId`(≤64) / `index` / `count` / `chunk`(≤ `MAX_CHUNK_CHARS`) |
| `OpenRuleEditorPayload` | S2C | `sessionId` / `targetId` / `protocolVersion` / `contextRevision` / `statusCode` / `messageArgs` / `fallbackMessage`；取锁失败时同样下发（`statusCode=LOCK_BUSY`、`sessionId` 为空）。唯一开屏路径：客户端 `RuleEditClientWorkspace` 用 `installOpenEditorPayloadSink(Consumer)` 注册，成功后 `EditorScreenHooks.open(request)` |
| `RuleSnapshotPayload` | S2C | `sessionId` / `requestId` / `snapshotJson`（≤ `MAX_SNAPSHOT_CHARS`） |
| `RuleSnapshotChunkPayload` | S2C | `sessionId` / `requestId` / `transferId` / `index` / `count` / `chunk` |
| `RuleSaveResultPayload` | S2C | `sessionId` / `operationId` / `statusCode` / `messageCode` / `messageArgs` / `resultVersion` / `writtenToDisk` / `reloaded` / `issues` |
| `RuleCatalogPayload` | S2C | `sessionId` / `requestId` / `catalogType` / `revision` / `json` / `lastPage` |

### 1.3 `core/network/transport`（工具与服务端）

| 类 | 职责 |
|---|---|
| `RuleEditLimits` | 全部限额常量与 UTF-8 字节计算 `encodedLength`（见 §4.3） |
| `RuleChangeSetChunker` / `RuleSnapshotChunker` | 上行变更集 / 下行快照切分：不超直发上限出整包，否则按 **UTF-8 字节**切分（不拆代理对） |
| `RuleEditTextChunks` | 共享的按 UTF-8 字节切分实现（上下行同一口径） |
| `RuleEditChunkAccumulator` | 上行分片重组：逐片校验、**重复下标以首片为准**、收齐后 `join()` 并复核总量；过期（1200 tick）清理 |
| `RuleEditPayloadCodec` | 载荷共用编解码片段（字符串列表 / 可空字符串 / `RuleIssue` 列表）；超限即抛解码异常 |
| `RuleEditPayloadRouter` | 客户端入站门面：字符串 sink（兼容）+ 结构化 sink（快照 / 分片 / 回执 / 目录）；服务端只装载空消费者 |
| `OpenRuleEditorPayload` | 承载 `installOpenEditorPayloadSink` / `dispatchOpenEditor`（开屏入口的唯一静态 sink） |
| `RuleEditServerContext` | 窄接口：向网络层暴露运行时能力（由平台 `RuleRuntimeHost` 实现） | `overlayRoot()`、`overlayNamespace()`、`typeRegistries()`、`loadMerged(server)`、`rebuildAndRescan(server)`、`sendTo(player, payload)` |
| `RuleEditServerHandler` | 服务端处理编排（**薄转发**） | `openEditor`、`handleConfirm`、`handleHeartbeat`、`handleClose`、`handleSnapshotRequest`、`handleCatalogRequest`、`handleChangeSet`、`handleChangeSetChunk`、`expireIdle`、`clearPlayer`、`reset`、`isReady`、`canEdit`、`sessionManager`/`service` |

### 1.4 `core/network/catalog`

| 类 | 职责 |
|---|---|
| `RuleCatalogService` | 消费 `core/catalog` 的候选数据源：按 `类型 + 来源修订号` 缓存、大小写不敏感过滤（id/label/subLabel）、分页切分、`invalidate()` 失效；只读切分，不做权限与会话校验 |

### 1.5 `core/service`（写入/会话）

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleEditService` | 编辑会话的业务编排（打开/确认/心跳/关闭/快照/目录/保存），只产出"要下发的载荷"，不直接碰网络 | `openEditor`、`confirm`、`heartbeat`、`close`、`requestSnapshot`、`requestCatalog`、`submitChangeSet`、`submitChunk`、`expireSessions`、`forceRelease`；幂等表 `recentResults`（最近 16 条） |
| `EditSessionManager` | 全局单一目标的会话状态机（FREE/OPENING/ACTIVE/APPLYING）与 `.edit_version` 版本戳 | `acquire` / `confirm` / `heartbeat` / `owns` / `isOwner` / `confirmed` / `beginApply` / `finishApply` / `release` / `expire` / `version` / `bumpVersion` / `synchronizeDiskRevision` |
| `RuleEditSessionLimits` | 会话时间与权限常量 | `TARGET_ID = itemdespawntowhat:rules`、`OPEN_CONFIRM_WINDOW_SECONDS=15`、`HEARTBEAT_INTERVAL_SECONDS=10`、`LEASE_SECONDS=60`、`REQUIRED_PERMISSION_LEVEL=2`、`MAX_OPERATION_HISTORY=16` |
| `RuleOverlayWriter` | 覆盖层逐 id 写入：预备 → 备份 → 原子提交 → 失败回滚 | `apply(changes, issues) → ApplyResult{writtenFiles, conflicts}` |
| `RuleSubmissionValidator` | 写入前唯一校验闸门（控制条目 / 解码 + 语义 + 动态引用） | `validate(changeSet, server, types, issues)` |
| `RuleSnapshotAssembler` | 来源分层索引 + 当前生效规则 → 下发快照 | `assemble(index, merged, version, contextRevision, effectTypes, conditionTypes, issues)` |

## 2. 快照形状

快照 = `{version, context_revision, entries[], issues[]}`，标题字段当前都取 `EditSessionManager.version()`（覆盖层修订号）。

条目 `RuleSnapshotEntry(id, origin, status, editable, effective, base, overlay, issues)`：

- **三视图**：`base` 为数据包层原始 JSON，`overlay` 为覆盖层原始 JSON，两者都保留原文（**不做模型往返**）；`effective` 优先取**模型编码结果**（解码成功时权威），失败时退回 `overlay` 或 `base` 原文，保证客户端仍能看到内容。缺失的视图以 JSON `null` 下发，客户端按"无此视图"处理。
- **`editable` 语义**：`editable = base != null || overlay != null`（该条目存在规则内容）。**纯数据包规则同样为 `true`**；`editable` **不表示「能否表单编辑」**——`status = invalid`（解码失败）的条目同样可以为 true，是否只读展示由客户端按 `status` / `issues` 决定。
- **`origin`**：`overlay` / `datapack` / `mixed`（两层都有即 mixed），由 `RuleSourceIndex` 归并结果判定。
- **`status`**：控制条目声明删除→`masked`、声明停用→`disabled`；否则解码成功→`active`、解码失败→`invalid`。`invalid` 条目会带上 `severity=error` 的 `RuleIssue`（无法定位时兜底 `itemdespawntowhat.edit.issue.decode`）。
- **`issues`**：把加载/校验问题按 `origin` / `field_path` 关联到具体条目；无法关联的问题作为快照级 issue（`origin=runtime`）下发。

> 控制条目判定必须**判取值而非存在性**：`"disabled": false` 是普通规则。加载层 `readFlag` 与 `RuleSnapshotAssembler` 都遵守该约定；用 `has()` 会把普通规则误当控制条目。

## 3. 独占编辑会话与目标锁

- **单一目标**：全局仅一个编辑目标 `TARGET_ID = itemdespawntowhat:rules`，同一时刻至多一个非 FREE 会话（不再按玩家分锁或按 UUID 隔离）。
- **状态机**：`FREE --取锁--> OPENING --客户端确认--> ACTIVE --提交变更集--> APPLYING --应用结束--> ACTIVE`；释放 / 租约到期 / 强制释放一律回到 FREE。
- **会话字段**：`ownerUuid`、不可预测的 `sessionId`（`UUID.randomUUID()`）、`state`、`openedTick`、`lastHeartbeatTick`。
- **时钟**：一律用服务端活动 tick（`MinecraftServer.getTickCount()`，20 tick = 1 秒），**单人世界暂停时租约不流逝**；服务端每 20 tick 调一次 `expireIdle`。
- **会话按玩家能力拆两层**：`EditSessionManager` 持有状态机 + 版本戳；`RuleEditService` 持有幂等表并产出载荷；`RuleEditServerHandler` 只做就绪/权限检查与发包转发。

## 4. 服务端处理流程

固定顺序（`RuleEditServerHandler` → `RuleEditService`）：

```text
请求快照：权限 → 运行时就绪 → 会话持有校验(owns + isOwner + confirmed) → heartbeat 续租
  → synchronizeDiskRevision(磁盘修订核对) → RuleSnapshotAssembler.assemble → 分片切分下发
提交变更集：operationId 合法性 → 总量上限 → 会话持有校验 → 幂等查表(命中直接回放)
  → 解析 RuleEditChangeSet → beginApply
  → 空集 → NO_CHANGES
  → synchronizeDiskRevision → 版本戳校验(不一致整批拒绝 VERSION_CONFLICT + 最新快照，不落盘)
  → RuleSubmissionValidator 完整校验(失败 VALIDATION_FAILED，不落盘)
  → RuleOverlayWriter.apply(基于磁盘原始内容) → conflicts 或 issues.errors 非空 → WRITE_FAILED
  → writtenFiles==0 → NO_CHANGES
  → rebuildAndRescan(重建索引+全维度回扫) → 成功 SUCCESS / 失败 SAVED_NOT_RELOADED
  → finishApply → 回执 + (冲突/未重载时追加)最新快照
```

### 4.1 授权与限流

- **权限** `canEdit` 与 `/idtw` 的 `hasAccess` 一致：单人世界或权限等级 ≥2；未就绪/无权限直接回 `UNAVAILABLE` / `NO_PERMISSION`。
- **每个授权检查点**（心跳、快照、目录、变更集、分片）都校验 `sessionId` 与状态匹配，失败回 `SESSION_EXPIRED` / `LOCK_NOT_OWNED`。
- **幂等**：`operationId` 由客户端生成，服务端对同一会话保留最近 16 条已处理回执，重复提交直接回放、不重复写盘。
- **并发保存保护**：`beginApply` 只允许 ACTIVE→APPLYING；正在 APPLYING 时的新提交回 `INVALID_REQUEST`（`apply in progress`）并保留会话。
- 玩家断开仅清分片缓存（`clearPlayer`），会话按租约到期释放；停服 `reset()` 清空分片与幂等表。

### 4.2 回执状态码

`RuleSaveStatus`：`SUCCESS / NO_CHANGES / NO_PERMISSION / LOCK_NOT_OWNED / LOCK_BUSY / SESSION_EXPIRED / VERSION_CONFLICT / VALIDATION_FAILED / WRITE_FAILED / SAVED_NOT_RELOADED / INVALID_REQUEST / UNAVAILABLE`。客户端**只认状态码分支**，禁止解析文案；打包失败（快照/解析）也各自有兜底状态（如 `itemdespawntowhat.edit.snapshot_failed`）。

### 4.3 网络限额

命名空间 `idtw`。直发 32 000 B；单片 30 000 B；变更集/快照上限均 4 MiB；`MAX_CHUNK_COUNT = ceil(4MiB / 30000)`（=140）；transferId ≤64；每玩家并发 2 个在途传输；空闲超时 60 s（1200 tick）；`MAX_ID_CHARS=64`、`MAX_CODE_CHARS=128`、`MAX_FALLBACK_CHARS=512`、`MAX_MESSAGE_ARGS=8`、`MAX_ISSUES_PER_RESULT=64`；编解码器留 `PACKET_SLACK_BYTES=4096` 余量，超限请求先抵服务端再回 `INVALID_REQUEST`，不直接断连。目录单页上限 200（默认 50）。**长度始终按 UTF-8 字节计**（`encodedLength`），防多字节绕过。

### 4.4 权威落盘

- 写盘依据**磁盘文件内容**，按 id 逐条 upsert/delete，保留未编辑条目、非对象数组成员与未知字段；命中控制条目或解码失败的文件不会被覆盖。
- 写文件用**原子写 + `.bak` 备份**，逐文件替换；任一环节失败**逆序回滚**已提交文件。删除单条文件的唯一条目时写成**空数组**而非删文件。
- `ApplyResult.writtenFiles()==0` 或 `conflicts` 非空都不算保存成功；I/O 失败尝试恢复，回滚失败明确报错并保留 `.bak`。
- **路径安全**：拒绝 `.`/`..` 路径段与越界、拒绝跟随符号链接；同 id 多处定义直接拒绝，要求人工消歧。

## 5. 版本戳与磁盘修订

- 版本文件 `overlayRoot/.edit_version`（无 `.json` 后缀，规则读取器不会扫描它）；缺失/非法按 0，`bumpVersion` 自增并落盘（落盘失败仅保持内存计数）。
- **成功的数据包重载 / 保存 / 覆盖层外部改动均推进版本戳**。
- `synchronizeDiskRevision` 对 `rules/**/*.json` 做 SHA-256 指纹（**含坏 JSON 原始字节**）比对，检测到手工改动即自增；**无法核对磁盘时拒绝保存**（不能把未知修订当作仍然匹配）。快照生成路径也先做这一步，避免客户端拿过期版本提交。

## 6. 分片传输

`RuleEditChunkAccumulator.accept`：逐片校验（transferId 长度、下标范围、单片字节数），**重复下标以首片为准**，收齐后 `join()` 并复核总量（≤4 MiB）；超过在途上限时丢弃最早传输；过期会话在 `expireIdle` 顺带清理。每玩家最多 2 个在途传输，未收齐不回执。`handleChangeSetChunk` 收齐后复用 `handleChangeSet` 的同一保存流程。下行快照超 4 MiB 时才分片（`RuleSnapshotChunker`），超过总片数上限则无法下发，回结构化失败回执。

## 7. 新契约字段如何进入快照与校验

规则契约（`display_name`、`triggers`、`source_cost`、`catalyst_cost`、`combination`、`outcomes`、`schema_version` 与条件树 `conditions`）通过两条通路进入编辑通道，无需在协议层逐个建字段：

- **快照侧**：覆盖层规则直接下发**文件原始 JSON**，所有字段（含尚未识别的字段）原样保留；数据包/内置规则由 `RuleCodecs` 的模型编码下发，天然覆盖全部契约字段。模型解码成功时 `effective` 用编码结果（权威形态），失败退回原文。
- **校验侧**：`RuleSubmissionValidator.validate` 是写入前的唯一闸门——对每条 upsert 用 `RuleCodecs.decoder(...)` 做**完整契约解码**（含条件树、新字段、候选结果与固定成本），再跑 `RuleValidation.validate`（语义）与 `RuleReferenceValidator.validate`（动态引用）。任一错误即 `issues.errors` 非空。
- **拒绝有损保存的落点**：旧 GUI 无法表达新结构（如仍写旧版二维数组条件、整数写法 `catalyst_cost`）时，解码**明确报错**（不静默兼容），整批在校验阶段被拒绝、**不落盘**，回 `VALIDATION_FAILED` 并附 `RuleIssue` 定位到具体字段。控制条目（`disabled`/`delete`）走独立校验分支：只允许 id + 一个值为 true 的控制字段。

## 8. 扩展点

- **改限额**：改 `RuleEditLimits`（注意逐片、总量、快照、并发、超时多处）。
- **改写盘策略**：改 `RuleOverlayWriter`（保持"整批预备 + 原子替换 + 失败回滚 + `verifyUnchanged`"）。
- **改快照形状**：改 `RuleSnapshotAssembler`（保持覆盖层用原始 JSON、`editable = base != null || overlay != null`）。
- **接入新目录类型**：在 `core/catalog` 实现 `RuleCatalogSource` 并登记到 `RuleCatalogSources.builtin`；枚举值加进 `RuleCatalogType`。
- **新增 payload**：新增协议类 + 在两端注册器登记；C2S 与 S2C **类型**都必须在公共初始化注册，否则连接协商判定通道缺失。

## 9. 相关

- 端到端链路：[../flows/edit-save-protocol.md](../flows/edit-save-protocol.md)
- 规则模型与字段：[rule-model.md](rule-model.md)
- 平台注册：[platform.md](platform.md)、[../systems/platform-abstraction.md](../systems/platform-abstraction.md)
- 校验：[../systems/issue-validation.md](../systems/issue-validation.md)
- 决策：[ADR-0016](../../../adr/0016-edit-protocol-changeset-version-stamp.md)、[ADR-0019](../../../adr/0019-exclusive-edit-session-and-target-lock.md)
