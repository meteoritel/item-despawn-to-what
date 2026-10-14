# 功能模块：编辑协议与持久化（`core/network` + `core/service` 写入/会话）

> 事实来源：`core/network/protocol/**`、`core/network/transport/**`、`core/network/catalog/**`、`core/service/{RuleEditService, EditSessionManager, RuleEditSessionLimits, RuleOverlayWriter, RuleSubmissionValidator, RuleSnapshotAssembler}.java`。
> 客户端编辑器已落地，本协议为现行客户端编辑通道。规则契约（新增字段与条件树）见 [rule-model.md](rule-model.md)；决策：[ADR-0019](../../../adr/0019-exclusive-edit-session-and-target-lock.md)、[ADR-0020](../../../adr/0020-source-catalog-and-overlay-control-entries.md)。

## 1. 类清单

### 1.1 `core/network/protocol`（协议数据模型）

| 类 | 职责 |
|---|---|
| `RuleEditProtocol` | 协议版本常量 `VERSION = 3`；握手不一致即拒绝 |
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
| `RequestRuleLifespanPayload` | C2S | `sessionId` / `requestId` |
| `RuleLifespanPayload` | S2C | `sessionId` / `requestId` / `lifespanTicks`（普通物品参考寿命；零表示尚未采样） |
| `SaveRuleChangeSetPayload` | C2S | `sessionId` / `operationId` / `changeSetJson`（直发，≤ `MAX_DIRECT_CHARS`） |
| `SaveRuleChangeSetChunkPayload` | C2S | `sessionId` / `operationId` / `transferId`(≤64) / `index` / `count` / `chunk`(≤ `MAX_CHUNK_CHARS`) |
| `OpenRuleEditorPayload` | S2C | `sessionId` / `targetId` / `protocolVersion` / `contextRevision` / `statusCode` / `messageArgs` / `fallbackMessage`；取锁失败时同样下发（`statusCode=LOCK_BUSY`、`sessionId` 为空）。唯一开屏路径：客户端 `RuleEditClientWorkspace` 用 `installOpenEditorPayloadSink(Consumer)` 注册，成功后 `EditorScreenHooks.open(request)` |
| `RuleSnapshotPayload` | S2C | `sessionId` / `requestId` / `snapshotJson`（≤ `MAX_SNAPSHOT_CHARS`） |
| `RuleSnapshotChunkPayload` | S2C | `sessionId` / `requestId` / `transferId` / `index` / `count` / `chunk` |
| `RuleSaveResultPayload` | S2C | `sessionId` / `operationId` / `statusCode` / `messageCode` / `messageArgs` / `resultVersion` / `writtenToDisk` / `reloaded` / `issues` |
| `RuleCatalogPayload` | S2C | `sessionId` / `requestId` / `catalogType` / `revision` / `json` / `lastPage` |

