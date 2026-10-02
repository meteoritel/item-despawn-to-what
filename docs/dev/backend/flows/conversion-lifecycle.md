# 纵向系统：转化生命周期

> 从一个掉落物进入世界，到它被转化为产物或自然消失的端到端流程。
> 类职责见 [conversion-runtime.md](../modules/conversion-runtime.md)；排期/退避见 [scheduling-budget.md](../systems/scheduling-budget.md)。决策：[ADR-0015](../../../adr/0015-runtime-scheduling-and-tracking.md)、[ADR-0017](../../../adr/0017-backend-cutover-and-budgeted-effects.md)。

## 1. 全景

```text
实体加入世界 → 排除判定 → 候选索引 → 建追踪 → 首次排期
   → 到期检查（年龄门槛 + DNF 条件）→ 未命中退避 | 命中提交
   → 源快照 + 轮次计算 → 隐式消耗（若有）→ 按序派发效果 → 清理
自然消失入口 → deferNaturalExpiry 拦截 discard → 队列内最后一次检查 → 转化或最终 discard
```

## 2. 分步

### 2.1 进入追踪

| 步骤 | 判定/动作 |
|---|---|
| 入口 | 平台事件 → `RuleRuntimeHost.onItemAdded` → `ConversionRuntime.onItemAdded` |
| 快速短路 | 规则索引为空且无活动调试场景 → 返回 |
| 排除 `excluded()` | `isRemoved` / 空物品 / `age == -32768`（无限寿命）/ 带 `check_lock`（死亡锁）/ 带 `converted`（已提交）任一即排除 |
| 候选 | `index.candidates(itemId)` 为空 → 不追踪 |
| 去重 | 已 tracked 的 UUID 直接返回（`ENTITY_LOAD` 会重复触发） |
| 排期 | `scheduleInitial`：`dueAge = min(min(rule秒数)×20, lifespan-1)`，`delay = max(0, dueAge - age)` |

### 2.2 到期检查（`attempt` → `select`）

| 检查 | 不通过时 |
|---|---|
| 实体失效 / 被排除 | 移除追踪 |
| 区块实体 ticking 门禁 `isPositionEntityTicking` | 退避 `checkIntervalTicks` |
| 转化锁 `locked` | 退避 1 tick |
| 候选为空 | 移除追踪 |
| 逐条候选：年龄门槛 `isEligible` | 未到龄记 `AGE_NOT_READY`，continue |
| 逐条候选：`ExpressionEvaluator.matches`（DNF） | 全部未命中 → 退避 |

**同一掉落物只执行优先级最高的一条命中规则**（候选排序：`优先级 desc → 条件叶数 desc → 定义序`）。

### 2.3 未命中退避

`failureCount++` → `delay = config.backoffTicks(failureCount)` → 年龄门槛修正（无规则到龄时提前到最近达龄时刻）→ `reschedule(min(delay, lifespan-age-1))`。若已 expiryPending 或过了寿命 → 移除追踪（expiryPending 时 discard）。

### 2.4 命中提交（`performConversion`）

| 步骤 | 动作 |
|---|---|
| 加锁 | `tracked.locked = true`；实体加 `converted` 持久标签（防重载/区块重进重复提交） |
| 轮次 | `perRound = perRoundSourceConsumption(rule)`；`available = 堆叠数`；`rounds = perRound>0 ? max(1, available/perRound) : 1` |
| 覆盖量 | `covered = perRound>0 ? min(rounds*perRound, available) : 1`（按实际可扣数量收敛） |
| 上下文 | 构造 `EffectContext`，含**源物品快照** `source.getItem().copy()` |
| 隐式消耗 | 未声明任何 `consume_*` → 先执行 `consume_source(count=1)` |
| 派发 | 按定义序 `dispatchEffect` 每条效果到效果队列 |
| 清理 | `finally`：解锁、移除追踪 |

### 2.5 效果执行（`runEffect`，任务到期时）

1. 效果级 `conditions` 不成立 → 跳过；
2. `chance` 未命中 → 跳过；
3. 类型未注册 → 记 ERROR 返回；
4. 调执行器；异常捕获记录（不影响后续效果）。

产出类效果乘 `rounds`、受 `limit` 收敛；一次性世界效果（闪电/爆炸/箭雨/天气）不乘 `rounds`。

### 2.6 自然消失

平台事件/Mixin 拦到原版 discard → `deferNaturalExpiry` 返回 true 时不 discard，置 `expiryPending`、取消旧任务、`delay=0` 立即排一次检查。到龄后即使条件不满足也在 `attempt` 中被移除并 `discard`。**原版没有"消失后"回调，拦截点在 discard 之前。**

## 3. 一个可核对的例子

规则：`minecraft:chicken` 存活 10 秒 → 生成 1 个 `minecraft:rotten_flesh`（未声明 `consume_*`）。

| tick | 事件 |
|---|---|
| 掉落物入世界 | `onItemAdded`：候选命中 → 建追踪；`dueAge = min(10×20, lifespan-1) = 200`，`delay = 200 - age` |
| 到龄 | `attempt` → `select`：年龄门槛通过、DNF 恒真 → 命中 |
| 提交 | `rounds = max(1, 1/1) = 1`；隐式 `consume_source(1)` 扣空源并 discard |
| 效果 | `spawn_item(count=1)` 在原位置生成腐肉 |
| 结束 | 解锁、移除追踪 |

（这正是 `/idtw debug run convert` 的场景，见 [debug-scenario-flow.md](debug-scenario-flow.md)。）

## 4. 相关

- 运行时总控与上下文：[../modules/conversion-runtime.md](../modules/conversion-runtime.md)
- 调度与退避：[../systems/scheduling-budget.md](../systems/scheduling-budget.md)
- 条件与效果类型：[../modules/type-system.md](../modules/type-system.md)
