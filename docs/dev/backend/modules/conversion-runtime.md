# 功能模块：转化运行时（`core/runtime`）

> 事实来源：`core/runtime/`（22 类）+ `core/state/`（3 类）；调度契约 `core/api/scheduler/`（11 类）与实现 `core/runtime/scheduler/`（8 类）见 [scheduling-budget.md](../systems/scheduling-budget.md)，本文不复述其类清单。
> 追踪、触发、条件求值、候选选择、完整组结算与返还、效果派发的引擎；**世界操作只在服务端主线程执行**。
> 决策：[ADR-0023](../../../adr/0023-triggers-fixed-cost-and-complete-group-settlement.md)（完整组结算）、[ADR-0024](../../../adr/0024-shared-server-tick-budget-scheduler.md)（共享预算调度）。完整组结算、固定成本与候选择一的契约在 `ConversionSettlement` 与 `CatalystReservations` 的类注释中就地记录。

## 1. 类清单

| 类 | 职责 | 关键成员 |
|---|---|---|
| `ConversionRuntime` | 总控：规则索引 + 维度级追踪 + 触发请求 + 排期/退避 + 结算派发 | `onItemAdded`、`requestEnvironmentalConversion`、`requestConversion`、`onServerTick`、`attempt`、`select`、`performConversion`、`deferNaturalExpiry`、`replaceRules`、`applySavedIndex`、`rescan`、`clear`、`shutdown`、`schedulerStats`、`budgetSnapshot` |
| `ConversionSettlement` | 一次转化的结算任务（计划 → 派发 → 交付），固定成本、完整组、真实账目 | `step`、`plan`、`dispatch`、`deliver`、`abort`、`account`、`capacityGroups` |
| `CatalystAllocation` | 催化剂共享库存分配 | 按逐引用需求求完整轮次；图构建与搜索可按 tick 预算续跑，输出逐轮支付清单；效果式消耗复用同一有预算分配算法 |
| `CatalystPaymentExecution` | `consume_catalyst` 效果的跨 tick 付款状态 | 库存采集、共享库存分配、付款清单整理与支付前逐项计费均可续接；扣减前重读完整清单并确认实体仍存活、物品类型、数量和半径，整轮校验后同一主线程步骤内一次扣减 |
| `EffectTask` | 单效果的预算任务 | 条件与概率仅在任务首次开始时判定，或按成功转化当刻固定的槽位输入跳过判定；`EffectExecution.advance` 返回 null 表示续接；结束与失败先置终态再各通知账目与执行状态一次，取消先置终态但只通知执行状态、不产生回执也不入账；取消丢弃未完成效果，不重放世界操作 |
| `CatalystReservations` | 运行期催化剂预留表（按维度 + 任务），预留 → 支付三态，不落盘 | `available`、`tryReserve`、`payGroup`、`releaseOwner`、`clear` |
| `SettlementLedger` | 主世界 `SavedData` 结算账本（进行中 + 已完成记录，带上限裁剪）；提交只维护双索引并标脏，不即时刷盘 | `get`、`find`、`unsettled`、`pendingDeliveryTotal`、`prune` |
| `SettlementRecord` | 单次结算记录：计划量、已开始组、真实完成量、返还交付进度；可持久化 | `needsRecovery`、`save`/`load`；账目变更方法私有，写入见 `SettlementRecord.Writer` |
| `SettlementRecord.Writer` | 账目变更的唯一受控入口：**每个变更方法在同一次调用内完成字段变更与账本双索引维护、标脏**；另有首次登记与失败撤销 | `register`、`of`、`record`、`discard`、`plan`、`markStarted`、`groupStarted`、`catalystPlan`、`catalystGroupPaid`、`addApplied`、`addPending`、`addProgress`、`effectSkipped`、`effectFailed`、`delivered`、`syncPendingDelivery`、`complete`、`interrupt`、`fail` |
| `SettlementRecovery` | 重启 / 维度加载后的待返还交付任务（`REBATE`） | `step`、`onCancelled`、`onFailed`、`finish` |
| `RoundRobinCursors` | 主世界 `SavedData` 轮询游标（规则 id + 维度，持久化） | `get`、`cursor`、`moveTo` |
| `CandidateStructureVersions` | 按规则实例缓存候选结构摘要（供轮询游标判定结构是否变化） | `versionOf(rule, registries)`、`clear()` |
| `RuleIndex` | "物品 id → 候选规则"查询结构：直接物品建索引、标签懒展开 | `build`、`candidates`、`ordered` |
| `CatalystThresholdProjection` | 门槛运行投影：把 `catalyst_present` **留空**的门槛解析为当前消耗配置下的有效值，在 `RuleIndex.build` 内执行 | `project`、`resolveThreshold` |
| `ExpressionEvaluator` | 条件求值入口（`matches` = `evaluate` == MATCH） | `matches`、`evaluate` |
| `ExpressionTreeEvaluator` | 条件树递归求值，四态（MATCH / NO_MATCH / UNAVAILABLE / ERROR） | `evaluate`、`evaluateNode` |
| `RuntimeConditionContext` | `ConditionContext` 的服务端 record 实现（纯参数聚合） | 注入按维度复用的 `RuntimeTagLookup` / `RuntimeClimateSampler` |
| `RuntimeEffectContext` | `EffectContext` 实现：**按转化组**构造，持有源快照、成功转化当刻固定的触发点、组信息与调度器 | `schedule`、`runWhenLoaded`、`reportProgress` |
| `RuntimeTagLookup` | `TagLookup` 的服务端实现：按 `kind:tagId` 缓存（不存在缓存空集） | `blockInTag` … `mobEffectInTag`、`members` |
| `RuntimeClimateSampler` | 气候采样，按 quart 坐标缓存，上限 4096、超限淘汰最旧 | `sample` |
| `LoadedChunks` | 运行时区块门面：转调契约层 `ChunkPresence`（**禁止为执行规则生成区块**） | `contains`、`containsArea` |
| `LifespanProvider` | 平台差异收敛点：掉落物自然寿命刻数 | `lifespanTicks(level, entity)`、`vanillaDefault()`（6000） |
| `PlayerDeathDrops` | 玩家死亡掉落排除策略（入世界前打 `check_lock`） | `mark(entity, item)` |
| `DropState` | 掉落物实体层状态：临时保护/冷却、永久保护/禁转，绝对值游戏刻计时 | `isProtected`、`cooldownRemaining`、`blocksConversion`、`withTemporary`、`withPermanentReturn`、`permanentFlagsMatch`、`unionTemporary` |
| `DropStateStore` | 实体状态读写门面（经 `IPlatformHelper`），保护/冷却时长注入 | `configure`、`get`/`set`/`clear`、`blocksEnvironmentalDamage`、`grantNewProduct`、`grantPermanentReturn` |
| `DamageClassification` | 环境销毁的伤害归因常量表（火 / 岩浆 / 仙人掌），common 内唯一来源 | `classify`、`isEnvironmental` |

