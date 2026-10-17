# 功能模块：编辑协议与持久化（`core/network` + `core/service` 写入/会话）

> 事实来源：`core/network/protocol/**`、`core/network/transport/**`、`core/network/catalog/**`、`core/service/{RuleEditService, EditSessionManager, RuleEditSessionLimits, RuleOverlayWriter, RuleSubmissionValidator, RuleSnapshotAssembler}.java`。
> 客户端编辑器已落地，本协议为现行客户端编辑通道。规则契约（新增字段与条件树）见 [rule-model.md](rule-model.md)；决策：[ADR-0019](../../../adr/0019-exclusive-edit-session-and-target-lock.md)、[ADR-0020](../../../adr/0020-source-catalog-and-overlay-control-entries.md)。

## 1. 类清单

### 1.1 `core/network/protocol`（协议数据模型）

| 类 | 职责 |
|---|---|
| `RuleEditProtocol` | 协议版本常量 `VERSION = 5`；握手不一致即拒绝 |
| `RuleSnapshot` | `{version, context_revision, List<RuleSnapshotEntry> entries, List<RuleIssue> issues}`；`serialize()`/`parse()`。客户端只按 JSON 渲染，不依赖服务端模型类 |
| `RuleSnapshotEntry` | 单条规则的来源三视图：`{id, origin, status, editable, category, effective, base, overlay, issues}`。`origin ∈ overlay\|datapack\|mixed`，`status ∈ active\|disabled\|masked\|invalid`；`category` 为显式分类，未声明时为 `null` |
| `RuleEdit` | `{id, action(UPSERT\|DELETE), rule}`；upsert 必带 rule，id 归一为小写下划线 |
| `RuleEditChangeSet` | `{expected_version, List<RuleEdit> edits}`；乐观并发基准，`serialize()`/`parse()` |
| `RuleSaveProgress` | 不可变阶段、计数、当前内容与可取消标记；当前内容最长 512 UTF-16 单元 |
| `RuleSaveStatus` | 回执状态码枚举（见 §4.3）；`messageKey()` = `itemdespawntowhat.edit.status.<小写状态码>` |
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
| `CancelRuleSavePayload` | C2S | `sessionId` / `operationId`；只请求取消，不关闭 GUI 或释放会话 |
| `RequestRuleSnapshotPayload` | C2S | `sessionId` / `requestId` |
| `RequestRuleCatalogPayload` | C2S | `sessionId` / `requestId` / `catalogType` / `filter` / `page` / `pageSize`（`MAX_PAGE_SIZE = 200`） |
| `RequestRuleLifespanPayload` | C2S | `sessionId` / `requestId` |
| `RuleLifespanPayload` | S2C | `sessionId` / `requestId` / `lifespanTicks`（普通物品参考寿命；零表示尚未采样） |
| `SaveRuleChangeSetPayload` | C2S | `sessionId` / `operationId` / `changeSetJson`（直发，≤ `MAX_DIRECT_CHARS`） |
| `SaveRuleChangeSetChunkPayload` | C2S | `sessionId` / `operationId` / `transferId`(≤64) / `index` / `count` / `chunk`(≤ `MAX_CHUNK_CHARS`) |
| `OpenRuleEditorPayload` | S2C | `sessionId` / `targetId` / `protocolVersion` / `contextRevision` / `statusCode` / `messageArgs` / `fallbackMessage`；取锁失败时同样下发（`statusCode=LOCK_BUSY`、`sessionId` 为空）。唯一开屏路径：客户端 `RuleEditClientWorkspace` 用 `installOpenEditorPayloadSink(Consumer)` 注册，成功后 `EditorScreenHooks.open(request)` |
| `RuleSnapshotPayload` | S2C | `sessionId` / `requestId` / `snapshotJson`（≤ `MAX_SNAPSHOT_CHARS`） |
| `RuleSnapshotChunkPayload` | S2C | `sessionId` / `requestId` / `transferId` / `index` / `count` / `chunk` |
| `RuleSaveProgressPayload` | S2C | `sessionId` / `operationId` / `sequence` / `progress{stage, completed, total, current, cancellable}` |
| `RuleSaveResultPayload` | S2C | `sessionId` / `operationId` / `statusCode` / `messageCode` / `messageArgs` / `resultVersion` / `writtenToDisk` / `reloaded` / `issues` |
| `RuleCatalogPayload` | S2C | `sessionId` / `requestId` / `catalogType` / `revision` / `json` / `lastPage` |

