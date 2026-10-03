# 纵向系统：转化生命周期

> 从一个掉落物进入世界，到它被转化为产物或自然消失的端到端流程。
> 类职责见 [conversion-runtime.md](../modules/conversion-runtime.md)；排期/退避/共享预算见 [scheduling-budget.md](../systems/scheduling-budget.md)；平台触发入口与 Mixin 见 [platform.md](../modules/platform.md)。决策：[ADR-0015](../../../adr/0015-runtime-scheduling-and-tracking.md)、[ADR-0017](../../../adr/0017-backend-cutover-and-budgeted-effects.md)、[round-2 决策 0001](../../../archive/backend-round-2/docs/adr/0001-conversion-commitment.md)。

## 1. 全景

```text
实体加入世界 → 排除判定 → 候选索引 → 建追踪 → 首次排期（CONDITION_CHECK）
   ├─ 自然消失：discard 前拦截（deferNaturalExpiry）→ delay=0 排一次检查（kind=natural）
   └─ 火/岩浆/仙人掌：真实致死分支（requestEnvironmentalConversion）→ 归类 → requestConversion
   → 到期检查 attempt：区块门禁 / 锁 / 禁转 / 冷却 → select（声明该 kind + 年龄门槛 + 条件树）
   → 未命中退避 | 命中提交 performConversion
   → 整堆源转入结算库存 → 计划（选一候选 + 定组数）→ 派发（逐组支付固定成本 + 派发效果）
   → 交付（剩余库存作为返还物加入世界）→ 完成
中断（重载/维度卸载/停服）→ 记录待返还 → 重启/维度加载后 SettlementRecovery 只交付待返还
```

## 2. 分步

### 2.1 进入追踪

| 步骤 | 判定 / 动作 |
|---|---|
| 入口 | 平台事件 → `RuleRuntimeHost.onItemAdded` → `ConversionRuntime.onItemAdded` |
| 快速短路 | 规则索引为空且无活动调试场景 → 返回 |
| 排除 `excluded()` | `isRemoved` / 空物品 / `age == -32768`（无限寿命）/ 带 `check_lock`（死亡锁）/ 带 `converted`（已提交）任一即排除 |
| 候选 | `index.candidates(itemId)` 为空 → 不追踪 |
| 去重 | 已 tracked 的 UUID 直接返回（`ENTITY_JOIN` 会重复触发） |
| 排期 | `scheduleInitial`：`dueAge = min(min(rule秒数)×20, lifespan-1)`，`delay = max(0, dueAge - age)`，以 `CONDITION_CHECK` 入队 |

### 2.2 触发路径

| 消失方式 | 入口 | 关键点 |
|---|---|---|
| 自然消失 | `deferNaturalExpiry`（原版 discard 前） | 首次拦截置 `expiryPending`、取消旧任务、`delay=0` 立即排一次 `attempt(kind=natural)`；到期后即使条件不满足也会在 `attempt` 中被移除并 `discard`。**原版没有"消失后"回调，拦截点在 discard 之前。** |
| 火 / 岩浆 / 仙人掌 | `requestEnvironmentalConversion` | 在真实致死分支（`ItemStack#onDestroyed` 之前）调用，`DamageClassification.classify` 归类后 `requestConversion`；接触危险源、受伤但存活、原版免疫伤害都不触发 |

两者共用 `requestConversion` 与同一份追踪/预算：规则未声明该消失方式时不参与（未声明按 `natural`）；环境请求在 `hurt` 调用栈内同步跑条件检查与规则选择。

### 2.3 到期检查（`attempt` → `select`）

| 检查 | 不通过时 |
|---|---|
| 实体失效 / 被排除 | 移除追踪 |
| 区块实体 ticking 门禁 `isPositionEntityTicking` | 退避 `checkIntervalTicks` |
| 转化锁 `locked` | 退避 1 tick |
| 永久禁转 `DropState.permanentConversionBan` | 移除追踪 |
| 转化冷却 `cooldownRemaining > 0` | 退避剩余冷却刻 |
| 候选为空 | 移除追踪 |
| `select`：逐条候选，未声明该 `kind` 跳过；`natural` 且非 expiryPending 时才过年龄门槛 `isEligible` | 未到龄记 `AGE_NOT_READY`，continue |
| `select`：`ExpressionEvaluator.matches`（条件树四态，仅 MATCH 通过） | 全部未命中 → 退避 |

**同一掉落物只执行优先级最高的一条命中规则**（候选排序：`优先级 desc → 条件叶数 desc → 定义序`）。

### 2.4 未命中退避

`failureCount++` → 若 `expiryPending` 或已过寿命 → 移除追踪（`expiryPending` 时 `discard`）；否则 `delay = config.backoffTicks(failureCount)` → 年龄门槛修正（无规则到龄时提前到最近达龄时刻）→ `reschedule(min(delay, lifespan-age-1))`。退避序列见 [scheduling-budget.md](../systems/scheduling-budget.md)。