`ConversionRuntime` 每维度持有一个 `LevelState { tracked, tags, climate }`；追踪状态按 `ResourceKey<Level>` 索引，**任务本体归公共调度器所有**，`LevelState` 不持有维度对象，维度卸载即整体释放。`core/state` 是与 `core/runtime` 并列的包：状态数据在 common，持久化落到平台实现。

## 2. 追踪与触发契约

### 2.1 进入追踪与排除

`onItemAdded` 快速短路（索引空且无活动调试场景）后按 `excluded()` 排除：`isRemoved` / 空物品 / `age == -32768`（原版无限寿命）/ 带 `itemdespawntowhat:check_lock`（死亡掉落锁）/ 带 `itemdespawntowhat:converted`（已提交转化）任一即排除。`index.candidates(itemId)` 为空则不追踪（**没有候选规则就不追踪**）。已 tracked 的 UUID 直接返回（`ENTITY_JOIN` 在区块重追踪时会重复触发）。

> 永久禁转（返还物）**不在** `excluded()`；它在 `attempt` 中经 `DropState.permanentConversionBan` 判定，命中即移除追踪（见 §5）。

### 2.2 首次排期

```text
lifespan        = max(1, lifespanProvider.lifespanTicks(level, entity))
earliestTrigger = clamp(min over 候选规则(rule.triggerAfterSeconds × 20), 0, ∞)
dueAge          = min(earliestTrigger, lifespan - 1)
delay           = max(0, dueAge - entity.getAge())
```

即**触发时刻 = min(候选规则秒数×20, lifespan−1) − 当前年龄**，以 `CONDITION_CHECK` 任务入公共调度器。

### 2.3 触发路径（同一请求路径）

