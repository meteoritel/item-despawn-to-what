# 功能模块：转化运行时（`core/runtime`）

> 事实来源：`core/runtime/**`（11 个文件）。
> 追踪、排期、条件求值、效果派发与双队列调度的引擎；**世界操作只在服务端主线程执行**。
> 决策：[ADR-0015](../../../adr/0015-runtime-scheduling-and-tracking.md)。

## 1. 类清单

| 类 | 职责 | 关键成员 |
|---|---|---|
| `ConversionRuntime` | 总控：规则索引 + 维度级追踪 + 双队列调度 + 转化编排 | `onItemAdded`、`attempt`、`select`、`performConversion`、`runEffect`、`deferNaturalExpiry`、`replaceRules`、`rescan`、`clear`、`shutdown`、`queueStats` |
| `TickScheduler` | 分桶到期任务队列（每 tick 预算 + 异常隔离） | `schedule(delay, task)`、`runDue(now)`；`SOFT_BUDGET_NANOS = 2_000_000` |
| `RuleIndex` | "物品 id → 候选规则"查询结构：直接物品建索引、标签懒展开 | `build(...)`、`candidates(itemId)` |
| `ExpressionEvaluator` | DNF 条件求值（组内 AND / 组间 OR / 叶级 NOT，全短路） | `matches(expression, context, conditionTypes)` |
| `RuntimeConditionContext` | `ConditionContext` 的服务端 record 实现（纯参数聚合） | 注入按维度复用的 `RuntimeTagLookup` / `RuntimeClimateSampler` |
| `RuntimeEffectContext` | `EffectContext` 实现：持有源快照、rounds、covered、效果调度器 | `schedule(...)`、`runWhenLoaded(...)` |
| `RuntimeTagLookup` | 标签成员查询，按 `kind:tagId` 缓存（不存在缓存空集） | `members(...)` |
| `RuntimeClimateSampler` | 气候采样，按 quart 坐标缓存，上限 4096，超限淘汰最旧 | `sample(pos)` |
| `LoadedChunks` | 无副作用区块存在性查询（**禁止为执行规则生成区块**） | `contains`、`containsArea` |
| `LifespanProvider` | 平台差异收敛点：掉落物自然寿命刻数 | `lifespanTicks(level, entity)`、`vanillaDefault()`（6000） |
| `PlayerDeathDrops` | 玩家死亡掉落排除策略（供 Fabric 最小入口复用） | `mark(entity, item)` |

`ConversionRuntime` 每维度持有一个 `LevelState { scheduler(检查), effects(效果), tracked, tags, climate }`；追踪状态按维度 key 索引，**任务上下文持有维度对象，维度卸载即整体释放**，避免强引用。

## 2. 转化生命周期

### 2.1 进入追踪（`onItemAdded`）

1. **快速短路**：规则索引为空且无活动调试场景 → 直接返回。
2. **排除判定** `excluded()`：`isRemoved` / 空物品 / `age == -32768`（原版无限寿命）/ 带 `itemdespawntowhat:check_lock`（死亡锁）/ 带 `itemdespawntowhat:converted`（已提交）任一即排除。
3. `index.candidates(itemId)` 为空则返回（**没有候选规则就不追踪**）。
4. UUID 去重（`ENTITY_LOAD` 在区块重追踪时会重复触发）；建 `TrackedState` 并 `scheduleInitial`。

### 2.2 首次排期

```text
lifespan        = max(1, lifespanProvider.lifespanTicks(level, entity))
earliestTrigger = min over 候选规则 ( max(0, rule.triggerAfterSeconds * 20) )
dueAge          = min(earliestTrigger, lifespan - 1)
delay           = max(0, dueAge - entity.getAge())
nextCheckTick   = level.getGameTime() + delay
```

即**触发时刻 = min(候选规则秒数×20, lifespan−1) − 当前年龄**。

### 2.3 到期判定（`attempt` → `select`）

`attempt` 依次：实体失效/被排除 → 移除追踪；**区块门禁** `isPositionEntityTicking` 不满足 → 退避 `checkIntervalTicks`；**转化锁** `locked` → 退避 1 tick；候选为空 → 移除；否则 `select`。

`select` 对候选（已排序）逐条：

- 非 expiryPending 时先过**年龄门槛** `isEligible`（未到龄记 `AGE_NOT_READY` 并 continue）；
- `ExpressionEvaluator.matches(rule.conditions(), ctx, conditionTypes)` 命中即返回该条。

**同一掉落物只执行优先级最高的一条命中规则**。候选排序（`RuleIndex`）：`优先级 desc → 条件叶数 desc → 定义序`。

### 2.4 未命中退避

```text
failureCount++
若 expiryPending 或已过寿命 → 移除追踪（expiryPending 时 discard）
否则 delay = config.backoffTicks(failureCount)         # 见 systems/config.md
  年龄门槛修正：若当前无一规则到龄，则 failureCount-- 并把 delay 提前到最近一条规则达龄时刻
  reschedule(min(delay, lifespan - age - 1))
```

### 2.5 命中提交（`performConversion`）