### 1.3 `core/network/transport`（工具与服务端）

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleEditLimits` | 全部限额常量与 UTF-8 字节计算 | `encodedLength(String)`、`MAX_*` 常量组（见 §4.4 常量清单） |
| `RuleChangeSetChunker` / `RuleSnapshotChunker` | 上行变更集 / 下行快照切分：不超直发上限出整包，否则按 **UTF-8 字节**切分（不拆代理对） | `split(sessionId, operationId, json)` / `split(sessionId, requestId, json)` |
| `RuleEditTextChunks` | 共享的按 UTF-8 字节切分实现（上下行同一口径） | `splitByUtf8Bytes`、`utf8Length`（包内） |
| `RuleEditChunkAccumulator` | 上行分片重组：逐片校验、**重复下标以首片为准**、收齐后返回有序分片并复核累计字节，后台按序拼接；过期（1200 tick）清理，在途传输超量时按插入顺序淘汰最早的一条 | `accept`、`isShapeValid`、`expireIdle`、`clear`、`clearAll`、`AcceptResult` |
| `RuleEditPayloadCodec` | 载荷共用编解码片段（字符串列表 / 可空字符串 / `RuleIssue` 列表）；超限即抛解码异常 | `writeStrings`/`readStrings`、`writeNullableString`/`readNullableString`、`writeIssues`/`readIssues`（包内） |
| `RuleEditPayloadRouter` | 客户端入站门面：字符串 sink（兼容）+ 结构化 sink（快照 / 分片 / 回执 / 进度 / 目录 / 寿命）；服务端只装载空消费者 | `installSnapshotSink`、`installSnapshotPayloadSink`、`installResultSink`、`installResultPayloadSink`、`installCatalogSink`、`installChunkSink`、`installLifespanSink`、`dispatchSnapshot`、`dispatchResult`、`dispatchChunk`、`dispatchCatalog`、`dispatchLifespan`、`installProgressSink`、`dispatchProgress` |
| `OpenRuleEditorPayload` | 承载 `installOpenEditorPayloadSink` / `dispatchOpenEditor`（开屏入口的唯一静态 sink） | `TYPE`、`STREAM_CODEC` |
| `RuleEditServerContext` | 窄接口：向网络层暴露运行时能力（由公共宿主核心以适配器实现，平台宿主只转发） | `overlayRoot()`、`overlayNamespace()`、`typeRegistries()`、`loadMerged(server)`、`rebuildAndRescan(server)`、`sendTo(player, payload)`、`referenceLifespanTicks()`、`saveReloadContext()`、`scheduleTask(realmKey, task, delayTicks)`、`applySavedIndex(server, index)` |
| `RuleEditServerHandler` | 服务端处理编排（**薄转发**） | `openEditor`、`handleConfirm`、`handleHeartbeat`、`handleClose`、`handleSnapshotRequest`、`handleCatalogRequest`、`handleLifespanRequest`、`handleChangeSet`、`handleChangeSetChunk`、`handleCancelSave`、`expireIdle`、`clearPlayer`、`reset`、`isReady`、`canEdit`、`sessionManager`/`service` |

### 1.4 `core/network/catalog`

| 类 | 职责 |
|---|---|
| `RuleCatalogService` | 消费 `core/catalog` 的候选数据源：按 `类型 + 来源修订号` 缓存（缓存归属请求时的服务端实例，换服整体清空）、大小写不敏感过滤（id/label/subLabel）、分页切分、`invalidate()` 显式失效（保存写出后调用）；只读切分，不做权限与会话校验 |

### 1.5 `core/service`（写入/会话）

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleEditService` | 编辑会话的业务编排（打开/确认/心跳/关闭/快照/目录/寿命/保存），只产出"要下发的载荷"，不直接碰网络 | `openEditor`、`confirm`、`heartbeat`、`close`、`requestSnapshot`、`requestCatalog`、`requestLifespan`、`pollLifespan`、`submitChangeSet`、`submitChunk`、`cancelSave`、`pollSaveProgress`、`pollSave`、`expireSessions`、`forceRelease`；幂等表 `recentResults`（最近 16 条） |
| `EditSessionManager` | 全局单一目标的会话状态机（FREE/OPENING/ACTIVE/APPLYING），委托文件所有者管理修订号 | `acquire` / `confirm` / `heartbeat` / `owns` / `isOwner` / `confirmed` / `beginApply` / `finishApply` / `release` / `expire` / `version` / `bumpVersion` / `readDiskRevision` / `acceptDiskRevision` |
| `RuleEditSessionLimits` | 会话时间与权限常量 | `TARGET_ID = itemdespawntowhat:rules`、`OPEN_CONFIRM_WINDOW_SECONDS=15`、`HEARTBEAT_INTERVAL_SECONDS=10`、`LEASE_SECONDS=60`、`REQUIRED_PERMISSION_LEVEL=2`、`MAX_OPERATION_HISTORY=16` |
| `RuleOverlayStore` | 覆盖层文件工作与修订号的单一所有者：有界串行队列、内容指纹、原子持久化、进展监测、按存档实例隔离的作业登记与停服隔离 | `submit`、`acceptRevision`、`committed`、`flushRevision`、`stalled`、`close`、`requireIdle` |
| `RuleOverlayWriter` | 覆盖层逐 id 写入：预备 → 备份 → 原子提交 → 失败回滚 | `apply(changes, issues) → ApplyResult{writtenFiles, conflicts, rollbackFailed}` |
| `RuleSubmissionValidator` | 写入前唯一校验闸门（控制条目 / 解码 + 语义 + 动态引用） | `validate(changeSet, server, types, issues)` |
| `RuleSnapshotAssembler` | 来源分层索引 + 当前生效规则 → 下发快照 | `assemble`、`encodeRule`（服务端线程）、`assembleEncoded`（纯 JSON 装配） |