### 1.3 `core/network/transport`（工具与服务端）

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleEditLimits` | 全部限额常量与 UTF-8 字节计算 | `encodedLength(String)`、`MAX_*` 常量组（见 §4.3 常量清单） |
| `RuleChangeSetChunker` / `RuleSnapshotChunker` | 上行变更集 / 下行快照切分：不超直发上限出整包，否则按 **UTF-8 字节**切分（不拆代理对） | `split(sessionId, operationId, json)` / `split(sessionId, requestId, json)` |
| `RuleEditTextChunks` | 共享的按 UTF-8 字节切分实现（上下行同一口径） | `splitByUtf8Bytes`、`utf8Length`（包内） |
| `RuleEditChunkAccumulator` | 上行分片重组：逐片校验、**重复下标以首片为准**、收齐后返回有序分片并复核累计字节，后台按序拼接；过期（1200 tick）清理 | `accept`、`isShapeValid`、`expireIdle`、`clear`、`clearAll`、`AcceptResult` |
| `RuleEditPayloadCodec` | 载荷共用编解码片段（字符串列表 / 可空字符串 / `RuleIssue` 列表）；超限即抛解码异常 | `writeStrings`/`readStrings`、`writeNullableString`/`readNullableString`、`writeIssues`/`readIssues`（包内） |
| `RuleEditPayloadRouter` | 客户端入站门面：字符串 sink（兼容）+ 结构化 sink（快照 / 分片 / 回执 / 目录 / 寿命）；服务端只装载空消费者 | `installSnapshotSink`、`installSnapshotPayloadSink`、`installResultSink`、`installResultPayloadSink`、`installCatalogSink`、`installChunkSink`、`installLifespanSink`、`dispatchSnapshot`、`dispatchResult`、`dispatchChunk`、`dispatchCatalog`、`dispatchLifespan` |
| `OpenRuleEditorPayload` | 承载 `installOpenEditorPayloadSink` / `dispatchOpenEditor`（开屏入口的唯一静态 sink） | `TYPE`、`STREAM_CODEC` |
| `RuleEditServerContext` | 窄接口：向网络层暴露运行时能力（由平台 `RuleRuntimeHost` 实现） | `overlayRoot()`、`overlayNamespace()`、`typeRegistries()`、`loadMerged(server)`、`rebuildAndRescan(server)`、`sendTo(player, payload)`、`referenceLifespanTicks()`、`saveReloadContext()`、`saveScheduler()`、`applySavedIndex(server, index)` |
| `RuleEditServerHandler` | 服务端处理编排（**薄转发**） | `openEditor`、`handleConfirm`、`handleHeartbeat`、`handleClose`、`handleSnapshotRequest`、`handleCatalogRequest`、`handleLifespanRequest`、`handleChangeSet`、`handleChangeSetChunk`、`expireIdle`、`clearPlayer`、`reset`、`isReady`、`canEdit`、`sessionManager`/`service` |

### 1.4 `core/network/catalog`

| 类 | 职责 |
|---|---|
| `RuleCatalogService` | 消费 `core/catalog` 的候选数据源：按 `类型 + 来源修订号` 缓存、大小写不敏感过滤（id/label/subLabel）、分页切分、`invalidate()` 失效；只读切分，不做权限与会话校验 |

### 1.5 `core/service`（写入/会话）

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleEditService` | 编辑会话的业务编排（打开/确认/心跳/关闭/快照/目录/寿命/保存），只产出"要下发的载荷"，不直接碰网络 | `openEditor`、`confirm`、`heartbeat`、`close`、`requestSnapshot`、`requestCatalog`、`requestLifespan`、`pollLifespan`、`submitChangeSet`、`submitChunk`、`expireSessions`、`forceRelease`；幂等表 `recentResults`（最近 16 条） |
| `EditSessionManager` | 全局单一目标的会话状态机（FREE/OPENING/ACTIVE/APPLYING）与 `.edit_version` 版本戳 | `acquire` / `confirm` / `heartbeat` / `owns` / `isOwner` / `confirmed` / `beginApply` / `finishApply` / `release` / `expire` / `version` / `bumpVersion` / `synchronizeDiskRevision` |
| `RuleEditSessionLimits` | 会话时间与权限常量 | `TARGET_ID = itemdespawntowhat:rules`、`OPEN_CONFIRM_WINDOW_SECONDS=15`、`HEARTBEAT_INTERVAL_SECONDS=10`、`LEASE_SECONDS=60`、`REQUIRED_PERMISSION_LEVEL=2`、`MAX_OPERATION_HISTORY=16` |
| `RuleOverlayWriter` | 覆盖层逐 id 写入：预备 → 备份 → 原子提交 → 失败回滚 | `apply(changes, issues) → ApplyResult{writtenFiles, conflicts}` |
| `RuleSubmissionValidator` | 写入前唯一校验闸门（控制条目 / 解码 + 语义 + 动态引用） | `validate(changeSet, server, types, issues)` |
| `RuleSnapshotAssembler` | 来源分层索引 + 当前生效规则 → 下发快照 | `assemble`、`encodeRule`（服务端线程）、`assembleEncoded`（纯 JSON 装配） |

| `RuleSaveTask` | 公共 MAINTENANCE 任务：后台解析/读写、逐条校验、分步发布与回扫 | `step`、`invalidate`；后台文件队列由本类持有 |
| `RuleSnapshotTask` | 快照读取、逐条解码/编码、后台 JSON 装配与分片 | `step`；每 tick 最多下发一片 |
| `RuleSaveReloadContext` | 启动/数据包重载时发布的原始基底、文件问题与解码上下文 | 对象身份用于识别重载；后台不访问 ResourceManager |

## 2. 快照形状

快照 = `{version, context_revision, entries[], issues[]}`，标题字段当前都取 `EditSessionManager.version()`（覆盖层修订号）。

条目 `RuleSnapshotEntry(id, origin, status, editable, effective, base, overlay, issues)`：