1. `tracked.locked = true`；给实体加 `converted` 标签（**持久化标记**，防止重载/区块重进重复提交）。
2. 轮次：`perRound = BuiltinTypeRegistries.perRoundSourceConsumption(rule)`；`available = 源堆叠数`；`rounds = perRound>0 ? max(1, available/perRound) : 1`。
3. `covered = perRound>0 ? min(rounds*perRound, available) : 1`——按实际可扣数量收敛，避免 `spawn_xp.per_source_item` 多给经验。
4. 构造效果上下文（**源物品快照** `source.getItem().copy()`）。
5. 隐式消耗：`rule.usesImplicitSourceConsumption()` 为真 → 先执行一次 `consume_source(count=1)`。
6. 按定义序 `dispatchEffect` 每条效果。
7. `finally`：解锁、移除追踪。

### 2.6 自然消失挂钩（`deferNaturalExpiry`）

返回 `true` 表示**已拦截原版 discard** 并改由队列处理：置 `expiryPending=true`、取消旧任务、以 `delay=0` 立即排一次 `attempt`。到龄后即使条件不满足也会在 `attempt` 中被移除并 `discard`。原版没有"消失后"回调，因此拦截点在 discard **之前**。

### 2.7 清理与重载

- `onItemRemoved`：离开世界/区块卸载时移除追踪并取消任务。
- `clear(level)`：维度卸载整体释放；`shutdown()`：释放全部（停服）。
- `replaceRules(...)`：重建索引、清空各维度 `tracked`/`scheduler`，并把 `tags`/`climate` 置 null（**防止读到旧数据包标签**）。
- `rescan(level)`：reload 后回扫 `level.getAllEntities()` 重建追踪。

## 3. 调度与性能

- **双队列**：`LevelState` 持 `scheduler`（条件检查）与 `effects`（世界操作/延迟效果）两个独立 `TickScheduler`；`onLevelTick` 每 tick 依次 `runDue` 两者，分别可观测（`queueStats`）。
- **每 tick 预算**：两个上限同时生效——`visited < maxTasksPerTick`（来自 `ServerConfig.maxChecksPerTick`，默认 512）**且** 未超过 2ms 软预算。注释明确：时间预算只能在任务之间检查，**单次原版原子操作不可被抢占**。
- **分桶与游标**：`buckets: TreeMap<Long, ArrayDeque<Task>>` 按到期刻分桶；`ready` 暂存已到期桶，**不复制积压任务**；零延迟任务也入队（防递归调度绕过预算）；跳过的刻在下一次 `runDue` 一并补执行。
- **有界缓存**：气候按 quart 坐标缓存上限 4096；标签按 `kind:tagId` 缓存；`RuleIndex` 的 `tagCache`/`queryCache` 懒展开；方块偏移在 `PlaceBlockExecutor` 静态预计算。全部随 `replaceRules` **整体失效重建**，不做单独失效。

## 4. 效果执行细节

- **顺序启动**：效果列表按定义序 `dispatchEffect`；`dispatchEffect` 只把效果登记到 `effects` 队列（`delay = max(0, effect.delayTicks())`，**相对规则触发时刻**）。
- **求值时机**：`runEffect` 在任务**实际到期时**才依序判定：① 效果级 `conditions`（不成立跳过）；② `chance`（`random().nextDouble() >= chance` 跳过）；③ 类型未注册 → 记 ERROR 并返回；④ 调执行器。
- **异常隔离**：`runEffect` 捕获 `RuntimeException`，记录"规则 id + 效果类型 + 位置"后吞掉，**不影响后续效果**；`TickScheduler.runDue` 另有兜底（`VirtualMachineError` 重抛，其余记日志）。执行器约定：**不捕获、不吞异常**。
- **区块门禁**：通用入口 `RuntimeEffectContext.schedule` → `runWhenLoaded`：目标位置区块未加载则 `schedule(20, task)` 重试，**不主动加载区块**。多个执行器内部另有门禁（生成实体检查实际落点、闪电/爆炸/箭雨/放置各有半径检查）。
- **快照 vs 实时**：`sourceStack()` 是触发时刻 `copy()` 快照（只读用途，如 `place_block.use_source_block`）；`consume_source` 必须用**实时堆叠**。

## 5. 扩展点

- **改排期/退避语义**：改 `ConversionRuntime.scheduleInitial` / `attempt` 与 `ServerConfig.backoffTicks`。
- **改规则选择排序**：改 `RuleIndex.build` 的比较键。
- **改条件求值**：改 `ExpressionEvaluator`（保持全短路；`surrounding_blocks` 的未加载区块门禁在其中）。
- **新增上下文能力**：改 `core/api/EffectContext` / `ConditionContext` 与两个 `Runtime*Context` 实现。

## 6. 相关

- 端到端链路：[../flows/conversion-lifecycle.md](../flows/conversion-lifecycle.md)
- 调度预算与缓存：[../systems/scheduling-budget.md](../systems/scheduling-budget.md)、[../systems/caching-indexing.md](../systems/caching-indexing.md)
- 类型与消耗语义：[type-system.md](type-system.md)、[rule-model.md](rule-model.md)
- 决策：[ADR-0015](../../../adr/0015-runtime-scheduling-and-tracking.md)