| `RuleSaveTask` | 公共 MAINTENANCE 任务：后台解析/读写、逐条校验、分步发布与回扫 | `step`、`invalidate`、`cancel`、`progress`、`pollCompletion`；后台工作提交给 `RuleOverlayStore` |
| `RuleSnapshotTask` | 快照读取、逐条解码/编码、后台 JSON 装配与分片 | `step`、`pollCompletion`；每 tick 最多下发 4 片 |
| `RuleSaveReloadContext` | 启动/数据包重载时发布的原始基底、文件问题与解码上下文 | 对象身份用于识别重载；后台不访问 ResourceManager |

## 2. 快照形状

快照 = `{version, context_revision, entries[], issues[]}`，标题字段当前都取 `EditSessionManager.version()`（覆盖层修订号）。

条目 `RuleSnapshotEntry(id, origin, status, editable, category, effective, base, overlay, issues)`：

- **三视图**：`base` 为数据包层原始 JSON，`overlay` 为覆盖层原始 JSON，两者都保留原文（**不做模型往返**）；`effective` 优先取**模型编码结果**（解码成功时权威），失败时退回 `overlay` 或 `base` 原文，保证客户端仍能看到内容。缺失的视图以 JSON `null` 下发，客户端按"无此视图"处理。
- **`editable` 语义（内容存在标记）**：`editable = base != null || overlay != null`，即该条目在数据包层或覆盖层是否存在非控制条目内容（只被控制条目声明停用 / 删除时为 `false`，**纯数据包规则为 `true`**）；客户端据此决定打开表单编辑还是只展示详情——`status = invalid` 或带 error 级 issue 的条目即使 `editable = true` 也只读展示。
- **`category`（展示分类）**：读取胜出原始视图（`overlay` 优先于 `base`）顶层的 `category`；缺失、非字符串或空白时为 `null`。只供列表分组，不进入运行时规则模型，不参与解码与执行。
- **`origin`**：`overlay` / `datapack` / `mixed`（两层都有即 mixed），由 `RuleSourceIndex` 归并结果判定。
- **`status`**：控制条目声明删除→`masked`、声明停用→`disabled`；否则解码成功→`active`、解码失败→`invalid`。`invalid` 条目会带上 `severity=error` 的 `RuleIssue`（无法定位时兜底 `itemdespawntowhat.edit.issue.decode`）。
- **`issues`**：把加载/校验问题按 `origin` / `field_path` 关联到具体条目；无法关联的问题作为快照级 issue（`origin=runtime`）下发。