### 2.5 命中提交（`performConversion`）

| 步骤 | 动作 |
|---|---|
| 接管源 | `held = source.copy()`；`source.setCount(0)`；实体打持久化 `converted` 标签（防重载/区块重进重复提交） |
| 每组成本 | `c = perRoundSourceConsumption(rule)`：显式 `source_cost` 优先，其次隐式 1，再次所有 `consume_source` 的 `count` 之和 |
| 账本 | 写 `SettlementRecord`（含源位置，供重启后返还搜索）到 `SettlementLedger`，`ConversionSettlement` 作为 `EFFECT` 任务立即入队 |
| 清理 | `finally`：解锁、移除追踪（源实体此刻已清空，自然路径随后 `discard`） |

### 2.6 计划 → 派发 → 交付（`ConversionSettlement`）

1. **计划**：`g = min(held / c, 候选最小完整容量)`；候选按组合模式逐个尝试，容量（`spawn_item`/`spawn_entity` 的 `limit`）不足或催化剂不足的候选跳过，选**一个**候选；全部不可用 → 组数 0、整堆返还；任一为一次性类型 → 组数封顶 1。容量/催化剂搜索受预算分片，未完成则本刻不落决定，下刻重试。
2. **派发**：逐组进行——先支付催化剂（`payGroup`，不足则截断组数并释放预留）、再扣固定源成本 `held -= c`、`record.groupStarted(c)` 写盘，开组后按定义序派发候选内效果（每个效果的 `delay_ticks` 经调度器顺延）；每刻派发量受 `dispatch_batch_size` 限制。
3. **效果回执**：执行器经 `EffectResult` / `reportProgress` 回执真实完成量，结算层 `account()` 分 `APPLIED`/`DEFERRED`/`SKIPPED`/`FAILED` 计数，**计划量不冒充成功量**。
4. **交付**：剩余库存 `held` 作为返还物分批加入世界（`ReturnItemSpawner`，位置搜索"起点附近 → 向上扫描 → 限高以上"分步推进）；交付一份才从待交付量里扣除，全部交付后 `record.complete`。

### 2.7 数量账目

```text
N = C + 已交付返还 + 待交付返还     # N 为整堆源初始数，C = 每组成本 × 已开始组数
```

只对**已开始的组**收完整成本；概率落空、效果条件不满足、执行途中部分失败仍属已支付组（不免费重试、不返还）。催化剂单独计数，不进源物品守恒式。

### 2.8 中断与恢复

- **中断**（规则重载 / 维度卸载 / 停服）：结算任务被带原因取消 → `abort` 以当前 `held` 同步待返还、记 `INTERRUPTED`/`FAILED`、释放未支付催化剂预留、写盘，**不做世界写入**。
- **恢复**：重启后首次服务端 tick、或维度加载/重载 `rescan` 时，`SettlementRecovery` 只交付 `SettlementRecord.pendingDelivery`（**不恢复旧效果队列、不重放已开始的组**），区块不可用时带退避重试；轮询游标（`RoundRobinCursors`）随存档持久化，重启后接续。

## 3. 一个可核对的例子

规则：`minecraft:chicken` 存活 10 秒 → 生成 1 个 `minecraft:rotten_flesh`（未声明 `consume_*`，隐式源成本 1，无 `limit`）。

| 阶段 | 事件 |
|---|---|
| 掉落物入世界 | `onItemAdded`：候选命中 → 建追踪；`dueAge = min(10×20, lifespan-1) = 200`，`delay = 200 - age` |
| 到龄 | `attempt` → `select`：年龄门槛通过、条件树恒 MATCH → 命中 |
| 提交 | `c = 1`；`held = source.copy()`、源清空、打 `converted`；写记录、入队结算 |
| 计划 | `g = min(held/1, 容量) = 1`；选唯一候选 `default` |
| 派发 | 扣成本 `held = 0`、`groupStarted(1)`；派发 `spawn_item(count=1)` |
| 交付 | `held = 0` → 无返还；`record.complete`；账目 `N = 1 = C(1) + 0 + 0` |

（这正是 `/idtw debug run convert` 的场景，见 [debug-scenario-flow.md](debug-scenario-flow.md)。）

## 4. 相关

- 运行时总控、类清单与结算契约：[../modules/conversion-runtime.md](../modules/conversion-runtime.md)
- 调度与退避、共享预算：[../systems/scheduling-budget.md](../systems/scheduling-budget.md)
- 条件与效果类型：[../modules/type-system.md](../modules/type-system.md)
- 平台触发入口与 Mixin：[../modules/platform.md](../modules/platform.md)
