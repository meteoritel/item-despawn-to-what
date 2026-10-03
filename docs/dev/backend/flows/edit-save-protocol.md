# 纵向系统：编辑保存协议

> 从"打开编辑器"到"规则落盘并重建索引"的端到端流程。客户端编辑器已落地，本流程为现行链路。
> 类职责见 [edit-protocol.md](../modules/edit-protocol.md)、[issue-validation.md](../systems/issue-validation.md)。决策：[ADR-0016](../../../adr/0016-edit-protocol-changeset-version-stamp.md)、[ADR-0019](../../../adr/0019-exclusive-edit-session-and-target-lock.md)。

## 1. 打开编辑器

```text
玩家执行 /idtw config edit（仅玩家可执行）
  → RuleEditServerHandler.openEditor：就绪 + 权限(单人/等级≥2) 检查
  → RuleEditService.openEditor → EditSessionManager.acquire 原子取锁（唯一入口）
  → 下发 OpenRuleEditorPayload：成功 statusCode=SUCCESS（带 sessionId）；
     失败 statusCode=LOCK_BUSY 且 sessionId 为空，messageArgs=当前持有者
  → 客户端 payloadSink（RuleEditClientWorkspace.onOpen）校验协议版本与 statusCode
  → 成功后发 ConfirmRuleEditorPayload、请求快照，并 EditorScreenHooks.open(request) 打开编辑界面
```

`RuleEditServerContext` 下发 `OpenRuleEditorPayload`；服务端无 sink 时静默丢弃（core 不反向依赖 client）。取锁失败也要下发载荷，客户端据此提示持有者。

## 2. 确认与请求快照

```text
ConfirmRuleEditorPayload (C2S)：version 校验 → owns + isOwner → EditSessionManager.confirm(OPENING→ACTIVE)
  → 立即回发最新快照
RequestRuleSnapshotPayload (C2S)
  → owns + isOwner 校验；未确认的 OPENING 会话不接受快照请求
  → heartbeat(sessionId, tick)            # 续租
  → synchronizeDiskRevision               # 手工改过覆盖层也给出权威修订号
  → RuleSnapshotAssembler.assemble(...)   # 覆盖层原始 JSON + 数据包层模型编码；editable = base/overlay 非空
  → RuleSnapshotChunker：≤4 MiB 出 RuleSnapshotPayload，超限按 UTF-8 字节切 RuleSnapshotChunkPayload
```

心跳 `HeartbeatRuleEditorPayload` 正常无回执，失效回 `SESSION_EXPIRED`（客户端据此回到未持有状态）。

## 3. 选择目录（分页）

```text
RequestRuleCatalogPayload (C2S) → owns + isOwner + confirmed 校验 → page/pageSize 与类型合法性校验
  → heartbeat → RuleCatalogService.page(server, type, filter, page, pageSize)
  → RuleCatalogPayload (S2C)：分页结果 + lastPage
```

目录候选来自 `core/catalog` 的九类数据源（物品/方块/实体/战利品表/群系/维度/标签/流体/状态效果）；`RuleCatalogService` 按"类型 + 来源修订号"缓存，保存/重载后 `invalidate()`。

## 4. 提交变更集

```text
SaveRuleChangeSetPayload / SaveRuleChangeSetChunkPayload* (C2S)
  → operationId 非空且 ≤64 B、变更集总量 ≤4 MiB
  → owns + isOwner 校验（失效 → SESSION_EXPIRED / LOCK_NOT_OWNED，不落盘）
  → 幂等查表：同一 operationId 直接回放上次回执，不重复写盘
  → 解析 RuleEditChangeSet（失败 → INVALID_REQUEST，记入幂等表）
  → beginApply（ACTIVE→APPLYING；正在 APPLYING 时回 INVALID_REQUEST 并保留会话）
  → 空集 → NO_CHANGES
  → expectedVersion 与 sessions.version() 不一致 → 整批拒绝（VERSION_CONFLICT）+ 最新快照，不落盘
  → RuleSubmissionValidator.validate(...)   # 控制条目 / 解码 + 语义 + 动态引用；无部分通过
  → RuleOverlayWriter.apply(...)            # 基于磁盘内容：预备→(.bak)→原子替换→失败回滚
  → rebuildAndRescan(server)               # 重建索引 + 全维度 rescan
  → 成功：SUCCESS（resultVersion=新版本）；**重载失败：SAVED_NOT_RELOADED（writtenToDisk=true, reloaded=false），保留上一版索引**
  → finishApply(ACTIVE) → 回执 + (冲突/未重载时追加)最新快照
```

*分片：`RuleEditChunkAccumulator` 按 `transferId` 重组，收齐后复用同一 `handleChangeSet`；未收齐不回执，超过总量上限回 `INVALID_REQUEST`。

## 5. 关键不变量

| 不变量 | 含义 |
|---|---|
| 服务端权威 | 全部写盘经服务端；客户端只提交意图 |
| 乐观并发 | `expectedVersion` 与 `sessions.version()` 不一致 → **整批拒绝且不落盘**，回最新快照 |
| 权威落盘 | 依据**磁盘文件内容**逐 id 增删改，保留未编辑条目/未知字段；控制条目、未涉及规则、坏 JSON 都不会被保存动作删除 |
| 整批语义 | 全部预备成功才逐个原子替换；任一失败逆序回滚；`writtenFiles==0` 或 `conflicts` 非空不算成功 |
| 会话隔离 | **全局目标级独占会话**（`TARGET_ID = itemdespawntowhat:rules`，OPENING 15 s / 心跳 10 s / 租约 60 s）；同一目标同一时刻只允许一个会话写，修订号用于乐观校验 |
| 幂等 | 同一 `operationId` 回放上次回执，不重复写盘 |
| 路径安全 | 拒绝 `.`/`..` 越界与符号链接 |

## 6. 版本戳推进时机

成功的数据包重载（`RuleRuntimeHost.reload`）与保存（`bumpVersion`）都推进 `sessions.version()`；快照下发前与保存前都先 `synchronizeDiskRevision()`，覆盖层被手工修改时自增。保存后 reload 失败时：仍 `bumpVersion()` 并回 `SAVED_NOT_RELOADED`（`writtenToDisk=true, reloaded=false`），同时**保留上一版索引**（规则已落盘，下次启动/重载生效）。`/idtw config convert` 已随 P8 结论退役（命令与 `RuleConvertService` 已删除），不再推进修订号；旧 v1.2.1 配置不再加载，需用 `/idtw config edit` 手工重建，见 [更新说明](../../../guide/update-notes.md)。

## 7. 相关

- 模块细节：[../modules/edit-protocol.md](../modules/edit-protocol.md)、[../modules/command.md](../modules/command.md)
- 校验：[../systems/issue-validation.md](../systems/issue-validation.md)
- 加载与索引重建：[rule-loading-flow.md](rule-loading-flow.md)