> 控制条目判定必须**判取值而非存在性**：`"disabled": false` 是普通规则。加载层 `readFlag` 与 `RuleSnapshotAssembler` 都遵守该约定；用 `has()` 会把普通规则误当控制条目。

## 3. 独占编辑会话与目标锁

- **单一目标**：全局仅一个编辑目标 `TARGET_ID = itemdespawntowhat:rules`，同一时刻至多一个非 FREE 会话（不再按玩家分锁或按 UUID 隔离）。
- **状态机**：`FREE --取锁--> OPENING --客户端确认--> ACTIVE --提交变更集--> APPLYING --应用结束--> ACTIVE`；OPENING/ACTIVE 可释放至 FREE；APPLYING 的关闭与强制释放请求延后至保存收尾，期间不因租约到期释放。
- **会话字段**：`ownerUuid`、不可预测的 `sessionId`（`UUID.randomUUID()`）、`state`、`openedTick`、`lastHeartbeatTick`。
- **时钟**：一律用服务端活动 tick（`MinecraftServer.getTickCount()`，20 tick = 1 秒），**单人世界暂停时租约不流逝**；服务端每 20 tick 调一次 `expireIdle`。
- **会话按玩家能力拆两层**：`EditSessionManager` 持有状态机 + 版本戳；`RuleEditService` 持有幂等表并产出载荷；`RuleEditServerHandler` 只做就绪/权限检查与发包转发。

## 4. 服务端处理流程

固定顺序（`RuleEditServerHandler` → `RuleEditService`）：

```text
请求快照：权限/就绪 → 会话持有与确认校验 → heartbeat → 启动 RuleSnapshotTask 后台读取
  → 后台核对修订与读取覆盖层 → 公共预算逐条解码、校验、编码（同份快照复用编解码器）
  → 后台装配三视图、序列化与切片 → 每 tick 最多下发 4 片
提交变更集：operationId 合法性 → 会话持有校验 → 幂等查表
  → 同一在途 operationId 等待同一结果；其它在途提交拒绝
  → beginApply → 排队 RuleSaveTask
  → 后台拼接分片、检查总字节、解析、读取磁盘修订 → 服务端接纳修订
  → 空集 NO_CHANGES / 版本不符 VERSION_CONFLICT
  → 公共预算逐条完整校验 → 后台预建 id 索引、准备文件、核对修订、备份与原子提交
  → 后台读取新覆盖层并合并缓存基底 → 公共预算逐条解码/校验 → 后台预建运行时索引
  → 发布索引、摘除旧队列 → 分步取消、恢复返还、回扫掉落物
  → finishApply → 回执；冲突/未重载的最新快照另行排队
```

保存步骤最多处理 `SAVE_ENTRIES_PER_STEP=8` 项，同时受公共 tick 预算和 `SAVE_STEP_BUDGET_NANOS=1_000_000`（1ms）步骤软预算约束；快照步骤最多处理 `SNAPSHOT_ENTRIES_PER_STEP=32` 项，不设单步时间预算，公共 tick 预算仍是唯一总闸。两类任务让出后最早下一 tick 续接，预算耗尽只延后不丢任务。没有固定编辑条数拒绝门槛，4 MiB 总字节限制仍有效。文件 I/O、JSON 拼接/解析/装配与纯模型索引构建使用进程级单后台线程，待执行队列上限为 `RuleOverlayStore.MAX_BACKGROUND_JOBS=8`；队列满时拒绝新后台工作，不转回主线程执行，尚未写盘的保存回 `UNAVAILABLE`。第三方 Codec、注册表相关校验、任务取消与世界访问均留在服务端线程。单次 Codec、规则查询或原版操作不可抢占，因此不承诺任意单步均小于预算。保存失败在索引发布前保留上一版索引；发布后的回扫失败回 `SAVED_NOT_RELOADED`，不回滚已发布索引。


### 4.1 保存进度与取消

