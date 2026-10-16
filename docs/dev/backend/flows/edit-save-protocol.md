# 纵向系统：编辑保存协议

> 从"打开编辑器"到"规则落盘并重建索引"的端到端流程。客户端编辑器已落地，本流程为现行链路。
> 类职责见 [edit-protocol.md](../modules/edit-protocol.md)、[issue-validation.md](../systems/issue-validation.md)。决策：[ADR-0019](../../../adr/0019-exclusive-edit-session-and-target-lock.md)、[ADR-0020](../../../adr/0020-source-catalog-and-overlay-control-entries.md)。

## 1. 打开编辑器

```text
玩家执行 /idtw config edit（仅玩家可执行）
  → RuleEditServerHandler.openEditor：就绪 + 权限(单人/等级≥2) 检查
  → RuleEditService.openEditor → EditSessionManager.acquire 原子取锁（唯一入口）
  → 下发 OpenRuleEditorPayload：成功 statusCode=SUCCESS（带 sessionId）；
     失败 statusCode=LOCK_BUSY 且 sessionId 为空，messageArgs=当前持有者
  → 客户端 payloadSink（RuleEditClientWorkspace.onOpen）校验协议版本与 statusCode
  → 成功后发 ConfirmRuleEditorPayload，并 EditorScreenHooks.open(request) 打开编辑界面
  → 首份快照由服务端确认入口提供；等待时列表显示加载提示
```

`RuleEditServerContext` 下发 `OpenRuleEditorPayload`；服务端无 sink 时静默丢弃（core 不反向依赖 client）。取锁失败也要下发载荷，客户端据此提示持有者。

## 2. 确认与请求快照

```text
ConfirmRuleEditorPayload (C2S)：version 校验 → owns + isOwner → EditSessionManager.confirm(OPENING→ACTIVE)
  → 启动最新快照的后台读取，主线程解码排入公共调度器
RequestRuleSnapshotPayload (C2S)
  → owns + isOwner 校验；未确认的 OPENING 会话不接受快照请求
  → heartbeat(sessionId, tick)            # 续租
  → RuleSnapshotTask：后台读取、核对修订；公共预算逐条解码/编码
  → 后台装配原始来源三视图与序列化、切片
  → 小快照直发，大快照每 tick 一片；超总量上限回 UNAVAILABLE
```

首次打开不额外发送 `RequestRuleSnapshotPayload`；该请求用于主动刷新与保存后的补拉。无保存与在途快照时，请求入口即启动后台读取，不等待 tick 轮询启动；主线程步骤在同一快照内复用解码器与编码 Codec，构建与执行仍受公共预算约束。

心跳 `HeartbeatRuleEditorPayload` 正常无回执；失效时按身份回不同状态码——原持有者回 `SESSION_EXPIRED`，非持有者回 `LOCK_NOT_OWNED`（客户端据此回到未持有状态）。

## 3. 选择目录（分页）

```text
RequestRuleCatalogPayload (C2S) → owns + isOwner 校验（失效 → SESSION_EXPIRED / LOCK_NOT_OWNED）
  → page/pageSize 合法性校验（越界 → INVALID_REQUEST）
  → confirmed 校验（未确认的 OPENING 会话不接受目录请求）→ 类型合法性校验
  → heartbeat → RuleCatalogService.page(server, type, filter, page, pageSize)
  → RuleCatalogPayload (S2C)：分页结果 + lastPage
```

目录候选来自 `core/catalog` 的九类数据源（物品/方块/实体/战利品表/群系/维度/标签/流体/状态效果）；`RuleCatalogService` 按"类型 + 来源修订号"缓存并归属请求时的服务端实例，保存真正写出后调用 `invalidate()` 显式失效，重载后的内容变化由读取时修订号变化触发重建。

## 4. 提交变更集

```text
直发/分片收齐 → operationId、会话、权限、幂等校验
  → beginApply → RuleSaveTask 入 MAINTENANCE 车道
  → 后台拼接与解析、核对字节和磁盘修订
  → 空集 NO_CHANGES；版本不符整批 VERSION_CONFLICT
  → 公共预算逐条完整校验，任一错误整批拒绝且不落盘
  → 后台基于磁盘 id 索引预备、核对、备份、提交；失败回滚
  → 后台合并新覆盖层与数据包基底；公共预算逐条解码与校验
  → 后台准备索引；服务端发布、摘除旧队列
  → 公共预算逐项取消旧任务、恢复返还、回扫掉落物
  → finishApply → 回执；VERSION_CONFLICT/SAVED_NOT_RELOADED 的最新快照另行排队
```

每步条数与软时间预算见[编辑协议](../modules/edit-protocol.md) §4。保存可以跨多个 tick；同一在途操作只运行一次，文件后台队列全进程串行。取消请求在提交确认前使后台回滚；主线程只传递原子信号。APPLYING 不因租约到期释放，关闭／强制释放在保存收尾后生效；已发布索引的清理必须完成。数据包重载等待文件工作结束后执行。


*分片：`RuleEditChunkAccumulator` 按 `transferId` 重组，收齐后复用同一 `handleChangeSet`；未收齐不回执，超过总量上限回 `INVALID_REQUEST`。

## 5. 关键不变量

| 不变量 | 含义 |
|---|---|
| 服务端权威 | 全部写盘经服务端；客户端只提交意图 |
| 乐观并发 | `expectedVersion` 与 `sessions.version()` 不一致 → **整批拒绝且不落盘**，回最新快照 |
| 权威落盘 | 依据**磁盘文件内容**逐 id 增删改，保留未编辑条目/未知字段；控制条目、未涉及规则、坏 JSON 都不会被保存动作删除 |
| 整批语义 | 全部预备与备份成功才逐个原子替换或删除；删除最后一个条目时删除文件，任一失败逆序恢复原内容；`writtenFiles==0` 或 `conflicts` 非空不算成功 |
| 会话隔离 | **全局目标级独占会话**（`TARGET_ID = itemdespawntowhat:rules`，OPENING 15 s / 心跳 10 s / 租约 60 s）；同一目标同一时刻只允许一个会话写，修订号用于乐观校验 |
| 保存进度 | 客户端立即显示保存模态，服务端节流下发对应操作的阶段计数；取消不关闭界面，回滚完成前独占锁仍保持 |
| 幂等 | 同一 `operationId` 回放上次回执，不重复写盘 |
| 路径安全 | 拒绝 `.`/`..` 越界与符号链接 |

新增规则可复用目标路径上合法的空 JSON 数组，写成单条规则对象；原空数组仍参与备份、外部修改核对与失败回滚。其它现存内容（包括损坏 JSON）不得被新增规则覆盖，多条目文件删除时保留所有未编辑成员。

## 6. 版本戳推进时机

数据包重载、保存写盘成功和检测到覆盖层外部改动均推进版本。保存与快照的目录指纹计算在后台执行；修订分配和后台持久化统一由 `RuleOverlayStore` 管理，已提交保存先登记修订再进行后续读取。保存写盘成功但索引发布前失败时保留上一版索引；索引发布后的回扫失败不撤回新索引，回 `SAVED_NOT_RELOADED`。

## 7. 相关

- 模块细节：[../modules/edit-protocol.md](../modules/edit-protocol.md)、[../modules/command.md](../modules/command.md)
- 校验：[../systems/issue-validation.md](../systems/issue-validation.md)
- 加载与索引重建：[rule-loading-flow.md](rule-loading-flow.md)
