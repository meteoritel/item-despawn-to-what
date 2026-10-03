# 横向系统：调度与性能预算

> 事实来源：`core/runtime/scheduler/**`（16 个类）、`core/runtime/ConversionRuntime.java`（`onServerTick`）、`core/runtime/ConversionSettlement.java`（效果与返还的分步执行）、`core/config/ServerConfig.java`。
> 相关模块：[conversion-runtime.md](../modules/conversion-runtime.md)；类清单由 modules 层维护，本文只讲机制。配置值见 [config.md](config.md)。
> 现行决策：[ADR-0024 共享服务器 tick 预算调度器](../../../adr/0024-shared-server-tick-budget-scheduler.md)（取代 ADR-0015 决策 3 与 ADR-0017 决策 4 的预算模型）。背景（已归档，仅作设计底稿）：[PLAN.md §4.6](../../../archive/backend-round-2/PLAN.md)、[ADR-0002 共享服务器预算](../../../archive/backend-round-2/docs/adr/0002-shared-server-budget.md)。

## 1. 一句话模型

**整个服务器只持有一个调度器**（`ServerScheduler`，由 `ConversionRuntime` 在构造时建立并持有）。所有维度、所有任务种类**共享同一份每 tick 时间软预算与全局工作量上限**，按「维度 × 任务种类」切成 lane 做公平轮转；预算耗尽的任务保留**原始到期刻**、跨 tick 延后，不做静默丢弃。

调度器取代了旧的 `TickScheduler`（按维度、按检查/效果各持一份预算）。核心目标：维度与队列之间不再各自记账、预算不再累加，同龄批量掉落物的集中到期在一个可配置窗口内被摊平。

## 2. 任务模型（`ServerTask` / `StepResult`）

| 类型 | 角色 |
|---|---|
| `ServerTask` | 最小任务单元：`kind()`、`tracingName()`、`step(ServerTickBudget)` 返回 `StepResult`、`onCancelled(reason)`、`onReleased()`。实现者**不得依赖平台类**，**单步必须有界**（长循环自行拆分）。 |
| `ServerTaskKind` | `CONDITION_CHECK` / `WORLD_SEARCH`（预留，现有实现仍并入条件检查）/ `EFFECT` / `REBATE` / `MAINTENANCE`。 |
| `StepResult` | `(outcome, workUnits, nextDelayTicks)`；紧凑构造器把 `workUnits` 夹到 **≥ 1**、`nextDelayTicks` 夹到 ≥ 0，防止零成本步骤绕过预算。 |
| `StepOutcome` | `DONE`（可释放）/ `YIELD`（让出，稍后继续）/ `RETRY`（业务重试）/ `FAILED`（记失败、不再入队）。 |
| `ScheduledTask` | 已入队任务的句柄。状态机 `PENDING → READY → RUNNING → DONE / FAILED / CANCELLED`；`cancel(reason)` 幂等且必带原因。 |
| `RunnableTask` | 把 `Runnable` 适配成一次性 `ServerTask`（固定 1 工作量）；是缺省 / 维护类任务的落点。 |

各任务种类的实际来源：

- `CONDITION_CHECK` —— `ConversionRuntime.scheduleCheck`（到期检查、退避重试、`deferNaturalExpiry` 的最后一次检查）。
- `EFFECT` —— `ConversionSettlement`（转化结算：计划 / 派发 / 交付三阶段）与 `RuntimeEffectContext.schedule`（延迟效果）。
- `REBATE` —— `SettlementRecovery`（停服 / 卸载后重启的返还交付）。
- `MAINTENANCE` —— `RunnableTask` 未指定种类时的缺省。
- `WORLD_SEARCH` —— **当前未使用**，为后续把位置搜索独立成种类预留。

## 3. 每 tick 预算（`ServerTickBudget`）

每 tick 开始时调度器调用 `budget.begin(gameTime, budgetNanos, maxWorkUnits)`，**时间上限与工作量上限同时生效**：

```text
usedWorkUnits >= maxWorkUnitsPerTick        # 全局工作量上限
或 System.nanoTime() >= deadline            # deadline = started + serverBudgetNanos
```

