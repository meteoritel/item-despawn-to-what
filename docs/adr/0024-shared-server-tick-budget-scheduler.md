# ADR-0024：所有维度共享的公共服务器 tick 预算调度器

- 状态：已实施（2026-10-03，第二轮后端改造；提交 `63eea8b`），已通过用户实机验收。
- 实现见：`core/runtime/scheduler/`（`ServerScheduler`、`RealmQueues`、`TaskLane`、`ServerTaskKind`、`SchedulerConfig`、`ServerTickBudget`、`StepResult`、`StepOutcome`、`CancelReason` 等）；配置键见 [config.md](../dev/backend/systems/config.md)。
- 替代范围：取代 [ADR-0015](0015-runtime-scheduling-and-tracking.md) 决策 3 的「每 tick `max_checks_per_tick` 分桶」与 [ADR-0017](0017-backend-cutover-and-budgeted-effects.md) 决策 4 的「检查 / 效果各自独立的分桶队列」中的预算模型。
- 依据：历史规划 `docs/archive/backend-round-2/PLAN.md` §4.6 与其 `0002-shared-server-budget`（本地归档，不纳入版本控制）。

## 背景

旧的到期调度按维度各持一套队列与预算：同龄掉落物集中到期时，单个维度就可能吃掉整 tick 时间；多维度并存时各维度预算**累加**，tick 峰值随维度数增长。检查和效果分属两套队列，也各自计时，无法给出「服务器这一 tick 总共花了多少」。

## 决策

1. **单一实例、单一预算**：服务器（common 引导层）持有一个 `ServerScheduler`；每 tick 只推进一次（`tick(gameTime)` 对同刻重复调用直接忽略）。所有维度与任务种类共享同一份**时间预算**（`server_budget_us`，默认 2000µs 软预算）与**工作量上限**（`effectiveMaxWorkUnitsPerTick`，缺省回退 `max_checks_per_tick`）。
2. **按维度与任务种类公平轮转**：每个 realm（维度）按 `ServerTaskKind`（`CONDITION_CHECK` / `WORLD_SEARCH` / `EFFECT` / `REBATE` / `MAINTENANCE`）分 lane；每 tick 以自增游标为起点轮流推进各 lane，先按额度搬运到期任务、再执行就绪任务，直到预算耗尽或无任务可推进。效果 lane 另有独立配额 `effects_work_units_per_tick`。
3. **检查平滑窗口**：每 lane 每 tick 的到期搬运额度按 `check_spread_window_ticks` 在窗口内分摊，并受 `dispatch_batch_size` 上限约束，避免同龄集中到期在单个 tick 内全量搬运。
4. **保留原始时间、跨 tick 延后不丢**：任务携带到期 tick，只有到期后才参与执行，公平轮转不会让任务提前执行；预算耗尽时任务留在队列，下一 tick 续接。任务可续接进度（`ServerTask.step` 返回 `StepResult`：`DONE` / `FAILED` / `YIELD`（预算或区块让出）/ `RETRY`（业务重试））。
5. **软预算、不抢占**：时间为软预算；普通 Java 调用与原版原子操作不能被安全中途抢占，单步过长需由业务侧拆分。世界访问始终在服务端主线程，不使用并行世界写入或硬中断。
6. **取消与生命周期**：`cancelRealm`（重载后按维度重扫，保留 realm）/ `releaseRealm`（维度卸载，清空并上报原因）/ `clear`（停服或重载，全部带原因取消）；取消计数按 `CancelReason` 分类，不留静默丢弃。
7. **平台只接一次推进入口**：平台侧只提供单一「服务器 tick 推进」入口调用 `tick`，避免维度事件重复推进同一份总预算。

## 考虑过的替代方案

- **每维度保留独立预算**：实现简单，但峰值随维度数累加，正是本轮要解决的集中开销问题。否决。
- **并行世界写入 / 硬中断抢占**：会破坏服务端线程模型、引入并发正确性风险，且原版 API 不提供可安全中断的单步。否决。
- **牺牲到期精度换吞吐（不保留原始时间）**：会让「配置延迟」被公平轮转提前执行，语义不可预测。否决（保留原始时间、只允许延后）。

## 后果

- 过载时表现为**延后**而非丢弃或提前执行；不再承诺任意负载下固定的到期精度或固定 tick 耗时。
- 调试性能口径升级为 **v3**：区分 `vanilla_tick_cost`（原版服务器 tick）与 `idtw_runtime_cost`（本模组运行时），`server_tick_cost` 现含本模组运行时成本，**v3 数据不可与 v2 直接混比**（见 [debug-validation-guide.md](../dev/debug-validation-guide.md)）。
- 新增任何长循环业务（位置搜索、返还交付等）都必须拆成有界步骤接入本调度器，禁止在别处另建完整服务器预算。