| 消失方式 | 平台入口 | 运行时动作 |
|---|---|---|
| 自然消失 | 原版 discard 前拦截 → `deferNaturalExpiry` | 置 `expiryPending`、以 `NATURAL_EXPIRY_DEFERRED` 取消旧任务、以 `delay=0` 排一次 `attempt(kind=NATURAL)`；返回 `true` 表示已接管本次 discard |
| 火 / 岩浆 / 仙人掌 | 真实致死分支 → `requestEnvironmentalConversion` | `DamageClassification.classify` 归类后调 `requestConversion(kind)`；`kind` 不属于三类则忽略 |

两条路径最终都进入同一个 `attempt` / `select`，共用同一份追踪状态与公共预算：自然路径经 `deferNaturalExpiry` → `scheduleCheck` 排队，环境路径经 `requestEnvironmentalConversion` → `requestConversion` 同步判定。`requestConversion`：`item`/`kind` 为空或 `excluded` → 返回；未 tracked → 先 `onItemAdded` 补齐；`locked` → 忽略（同一死亡多份伤害调用的去重）；否则 `attempt`。环境致死请求在 `hurt` 调用栈内**同步**执行「条件检查 + 规则选择」（单实体一次、量小），效果一律经调度器排队在共享预算内执行。规则未声明该消失方式时不参与（`rule.effectiveTriggers()`，未声明按 `natural`）。

### 2.4 到期判定（`attempt` → `select`）

`attempt` 依次：实体失效/被排除 → 移除追踪；区块实体 ticking 门禁不满足 → 退避 `checkIntervalTicks`；`locked` → 退避 1 tick；**永久禁转** → 移除追踪；**转化冷却未到期** → 退避剩余冷却；候选为空 → 移除；否则 `select`。

`select` 遍历已排序候选，跳过未声明 `kind` 的规则；**年龄门槛只对 `natural` 且非 expiryPending 生效**（环境致死在当前刻立即判定，与存活时长无关）；`ExpressionEvaluator.matches` 命中即取该条。**同一掉落物只执行优先级最高的一条命中规则**，候选排序（`RuleIndex`）：`优先级 desc → 条件叶数 desc → 定义序`。候选规则在进入索引前已做过催化剂门槛投影（留空门槛解析成当前消耗配置下的有效值），这里读到的已是投影后的条件树，见 [catalyst-threshold-projection.md](../systems/catalyst-threshold-projection.md)。

### 2.5 未命中退避

`failureCount++` → 若 `expiryPending` 或已过寿命则移除追踪（`expiryPending` 时 `discard`）；否则 `delay = config.backoffTicks(failureCount)`，再做年龄门槛修正（当前无一规则到龄则 `failureCount--` 并把 delay 提前到最近达龄时刻；否则 `delay = min(delay, nextAge)`），最终 `reschedule` 与剩余寿命取 min。退避算法细节见 [scheduling-budget.md](../systems/scheduling-budget.md)、[config.md](../systems/config.md)。

## 3. 完整组结算与数量账目（`ConversionSettlement`）

命中后 `performConversion` 把**整堆源物品转入结算库存**：`held = source.copy()`、`source.setCount(0)`、给实体打持久化 `converted` 标签，写一条 `SettlementRecord` 到账本，并把 `ConversionSettlement` 作为 `EFFECT` 任务立即入队。**「扣减 → 登记 → 调度注册」是一个补偿单元**：调度注册抛异常时整堆库存写回源实体、撤销 `converted` 标签与账本登记，只有全部成功才把所有权交给结算任务；结算任务得到所有权后，中断与失败都不再把库存退回世界。结算任务三阶段：计划 → 派发 → 交付。

