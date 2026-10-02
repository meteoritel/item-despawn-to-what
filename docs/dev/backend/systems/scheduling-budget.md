# 横向系统：调度与性能预算

> 事实来源：`core/runtime/TickScheduler.java`、`core/runtime/ConversionRuntime.java`、`core/config/ServerConfig.java`。
> 相关模块：[conversion-runtime.md](../modules/conversion-runtime.md)；配置值见 [config.md](config.md)。决策：[ADR-0015](../../../adr/0015-runtime-scheduling-and-tracking.md)、[ADR-0017](../../../adr/0017-backend-cutover-and-budgeted-effects.md)。

## 1. 分桶调度器（`TickScheduler`）

| 结构 | 说明 |
|---|---|
| `buckets: TreeMap<Long, ArrayDeque<Task>>` | 按**到期刻**分桶 |
| `ready: ArrayDeque<ArrayDeque<Task>>` | 暂存已到期桶，从队首取任务，**不复制积压任务**（保留游标） |
| `schedule(delay, task)` | 入队；**零延迟任务也入队**，防止递归调度绕过预算 |
| `runDue(now)` | 每 tick 把 `key <= now` 的桶整体移入 `ready` 并执行到预算耗尽 |

- **逾期补执行**：跳过的游戏刻，其 `key <= now` 桶在下一次 `runDue` 被一并 drain，故跳刻也会补执行。
- **异常隔离**：`runDue` 对每个任务兜底捕获——`VirtualMachineError` 重抛，其余 `Throwable` 记日志，单任务异常不影响队列。

## 2. 双队列（每维度）

`ConversionRuntime.LevelState` 持有两个**独立** `TickScheduler`：

| 队列 | 承载 |
|---|---|
| `scheduler`（检查） | 到期条件检查任务 |
| `effects`（效果） | 世界操作 / 延迟效果任务 |

`onLevelTick` 每 tick 依次 `runDue` 两者；分别可观测（`queueStats`），压测时可区分"检查积压"还是"世界操作积压"。

## 3. 每 tick 预算

**两个上限同时生效**：

```text
visited < maxTasksPerTick          # 来自 ServerConfig.maxChecksPerTick，默认 512
且 System.nanoTime() < deadline    # deadline = started + 2_000_000ns（2ms 软预算）
```

- 预算是**每个维度、每类队列各自**的，不是全局。
- **时间预算只能在任务之间检查**——单次原版原子操作（爆炸、实体 AABB 查询、单轮复杂战利品）无法被软预算抢占。因此软预算不能证明"TPS 达标"，指标须由实机压测支持。
- 超出预算的部分顺延到下一 tick；队列积压时"访问次数包含取消条目"，不等于成功转化数。

## 4. 指数退避

条件不满足时的重试间隔（`ServerConfig.backoffTicks(failureCount)`）：

```text
base  = max(1, checkIntervalTicks)        # 默认 20
shift = min(max(0, failureCount - 1), 20)
ticks = min(base << shift, backoffMaxTicks)   # 默认封顶 100
```

即序列 `checkIntervalTicks × 2^(n-1)`，封顶 `backoff_max_ticks`（默认 5 秒）。到自然消失前停止。

**年龄门槛修正**：`select` 未命中后，若当前**没有任何候选规则到龄**，`failureCount--` 并把 delay 提前到最近一条规则达龄时刻，避免在未到期前白白退避；否则 `delay = min(backoff, nextAge)`。最终 `reschedule` 再与剩余寿命取 min（`min(delay, lifespan - age - 1)`）。

## 5. 自然消失的最后一次检查

`deferNaturalExpiry` 拦截原版 discard 后，以 `delay=0` 立即排一次检查（仍受预算约束），保证"队列里的最后判定"在自然消失前完成；到龄后条件不满足也会被移除并 discard。

## 6. 扩展点与踩坑

- 改预算/退避基数 → `ServerConfig`（见 [config.md](config.md)）；改队列机制 → `TickScheduler`。
- 踩坑：预算是"每队列每维度"；软预算不抢占原子操作；零延迟任务也走队列（不要绕过它直接执行）；积压访问数含取消条目。

## 7. 相关

- 生命周期中的排期/退避：[../modules/conversion-runtime.md](../modules/conversion-runtime.md)、[../flows/conversion-lifecycle.md](../flows/conversion-lifecycle.md)
- 配置字段：[config.md](config.md)