- 时间预算 `serverBudgetNanos` = `server_budget_us × 1000`（默认 2000us = **2ms 软预算**）。
- 工作量上限 `maxWorkUnitsPerTick` = `effectiveMaxWorkUnitsPerTick()`（`max_work_units_per_tick`，缺省回退 `max_checks_per_tick`，默认 **512**）。
- 两者**任一耗尽** `exhausted()` 即为真；调用方必须在**每个任务之间**检查。
- **扣费口径**：每次 `step` 后 `charge(result.workUnits())`；到期搬运固定 `chargeDrain()` = 1 个工作量并单独计数；`ConversionSettlement` 在容量 / 催化剂查询处还会额外 `charge`（例如每次世界查询 `CAPACITY_QUERY_UNITS = 2`）。超额度时截断，绝不越过本 tick 上限。

**效果类的单独限额**：`effects_work_units_per_tick`（默认 64）。`EFFECT` lane 在本 tick 累计消耗的工作量达到该值后，本轮内被跳过，**避免效果类吃掉全部公共额度**而饿死条件检查。

结算后产出只读快照 `ServerTickBudgetSnapshot`：`usageRatio()`（预算使用率，仅观测）、`exhaustionReason()`（`time` / `work_units` / `none`）。快照的 `usedNanos` 是软预算的实测耗时，**不得据此宣称 TPS 达标**。

## 4. 公平轮转与到期搬运

`ServerScheduler.tick(gameTime)` **每个游戏刻只推进一次**：同一 `gameTime` 的重复调用直接忽略（保证「每 tick 一个总预算」）。时钟取 `server.overworld().getGameTime()`（各维度共享同一值）。

组织方式：`RealmQueues`（一个 realm = 一个维度）按 `ServerTaskKind` 分出多条 `TaskLane`；**lane 是公平轮转与搬运分片的最小单位，realm 与 lane 都不持有独立预算**。本轮参与的 lane = `active()`（`pending > 0` 或 `ready` 非空）的那些。

每 tick 的推进流程（`runTick`）：

1. **轮转起点**：`rotationCursor` 每 tick 递增，遍历 lane 时从不同位置起步，保证跨 tick 公平。
2. **搬运额度**：对每条 lane 先算本 tick 的 `drainAllowance`（见下）；额度在**每 tick 每 lane 只计一次**（`countDue` 要遍历到期桶，不能每 pass 重扫）。
3. **按 pass 轮转**：对每条 lane，**优先搬运**一个到期任务进就绪队列（`takeDue`，扣 1 工作量），没有可搬则**取一个就绪任务执行一步**（`pollReady` → `runStep`）。`EFFECT` lane 额外受效果类工作量上限约束。`maxPasses = max(16, maxWorkUnitsPerTick×2 + lane 数×2)` 作为兜底上界。

**检查平滑窗口**（`check_spread_window_ticks`，默认 20）与 **搬运批量**（`dispatch_batch_size`，默认 64）共同决定搬运额度：

```text
due    = lane.countDue(gameTime, max(batch, batch × window))   # 统计上限，避免为巨大积压全量遍历
spread = ceil(due / window)
额度   = clamp(spread, 1, batch)
```

即：一个到期刻集中到期的 `due` 个任务，被摊到约 `window` 个 tick 里搬运（每 tick 约 `due/window` 个），同时每 lane 每 tick 至多搬 `batch` 个。这是 PLAN §4.6 允许的**时间精度让步**：以到期时刻的精度换取集中工作量分散。

**不提前、不丢弃**：`takeDue` 只搬 `桶键 <= gameTime` 的桶——公平轮转不会让任务提前执行；未搬到的到期桶保留原始 `dueTick`，跨 tick 延后而非丢弃。

## 5. 取消（`CancelReason`）

任务取消一律**带原因**并计入统计，**恒不静默丢弃**（`SchedulerStats.dropped` 恒为 0）：

| 原因 | 触发 |
|---|---|
| `RULE_RELOAD` | 规则重载 / 维度回扫（`replaceRules`、`rescan`） |
| `DIMENSION_UNLOAD` | 维度卸载（`ConversionRuntime.clear`） |
| `SERVER_STOP` | 停服（`shutdown`） |
| `ENTITY_REMOVED` | 源实体被移除 |
| `SCENARIO_STOP` | 开发场景主动停止 |

三个入口的语义差异：

- `cancelRealm(key, reason)` —— 取消该 realm 的**全部任务但保留 realm**（重载 / 回扫后同维度继续用）。
- `releaseRealm(key, reason)` —— 逐项上报原因后**移除 realm**（维度卸载）。
- `clear(reason)` —— 取消并移除**全部 realm**（停服）。

