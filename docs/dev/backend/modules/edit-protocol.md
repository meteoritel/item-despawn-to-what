# 功能模块：编辑协议与持久化（`core/network` + `core/service` 写入/会话）

> 事实来源：`core/network/protocol/**`、`core/network/transport/**`、`core/service/{RuleOverlayWriter, RuleSubmissionValidator, RuleSnapshotAssembler, EditSessionManager}.java`。
> 当前前端是占位页，**协议保留供下一轮前端使用**。决策：[ADR-0016](../../../adr/0016-edit-protocol-changeset-version-stamp.md)。

## 1. 类清单

### 1.1 `core/network/protocol`（数据模型）

| 类 | 职责 |
|---|---|
| `RuleSnapshot` | `version + List<JsonObject> rules + List<String> issues`；`serialize()`/`parse()`。客户端只按 JSON 渲染，不依赖服务端模型类 |
| `RuleEdit` | `{id, Action(UPSERT\|DELETE), rule}`；upsert 必带 rule；id 归一为小写下划线 |
| `RuleEditChangeSet` | `{expectedVersion, List<RuleEdit> edits}`；乐观并发基准 |

### 1.2 `core/network/transport`（传输）

| 类 | 方向 | 字段 / 用途 |
|---|---|---|
| `RequestRuleSnapshotPayload` | C2S | 无字段；请求快照 |
| `SaveRuleChangeSetPayload` | C2S | `changeSetJson`（直发，≤ MAX_DIRECT_CHARS） |
| `SaveRuleChangeSetChunkPayload` | C2S | `transferId`(≤64) / `chunkIndex` / `chunkCount` / `chunkData`(≤ MAX_CHUNK_CHARS) |
| `RuleSnapshotPayload` | S2C | `snapshotJson`（≤ MAX_SNAPSHOT_CHARS） |
| `RuleSaveResultPayload` | S2C | `text`：成功回执 / 冲突 / 无权限等人类可读文本 |
| `OpenRuleEditorPayload` | S2C | 无字段（打开编辑界面信号）；`installOpenEditorSink(Runnable)` 只在客户端注册回调 |
| `RuleEditLimits` | — | 全部限额常量与 UTF-8 字节计算 |
| `RuleEditChunkAccumulator` | — | 分片接收：逐项校验、重复下标以首片为准、收齐后 `join()` |
| `RuleEditPayloadRouter` | — | 入站文本分发点（静态 `snapshotSink`/`resultSink`） |

`RuleEditLimits`：命名空间 `idtw`；直发 32 000 B；单片 30 000 B；变更集 4 MiB；`MAX_CHUNK_COUNT = ceil(4MiB / 30000)`；transferId ≤64；每玩家并发 2；空闲超时 60s；快照 ≤1 000 000 B。**长度始终按 UTF-8 字节计**（`encodedLength`），防多字节绕过。

### 1.3 `core/network/transport`（服务端）

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleEditServerContext` | 窄接口：向网络层暴露运行时能力（由平台 `RuleRuntimeHost` 实现） | `overlayRoot()`、`overlayNamespace()`、`typeRegistries()`、`loadMerged(server)`、`rebuildAndRescan(server)`、`sendTo(player, payload)` |
| `RuleEditServerHandler` | 服务端处理流程编排 | `handleSnapshotRequest`、`handleChangeSet`、`handleChangeSetChunk`、`expireIdle`、`clearPlayer`、`reset`、`sessionManager(ctx)` |

### 1.4 `core/service`（写入/会话）

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleOverlayWriter` | 覆盖层逐 id 写入：预备 → 备份 → 原子提交 → 失败回滚 | `apply(changes, issues) → ApplyResult{writtenFiles, conflicts}` |
| `RuleSubmissionValidator` | 写入前唯一校验闸门（解码 + 语义 + 动态引用） | `validate(changeSet, server, types, issues)` |
| `RuleSnapshotAssembler` | 当前生效规则全集 → 下发客户端快照 | `assemble(merged, overlayRoot, ns, version, effectTypes, conditionTypes, issues)` |
| `EditSessionManager` | per-player 会话 + `.edit_version` 版本戳 + 磁盘修订指纹 | `version` / `open` / `isActive` / `close` / `versionMatches` / `bumpVersion` / `synchronizeDiskRevision` / `activeSessionCount` |

