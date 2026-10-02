# 纵向系统：编辑保存协议

> 从"打开编辑器"到"规则落盘并重建索引"的端到端流程。客户端编辑器已落地，本流程为现行链路。
> 类职责见 [edit-protocol.md](../modules/edit-protocol.md)、[issue-validation.md](../systems/issue-validation.md)。决策：[ADR-0016](../../../adr/0016-edit-protocol-changeset-version-stamp.md)。

## 1. 打开编辑器

```text
玩家执行 /idtw config edit
  → RuleEditService.openEditor(player, tick) 原子取锁（唯一入口）
  → 下发 OpenRuleEditorPayload：成功 statusCode=SUCCESS，失败 statusCode=LOCK_BUSY 且 sessionId 为空
  → 客户端 payloadSink（RuleEditClientWorkspace.onOpen）校验协议版本与 statusCode
  → 成功后发 ConfirmRuleEditorPayload、自动请求快照，并 EditorScreenHooks.open(request) 打开编辑界面
```

`OpenRuleEditorPayload` 服务端无 sink 时静默丢弃（core 不反向依赖 client）。

## 2. 请求快照

```text
RequestRuleSnapshotPayload (C2S)
  → 运行时就绪校验（overlayRoot + typeRegistries 非空）
  → 会话持有者校验 owns(sessionId) + isOwner(player)；未确认的 OPENING 会话不接受快照请求
  → heartbeat(sessionId, tick)            # 续租
  → RuleSnapshotAssembler.assemble(...)   # 覆盖层原始 JSON + 数据包层模型编码；editable = base/overlay 非空
  → RuleSnapshotPayload (S2C)
```

## 3. 提交变更集

```text
SaveRuleChangeSetPayload / SaveRuleChangeSetChunkPayload* (C2S)
  → 解析 RuleEditChangeSet
  → 会话持有者校验（失效 → session_expired / lock_not_owned，不落盘）
  → 空集 → NO_CHANGES 直接返回
  → expectedVersion 与当前 sessions.version() 不一致 → 整批拒绝（version_conflict）+ 最新快照，不落盘
  → RuleSubmissionValidator.validate(...)   # 解码 + 语义 + 动态引用；无部分通过
  → RuleOverlayWriter.apply(...)            # 基于磁盘内容：预备→(.bak)→原子替换→失败回滚
  → beginApply(sessionId) → rebuildAndRescan(server) → finishApply(sessionId, tick)   # 重建索引 + 全维度 rescan
  → 成功：bumpVersion + SUCCESS；**重载失败：bumpVersion + SAVED_NOT_RELOADED（writtenToDisk=true, reloaded=false），保留上一版索引**
  → RuleSaveResultPayload(回执) + RuleSnapshotPayload(新快照)
```

*分片：收齐后复用同一 `handleChangeSet`；未收齐不回执。

## 4. 关键不变量

| 不变量 | 含义 |
|---|---|
| 服务端权威 | 全部写盘经服务端；客户端只提交意图 |
| 乐观并发 | 上下文修订号（`sessions.version()`）不一致 → **整批拒绝且不落盘**，回最新快照 |
| 权威落盘 | 依据**磁盘文件内容**逐 id 增删改，保留未编辑条目/未知字段；控制条目、未涉及规则、坏 JSON 都不会被保存动作删除 |
| 整批语义 | 全部预备成功才逐个原子替换；任一失败逆序回滚；`writtenFiles==0` 或 `conflicts` 非空不算成功 |
| 会话隔离 | **全局目标级独占会话**（`RuleEditSessionLimits.TARGET_ID = itemdespawntowhat:rules`，OPENING 15s / 心跳 10s / 租约 60s）；同一目标同一时刻只允许一个会话写，修订号用于乐观校验 |
| 路径安全 | 拒绝 `.`/`..` 越界与符号链接 |

## 5. 版本戳推进时机

`成功 reload / save 推进上下文修订号（bumpVersion）`。保存后 reload 失败时：仍 `bumpVersion()` 并回 `SAVED_NOT_RELOADED`（`writtenToDisk=true, reloaded=false`），同时**保留上一版索引**（规则已落盘，下次启动/重载生效）。`/idtw config convert` 已随 P8 结论退役（命令与 `RuleConvertService` 已删除），不再推进修订号；旧 v1.2.1 配置不再加载，需用 `/idtw config edit` 手工重建，见 [更新说明](../../../guide/update-notes.md)。

## 6. 相关

- 模块细节：[../modules/edit-protocol.md](../modules/edit-protocol.md)、[../modules/command.md](../modules/command.md)
- 校验：[../systems/issue-validation.md](../systems/issue-validation.md)
- 加载与索引重建：[rule-loading-flow.md](rule-loading-flow.md)