- `RuleEditService.pollSaveProgress` 读取最新不可变样本，最多每 5 个活动 tick 下发一次变化；序号按操作递增。后台只写 `AtomicReference` 样本，不访问玩家或发送网络。客户端按 sessionId、operationId、sequence 接纳；无关回执与迟到进度不能解除 APPLYING。
- 进度包含解析、校验、文件准备、提交、回滚、读取、解码、索引、发布与清理、取消等待。总数为零表示不可计量；高级信息显示规则 id、相对文件名或整批范围。进度表达当前阶段计数，不估计剩余时间。
- `RuleSaveTask.CommitState` 的 OPEN→CANCELLED 与 OPEN→COMMITTED 通过 CAS 竞争：后台最后一次文件替换后原子确认提交，确认失败在同一后台调用线程逆序回滚；主线程只发失效信号，既不取消 Future，也不恢复文件。取消收尾等待所有已排队后台工作完成，不在主线程阻塞。
- 保存入口拒绝携带原 operationId；在途请求的权限丢失／不可用拒绝延后到保存收尾，不用提前回执解除客户端 APPLYING。取消只校验会话与原持有者，不授予新增写入权限。
- 提交确认前允许取消；成功回滚回 `CANCELLED`，回滚失败回 `WRITE_FAILED` 并保留 `.bak` 提示。确认提交后取消按钮禁用，迟到取消只等待原保存终态，不回滚已提交规则。分片尚未收齐时的取消清除传输并记录幂等取消回执。

### 4.2 授权与限流

- **权限** `canEdit` 与 `/idtw` 的 `hasAccess` 一致：单人世界或权限等级 ≥2；未就绪/无权限直接回 `UNAVAILABLE` / `NO_PERMISSION`。
- **每个授权检查点**（心跳、快照、目录、变更集、分片）都校验 `sessionId` 与状态匹配，失败回 `SESSION_EXPIRED` / `LOCK_NOT_OWNED`。
- **目录请求校验顺序**：先检查会话归属与已确认状态，再检查分页参数和目录类型；未确认的 OPENING 会话优先返回会话失效回执，已确认会话的非法参数返回 `INVALID_REQUEST`。仅通过全部检查的请求续期心跳。
- **寿命查询**：权限与就绪检查后，服务层校验已确认会话；只读取平台缓存的普通物品参考寿命，不解析源匹配、不展开标签、不创建实体。平台尚未采样时应答零并保留单项待交付请求，`expireIdle` 每 20 tick 检查采样状态，完成后向仍持有权限与已确认会话的玩家补发一次缓存结果。参考值不随规则重载失效；客户端仅接受当前会话与最近请求号的应答，正寿命结果保留到断线。
- **幂等**：同一在途 `operationId` 等待原任务；最近 16 条已处理回执直接回放，不重复写盘。
- **并发保存保护**：`beginApply` 只允许 ACTIVE→APPLYING；正在 APPLYING 时的新提交回 `INVALID_REQUEST`（`apply in progress`）并保留会话。
- 会话失效、关闭、强制释放或玩家离线/失去权限使尚未提交的保存失效；后台在提交检查点退出并回滚。APPLYING 期间保持锁；关闭或强制释放记录延后释放意图，`pollSave` 确认文件工作退出后通过 `finishApply` 释放；正常完成回到 ACTIVE 并刷新租约。已发布索引必须完成旧任务清理与回扫；停服在 vanilla 最终存档前同步取消未清理的结算，后台收到失效信号，停服文件等待不持有网络处理器或编辑服务监视器；未开始的文件工作可以撤销，修订持久化与运行中的工作等待真实退出。等待上限为 30 秒；到期限仍未结束的作业**放弃登记并解除对后续存档的阻塞**，记一条 ERROR（作业可能仍在写盘，仍占用那唯一一条 IO 线程），只有仍持有在途作业的存档实例才会阻塞下一个存档启动。
- `/idtw config reload/validate/list` 与 `/idtw rule list/show` 在保存期间明确提示稍后重试，避免读取整批替换的中间状态。
- 数据包重载在保存期间只保留最近一次延后请求，并使尚未提交的保存失效；文件工作结束后才执行，避免读取替换中间状态。快照至多一个准备任务与一个最新待启动请求，保存期间暂缓准备。

### 4.3 回执状态码

`RuleSaveStatus`：`SUCCESS / NO_CHANGES / CANCELLED / NO_PERMISSION / LOCK_NOT_OWNED / LOCK_BUSY / SESSION_EXPIRED / VERSION_CONFLICT / VALIDATION_FAILED / WRITE_FAILED / SAVED_NOT_RELOADED / INVALID_REQUEST / UNAVAILABLE`。客户端**只认状态码分支**，禁止解析文案；打包失败（快照/解析）也各自有兜底状态（如 `itemdespawntowhat.edit.snapshot_failed`）。