`ScheduledTask.cancel(reason)` 是幂等的：仅当任务仍在队列（`PENDING` / `READY`）时回退计数；已完成 / 失败 / 已取消的再次取消直接返回 false。

## 6. 观测面（`SchedulerStats` / `KindStats` / `QueueStats`）

- `SchedulerStats`（跨维度合并，`ConversionRuntime.schedulerStats()`）：`ticks` / `steps` / `released` / `failed` / `drained` / `cancelled`（+`cancelledByReason`）/ `exhaustedByTime` / `exhaustedByWorkUnits` / `pending` / `peakPending` / `realms` / `maxReadyDelayTicks` / `lastBacklogClearTicks` / 最近一 tick 的 `lastMaxReadyDelayTicks` 与 `lastDrainedTasks` / 累计 `drainedTicks` 与 `maxDrainedInTick` / `perKind`。
- `KindStats`（按种类）：`pending` / `steps` / `failed` / `requeued`（= `yielded + retried`）/ `deferred`（本轮结束时仍未执行的就绪任务）/ `done` / `yielded` / `retried` / `workUnits` / `lastMicros`（最近一 tick 该类忙碌微秒，跨维度合并）。
- `QueueStats`（单 lane）：`pending` / `peakPending` / `totalVisited` / `lastMicros` / `maxMicros` / `oldestDelay`。
- `ConversionRuntime` 的读入口：`schedulerStats()`、`budgetSnapshot()`、`queueStats(level, kind)`（旧的 `queueStats(level, effects 布尔)` 只在检查 / 效果两类间选择）、`pendingTasks(level)`。

`exhaustedByTime` / `exhaustedByWorkUnits` 只在**本 tick 结束时仍有待推进任务**时才自增，用于区分「预算耗尽且确实欠账」与「预算未被用满」。

## 7. 边界与踩坑

- **软预算不抢占原子操作**：时间预算只能在任务之间检查。单次原版世界操作（爆炸、实体 AABB 查询、单轮复杂战利品）无法被软预算中途抢占，因此软预算**不能证明「TPS 达标」**，指标须由实机压测支持。
- **世界操作仍在服务端线程**：调度器只决定「何时、执行多少」，不改变世界访问的线程；平台层**只接一次服务器 tick 推进入口**，不按维度重复推进同一份总预算（见 [platform-abstraction.md](platform-abstraction.md) §5）。
- **配置一次性读取**：预算参数经 `SchedulerConfig.from(config)` 在 `ConversionRuntime` **构造时**读取一次并做下界保护（各值 ≥ 1）。数据包重载 / `/idtw rule reload` **不会**重建调度器，故改预算类参数需**重启服务端**。
- **零延迟任务也入队**（`schedule` 对 delay ≤ 0 取 `lastGameTime`）：递归调度不能绕过预算。
- **`dispatch_batch_size` 是通用批量**：既是每 lane 每 tick 的搬运上限，也是 `ConversionSettlement` 中**效果派发**与**返还交付**的每步批量上限。
- 队列积压时 `pending` 含取消前尚未回退的条目口径以统计为准，不等于成功转化数。

## 8. 扩展点

- **改预算 / 窗口 / 批量** → `ServerConfig`（见 [config.md](config.md)）+ `SchedulerConfig.from`。
- **新增任务种类** → 扩 `ServerTaskKind`；注意 `SchedulerStats` 按全枚举聚合，`perKind` 会自动增项。
- **新增任务** → 实现 `ServerTask`（`kind` / `tracingName` / **有界** `step`），经 `scheduler.schedule` / `scheduleAt` 入队；长操作按 `StepResult.yield(...)` 分步。
- **改搬运平滑** → `ServerScheduler.drainAllowance`（窗口与批量在此合成额度）。
- **改公平策略** → `runTick` 与 `rotationCursor`。

## 9. 相关

- 生命周期中的排期 / 退避：[../modules/conversion-runtime.md](../modules/conversion-runtime.md)、[../flows/conversion-lifecycle.md](../flows/conversion-lifecycle.md)
- 配置字段（含全部预算键）：[config.md](config.md)
- 平台侧的唯一推进入口与事件接入：[platform-abstraction.md](platform-abstraction.md)、[../modules/platform.md](../modules/platform.md)