- **每组固定源成本** `c = perRoundSourceConsumption(rule, sourceStack)`（`BuiltinTypeRegistries`）四种情形：显式 `source_cost` → 按当前源堆叠取直接物品，否则取 `source.items` 中首个命中的标签，缺省 1；未声明 `source_cost` 且未声明任何 `consume_*` → 隐式 1；未声明 `source_cost` 但声明了 `consume_source` → 各 `consume_source` 对当前源物品数量之和（`counts` 优先、缺省效果 `count`）；只声明其它消耗效果（如 `consume_fluid`）→ 0，表示不按堆叠轮次展开。`source_cost` 与 `consume_source` 由 `RuleValidation` 拒绝同时声明。
- **计划组数** `planned = min(held / c, 催化剂可用组数上界)`（`c ≤ 0` 时按 1 组；未声明 `catalyst_cost` 时上界不设限），**候选容量不参与组数上界**——容量只在开组前复核，不足即截断已开始组数并把剩余源交还（见本节「每组准入」）。统一 `spawn_entity` 的物品与通用实体子类使用服务端 `nearby_products` 阈值，相同 TaggedId 的多个动作合并本组需求；经验子类不参与该阈值，经验按本组实际源成本以 `long` 汇总总点数后，按单球点数上限（32767）装满、每批最多 16 球、批次之间至少隔 1 个 tick 续接交付。仅含一次性动作的候选对同一源封顶一组，混合候选仍可多组。
- **每组准入**：未开组再次检查真实邻域容量（物品与通用实体；经验不参与），不足时不支付该组成本并返还剩余源。**经验不参与并不意味着整组一定开得出**：同一候选或同一组里还有物品或通用实体产出时，它们仍然受各自容量约束，容量不足同样会阻塞或返还。已开始组不再检查邻近阈值，执行器继续分批完成，允许短暂超额。配置阈值不是硬上限，不清理已生成实体；不同 tag 的重叠集合没有统一硬预留保证，预算/区块/异常中断保持原契约。
- **同刻容量复用**：每次容量测量记录候选维度，**同一 tick 内同一候选只查一次世界并复用结果**；每步开始时清空复用表（每步归零），避免跨刻沿用旧容量把 room 算大。
- **候选选择**：每个转化组只选**一个**候选结果。`ROUND_ROBIN`（默认）从游标处依次尝试，`PRIORITY` 取第一个可用候选；容量不足或催化剂不足的候选跳过，**全部候选都无法完成一组时整堆返还、不支付任何成本**。不能把组合模式实现成"全部结果执行、只调先后顺序"。
- **催化剂固定成本**（`rule.catalystCost()`）：一组结算需分别支付 `items` 各引用的 `counts` 数量，缺省采用 `count`。`CatalystAllocation` 用共享库存分配完整轮次，重叠标签不会重复使用库存；图构建与增广搜索按预算跨 tick 续跑。计划期登记逐轮支付清单并预留（`CatalystReservations.tryReserve`，全有或全无、绝不半预留），**开组时先支付再开组**（`payGroup`：PAID / RETRY / SHORT）；不足则截断组数、释放预留，剩余源走返还（**绝不"少扣照常产出"**）。支付计费进度可跨 tick 保留，真正扣除前重新核对全部实体、物品类型及预留数量。催化剂账目使用本组各引用数量之和，不进源物品守恒式。
- **数量账目守恒**：设整堆源初始 `N`，每组源成本 `c`，实际开始组数 `g`，则

  ```text
  C = c × g                         # 已消费源（只对已开始的组收费）
  N = C + 已交付返还 + 待交付返还    # 结算守恒
  ```

  概率落空、效果条件不满足、执行途中部分失败都**仍属已支付组**（不免费重试、不返还成本）；真实完成量只来自执行器回执（`EffectResult.appliedUnits` + `reportProgress`），**计划量不冒充成功量**。不足一组不强制产出。

  账目变更只能经受控写入句柄：`SettlementRecord` 的变更方法私有，写入统一经 `SettlementRecord.Writer`，
  句柄的每个变更方法都在同一次调用内完成字段变更与账本双索引维护、标脏（提交只标脏不即时刷盘），
  调用方不存在"改了字段却忘记落账"的路径；结算任务的执行器进度回执也走句柄（`addProgress` 自己落账，且只改
  `applied_units`/`pending_units`，绝不触碰待交付量）。结算任务的正常完成、显式中止、预算耗尽提前让出与为交付而让出
  都经任务内同一状态出口；该出口的固定职责是**按当前库存所有权决定是否把待交付数量对齐到手里的结算库存**，
  而不是唯一的标脏点。首次登记、登记失败补偿、恢复交付与终态各自经句柄的语义入口落账。
  记录里用显式的**库存所有权标记**表示 `held` 是否仍归本次结算：持有期间由任务出口把待交付数量对齐到手里库存；所有权移交后该出口不再对齐，也不覆盖恢复任务推进的交付进度，终态由相应的受控写入入口自行落账。

  物品与标签引用只有**唯一匹配实现**（`core/type/TaggedItemMatch.java`）：效果式消耗、催化剂固定成本与催化剂条件求值共用同一份匹配语义，调用方不得复刻。