### 4.4 网络限额

命名空间 `idtw`。直发 32 000 B；单片 30 000 B；变更集/快照上限均 4 MiB；`MAX_CHUNK_COUNT = ceil(4MiB / 30000)`（=140）；transferId ≤64；每玩家并发 2 个在途传输；空闲超时 60 s（1200 tick）；`MAX_ID_CHARS=64`、`MAX_CODE_CHARS=128`、`MAX_FALLBACK_CHARS=512`、`MAX_MESSAGE_ARGS=8`、`MAX_MESSAGE_ARG_CHARS=256`、`MAX_ISSUES_PER_RESULT=64`；编解码器留 `PACKET_SLACK_BYTES=4096` 余量，超限请求先抵服务端再回 `INVALID_REQUEST`，不直接断连。目录单页上限 200（默认 50）。**长度始终按 UTF-8 字节计**（`encodedLength`），防多字节绕过。

### 4.5 权威落盘

- 写盘依据**磁盘文件内容**，按 id 逐条 upsert/delete，保留未编辑条目、非对象数组成员与未知字段；命中控制条目或解码失败的文件不会被覆盖。
- 保存扫描遇到非法 JSON 时记录包含文件路径和解析异常的告警，保留原文件并继续处理其它文件；加载路径的结构化问题仍由规则解析器报告。
- 写文件用**原子写 + `.bak` 备份**，逐文件替换；任一环节失败**逆序回滚**已提交文件，并清理本轮生成的 `.bak` 与临时文件。文件内条目被删空时**直接删除该文件**，不写空数组占位。
- **单次保存只扫描并解析目录一次**，后续阶段复用已读字节与元数据指纹：准备阶段只核对 `size + mtime` 指纹，逐文件替换前再做一次**逐字节**比对闸门。保留逐字节闸门是有意为之——只比 `size + mtime` 会漏掉同尺寸且时间戳未变的外部改动；比对不一致即判定为外部修改并整批中止。
- `ApplyResult.writtenFiles()==0` 或 `conflicts` 非空都不算保存成功；I/O 失败尝试恢复，回滚失败明确报错并保留 `.bak`。
- **路径安全**：拒绝 `.`/`..` 路径段与越界、拒绝跟随符号链接；编辑命中同 id 多处定义时拒绝，未编辑的歧义条目不阻断其它保存。
- **查询索引**：加载后一次构建 id→位置列表，逐条查询不全量扫描；删除使用稳定槽位，输出时略过 Java null 删除槽位并保留 JSON null。单成员无 id 条目的文件路径推导随删除增量维护。

## 5. 版本戳与磁盘修订

- 版本文件为覆盖层根目录的 `.edit_version`（非 JSON，规则读取器不扫描）；文件缺失或非法按零建立基线，非法值记录警告。保持整数协议格式，递增溢出显式抛出异常。
- `RuleOverlayStore` 统一分配修订；成功数据包重载、文件提交、检测到覆盖层外部改动均推进。重载不在服务端线程扫描目录，下次后台读取建立覆盖层基线。保存提交后在原文件调用中登记修订并尝试持久化，不由保存任务预测下一版本。
- 所有内部持久化均使用同一后台线程，写入最新已分配修订；同目录临时文件准备后原子替换，不支持原子移动时退回普通替换。旧调用不能把旧修订写回。失败记录日志并保留待持久化值，服务层 tick 至少间隔 5 秒重试；规则文件仍是权威来源，持久化失败不回滚已提交规则。
- `readDiskRevision` 在后台按路径排序计算 SHA-256，包含坏 JSON 原始字节；逐块读取并检查取消。提交前再次核对目录指纹与各目标原始字节，快照读取前后也核对修订。无法核对时拒绝尚未写盘的保存；已提交但后续读取失败回 `SAVED_NOT_RELOADED`。
- 文件 Future 仅表示实际调用已经返回，不对其执行取消或超时完成。任一作业排队或执行超过 60 秒无进展时记录一次阶段告警，保存／快照发出协作取消；实际调用退出前保持隔离，拒绝新的保存与重载。底层 I/O 不保证响应取消，不在超时后提前开放目标。
- 调度器取消或 FAILED 摘除句柄后，`RuleEditService` 仍轮询保存／快照的实际完成，幂等接纳结果并交付终态；后台不操作会话、世界或网络。回滚失败将目标置为恢复隔离，后续保存、重载与命令读取被拒绝，需检查 `.bak` 并在修复文件后重启。
- 保存与快照交接后台前固定模型集合、JSON 映射、指纹与修订号；交接后的可变元素不再由服务端线程修改。现有备份与逐文件替换不提供进程崩溃后的整批事务恢复。

