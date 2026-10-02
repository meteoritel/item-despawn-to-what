# 纵向系统：编辑保存协议

> 从"打开编辑器"到"规则落盘并重建索引"的端到端流程。当前前端为占位页，协议保留供下一轮前端使用。
> 类职责见 [edit-protocol.md](../modules/edit-protocol.md)、[issue-validation.md](../systems/issue-validation.md)。决策：[ADR-0016](../../../adr/0016-edit-protocol-changeset-version-stamp.md)。

## 1. 打开编辑器

```text
玩家执行 /idtw config edit
  → RuleCommandContext.editContext().sendTo(player, new OpenRuleEditorPayload())
  → 客户端安装的 openEditorSink 回调 → 打开编辑界面
  → 客户端发 RequestRuleSnapshotPayload
```

`OpenRuleEditorPayload` 服务端无 sink 时静默丢弃（core 不反向依赖 client）。

## 2. 请求快照

```text
RequestRuleSnapshotPayload (C2S)
  → 权限校验 canEdit（单人世界或权限 ≥2）
  → 运行时就绪校验（overlayRoot + typeRegistries 非空）
  → synchronizeDiskRevision()          # 磁盘修订核对，检测手工改动
  → sessions.open(player)              # 打开/续期 per-player 会话
  → RuleSnapshotAssembler.assemble(...)  # 覆盖层原始 JSON(editable) + 数据包层模型编码(只读)
  → RuleSnapshotPayload (S2C)
```

## 3. 提交变更集

```text
SaveRuleChangeSetPayload / SaveRuleChangeSetChunkPayload* (C2S)
  → 解析 RuleEditChangeSet
  → 会话有效？(空闲超时 → session_expired，不落盘，补发最新快照)
  → 空集 → 直接返回
  → synchronizeDiskRevision()          # 磁盘无法核对 → 拒绝保存
  → 版本戳校验 versionMatches(expectedVersion)？不一致 → 整批拒绝 + 回冲突文本 + 最新快照，不落盘
  → RuleSubmissionValidator.validate(...)   # 解码 + 语义 + 动态引用；无部分通过
  → RuleOverlayWriter.apply(...)            # 基于磁盘内容：预备→(.bak)→原子替换→失败回滚
  → rebuildAndRescan(server)                # 重建索引 + 全维度 rescan
  → 推进版本戳
  → RuleSaveResultPayload(回执) + RuleSnapshotPayload(新快照)
```

*分片：收齐后复用同一 `handleChangeSet`；未收齐不回执。

## 4. 关键不变量

| 不变量 | 含义 |
|---|---|
| 服务端权威 | 全部写盘经服务端；客户端只提交意图 |
| 乐观并发 | 版本戳不一致 → **整批拒绝且不落盘**，回最新快照 |
| 权威落盘 | 依据**磁盘文件内容**逐 id 增删改，保留未编辑条目/未知字段；控制条目、未涉及规则、坏 JSON 都不会被保存动作删除 |
| 整批语义 | 全部预备成功才逐个原子替换；任一失败逆序回滚；`writtenFiles==0` 或 `conflicts` 非空不算成功 |
| 会话隔离 | per-player 会话（空闲 5 分钟）；并发保护靠版本戳，非全局锁 |
| 路径安全 | 拒绝 `.`/`..` 越界与符号链接 |

## 5. 版本戳推进时机

`成功 reload / save / convert 均推进版本戳`。保存后 reload 失败时：显式 `bumpVersion()` 并回 `saved_reload_failed`，同时**保留上一版索引**（规则已落盘，下次启动/重载生效）。

## 6. 相关

- 模块细节：[../modules/edit-protocol.md](../modules/edit-protocol.md)、[../modules/command.md](../modules/command.md)
- 校验：[../systems/issue-validation.md](../systems/issue-validation.md)
- 加载与索引重建：[rule-loading-flow.md](rule-loading-flow.md)