- **一次性效果**（`EffectType.oneShot()`，如闪电/爆炸/箭雨/天气）不参与容量；仅含一次性效果的候选对同一源最多一组，其余含可重复产出者可多组但其中一次性效果仍只尝试一次。
- **轮询游标**按 `规则 id + 维度` 共享并持久化（`RoundRobinCursors`）；候选结构版本（候选 id、开关、效果顺序与参数的 SHA-256 摘要）变化时游标归零，避免旧位置指向新结构。摘要按规则实例缓存，规则索引重建、保存后索引发布与停服时清空。

## 4. 持久化与恢复

- 数量账目在 `SettlementRecord` 中以 `long` 保存（`applied_units` / `pending_units`），`SettlementLedger.pendingUnitsTotal()` 亦为 `long`，累加经 `Math.addExact` 防溢出。存档写 `tag.putLong(...)`，读 `tag.getLong(...)`：`CompoundTag.getLong` 接受数值型标签，旧存档里的 `int` 字段可原样读出，无需迁移。
- `SettlementLedger` 存于**主世界** `SavedData`（`itemdespawntowhat_settlements`），维度卸载/停服都不丢记录；已完成且交付完毕的旧记录按 tick 过期（保留 6000 tick）并按条数上限兜底裁剪，**未结清记录一条不裁**。
- 中断（`onCancelled`）与异常（`step` 的 catch）走同一条 `abort` 顺序：置 `cancelled`、清除库存所有权标记（仍持有时才同步待返还数量）、记 `INTERRUPTED`/`FAILED`、释放未支付催化剂预留，最后由任务状态出口落账。**本路径不做任何世界写入**（维度卸载/停服时加入世界不安全）。
- 恢复：`onServerTick` 首次服务端 tick 后全局扫描一次，`rescan`（维度加载/重载）补扫该维度；未加载维度的记录保持在账本等待。`SettlementRecovery` 只交付记录里的 `pendingDelivery`，**不恢复旧效果队列、不重放已开始的组**；`needsRecovery()` 只看待返还库存（`pendingDelivery > 0 && !source.isEmpty()`），绝不把"已派发未回执"的 `pendingUnits` 当成待返还库存。`activeRecoveries` 幂等位保证同一记录同一时刻只允许一个交付任务。
- `SettlementRecovery` 失败与取消共用幂等清理：不补做世界写入，保留待返还数据并释放预留；`finally` 释放 `activeRecoveries`，即使收尾写盘失败也允许后续扫描重新排期。
- 保存重载通过 `applySavedIndex` 发布预建索引，立即摘除旧队列并逐项取消，再用账本 id 游标逐条恢复返还、用已加载掉落物 id 游标跨 tick 回扫。掉落物加入/移除事件维护该有序集合；临近自然消失的物品在等待回扫期间及时按新索引补追踪。

## 5. 实体状态（`core/state`）

| 对象 | 环境保护 | 转化状态 |
|---|---|---|
| 新产物掉落物 | 默认 2 秒（`new_product_protection_seconds`），仅防火 / 岩浆 / 仙人掌伤害 | 默认 5 秒转化冷却（`conversion_cooldown_seconds`） |
| 未消耗源物品的返还掉落物 | 本实体生命周期内永久防上述三种伤害 | 本实体生命周期内永久禁转 |

状态只挂在实体上、不进 `ItemStack`：拾取后实体消亡，重丢得到的是普通掉落物。计时用绝对值服务端游戏刻（`ServerLevel#getGameTime()`）：卸载不暂停、重启后继续。时长由 `DropStateStore.configure` 在平台引导时注入，`0` 表示不授予。保护仅豁免三类环境伤害（`DropStateStore.blocksEnvironmentalDamage` 经 `DamageClassification` 判定，先于原版扣血）；合并时永久标志不一致禁止合并，临时状态取较晚到期刻。授予入口唯一：`ReturnItemSpawner.applyGrant`（转化产物 → `grantNewProduct`，返还物 → `grantPermanentReturn`）。

## 6. 效果回执与上下文