## 6. 分片传输

`RuleEditChunkAccumulator.validate` 逐片检查 transferId、下标和单片字节数，并将不可变载荷和字节数封装为只能由校验入口创建的 `ValidatedChunk`。服务层先校验、查询幂等回执，再调用 `acceptValidated` 重组；直接调用 `accept` 也经过同一校验入口。接收阶段复用字节数，重复下标以首片为准且仅首片计入累计值；收齐时按累计字节复核总量并返回有序分片，保存后台再拼接。每玩家至多 2 个在途传输，未收齐不回执，超量回 `INVALID_REQUEST`，空闲 1200 tick 清理。下行快照不超过 32 000 B 时直发，大快照每片 30 000 B、每 tick 最多 4 片；总量超过 4 MiB 或总片数超过上限时回结构化失败回执。

## 7. 新契约字段如何进入快照与校验

规则契约（`display_name`、`triggers`、`source_cost`、`catalyst_cost`、`combination`、`outcomes`、`schema_version` 与条件树 `conditions`）通过两条通路进入编辑通道，无需在协议层逐个建字段：

- **快照侧**：覆盖层规则直接下发**文件原始 JSON**，所有字段（含尚未识别的字段）原样保留；数据包/内置规则由 `RuleCodecs` 的模型编码下发，天然覆盖全部契约字段。模型解码成功时 `effective` 用编码结果（权威形态），失败退回原文。
- **校验侧**：`RuleSubmissionValidator.validateEdit` 是跨 tick 保存逐条复用的完整校验闸门（批量入口 `validate` 也委托它）——对每条 upsert 用 `RuleCodecs.decoder(...)` 做**完整契约解码**（含条件树、新字段、候选结果与固定成本），再跑 `RuleValidation.validate`（语义）与 `RuleReferenceValidator.validate`（动态引用）。任一错误即 `issues.errors` 非空。
- **拒绝有损保存的落点**：旧 GUI 无法表达新结构（如仍写旧版二维数组条件、整数写法 `catalyst_cost`）时，解码**明确报错**（不静默兼容），整批在校验阶段被拒绝、**不落盘**，回 `VALIDATION_FAILED` 并附 `RuleIssue` 定位到具体字段。控制条目（`disabled`/`delete`）走独立校验分支：只允许 id + 一个值为 true 的控制字段。

## 8. 扩展点

- **改限额**：改 `RuleEditLimits`（注意逐片、总量、快照、并发、超时多处）。
- **改写盘策略**：改 `RuleOverlayWriter`（保持"整批预备 + 原子替换 + 失败回滚 + 替换前内容闸门"；元数据核对是 `verifyMetadataUnchanged`，逐字节比对是 `verifyContentUnchanged`）。
- **改快照形状**：改 `RuleSnapshotAssembler`（保持覆盖层用原始 JSON、`editable = base != null || overlay != null`）。
- **接入新目录类型**：在 `core/catalog` 实现 `RuleCatalogSource` 并登记到 `RuleCatalogSources.builtin`；枚举值加进 `RuleCatalogType`。
- **新增 payload**：新增协议类 + 在两端注册器登记；C2S 与 S2C **类型**都必须在公共初始化注册，否则连接协商判定通道缺失。

## 9. 相关

- 端到端链路：[../flows/edit-save-protocol.md](../flows/edit-save-protocol.md)
- 规则模型与字段：[rule-model.md](rule-model.md)
- 平台注册：[platform.md](platform.md)、[../systems/platform-abstraction.md](../systems/platform-abstraction.md)
- 校验：[../systems/issue-validation.md](../systems/issue-validation.md)
- 决策：[ADR-0019](../../../adr/0019-exclusive-edit-session-and-target-lock.md)、[ADR-0020](../../../adr/0020-source-catalog-and-overlay-control-entries.md)