- **三视图**：`base` 为数据包层原始 JSON，`overlay` 为覆盖层原始 JSON，两者都保留原文（**不做模型往返**）；`effective` 优先取**模型编码结果**（解码成功时权威），失败时退回 `overlay` 或 `base` 原文，保证客户端仍能看到内容。缺失的视图以 JSON `null` 下发，客户端按"无此视图"处理。
- **`editable` 语义（内容存在标记）**：`editable = base != null || overlay != null`，即该条目在数据包层或覆盖层是否存在非控制条目内容（只被控制条目声明停用 / 删除时为 `false`，**纯数据包规则为 `true`**）；客户端据此决定打开表单编辑还是只展示详情——`status = invalid` 或带 error 级 issue 的条目即使 `editable = true` 也只读展示。
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
请求快照：权限/就绪 → 会话持有与确认校验 → heartbeat → 排队 RuleSnapshotTask
  → 后台核对修订与读取覆盖层 → 公共预算逐条解码、校验、编码
  → 后台装配三视图、序列化与切片 → 每 tick 下发一片
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

保存与快照步骤最多处理 `SAVE_ENTRIES_PER_STEP=8` 项，同时受公共 tick 预算和 `SAVE_STEP_BUDGET_NANOS=1_000_000`（1ms）步骤软预算约束；让出后最早下一 tick 续接。没有固定编辑条数拒绝门槛，4 MiB 总字节限制仍有效。文件 I/O、JSON 拼接/解析/装配与纯模型索引构建使用进程级单后台线程，待执行队列上限为 `RuleSaveTask.MAX_BACKGROUND_JOBS=8`；队列满时拒绝新后台工作，不转回主线程执行，尚未写盘的保存回 `UNAVAILABLE`。第三方 Codec、注册表相关校验、任务取消与世界访问均留在服务端线程。单次 Codec、规则查询或原版操作不可抢占，因此不承诺任意单步均小于预算。保存失败在索引发布前保留上一版索引；发布后的回扫失败回 `SAVED_NOT_RELOADED`，不回滚已发布索引。


### 4.1 授权与限流

- **权限** `canEdit` 与 `/idtw` 的 `hasAccess` 一致：单人世界或权限等级 ≥2；未就绪/无权限直接回 `UNAVAILABLE` / `NO_PERMISSION`。
- **每个授权检查点**（心跳、快照、目录、变更集、分片）都校验 `sessionId` 与状态匹配，失败回 `SESSION_EXPIRED` / `LOCK_NOT_OWNED`。
- **目录请求校验顺序**：先检查会话归属与已确认状态，再检查分页参数和目录类型；未确认的 OPENING 会话优先返回会话失效回执，已确认会话的非法参数返回 `INVALID_REQUEST`。仅通过全部检查的请求续期心跳。
- **寿命查询**：权限与就绪检查后，服务层校验已确认会话；只读取平台缓存的普通物品参考寿命，不解析源匹配、不展开标签、不创建实体。平台尚未采样时应答零并保留单项待交付请求，`expireIdle` 每 20 tick 检查采样状态，完成后向仍持有权限与已确认会话的玩家补发一次缓存结果。参考值不随规则重载失效；客户端仅接受当前会话与最近请求号的应答，正寿命结果保留到断线。
- **幂等**：同一在途 `operationId` 等待原任务；最近 16 条已处理回执直接回放，不重复写盘。
- **并发保存保护**：`beginApply` 只允许 ACTIVE→APPLYING；正在 APPLYING 时的新提交回 `INVALID_REQUEST`（`apply in progress`）并保留会话。
- 会话失效、关闭、强制释放或玩家离线/失去权限使尚未提交的保存失效；后台在提交检查点退出并回滚。会话锁仍按原租约规则释放。已发布索引必须完成旧任务清理与回扫；停服在 vanilla 最终存档前同步取消未清理的结算，后台收到失效信号，停服阶段等待文件队列退出/回滚，完成后才允许同进程下一存档启动。
- `/idtw config reload/validate/list` 与 `/idtw rule list/show` 在保存期间明确提示稍后重试，避免读取整批替换的中间状态。
- 数据包重载在保存期间只保留最近一次延后请求，并使尚未提交的保存失效；文件工作结束后才执行，避免读取替换中间状态。快照至多一个准备任务与一个最新待启动请求，保存期间暂缓准备。

### 4.2 回执状态码

`RuleSaveStatus`：`SUCCESS / NO_CHANGES / NO_PERMISSION / LOCK_NOT_OWNED / LOCK_BUSY / SESSION_EXPIRED / VERSION_CONFLICT / VALIDATION_FAILED / WRITE_FAILED / SAVED_NOT_RELOADED / INVALID_REQUEST / UNAVAILABLE`。客户端**只认状态码分支**，禁止解析文案；打包失败（快照/解析）也各自有兜底状态（如 `itemdespawntowhat.edit.snapshot_failed`）。

### 4.3 网络限额

