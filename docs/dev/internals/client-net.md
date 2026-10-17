# 接口层：协议工作区（client/net）

> 层级：接口层；模块路径：`client/net`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/net`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 协议工作区 | `client/net` | §2 |

## 2. 模块：协议工作区

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.net`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/net`
- 覆盖：6 个 `.java` 文件（包内无 `package-info.java`）

### 2.2 职责

- 维护客户端编辑会话状态机：`FREE`、`OPENING`、`ACTIVE`、`APPLYING`。
- 组装并发送上行载荷：确认、心跳、关闭、取消保存、快照请求、目录请求、寿命请求、来源视图请求、变更集及其分片。
- 接收并缓存下行快照、目录页、保存回执、保存进度、寿命应答与来源视图。
- 把同一 `transferId` 的下行分片重组为完整 JSON 后按整包处理（快照与来源视图共用）。
- 来源视图缓存最近 8 条（访问顺序淘汰），快照修订号变化时整体失效、会话结束清空；请求失败或超时只降级来源对照。
- 提供 `client/ui` 侧的唯一入口 `RuleEditClientWorkspace`，并以 `EditorScreenHooks` 作为界面实现的反向注册槽。
- 在会话失效、租约失效或断线时结束会话并通知界面关闭。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `RuleEditClientWorkspace` | class（final，单例） | 见下方成员表 | 工作区唯一入口：状态读取、请求、保存、会话收尾 |
| `RuleEditClientWorkspace.PacketSender` | interface（嵌套） | `send(CustomPacketPayload payload)` | 上行发送器注入点，由平台客户端初始化注入 |
| `RuleEditClientState` | enum | `FREE`、`OPENING`、`ACTIVE`、`APPLYING` | 会话状态机；只有 `ACTIVE` 允许提交 |
| `EditorOpenRequest` | record | `sessionId`、`targetId`、`contextRevision`、`protocolVersion`、`statusCode`、`messageArgs`、`fallbackMessage` | 打开请求的只读快照，`client/ui` 侧不得改写 |
| `EditorScreenHooks` | class（final，工具类） | `setOpener(EditorScreenOpener)`、`opener()`、`open(EditorOpenRequest)`、`close()` | 界面实现注册槽；未注册只记日志 |
| `EditorScreenOpener` | interface | `open(EditorOpenRequest request)`、`close()` | 由 `client/ui` 侧实现的回调 |
| `RuleChunkBuffer` | class（final，包内可见） | 包内可见的 `accept(RuleChunkPayload, long)`、`clear()` | 下行分片重组缓冲，快照与来源视图共用，不导出到包外 |

`RuleEditClientWorkspace` 公开成员：

| 成员 | 作用 |
|---|---|
| `instance()` | 取静态单例 |
| `installSender(PacketSender)` | 注入或清除上行发送器 |
| `installSinks()` | 安装 9 个 S2C 消费者，幂等 |
| `state()`、`active()` | 读取状态；`active()` 对 `ACTIVE` 与 `APPLYING` 返回真 |
| `sessionId()`、`contextRevision()` | 读取会话标识与上下文中枢修订号 |
| `openRequest()`、`openFailure()` | 读取最近一次成功被接受的打开请求与最近一次被拒绝的打开请求 |
| `snapshot()`、`lastResult()` | 读取最近一次快照与最近一次保存回执 |
| `catalog(RuleCatalogType)` | 读取最近一次收到的指定类型目录页 |
| `directory(RuleCatalogType)`、`refreshDirectory(RuleCatalogType)`、`retryDirectory(RuleCatalogType)`、`directoryFailed(RuleCatalogType)`、`invalidateDirectories()` | 按类型懒加载并合并分页的完整目录 |
| `referenceLifespanTicks()` | 读取当前连接的统一参考寿命，首次请求后缓存 |
| `newOperationId()` | 生成保存操作标识 |
| `requestSnapshot()`、`requestCatalog(RuleCatalogType, String, int, int)` | 上行请求快照与目录 |
| `requestSourceView(String ruleId)`、`sourceView(String)`、`sourceViewLoading(String)`、`sourceViewFailed(String)`、`retrySourceView(String)` | 按需请求某条规则的来源视图并读取三态；命中缓存不重复请求 |
| `save(String operationId, String changeSetJson)` | 提交变更集，超过直发上限时自动分片 |
| `saveProgress()`、`cancelRequested()`、`cancelSave()` | 读取保存进度、读取取消标记、请求取消保存 |
| `close(String reasonCode)`、`reset()`、`tick()` | 关闭会话、清空工作区、驱动心跳 |

### 2.4 依赖关系

- 依赖本模组：`core/network/protocol`（1 个文件 import：`RuleCatalog`、`RuleCatalogType`、`RuleEditProtocol`、`RuleSaveProgress`、`RuleSaveStatus`、`RuleSnapshot`、`RuleSourceView`）
- 依赖本模组：`core/network/transport`（2 个文件 import：`RuleEditClientWorkspace` 的全部载荷与路由类型、`RuleChunkBuffer` 的 `RuleChunkPayload` 与 `RuleEditLimits`）
- 依赖本模组：`core/service`（1 个文件 import，共 1 处：`RuleEditSessionLimits`）
- 依赖外部：`net.minecraft.client`（`Minecraft`）、`net.minecraft.client.multiplayer`（`ClientPacketListener`）、`net.minecraft.network.protocol.common.custom`（`CustomPacketPayload`）
- 依赖外部：`org.apache.logging.log4j`（`LogManager`、`Logger`，仅 `RuleEditClientWorkspace`）、`org.slf4j`（`Logger`、`LoggerFactory`，仅 `EditorScreenHooks`）、`org.jetbrains.annotations`（`Nullable`）
- 依赖外部：Java 标准库 `java.util`（`EnumMap`、`List`、`Map`、`UUID`）
- 被依赖：`client/edit`（2 个文件 import，共 5 处）、`client/event`（2 个文件 import，共 2 处）、`client/register`（1 个文件 import，共 1 处）、`client/ui/screen`（3 个文件 import，共 5 处）、`client/ui/screen/form`（1 个文件 import，共 1 处）、`dev/guiscreenshot`（1 个文件 import，共 2 处）
- 无本模组客户端包依赖：本包不 import 任何 `client/edit` 或 `client/ui` 类

### 2.5 关键实现位置

- `RuleEditClientWorkspace#instance`：静态单例 `INSTANCE`；`installSender`、`installSinks` 是转发到该实例的静态方法。
- `RuleEditClientWorkspace#installSinks`：以 `sinksInstalled` 标志保证幂等，安装 8 个 `RuleEditPayloadRouter` 消费者（快照、快照分片、回执、进度、目录、寿命、来源视图、来源视图分片）与 `OpenRuleEditorPayload#installOpenEditorPayloadSink`，共 9 个。
- `RuleEditClientWorkspace#onOpen`：方法入口只对 `null` 载荷直接返回；随后构造 `EditorOpenRequest`，先处理空会话标识（取锁失败的 `LOCK_BUSY`/`UNAVAILABLE` 等，服务端不带会话号）写入 `openFailure` 并返回，再校验 `protocolVersion` 等于 `RuleEditProtocol#VERSION`（6）、`RuleSaveStatus#fromId(statusCode)` 等于 `SUCCESS`，不通过时同样写入 `openFailure` 并返回。
- `RuleEditClientWorkspace#onOpen` 的成功路径：写入 `sessionId` 与 `contextRevision`，清空快照、目录缓存与回执，置 `OPENING`，发送 `ConfirmRuleEditorPayload(sessionId, RuleEditProtocol.VERSION)`，置 `ACTIVE`，最后调用 `EditorScreenHooks#open`。
- `RuleEditClientWorkspace#save`：仅在 `ACTIVE`、会话标识非空、已连接、且两个参数都非空时接受；用 `RuleChangeSetChunker#split` 生成载荷列表，置 `APPLYING` 并把 `saveProgress` 初始化为 `Stage.PREPARING`。
- `RuleEditClientWorkspace#tick`：状态为 `FREE` 或会话标识为空时直接返回；未连接时 `reset()`；否则按 `RuleEditSessionLimits#HEARTBEAT_INTERVAL_TICKS`（10 秒 = 200 tick）发送 `HeartbeatRuleEditorPayload`。
- `RuleEditClientWorkspace#referenceLifespanTicks`：以 `ClientPacketListener` 实例身份隔离缓存，连接变化即清零；仅在 `ACTIVE` 时请求，首次发 `RequestRuleLifespanPayload`，应答匹配 `sessionId` 与 `requestId` 后写入非负值。
- `RuleEditClientWorkspace#directory`：`sessionId` 为空时返回 `null`；超时判定用 `System.currentTimeMillis() > load.deadline`，超时置 `failed`；未完成且状态为 `ACTIVE` 时继续请求下一页。
- `RuleEditClientWorkspace#requestDirectory`：每页请求 200 条，超时窗口 15000 毫秒。
- `RuleEditClientWorkspace#acceptDirectory`：修订号变化时清空已合并条目并从第 0 页重取；`validating` 分支在修订号未变时复用整份缓存。
- `RuleEditClientWorkspace#onCatalog`：按类型的最近一次 `requestId` 丢弃过时响应；`requestId` 为空时只按 `sessionId` 接受。
- `RuleEditClientWorkspace#onResult`：`SESSION_EXPIRED`、`LOCK_NOT_OWNED`、`LOCK_BUSY` 触发 `endSession()`；其余状态码在回执属于当前会话时把 `APPLYING` 置回 `ACTIVE`、写入 `contextRevision`，并对 `SUCCESS` 与 `NO_CHANGES` 补拉快照。
- `RuleEditClientWorkspace#onProgress`：仅在 `APPLYING`、会话与操作标识匹配、`sequence` 递增时接受进度；进入不可取消阶段时清除 `cancelRequested`。
- `RuleEditClientWorkspace#close`：`APPLYING` 时直接返回；否则在已连接时发送 `CloseRuleEditorPayload` 并 `endSession()`。
- `RuleEditClientWorkspace#endSession`：以 `endingSession` 做重入保护，清空会话态、分片缓冲与来源视图缓存，仅在之前确实持有会话时调用 `EditorScreenHooks#close`。
- `RuleChunkBuffer#accept`：校验 `transferId` 非空、`1 <= count <= RuleEditLimits#MAX_CHUNK_COUNT`、`0 <= index < count`；`transferId`、`sessionId`、`count` 任一变化或空闲超时即开新批次。
- `RuleChunkBuffer#isStale`：`tick - lastTick > RuleEditLimits#TRANSFER_TIMEOUT_TICKS` 判超时；该常量为 1200 tick（`TRANSFER_TIMEOUT_MILLIS` 60000 除以每 tick 50 毫秒）。
- `RuleChunkBuffer#accept` 的收齐路径：按下标顺序拼接，缺失下标按空串处理，返回完整 JSON 后 `clear()`。
- `RuleEditClientWorkspace#requestSourceView`：仅在 `ACTIVE`、会话非空且已连接时请求；已就绪或在途直接返回，失败后不自动重试，由界面调 `retrySourceView`。
- `RuleEditClientWorkspace#onSourceView`：按 `requestId` 配到在途缓存项；状态码非 `SUCCESS` 或 JSON 解析失败都标记失败；`onSourceViewChunk` 收齐分片后复用同一入口。
- `RuleEditClientWorkspace#onSnapshot`：快照修订号变化时清空来源视图缓存与分片缓冲（内容可能已被保存或数据包重载改写）；`sourceView`/`sourceViewLoading`/`sourceViewFailed` 在读取时按 5 秒期限判定超时。
- `EditorScreenHooks#setOpener`、`#opener`：`volatile` 静态字段的读写；`#open`、`#close` 在字段为 `null` 时记录日志并返回。