## 2. 快照形状

条目形状固定为 `{ rule, origin, editable }`：

- **覆盖层规则**用**文件原始 JSON**（不做模型往返，**不丢未识别字段**）且 `editable=true`，下发前强制注入最终 id；
- **内置/世界数据包规则**由模型编码、`editable=false`。

> 控制条目判定必须**判取值而非存在性**：`"disabled": false` 是普通规则。`RuleSnapshotAssembler` 的 `isTrueFlag` 与加载层 `readFlag` 都遵守该约定；用 `has()` 会把普通规则误当控制条目。

## 3. 服务端处理流程

固定顺序（`RuleEditServerHandler`）：

```text
请求快照：权限 → 运行时就绪 → 磁盘修订核对(synchronizeDiskRevision) → 打开/续期 per-player 会话 → 下发原始覆盖层快照
提交变更集：解析 → 会话有效(空闲超时则回 session_expired 且不落盘) → 空集直接返回
  → 磁盘修订核对 → 版本戳校验(不一致则整批拒绝、只回冲突文本+最新快照、不落盘)
  → RuleSubmissionValidator 完整校验 → RuleOverlayWriter.apply(基于磁盘内容)
  → rebuildAndRescan(重建索引+全维度回扫) → 推进版本 → 回执 + 新快照
```

- **权限** `canEdit` 与 `/idtw` 一致：单人世界或权限等级 ≥2。
- **会话按玩家 UUID 隔离**（`Map<UUID, Long>`），空闲 5 分钟超时；并发保护由**版本戳**承担，而非旧链路的全局单 UUID 锁。
- **写盘基于磁盘原始内容**：按 id 逐条 upsert/delete，保留未编辑条目、非对象数组成员与未知字段；写文件用**原子写 + `.bak` 备份**，逐文件替换；任一环节失败**逆序回滚**已提交文件。删除单条文件的唯一条目时写成**空数组**而非删文件。
- **写盘失败判定**：`ApplyResult.writtenFiles()==0` 或 `conflicts` 非空都不算保存成功；I/O 失败尝试恢复已写文件，回滚失败明确报错并保留 `.bak`。
- **路径安全**：拒绝 `.`/`..` 路径段与越界、拒绝跟随符号链接；同 id 多处定义直接拒绝，要求人工消歧。

## 4. 版本戳与磁盘修订

- 版本文件 `overlayRoot/.edit_version`（无 `.json` 后缀，规则读取器不会扫描它）；缺失/非法按 0，`bumpVersion` 自增并落盘（落盘失败仅保持内存计数）。
- **成功 reload / save / convert 均推进版本戳**。
- `synchronizeDiskRevision` 对 `rules/**/*.json` 做 SHA-256 指纹（含坏 JSON 原始字节）比对，检测到手工改动即自增；**无法核对磁盘时拒绝保存**（不能把未知修订当作仍然匹配）。

## 5. 分片传输

`RuleEditChunkAccumulator.accept`：逐片校验（transferId 长度、下标范围、单片大小），**重复下标以首片为准**，收齐后 `join()` 并再校验总大小（≤4 MiB）；过期会话在接收路径顺带清理。每玩家最多 2 个在途传输。未收齐不回执。`handleChangeSetChunk` 收齐后复用 `handleChangeSet`。

## 6. 扩展点

- **改限额**：改 `RuleEditLimits`（注意逐片、总量、快照、并发、超时多处）。
- **改写盘策略**：改 `RuleOverlayWriter`（保持"整批预备 + 原子替换 + 失败回滚 + verifyUnchanged"）。
- **改快照形状**：改 `RuleSnapshotAssembler`（保持覆盖层用原始 JSON）。
- **新增 payload**：新增协议类 + 在两端注册器登记（C2S 与 S2C **类型**都必须在公共初始化注册，否则连接协商判定通道缺失）。

## 7. 相关

- 端到端链路：[../flows/edit-save-protocol.md](../flows/edit-save-protocol.md)
- 平台注册：[platform.md](platform.md)、[../systems/platform-abstraction.md](../systems/platform-abstraction.md)
- 校验：[../systems/issue-validation.md](../systems/issue-validation.md)
- 决策：[ADR-0016](../../../adr/0016-edit-protocol-changeset-version-stamp.md)
