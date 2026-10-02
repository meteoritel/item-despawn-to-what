# ADR-0019：全局独占编辑会话与目标级编辑锁

- 状态：已实施（P3，2026-10-02）；契约见 [实施契约 §3](../plan/plan-frontend-rewrite-contract.md)，常量与 DTO 在 `core/service/EditSessionManager.java`、`core/network/protocol/`、`core/network/transport/`。
- 依据：[规划书 §6](../plan/plan-frontend-rewrite.md)（独占编辑锁与时序）、[规划书 §7.1](../plan/plan-frontend-rewrite.md)（协议升级）。
- 替代范围：取代 [ADR-0016](0016-edit-protocol-changeset-version-stamp.md) 中「per-player 会话 + 仅靠版本戳」的并发模型，并升级到协议版本 2。

## 背景

旧链路是「全局单 UUID 锁」；ADR-0016 改成 per-player 编辑会话（空闲 5 分钟超时）后，并发保护主要由版本戳承担。但规划书 §6 指出：规则编辑目标是**全局唯一的一份覆盖层**，多玩家同时编辑只靠「提交时版本冲突」兜底，会出现编辑中的视图与磁盘状态长期分叉；同时旧实现用墙钟计时（单人世界暂停时租约照旧流逝），且回执混用文案与状态码，客户端无法可靠分支。

## 决策

1. **单一编辑目标**：`RuleEditSessionLimits.TARGET_ID = "itemdespawntowhat:rules"`；同一时刻至多 1 个 `OPENING`/`ACTIVE`/`APPLYING` 会话，不再按玩家分锁（契约 §3.1）。
2. **状态机**：`FREE --(指令取锁成功)--> OPENING --(客户端确认)--> ACTIVE --(提交变更集)--> APPLYING --(应用结束)--> ACTIVE`；释放/断线/租约到期/强制释放一律回到 FREE。
3. **时钟与常量**：`OPEN_CONFIRM_WINDOW_SECONDS=15`、`HEARTBEAT_INTERVAL_SECONDS=10`、`LEASE_SECONDS=60`、`REQUIRED_PERMISSION_LEVEL=2`；时间一律用服务端活动时钟 `MinecraftServer.getTickCount()`，**禁止** `System.currentTimeMillis()`，单人世界暂停时租约不流逝。会话持有 `ownerUuid`、不可预测的 `sessionId`（`UUID.randomUUID()`，不得由玩家名/时间戳推导）、`state`、`lastHeartbeatTick`、`openedTick`；服务端每 20 tick 检查超时。
4. **协议版本化**：`RuleEditProtocol.VERSION = 2`，握手阶段版本不匹配即拒绝；`RuleEditLimits.MAX_SNAPSHOT_BYTES` 从 `1_000_000` 提升到 `4 * 1024 * 1024`（契约 §3.2）。
5. **回执状态码化**：`RuleSaveStatus{SUCCESS, NO_CHANGES, NO_PERMISSION, LOCK_NOT_OWNED, LOCK_BUSY, SESSION_EXPIRED, VERSION_CONFLICT, VALIDATION_FAILED, WRITE_FAILED, SAVED_NOT_RELOADED, INVALID_REQUEST, UNAVAILABLE}`；客户端**只认状态码**，禁止解析中文文案（契约 §3.4）。
6. **结构化问题**：`RuleIssue(severity, ruleId, origin, fieldPath, messageCode, messageArgs, fallbackMessage)`；`messageCode` 是全限定翻译 key（例如 `itemdespawntowhat.edit.error.leaf_limit`），必须同时存在于 `en_us.json` 与 `zh_cn.json`（契约 §3.5）。
7. **幂等与分片**：`operationId` 由客户端生成，服务端对同一会话保留最近 16 个已处理 id，重复提交直接返回上次回执、不重复写盘；大变更集用 `transferId` + `index`/`count` 分片，同一玩家最多 2 个在途传输，60 秒无进展丢弃。
8. **授权检查点**：心跳、快照请求、目录请求、变更集提交、分片任一步骤都必须校验 `sessionId` 与状态匹配，否则回执 `SESSION_EXPIRED`/`LOCK_NOT_OWNED`（契约 §3.3）。
9. **唯一生产入口**：客户端只能由服务端 `OpenRuleEditorPayload` 打开编辑器；指令路径必须**先取锁成功**才发 payload，失败下发 `LOCK_BUSY` 与持有者信息。快捷键在不持有会话时只能提示「请先用指令打开」（契约 §3.7、§5）。
10. **管理指令**：`/idtw config edit-lock status`（查看自身会话，有权限时显示持有者）与 `/idtw config edit-lock release`（权限 ≥2 强制释放）。

## 理由

- 编辑目标是全局唯一的覆盖层，「按玩家分锁」并没有真正隔离写入，只是把冲突推给版本戳；改成单一目标锁后，服务端可以保证「同一时刻只有一份视图在被编辑」，版本戳继续承担磁盘内容的乐观并发。
- 用 tick 时钟替换墙钟，直接修复单人世界暂停导致会话过期的问题（契约 §7 验收基线明确要求「暂停时租约不流逝」）。
- `sessionId` 不可由玩家名/时间戳推导，避免第三方伪造会话；状态码 + `RuleIssue` 让客户端与日志都基于结构化数据决策，而不是解析显示文案。
- 幂等 `operationId` 与分片上限共同保证「网络重发不会重复写盘」和「大快照不会被截断后使用」。
- 权限（等级 ≥2）与「先取锁再打开」把授权检查放在服务端，客户端无法绕过。

## 后果

- P3 必须实现会话状态机、协议版本拒绝、分片装配与幂等表；客户端所有失败分支按 `RuleSaveStatus` 处理，未知码走 `UNAVAILABLE` 兜底。
- 快照与变更集上限提升到 4 MiB 后，必须保留按 UTF-8 **字节**计算的限额（不得把字符数当字节数）并显式拒绝超量请求。
- 新增任何编辑入口都要复用取锁流程；禁止出现「绕过独占锁的生产入口」。
- 会话失效、写盘成功但重载失败（`SAVED_NOT_RELOADED`）等状态必须能回显到界面，且沿用既有版本戳语义（见 [ADR-0016](0016-edit-protocol-changeset-version-stamp.md)）与权威落盘规则。
- 编辑会话的时限常量（15/10/60 秒）成为客户端心跳周期与 UX 的硬约束；修改需同步契约 §3.1。