### 2.6 代码现状（可核对）

- 目录概念在同一个类里有两种命名：`catalog(RuleCatalogType)` 与字段 `catalogs` 表示最近一页目录响应缓存；`directory(RuleCatalogType)` 与字段 `directories`、内部类 `DirectoryLoad` 表示按类型懒加载并合并分页的完整目录；请求方法名为 `requestCatalog`，重试方法名为 `retryDirectory`。
- `EditorOpenRequest` 的组件顺序为 `contextRevision` 在前、`protocolVersion` 在后；`OpenRuleEditorPayload` 的顺序为 `protocolVersion` 在前、`contextRevision` 在后，字段名一一对应但声明顺序不同。
- 两个 record 的空值归一化不同：`EditorOpenRequest` 把空 `statusCode` 归一化为空串，`OpenRuleEditorPayload` 把空 `statusCode` 归一化为 `RuleSaveStatus#UNAVAILABLE.id()`。
- 同一包内混用两套日志门面：`RuleEditClientWorkspace` 用 `org.apache.logging.log4j.LogManager`，`EditorScreenHooks` 用 `org.slf4j.LoggerFactory`，两者使用的日志名同为字符串 `"itemdespawntowhat-client-net"`。
- `RuleChunkBuffer` 是包内可见类型（`final class`，无 `public`），`accept` 与 `clear` 也是包内可见；本包对外导出的类型只有另外 5 个与嵌套接口 `PacketSender`。
- `RuleEditClientWorkspace#DirectoryLoad` 的字段类型写成全限定名 `com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry` 与 `java.util.LinkedHashMap`，这两个类型没有出现在 import 列表中。
- `client/net` 无 `package-info.java`。
- `RuleEditClientWorkspace#save` 的异常分支：捕获 `RuntimeException` 后，连接仍在时调用 `cancelSave()` 并返回 `true`，连接已断开时 `reset()` 并返回 `false`。
- `RuleEditClientWorkspace` 的来源视图相关方法与 `referenceLifespanTicks`、`saveProgress`、`cancelRequested`、`cancelSave` 带 JavaDoc，其余 public 方法为行注释；`EditorScreenOpener` 的两个方法只有行注释，无 JavaDoc。
- `EditorScreenHooks#open` 的参数未标注 `@Nullable`，方法体内却对 `null` 做了判空并走日志分支。
- `RuleEditClientWorkspace#referenceLifespanTicks` 是唯一使用 `ClientPacketListener` 作为缓存隔离键的成员；`#reset` 会清空该缓存与打开请求，但保留已注入的发送器与 S2C 消费者。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `RuleEditClientWorkspace` | 客户端编辑会话工作区：状态机、心跳、上行请求、下行缓存、分片组装、会话收尾 | `instance()`、`installSender`、`installSinks`、`onOpen`、`save`、`tick`、`endSession` |
| `RuleEditClientWorkspace.PacketSender` | 上行载荷发送器接口 | `send(CustomPacketPayload)` |
| `RuleEditClientState` | 会话状态机枚举 | `FREE`、`OPENING`、`ACTIVE`、`APPLYING` |
| `EditorOpenRequest` | 打开规则编辑器屏幕的请求记录 | 7 个组件；紧凑构造器归一化空值为空串或空列表 |
| `EditorScreenHooks` | 界面实现注册槽，未注册时只记日志 | `setOpener`、`opener`、`open`、`close` |
| `EditorScreenOpener` | 由 `client/ui` 侧实现的回调接口 | `open`、`close` |
| `RuleChunkBuffer` | 下行分片按 `transferId` 重组，超时或会话切换时重置 | `accept`、`clear`、`start`、`isStale` |

## 4. 相关文档

- [客户端前端实现文档索引](README.md)
- [入口层：平台客户端接入](client-entry.md)
- [抽象层：客户端](../layers/client.md)
- [后端：编辑协议](../backend/modules/edit-protocol.md)
- [ADR-0019：独占编辑会话与目标锁](../../adr/0019-exclusive-edit-session-and-target-lock.md)
- [文档约定](../conventions.md)
- [开发者文档索引](../README.md)