- `RuntimeEffectContext` **按转化组**构造（每组一个实例）：`rounds()` 恒为 1（一轮 = 一组），`coveredSourceItems()` 取本组固定源成本（`source_cost ≤ 0` 时按 1，`groupSourceCost` 为同一原值），`outcomeId` / `groupIndex` / `groupCount` 描述本组；`reportProgress(units)` 把"已受理"收敛为"已完成"；`safeSpawn()` / `fillOrigin()` / `positionSearchChecksPerTick()` 是候选级位置策略。
- `schedule(delay, task)` 经公共调度器以 `EFFECT` 任务登记，经 `runWhenLoaded` 在目标区块未加载时 `schedule(20, task)` 重试，**不主动加载区块**。
- 成功转化当刻（源实体清空前、同一补偿单元内）先固定触发点坐标，并为「需要先固定输入」的效果类型逐组生成 `EffectPreparation`（键 = 候选 id + 槽序号 + 组序号；潜在组数按 `source_cost > 0 ? available / source_cost : 1` 取上界，未启动的组只是多算准备，不选候选、不付催化剂、不派发）：效果级 `conditions` 与 `chance` 按组各判定一次并随结算任务携带，源实体清空后不再重新判定，也不跨组复用同一随机判定；一次性效果只尝试一组，固定一份即可。需要随机抽取的类型引用（标签成员、药水效果）同样在此抽定并随类型自有的准备记录携带（记录字段只读；负载元素不保证不可变）。未产出准备的类型不在成功转化当刻固定。
- `EffectTask` 在单效果开始时按已固定输入执行（跳过则直接给出 `SKIPPED`），未固定的类型就地判效果级 `conditions` 和 `chance`，只判一次；类型未注册 → `FAILED`。同步 `EffectExecutor` 经默认 `start` 保持兼容；长计算覆盖 `start` 返回按 `ServerTickBudget` 续接的 `EffectExecution`；结束与失败先置终态并摘掉执行状态引用，再各通知账目与 `EffectExecution.onFinished` 一次；取消同样先置终态并摘引用，但只通知 `EffectExecution.onCancelled`，不产生回执也不入账。预算耗尽只让出任务，不生成业务失败或材料不足回执。最终 `EffectResult` 由结算层 `account()` 分类计数：`APPLIED` → `addApplied`，`DEFERRED` → `addApplied` + `addPending`，`SKIPPED` → `effectSkipped`，`FAILED` → `effectFailed`。

## 7. 扩展点

催化剂付款仍采用软预算：邻域查询、排序、数组准备及分配算法内部的单次整理操作不可抢占；最终清单复核与整轮扣减也不能跨 tick 拆开。预算续接限制连续搜索与清单整理的工作量，不保证每个原子步骤都小于时间预算。`consume_catalyst` 的直接同步执行入口返回 `budget_required` 失败回执，调用方应使用 `start` 并在公共调度器内推进执行状态。

- **改排期/退避语义**：改 `ConversionRuntime.scheduleInitial` / `attempt` / `reschedule` 与 `ServerConfig.backoffTicks`。
- **改规则选择排序**：改 `RuleIndex.RULE_ORDER` 比较键。
- **改条件求值**：改 `ExpressionTreeEvaluator`（保持四态；`inverted` 只互换 MATCH/NO_MATCH）。
- **改候选组合/容量语义**：改 `ConversionSettlement.plan` / `capacityGroups` / `dispatch`。
- **改数量账目或恢复**：改 `SettlementRecord` 字段与 `SettlementLedger` / `SettlementRecovery`。
- **改实体状态策略**：改 `DropState` / `DropStateStore`（时长字段见 [config.md](../systems/config.md)）；平台存取落在 `IPlatformHelper`。
- **新增上下文能力**：改 `core/api/EffectContext` / `ConditionContext` 与两个 `Runtime*Context` 实现。

## 8. 相关

- 端到端链路：[../flows/conversion-lifecycle.md](../flows/conversion-lifecycle.md)
- 调度预算与缓存：[../systems/scheduling-budget.md](../systems/scheduling-budget.md)、[../systems/caching-indexing.md](../systems/caching-indexing.md)
- 类型与消耗语义：[type-system.md](type-system.md)、[rule-model.md](rule-model.md)
- 平台触发入口与 Mixin：[platform.md](platform.md)
- 配置字段：[../systems/config.md](../systems/config.md)
- 决策：[ADR-0023](../../../adr/0023-triggers-fixed-cost-and-complete-group-settlement.md)、[ADR-0024](../../../adr/0024-shared-server-tick-budget-scheduler.md)