命名空间 `idtw`。直发 32 000 B；单片 30 000 B；变更集/快照上限均 4 MiB；`MAX_CHUNK_COUNT = ceil(4MiB / 30000)`（=140）；transferId ≤64；每玩家并发 2 个在途传输；空闲超时 60 s（1200 tick）；`MAX_ID_CHARS=64`、`MAX_CODE_CHARS=128`、`MAX_FALLBACK_CHARS=512`、`MAX_MESSAGE_ARGS=8`、`MAX_MESSAGE_ARG_CHARS=256`、`MAX_ISSUES_PER_RESULT=64`；编解码器留 `PACKET_SLACK_BYTES=4096` 余量，超限请求先抵服务端再回 `INVALID_REQUEST`，不直接断连。目录单页上限 200（默认 50）。**长度始终按 UTF-8 字节计**（`encodedLength`），防多字节绕过。

### 4.4 权威落盘

- 写盘依据**磁盘文件内容**，按 id 逐条 upsert/delete，保留未编辑条目、非对象数组成员与未知字段；命中控制条目或解码失败的文件不会被覆盖。
- 写文件用**原子写 + `.bak` 备份**，逐文件替换；任一环节失败**逆序回滚**已提交文件。删除单条文件的唯一条目时写成**空数组**而非删文件。
- `ApplyResult.writtenFiles()==0` 或 `conflicts` 非空都不算保存成功；I/O 失败尝试恢复，回滚失败明确报错并保留 `.bak`。
- **路径安全**：拒绝 `.`/`..` 路径段与越界、拒绝跟随符号链接；编辑命中同 id 多处定义时拒绝，未编辑的歧义条目不阻断其它保存。
- **查询索引**：加载后一次构建 id→位置列表，逐条查询不全量扫描；删除使用稳定槽位，输出时略过 Java null 删除槽位并保留 JSON null。单成员无 id 条目的文件路径推导随删除增量维护。

## 5. 版本戳与磁盘修订

- 版本文件 `overlayRoot/.edit_version`（无 `.json` 后缀，规则读取器不会扫描它）；缺失/非法按 0，`bumpVersion` 自增并落盘（落盘失败仅保持内存计数）。
- **成功的数据包重载 / 保存 / 覆盖层外部改动均推进版本戳**。首次目录基线由后台读取，打开编辑器时只读取小型版本文件。
- `readDiskRevision` 在后台对 `rules/**/*.json` 做 SHA-256 指纹（含坏 JSON 原始字节）；服务端通过 `acceptDiskRevision` 接纳版本，后台 `persistRevision` 写入版本文件。检测到手工改动即推进版本；提交前再次核对整目录与各目标原始字节。无法核对时拒绝尚未写盘的保存；已写盘后重载核对失败回 `SAVED_NOT_RELOADED`。快照读取前后也核对修订。

## 6. 分片传输

`RuleEditChunkAccumulator.accept` 逐片检查 transferId、下标和单片字节数，重复下标以首片为准；收齐时按累计字节复核总量并返回有序分片，保存后台再拼接。每玩家至多 2 个在途传输，未收齐不回执，超量回 `INVALID_REQUEST`，空闲 1200 tick 清理。下行快照不超过 32 000 B 时直发，大快照每片 30 000 B、每 tick 一片；总量超过 4 MiB 或总片数超过上限时回结构化失败回执。

## 7. 新契约字段如何进入快照与校验

规则契约（`display_name`、`triggers`、`source_cost`、`catalyst_cost`、`combination`、`outcomes`、`schema_version` 与条件树 `conditions`）通过两条通路进入编辑通道，无需在协议层逐个建字段：

- **快照侧**：覆盖层规则直接下发**文件原始 JSON**，所有字段（含尚未识别的字段）原样保留；数据包/内置规则由 `RuleCodecs` 的模型编码下发，天然覆盖全部契约字段。模型解码成功时 `effective` 用编码结果（权威形态），失败退回原文。
- **校验侧**：`RuleSubmissionValidator.validateEdit` 是跨 tick 保存逐条复用的完整校验闸门（批量入口 `validate` 也委托它）——对每条 upsert 用 `RuleCodecs.decoder(...)` 做**完整契约解码**（含条件树、新字段、候选结果与固定成本），再跑 `RuleValidation.validate`（语义）与 `RuleReferenceValidator.validate`（动态引用）。任一错误即 `issues.errors` 非空。
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
- 决策：[ADR-0019](../../../adr/0019-exclusive-edit-session-and-target-lock.md)、[ADR-0020](../../../adr/0020-source-catalog-and-overlay-control-entries.md)
