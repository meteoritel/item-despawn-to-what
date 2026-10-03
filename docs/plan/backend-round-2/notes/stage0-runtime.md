# 阶段 0-B 调研笔记：性能基线工具与调度现状核实

- 任务：task-2（owner: runtime-scheduler），对应 `docs/plan/backend-round-2/PLAN.md` 阶段 0（L238-244）与阶段 2（L254-260）。
- 证据方式：**纯静态代码阅读**（read/grep/glob）。本阶段不修改任何 Java/JSON/构建文件，不运行 `gradlew` / `tools/dsh-build.ps1`，不启动游戏，故**本文不含任何实测性能数字**。
- 引用格式：`文件相对路径:行号`（行号以当前工作区 `1.21.1` 分支代码为准）。
- 环境事实（来自 `docs/plan/backend-round-2/PROGRESS.md:52,60`，非本人核实）：`PROGRESS.md` 已记录「到期搬运在 while 循环内做，搬运本身不完全计入预算（阶段 2 需修正）」；IDEA MCP 本会话 schema session 失效，本阶段未使用 IDE 检查。
- 名义工作目录：`D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult`。核心源码前缀 `common/src/main/java/com/meteorite/itemdespawntowhat/`。

---

## 1. TickScheduler 与 ConversionRuntime.LevelState 双队列的实际行为

### 1.1 结构现状（证据）

| 事实 | 证据 |
| --- | --- |
| 调度器是 `public final class TickScheduler`，只持有 `Runnable`，不引用 `ItemEntity` 或任何具体效果 | `common/.../core/runtime/TickScheduler.java:10,46-55` |
| 内部结构：`TreeMap<Long, ArrayDeque<Task>> buckets`（到期桶）+ `ArrayDeque<ArrayDeque<Task>> ready`（就绪队列） | `core/runtime/TickScheduler.java:13-14` |
| 时间预算为**类常量**：`public static final long SOFT_BUDGET_NANOS = 2_000_000L;` | `core/runtime/TickScheduler.java:11` |
| 工作量上限为**构造参数**，来自 `ServerConfig.maxChecksPerTick`（默认 512） | `core/runtime/TickScheduler.java:15,42-44`；`core/config/ServerConfig.java:27,38,58`；`neoforge/.../runtime/RuleRuntimeHost.java:296-299` |
| 每个维度持有**两条**独立 `TickScheduler`（检查 `scheduler` + 效果 `effects`），各自独立预算 | `core/runtime/ConversionRuntime.java:54-65`（`LevelState(int maxChecksPerTick)` 内 `new TickScheduler(...)` 两次） |
| 每维度每 tick 调两次 `runDue`（顺序执行，互不共享剩余额度） | `core/runtime/ConversionRuntime.java:139-144`（`onLevelTick`：`state.scheduler.runDue(now); state.effects.runDue(now);`） |
| 平台接线：NeoForge `LevelTickEvent.Post` / Fabric `END_WORLD_TICK` 每维度一次推进 | `neoforge/.../runtime/RuleRuntimeEvents.java:92-96`、`fabric/.../runtime/RuleRuntimeEvents.java:63` → `RuleRuntimeHost.tickLevel`（neoforge:268-273 / fabric:259） |

由此得到：**每个服务端 tick 上独立创建的时间预算窗口数 = 2 × 已加载维度数**，顺序串行，无全局上限（理论上限 = 2 × 维度数 × 2 ms）。

### 1.2 预算如何分配（`runDue` 逐句）

`core/runtime/TickScheduler.java:57-80`：

```
public int runDue(long now):
  int visited = 0; lastTick = now;
  long started = System.nanoTime();
  long deadline = started + SOFT_BUDGET_NANOS;        // 2 ms，硬编码
  while (visited < maxTasksPerTick && System.nanoTime() < deadline) {
      while ((entry = buckets.firstEntry()) != null && entry.getKey() <= now) {
          ready.add(buckets.pollFirstEntry().getValue());   // ← 到期搬运
      }
      if (ready.isEmpty()) break;
      ArrayDeque<Task> queue = ready.peek();
      Task task = queue.remove();
      if (queue.isEmpty()) ready.remove();
      visited++;
      try { task.run(); } catch (VirtualMachineError fatal) { throw fatal; }
      catch (Throwable failure) { LOGGER.error("延迟任务执行失败", failure); }
  }
  lastMicros = (System.nanoTime() - started) / 1000;   // 微秒
  maxMicros = Math.max(maxMicros, lastMicros);
  totalVisited += visited;
  return visited;
```

- 两个闸门同时生效：工作量 `visited < maxTasksPerTick` 与时间 `System.nanoTime() < deadline`（与 `docs/dev/backend/systems/scheduling-budget.md` §3 描述一致）。
- 预算是**每个维度、每类队列各自**的，不是服务器级共享；该结论同时由文档（`docs/dev/backend/systems/scheduling-budget.md` §2「预算是每个维度、每类队列各自的，不是全局」）与代码结构确认。
- 软预算粒度只能是**任务之间**：单次原版世界操作不可抢占（`core/runtime/TickScheduler.java:23` 注释；文档同节）。

### 1.3 到期搬运是否受预算约束 → **否（这是最关键的偏差）**

- `ready` 的填充发生在**内层 `while`**（`TickScheduler.java:59-61`），该循环内部**没有任何 `nanoTime()` 或工作量检查**；外层条件在搬运之前求值，首次迭代必然进入（`deadline` 刚计算完，必然未过期）。
- 因此一次 `runDue` 可以先以 O(到期桶总数) 的代价把**全部**到期任务从 `buckets` 搬进 `ready`（单次 `pollFirstEntry` 是 O(log n)，搬 N 个即 O(N log N)），之后才开始计时与限量执行。
- 搬运的代价**不计入 `visited`、也不计入 `lastMicros` 本身**（`started` 在搬运前已取，所以搬运时间其实**被算进** `lastMicros`，但**不受** `deadline` 约束——即「先超支、后记账」）。这正是 `PROGRESS.md:52` 所指的「搬运本身不完全计入预算」。
- 结论：同龄大量掉落物同 tick 到期时，存在**预算外的搬运工作量**，与 ADR-0002「到期搬运和位置搜索同样受预算约束」直接冲突。

### 1.4 延后、丢弃与丢任务语义矩阵（证据）

| 场景 | 实际行为 | 证据 |
| --- | --- | --- |
| 预算/工作量耗尽，`ready` 仍有任务 | 任务保留在 `ready` 中，下个 tick 继续执行（队列游标不复制） | `core/runtime/TickScheduler.java:9`（类注释）、`:57-80` |
| 时间预算耗尽 | 剩余到期桶留在 `buckets`，下一个 tick 的 `runDue(now')` 用新的 `now` 补执行（跳过的游戏刻也能补） | `TickScheduler.java:9,59` |
| 桶已过期但未到 ready | `entry.getKey() <= now` 才搬运；延后不丢弃 | `TickScheduler.java:59` |
| 维度卸载（`clear(level)`） | `levels.remove` + `scheduler.clear` + `effects.clear` + `tracked.clear` → **队列内任务全部丢弃，无持久化** | `core/runtime/ConversionRuntime.java:165-172`；`TickScheduler.clear():91-96` |
| 停服（`shutdown()`） | 同上三清 → 丢弃 | `core/runtime/ConversionRuntime.java:175-183` |
| 规则重载（`replaceRules`）/ `rescan` | `tracked.clear()` + `scheduler.clear()`；**未清 `effects`** → 效果队列中的旧规则任务可能继续执行 | `ConversionRuntime.java:94-106`、`:147-162` |
| 单个物品移出世界（`onItemRemoved`） | `tracked.remove` + `task.cancel()`（只取消检查任务句柄，`effects` 中的已排效果不可取消） | `ConversionRuntime.java:465-471` |
| 任务异常 | 记录日志并继续（`VirtualMachineError` 重抛），`visited` 已计数 | `TickScheduler.java:72-77` |
| `Task.cancel()` | `action=null; pending--`；任务仍留在队列里占位，取出后 `run()` 空转 | `TickScheduler.java:27-40` |
| 可观测统计 | `Stats(pending, peakPending, totalVisited, lastMicros, maxMicros, oldestDelay)`；`oldestDelay` 只统计 `ready` 头部任务（`pending==0 || first==null` 时为 0），**且只在 `runDue` 末尾按该队列的 `lastTick` 计算** | `TickScheduler.java:24,84-89` |

**延后是否丢任务**：跨 tick 延后本身不丢（只要维度未卸载/未停服/未重载规则）；但以下三种情况会丢：维度卸载、停服、规则重载（且重载时 `effects` 队列未清理，属于**不一致**）。

### 1.5 与 ADR-0002 的差距清单

ADR-0002 = `docs/plan/backend-round-2/docs/adr/0002-shared-server-budget.md`。

| # | ADR-0002 要求 | 代码现状 | 差距 | 证据 | 阶段 2 处置 |
| --- | --- | --- | --- | --- | --- |
| R1 | 公共任务调度器（common，不绑定掉落物业务） | `TickScheduler` 在 `core/runtime`，仅持有 `Runnable`，无 `ItemEntity` 依赖 | **部分满足**：位置对，但接口过弱（无维度/种类/可续接/取消通知） | `core/runtime/TickScheduler.java:10,27-55` | 保留类名与包位，接口升级为 `ServerTask`（见 §5.3） |
| R2 | 服务器每 tick 一个**总**预算 | 每 `LevelState` 两条队列，各自 `runDue` 各用 2 ms | **直接冲突**：预算单元是「维度×队列」而非「服务器×tick」 | `ConversionRuntime.java:54-65,139-144`；`TickScheduler.java:11` | 新增 `ServerScheduler.tick(...)` 作为唯一预算入口 |
| R3 | 各维度检查/效果/返还公平轮转、共享剩余额度 | 各维度队列独立，无跨维度轮转与共享 | **缺** | `TickScheduler.java:13-14,57-80` | `RealmQueues` + 跨 realm 往返游标 |
| R4 | 到期搬运受预算约束 | 内层 drain 无 `deadline`/工作量检查，可先搬空全部到期桶 | **冲突** | `TickScheduler.java:59-61` | `TaskDrainer` 分片，每步计入工作量与时间 |
| R5 | 位置搜索受预算约束 | `candidatesFor` / `select` 在 `attempt`（单个 `Runnable`）内执行，不可中途让出 | **部分冲突** | `ConversionRuntime.java:292-317` | 拆成 `CONDITION_CHECK` 与 `WORLD_SEARCH` 两类步骤 |
| R6 | 任务保留可续接进度 | 只有 `Runnable` + 重排；`attempt` 无中间态 | **缺** | `TickScheduler.java:27-40`；`ConversionRuntime.java:270-320` | `ServerTask.step(budget)` 返回 `YIELD` + `nextDelayTicks`，进度存在任务对象 |
| R7 | 跨 tick 延后 + 可配置检查平滑窗口 | 已有跨 tick 延后；无平滑窗口（同一 `dueTick` 桶内 FIFO 全量处理） | **缺平滑窗口** | `TickScheduler.java:57-80`；`ConversionRuntime.java:239-252` | `BudgetConfig.checkSpreadWindowTicks` + 到期搬运切片 |
| R8 | 放弃严格到期精度（换取工作量分散） | 事实上已经是「软到点」（超预算即延后），但**无量化指标** | **缺观测** | `DebugPerformanceWindow.java:61`（仅 max，且只覆盖 ready）；`TickScheduler.java:84-89` | 新增延迟分布与积压清空时间 |
| R9 | 世界操作保持服务端线程，不用并行写入/硬中断 | 满足：全部任务在维度 tick 内串行执行 | **无差距** | `ConversionRuntime.java:139-144`；两端 `RuleRuntimeEvents` | 保持 |
| R10 | 默认预算与窗口依据阶段基线与对照测量确定 | 预算为硬编码常量；`ServerConfig` 无任何预算/窗口字段；阶段 0 无实测数据 | **缺配置面 + 缺基线** | `TickScheduler.java:11`；`ServerConfig.java:24-31,53-66` | 阶段 0 给候选值（§4.2），阶段 2 落入 config，实机标定 |

---

## 2. 性能观察入口现状

### 2.1 现有可测指标（字段级证据）

| 指标 | 输出字段 | 证据 |
| --- | --- | --- |
| 服务端整 tick 耗时（微秒）分布 | `server_tick_cost.{samples,mean_us,max_us,p50_us,p95_us,p99_us}` | `core/debug/DebugPerformanceWindow.java:79-80,107`、`core/debug/DebugMeasurements.java:29-40` |
| 检查队列每次 `runDue` 耗时分布 | `checks.{...}`（同上 7 个字段） | `DebugPerformanceWindow.java:46,84,108`；`core/runtime/ConversionRuntime.java:222-226` |
| 效果队列每次 `runDue` 耗时分布 | `effects.{...}` | `DebugPerformanceWindow.java:46,85,109` |
| 窗口内任务访问次数 | `checks.visited_in_window` / `effects.visited_in_window` | `DebugPerformanceWindow.java:43-45,56` |
| 计数器重置次数 | `counter_resets` | `DebugPerformanceWindow.java:43,57` |
| 队列积压 | `mean_pending_at_tick_end`、`max_pending_at_tick_end`、`final_pending` | `DebugPerformanceWindow.java:58-60` |
| 就绪延迟（只有最大值，无分布） | `max_oldest_ready_delay_ticks` | `DebugPerformanceWindow.java:61`；`TickScheduler.java:84-89` |
| 采样完整度与窗口口径 | `sampled_seconds`、`sampled_ticks`、`world_ticks_advanced`、`observed_ticks_per_second`、`max_tick_interval_ms`、`sample_limit_reached`、`index_changes`、`queue_scope_dimension` | `DebugPerformanceWindow.java:96-106` |
| 正确性判定与账目 | `verdict(PASS/FAIL/INCOMPLETE)`、`expected_conversions`、`conversions_in_window`、`output_items_in_window`、`remaining_scene_tasks`、`checks_pending_after_cleanup`、`effects_pending_after_cleanup` | `core/debug/DebugScenarioRun.java:390-434` |
| 每秒 FRAME 明细 | `tracked_in_dimension`、`total_pending_in_dimension`、`checks_pending`、`effects_pending`、`checks_last_us`、`effects_last_us` | `DebugScenarioRun.java:110-136` |
| 环境参数（可复现性锚点） | `format/platform/minecraft_version/java_version/available_processors/max_heap_bytes/tick_rate/benchmark/entities/dimension/origin/grid_spacing_blocks/warmup_world_ticks/...` | `core/debug/DebugScenarioManager.java` START 日志；`core/debug/DebugLog.java:43-47` |

**回答「现有是否可测 P95/P99/max/pending」**：可以，但**只对「整 tick 耗时」和「每次 `runDue` 耗时」**。pending 有 mean/max/final 三个口径。**延迟只有最大值**，没有分位数。

### 2.2 现有可配置项

配置文件：`config/itemdespawntowhat/server.json`（`neoforge/.../runtime/RuleRuntimeHost.java:301-305`、`fabric/.../runtime/RuleRuntimeHost.java:289-295`；`ServerConfig.java:20-23` 类注释）。

| 配置键 | 默认 | 范围 | 作用 | 证据 |
| --- | --- | --- | --- | --- |
| `check_interval_ticks` | 20 | 1..1200 | 条件检查间隔 | `ServerConfig.java:25,36,54-55` |
| `backoff_max_ticks` | 100 | 1..72000 | 退避上限 | `ServerConfig.java:26,37,56-57` |
| `max_checks_per_tick` | 512 | 1..100000 | 每维度、**每类队列**每 tick 任务访问上限 | `ServerConfig.java:27,38,58-59`；`docs/dev/backend/systems/config.md:13` |
| `overlay_directory` | `itemdespawntowhat` | — | 规则覆盖层目录 | `ServerConfig.java:28,39,60-61` |
| `fabric_lifespan_fallback_ticks` | 6000 | 1..72000 | Fabric 端寿命兜底（NeoForge 用 `entity.lifespan`） | `ServerConfig.java:29,40,62-63`；`RuleRuntimeHost.java`(fabric):280-282 / (neoforge):291-294 |
| `debug_logging` | false | — | 是否落盘调试日志 | `ServerConfig.java:30,41,64-65` |

**缺口（明确）**：没有任何「时间预算 / 每 tick 工作量上限（全局语义）/ 自然检查平滑窗口 / 分步规模」字段。预算`2_000_000ns` 与「每队列 512 次」是唯一的两个闸门，前者硬编码、后者是每维度每队列语义。

### 2.3 阶段 2/7 需要新增的指标

1. **预算使用率**：`used_nanos / budget_nanos`，且粒度必须是「服务器每 tick 一个总预算」，不是每次 `runDue`。
2. **预算耗尽原因区分**：`exhausted_by=time|work_units|none`（当前无法区分「预算耗尽」与「工作量上限耗尽」）。
3. **分步耗时**：到期搬运（drain）、条件检查、位置搜索（候选遍历/选择）、效果执行、返还各自的分位数。
4. **按任务种类统计**：每类任务的 `dispatched/yielded/retried/failed/cancelled`。
5. **延迟分布**：就绪延迟（所有维度合并）的 P50/P95/P99/max，以及「最老未执行任务」的年龄。
6. **积压清空时间**：从首次 `pending > 0` 到回到 0 所经历的 tick 数。
7. **取消/丢弃计数**：`cancel` 数、维度卸载丢弃数、停服丢弃数、规则重载丢弃数（当前 `clear()` 是静默丢弃）。
8. **跨维度合并视图**：当前 `DebugPerformanceWindow` 绑定单个 `ServerLevel`（`DebugPerformanceWindow.java:67-72,106`），需要服务器级汇总。
9. **返还类任务指标**（阶段 5/6 引入后才有数据）。
10. **平滑窗口生效证据**：同一 `dueTick` 桶的到期任务在多少 tick 内被铺开（当前无法观测）。

---

## 3. 基线场景可重复性

### 3.1 前置条件（硬性）

1. **必须是开发环境**：`/idtw debug` 只在 `DebugMode.ENABLED = Services.PLATFORM.isDevelopmentEnvironment()` 时注册（`core/debug/DebugMode.java:11`；`core/command/RuleCommandTree.java:25-33`）。
2. **单人世界或权限等级 ≥ 2**：`hasAccess` 要求 `server.isSingleplayer() || source.hasPermission(2)`（`RuleCommandTree.java:36-39`）。
3. **发起者必须是玩家**：`start` 要求 source 是 `ServerPlayer`，否则失败键 `player_only`；同一服务器同时只允许一个场景（`busy`）。
4. **场景物品必须位于已加载区块且位置可 tick**：每源校验 `LoadedChunks.containsArea(level, pos, 1)` 且 `level.isPositionEntityTicking(pos)`，不满足则不入场（`DebugScenarioRun.java:146-149`）。
5. **配置固定**：先记录 `config/itemdespawntowhat/server.json` 的三项（`check_interval_ticks=20`、`max_checks_per_tick=512`、`backoff_max_ticks=100`）。
6. **年龄与到期点**：benchmark 场景规则 `trigger_after_seconds=10`（`DebugScenarioDefinition.java:50-53`，benchmark 分支），`scheduleInitial` 里 `dueAge = min(min(trigger*20,..), lifespan-1)`（`ConversionRuntime.java:239-252`），所有源在准备阶段生成、年龄≈0 → **同批源在同一 tick 前后集中到期**，正是要测的「同龄集中到期」。
7. **准备阶段自身的分步规模**：`DebugScenarioRun` 准备每 tick 最多 128 个源、且单 tick 截止 `System.nanoTime()+2_000_000`（`DebugScenarioRun.java:142-143`），总准备超 30 秒抛 `IllegalStateException`（`DebugScenarioRun.java:91-93`）→ 10000 源约需 79 tick ≈ 4 秒。**命令的 `seconds` 是 MEASURE 窗口，不含准备与 20 tick 预热**。

### 3.2 具体可用命令（不运行游戏，仅记录）

| 场景 | 命令 | 可得数据 | 备注 |
| --- | --- | --- | --- |
| S0 空白对照 | `/idtw debug bench baseline 60` | 纯服务端 tick 基线（实体数 0，且 `baseline` **完全不建规则**） | `RuleDebugCommands.java:28-33`（entities=0）；`DebugScenarioDefinition.java:50-53`（baseline 无规则） |
| S1 单维度同龄集中到期（有转化） | `/idtw debug bench convert 10000 60` | 同 tick 约 10000 个检查任务 + 效果任务；`checks/effects` 分位、积压、`max_oldest_ready_delay_ticks` | 规则 `itemdespawntowhat:debug/convert`，效果 `spawn_item` → `prismarine_shard`（`DebugScenarioDefinition.java:84-105`） |
| S2 单维度同龄集中到期（无转化噪声） | `/idtw debug bench normal 10000 10` | 纯后台检查噪声（`normal` 无规则 → 不会转化） | 同上 |
| S3 重试/退避压力 | `/idtw debug bench retry 10000 60` | 条件恒假（y_level min=y+3）→ 退避序列 | `DebugScenarioDefinition.java:54-64` |
| S4 多维度同龄集中到期 | **无单条命令**。需要 N 个玩家各自在目标维度内执行 `/idtw debug bench convert <N> 60`，尽量对齐启动 tick；或先执行再迅速切维度（场景绑定 `player.serverLevel()`） | 目前只能得到单维度视图（`queue_scope_dimension`） | 场景维度由发起者所在维度决定 |
| S5 集中环境销毁 | **当前不可用**：仓库内不存在岩浆/仙人掌/火焰销毁入口（见 §3.3），且 bench 场景把寿命语义替换为 10s 触发，`ItemExpireEvent` 路径只能由功能场景 `/idtw debug run expiry`（1 个实体）覆盖 | 无 | 需阶段 3/5 的环境销毁改造完成后才能测 |

### 3.3 环境销毁入口现状（grep 结论）

对 `(?i)(lava|cactus|onItemRemoved|deferNaturalExpiry)` 全 Java 检索：

- **没有任何岩浆/仙人掌/火焰销毁检测代码。** 现有的寿命入口只有：
  - `common/.../core/runtime/ConversionRuntime.java:465` `onItemRemoved`、`:474` `deferNaturalExpiry`；
  - NeoForge：`RuleRuntimeEvents.java:75`（`EntityLeaveLevelEvent`→`onItemRemoved`）、`:83`（`ItemExpireEvent`→`deferNaturalExpiry`）；
  - Fabric：`RuleRuntimeEvents.java:59`（`onItemRemoved`）、`mixin/ItemEntityMixin.java:15`（Fabric 无 `ItemExpireEvent`，用 Mixin 调 `RuleRuntimeHost.deferNaturalExpiry`）。
- 结论：阶段 0 的「集中环境销毁」只能记录为**待阶段 3/5 具备入口后补测的场景**，不要伪造数据。

### 3.4 可重复性记录要求（每次运行必须留存）

- START 日志整条（含 `format`、`platform`、`minecraft_version`、`java_version`、`available_processors`、`max_heap_bytes`、`tick_rate`、`benchmark`、`entities`、`dimension`、`origin`、`grid_spacing_blocks`、`warmup_world_ticks=20`、`queue_soft_budget_us=2000`、`scenario_rules`）；
- 1 秒粒度 FRAME 日志（`tracked_in_dimension`/`total_pending_in_dimension`/`checks_pending`/`effects_pending`/`checks_last_us`/`effects_last_us`）；
- 最终 summary（`server_tick_cost`、`checks`、`effects`）+ `verdict`；
- 窗口上限提示：`DebugMeasurements.MAX_SAMPLES = 12_000`（`DebugMeasurements.java:9,17`），`full()` 后停止采样（`DebugPerformanceWindow.java:93`）→ 单次窗口超过约 12000 tick（≈10 分钟）后统计不再增长，需要拆段测量；
- 世界侧变量：种子、已加载区块范围、`simulation-distance`、其它实体数量、世界时间；这些**不在**现有日志内，需要人工固定并记录。

---

## 4. 阶段 0 基线数据清单与初始候选值

### 4.1 必须留存的基线数据（字段级，全部来自现有输出）

| # | 数据 | 输出位置 | 用途 |
| --- | --- | --- | --- |
| B1 | `server_tick_cost.p50/p95/p99/max_us` | 场景 summary | 阶段 7 对照主指标 |
| B2 | `checks.p50/p95/p99/max_us`、`checks.visited_in_window` | 场景 summary | 到期搬运 + 检查的总耗时 |
| B3 | `effects.p50/p95/p99/max_us`、`effects.visited_in_window` | 场景 summary | 效果执行耗时 |
| B4 | `checks/effects.max_pending_at_tick_end`、`final_pending` | 场景 summary | 积压水平 |
| B5 | `checks/effects.max_oldest_ready_delay_ticks` | 场景 summary | 唯一现有的延后指标（口径受限于 `ready`） |
| B6 | `sampled_seconds/sampled_ticks/world_ticks_advanced/observed_ticks_per_second/max_tick_interval_ms/sample_limit_reached` | 场景 summary | 窗口可信度；防止把丢帧当成调度延后 |
| B7 | `verdict` + `expected_conversions/conversions_in_window/output_items_in_window/remaining_scene_tasks` | 场景 summary | 数量正确性；调度改造不得改变结果数 |
| B8 | `checks_pending_after_cleanup/effects_pending_after_cleanup` | cleanup 输出 | 收尾是否残留任务 |
| B9 | 1s FRAME 时间序列 | 日志 | 积压形成/清空的形态（当前只能人工从序列推） |
| B10 | START 环境行 | 日志 | 可复现性锚点 |
| B11 | S0 空白对照（baseline）全量 | 场景 summary | 扣除世界本身开销 |

**阶段 0 明确无法产出的量（须在阶段 2 补仪表后才能测）**：预算使用率、按种类的分步耗时、延迟分位数、积压清空时间、取消/丢弃计数、跨维度合并视图、返还类指标（详见 §2.3）。

### 4.2 总预算 / 工作量上限 / 平滑窗口初始候选值

> 说明：下表是**代码推导出的初始候选区间**，不是实测结论；阶段 0 未运行游戏，故不宣称任何毫秒收益。

| 参数（建议键名） | 初始候选 | 区间 | 推导理由（基于现有代码常量与结构） |
| --- | --- | --- | --- |
| 服务器每 tick 总预算 `server_budget_us` | **2000 µs（2 ms）** | 1000～3000 µs | ① 现值为 `TickScheduler.SOFT_BUDGET_NANOS = 2_000_000`（`TickScheduler.java:11`），保持 2 ms 可让「单维度」场景的行为几乎不变，便于前后对照；② 现状最坏为 2 × 维度数 × 2 ms，改为**全局共享**后主世界+2 维度场景的总上限从 12 ms 降到 2 ms，这本身就是 ADR-0002 想要的方向；③ 2 ms 相对 50 ms tick（`tick_rate` 见 START 日志）留出约 4% 的软预算余量，且远小于 `DebugScenarioRun` 准备阶段自用的 2 ms 分步闸门；④ 硬编码 `1000` 与 `3000` 只作为实机标定的上下界，不作为承诺值。 |
| 每 tick 工作量上限（全局）`max_work_units_per_tick` | **512** | 128～2048 | ① 沿用 `ServerConfig.DEFAULT_MAX_CHECKS_PER_TICK = 512`（`ServerConfig.java:38`，范围 1..100000）以免引入新默认值；② 语义必须改为**每 tick 全局**（现状是每维度每队列 512，见 §2.2），故保持数值即等于在多变场景中收紧总访问量；③ 512 与 2 ms 双闸门中「谁先到谁生效」——500 次访问若都命中缓存，通常时间先到、工作量闸门不生效，反之亦然，两个都需要保留。 |
| 效果任务预算份额 `effects_work_units_per_tick` | **64** | 32～128 | ① 现状效果队列与检查队列**同权**（各 512、各 2 ms），但效果任务通常更重（世界写入）；给予一个更小的专属份额可以让检查保底推进、避免效果把共享额度吃光；② 64 与 `DebugScenarioRun` 准备阶段的 128 分步规模同量级（`DebugScenarioRun.java:142-143`）；③ 该值只是「份额上限」，实际仍受总预算约束。 |
| 自然检查平滑窗口 `check_spread_window_ticks` | **20 tick（1 秒）** | 20～40 tick | ① `check_interval_ticks` 默认 20（`ServerConfig.java:36`），把同龄到期的检查摊到 20 tick 内，单个物品的最坏检查延后仍在一个原生检查周期内，不引入新的语义偏差；② 上限 40 tick 与退避基数 `base = max(1, check_interval_ticks)`（`ServerConfig.java:108-114`）保持同一数量级，避免平滑窗口大于退避步长导致节奏失真；③ **语义二选一（需在阶段 2 定稿并写明）**：(A) 把同一到期时刻的检查任务在窗口内按哈希/轮转**分配新的 `dueTick`**——实现简单，但会修改任务原始到期时间，与 ADR-0002「保留原始时间」矛盾；(B) 保留原 `dueTick`、通过**每 tick 搬运切片**把工作量铺开——更贴合 ADR-0002，但「同 tick 恰逢已到期」的场景无法靠切片区分随机与集中到期。**建议采用 (B)**：`dispatch_batch_size` 作为切片大小，`check_spread_window_ticks` 只作为每 tick 搬运上限的缩放因子（`ceil(pendingDue / window)` 的补充上限），不修改 `dueTick`。 |
| 每 tick 到期搬运切片 `dispatch_batch_size` | **64** | 32～256 | ① 直接对应 §1.3 的关键缺陷：drain 必须分片且计入工作量与时间；② 64 使 10000 个同龄到期任务至少需要 157 tick 才能全部进入执行阶段，把集中工作量摊到约 8 秒——这是**故意**的延后（ADR-0002 明确放弃严格到期精度）；③ 与 `DebugScenarioRun` 准备阶段 128/2ms 的既有做法同量级，便于复用经验。 |
| 单步工作量上限 `max_work_units_per_step` | **1**（一步一任务） | 1～8 | 沿用现有「一次 `runDue` 取一个任务执行」的粒度（`TickScheduler.java:68-70`），保证 2 ms 软预算的检查点密度最高。 |

### 4.3 测量计划（阶段 2 落地后执行；本阶段不执行）

1. 固定世界与配置，依次跑 S0 → S1 → S2 → S3，各 3 次，记录 §4.1 的 B1–B11。
2. 门面切换前后各跑一轮（阶段 2 的「等价重构」步骤 vs「预算共享」步骤，见 §5.4），用 `server_tick_cost.p95/p99` 与 `checks/effects.max_us` 做对照；**判定达标口径**按阶段 7（`PLAN.md:294-300`）：tick 与各类任务 P95/P99/max、积压、最大就绪延迟、积压清空时间。
3. S4（多维度）与 S5（集中环境销毁）在具备条件后再补；S5 依赖阶段 3/5 的销毁入口（§3.3）。

---

## 5. 阶段 2 改动面清单（文件级）与接口草案

> 目标（`PLAN.md:254-260` + ADR-0002）：一个公共调度器承载**服务器每 tick 一个总预算**，维度与任务种类公平轮转，到期搬运逐步推进，延后任务保留原始到期时间与可续接进度，并在取消/卸载时给业务一次通知机会；让已有自然检查直接接入同一预算。

### 5.1 新包路径建议

`common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/scheduler/`

理由：① 纯 common、无平台 import（满足 AGENTS.md 平台隔离）；② 与 `core/runtime` 中的业务类（`ConversionRuntime` 等）分层，便于阶段 7 复用；③ 不依赖 `ItemEntity`/`ItemStack`/`ServerLevel`，只依赖 `ServerTickBudget`，满足 ADR-0002「不绑定掉落物业务」。

### 5.2 文件级改动清单

**新增（11 个，全部在 `core/runtime/scheduler/`）**

| 文件 | 职责 |
| --- | --- |
| `ServerTickBudget.java` | 每 tick 单一预算实例：时间 + 工作量的记账与查询 |
| `ServerTickBudgetSnapshot.java` | 只读快照（record），供 debug/指标读取，不暴露可写状态 |
| `BudgetConfig.java` | 预算与平滑参数（由 `ServerConfig` 映射） |
| `ServerTaskKind.java` | 任务种类枚举（检查/搜索/效果/返还/维护） |
| `ServerTask.java` | 可续接任务接口 |
| `StepResult.java` + `StepOutcome.java` + `CancelReason.java` | 单步结果与中断原因 |
| `ScheduledTask.java` | 任务句柄与状态机 |
| `TaskDrainer.java` | 某个 realm 的到期搬运分片入口 |
| `RealmQueues.java` | 单 realm 的到期桶 + 就绪队列 |
| `ServerScheduler.java` | 服务器级调度器：唯一 tick 入口、公平轮转、统计 |
| `LegacyTaskAdapter.java` | 把现有 `Runnable` 任务适配成 `ServerTask`（过渡/测试用） |

**修改**

| 文件 | 改动 | 相关行 |
| --- | --- | --- |
| `core/runtime/TickScheduler.java` | 删除 `SOFT_BUDGET_NANOS` 与自持预算；降级为兼容门面（构造时绑定 `ServerScheduler`），或直接由 `LegacyTaskAdapter` 取代；`runDue` 的 drain 分片 | `:11,57-80,91-96` |
| `core/runtime/ConversionRuntime.java` | `LevelState` 去掉两条 `TickScheduler`；新增服务器级推进入口；`attempt`/候选选择拆成可让出步骤；`deferNaturalExpiry` 走统一入口；`queueStats`/`pendingTasks` 改读 `ServerScheduler` | `:54-65,139-144,211-226,239-320,357-395,474-489` |
| `core/runtime/RuntimeEffectContext.java` | `scheduler` 字段类型 `TickScheduler` → `ServerScheduler`；`schedule` 内部用 `LegacyTaskAdapter`/`ServerTask` 包装 | `:23,28` |
| `core/config/ServerConfig.java` | 新增 `server_budget_us`、`max_work_units_per_tick`、`effects_work_units_per_tick`、`check_spread_window_ticks`、`dispatch_batch_size`；旧 `max_checks_per_tick` 语义迁移（保留兼容读取或改名） | `:24-31,36-41,53-66` |
| `core/debug/DebugPerformanceWindow.java` | 构造/sample 改从 `ServerScheduler` 取快照；输出新增使用率、耗尽原因、按种类耗时、延迟分布、清空时间 | `:67-72,75-87,96-111` |
| `core/debug/DebugScenarioManager.java` | `schedule(...)` 返回类型 `TickScheduler.Task` → `ScheduledTask` | `:127-137` |
| `core/debug/DebugScenarioRun.java` | `tasks` 集合类型与 `Task::cancel` 调用点 | `:405-408` |
| `core/debug/DebugLog.java` | `queue_soft_budget_us` 改读配置，注入新增预算字段 | `:43-47` |
| `neoforge/.../runtime/RuleRuntimeEvents.java` | 预算推进点从 `LevelTickEvent.Post`（:92-96）迁到 `ServerTickEvent.Post`（:99-105，已存在）调一次 `onServerTick` | `:92-105` |
| `neoforge/.../runtime/RuleRuntimeHost.java` | 新增 `serverTick(MinecraftServer)`，保留/改造 `tickLevel` 为 realm 注册与清理 | `:268-273` |
| `fabric/.../runtime/RuleRuntimeEvents.java` | 从 `END_WORLD_TICK`（:63）迁到 `END_SERVER_TICK`（:64-70，已存在） | `:63-70` |
| `fabric/.../runtime/RuleRuntimeHost.java` | 同 NeoForge | `:259` |

**明确不改（边界）**

- `core/api/EffectContext.java`：`schedule(int delayTicks, Runnable task)` 等签名保持不变，避免影响阶段 4/5 的效果执行器。
- `core/api/EffectExecutor.java`：签名与回执属于阶段 4（ADR-0001）。
- 规则模型（`core/model/Rule.java`）、数据包格式、类型注册表：阶段 2 不涉及。

### 5.3 接口草案（方法签名级）

```java
package com.meteorite.itemdespawntowhat.core.runtime.scheduler;

/** 任务种类：决定公平轮转的轮次权重与指标分组。 */
public enum ServerTaskKind { CONDITION_CHECK, WORLD_SEARCH, EFFECT, REBATE, MAINTENANCE }

/** 单步结局。YIELD/RETRY 表示任务保留自身进度、稍后继续。 */
public enum StepOutcome { DONE, YIELD, RETRY, FAILED }

/** 任务被移除的原因，业务方据此决定返还或落账。 */
public enum CancelReason { RULE_RELOAD, DIMENSION_UNLOAD, SERVER_STOP, ENTITY_REMOVED, SCENARIO_STOP }

/** 单步结果：workUnits 由调度器记账，nextDelayTicks 只在 YIELD/RETRY 时有意义。 */
public record StepResult(StepOutcome outcome, int workUnits, long nextDelayTicks, String message) {
    public static StepResult done(int workUnits);                              // 任务完成
    public static StepResult yield(int workUnits, long nextDelayTicks);        // 主动让出，保留进度
    public static StepResult retry(int workUnits, long nextDelayTicks);        // 条件未满足，按延迟重试
    public static StepResult failed(int workUnits, String message);            // 业务失败
}

/** 可续接任务：只暴露调度需要的四件事，不感知 ItemEntity / ServerLevel 具体类型。 */
public interface ServerTask {
    ServerTaskKind kind();                 // 任务种类
    String tracingName();                  // 日志与指标的稳定名字
    long dueTick();                        // 原始到期 tick（保留，不因延后改写）
    StepResult step(ServerTickBudget budget);  // 一次有界单步
    default void onCancelled(CancelReason reason) {}  // 中断通知（返还/落账入口）
    default void onReleased() {}                      // 资源释放
}

/** 某个 realm（维度/场景）的到期搬运分片器：由 realm 自己实现，但每步必须有界。 */
public interface TaskDrainer {
    // 至多搬运 maxItems 个 dueTick < upToTickExclusive 的任务，返回实际搬运数；必须检查 budget
    int drain(long upToTickExclusive, int maxItems, ServerTickBudget budget);
}

/** 每 tick 单一预算：时间与工作量两个闸门，记账由调度器完成。 */
public final class ServerTickBudget {
    public void beginTick(long gameTime, long nowNanos);   // 每 tick 由 ServerScheduler 调用一次
    public long gameTime();                                // 当前游戏刻
    public boolean expired();                              // 时间或工作量任一耗尽
    public boolean canAfford(int workUnits);               // 预留判断（大步骤自行分片）
    public void charge(int workUnits);                     // 记录工作量
    public int remainingWorkUnits();                       // 剩余工作量
    public long usedNanos();                               // 已用纳秒（由调度器测量）
    public long budgetNanos();                             // 本 tick 总预算
    public boolean snapshotReady();                        // 是否已完成本 tick 记账
    public ServerTickBudgetSnapshot snapshot();            // 只读快照
}

/** 只读快照：指标读取方只依赖它，避免拿到可写预算对象。 */
public record ServerTickBudgetSnapshot(
        long gameTime, long budgetNanos, long usedNanos,
        int usedWorkUnits, int maxWorkUnits,
        boolean exhausted, boolean exhaustedByTime, boolean exhaustedByWorkUnits) {}

/** 预算与平滑参数：由 ServerConfig 映射，不在调度器内硬编码。 */
public record BudgetConfig(
        long budgetNanos,             // 服务器每 tick 总预算（默认 2_000_000）
        int maxWorkUnitsPerTick,      // 全局工作量上限（默认 512）
        int effectsWorkUnitsPerTick,  // 效果任务专属份额（默认 64）
        int checkSpreadWindowTicks,   // 平滑窗口（默认 20）
        int dispatchBatchSize,        // 每 tick 到期搬运切片（默认 64）
        int maxWorkUnitsPerStep) {    // 单步工作量上限（默认 1）
    public static BudgetConfig from(ServerConfig config);  // 迁移期映射
}

/** 任务句柄：业务持句柄取消，调度器用它推进状态。 */
public final class ScheduledTask {
    public enum State { PENDING, READY, CANCELLING, CANCELLED, DONE, FAILED }
    private final ServerTask task; private State state; private long readySinceTick;
    public ServerTask task();
    public State state();
    public boolean cancelled();
    public boolean cancel();        // 幂等；成功置 CANCELLING 并在下次取出时回调 onCancelled
}

/** 单 realm 的队列：到期桶 + 就绪队列，搬运由 TaskDrainer 分片。 */
public final class RealmQueues {
    public RealmQueues(Object realmKey, TaskDrainer drainer);
    public Object realmKey();
    public void enqueue(ScheduledTask handle);   // 按 handle.task().dueTick() 入桶
    public boolean readyEmpty();
    public int readySize();
    public int pendingCount();                   // 含已取消未取出者
    public ScheduledTask pollReady();            // 取出一个就绪任务
    public void clear(CancelReason reason);      // 清空并回调 onCancelled
}

/** 服务器级调度器：ADR-0002 的唯一预算入口。 */
public final class ServerScheduler {
    public ServerScheduler(BudgetConfig config);
    public BudgetConfig config();
    public ScheduledTask schedule(ServerTask task);                 // 任务自带 dueTick
    public ScheduledTask schedule(ServerTask task, int delayTicks);  // 便捷重载
    public RealmQueues realm(Object realmKey, TaskDrainer drainer);  // 注册/获取 realm
    public void tick(long gameTime);            // 每服务端 tick 调一次：建预算→搬运→公平执行→记账
    public void releaseRealm(Object realmKey, CancelReason reason);
    public void clear(CancelReason reason);
    public void reconfig(BudgetConfig config);
    public ServerTickBudgetSnapshot lastSnapshot();
    public SchedulerStats stats();
    public int pendingCount();
}

/** 汇总统计（阶段 7 对照用）。 */
public record SchedulerStats(
        long dispatched, long yielded, long retried, long failed, long cancelled,
        long timeExhaustions, long workUnitExhaustions,
        long maxReadyDelayTicks, long lastBacklogClearedTicks,
        int pending, int peakPending) {}

/** legacy Runnable 适配：迁移期让既有任务保持语义不变。 */
public final class LegacyTaskAdapter implements ServerTask {
    public LegacyTaskAdapter(String tracingName, long dueTick, Runnable action);
    public ServerTaskKind kind();
    public String tracingName();
    public long dueTick();
    public StepResult step(ServerTickBudget budget);  // 执行 action，返回 done(1)
    public void onCancelled(CancelReason reason);     // 迁移期不执行 action（与原 Task.cancel 一致）
}
```

**同步需要的既有接口改动（签名级）**

```java
// ConversionRuntime：新增服务器级入口；每维度推进只做 realm 注册/清理，不再各自跑预算
public void onServerTick(MinecraftServer server);                 // 唯一预算推进入口
public void onLevelTick(ServerLevel level);                       // 保留：仅维护 realm 存在性，不跑预算
public void onItemAdded(ServerLevel level, ItemEntity item);      // 不变；内部改为 ServerScheduler.schedule(...)
public ServerTickBudgetSnapshot budgetSnapshot();                 // 供 debug/指标读取

// RuntimeEffectContext：字段类型变化，外部签名不变（EffectContext 是公共 API）
private final ServerScheduler scheduler;                          // 原 TickScheduler scheduler
public void schedule(int delayTicks, Runnable task);              // 签名保持；内部 LegacyTaskAdapter

// DebugScenarioManager：句柄类型变化
public static ScheduledTask schedule(ItemEntity source, ServerScheduler scheduler,
                                     long now, int delay, Runnable action);
```

### 5.4 迁移次序（每步可编译，便于前后对照）

1. 新增 `core/runtime/scheduler/` 包（无引用者，不影响行为）。
2. `ServerConfig` 增加字段 + `DebugLog` 输出（默认值与现状等价）。
3. `ConversionRuntime` 的两条 `TickScheduler` → 单一 `ServerScheduler`，用 `LegacyTaskAdapter` 承载现有 `Runnable`；**推进点暂不变**（此时 `ServerTickBudget.beginTick` 采用「每 tick 首次生效、后续复用同一预算」的退化实现）→ 行为应与此前等价，可跑 S0–S3 做对照。
4. 推进点从 `LevelTickEvent.Post`/`END_WORLD_TICK` 迁到 `ServerTickEvent.Post`/`END_SERVER_TICK` → 此时真正共享总预算，满足 ADR-0002 R2/R3。
5. `TaskDrainer` 分片 + `checkSpreadWindowTicks` 生效 → 满足 R4/R7。
6. `DebugPerformanceWindow` 指标扩展（§2.3）→ 为阶段 7 提供对照数据。
7. 回跑到 S1/S4，确认「数量正确、延后不丢、延迟不提前、队列推进不重复」（`PLAN.md:260` 验收）。

### 5.5 本阶段未核实 / 风险

- **未核实**：两端 `ServerTickEvent.Post`/`END_SERVER_TICK` 是否在原版 tick 耗时数组写入之后派发（`DebugPerformanceWindow.java:74` 的注释如此声称，但本阶段未实测）。
- **未核实**：多维度场景能否在人工操作下把 N 个 `bench` 的启动 tick 对齐到同一 tick（未运行游戏）。
- **已知限制**：阶段 0 无任何实测数字；§4.2 全部是代码推导的候选值。
- **已知限制**：本阶段未使用 IDEA MCP 检查（`PROGRESS.md:60` 记录 schema session 失效），全部证据为静态阅读。
- **风险**：把 `max_checks_per_tick` 从「每维度每队列」改为「每 tick 全局」是**语义变更**，会改变多变场景下的行为；建议保留旧键名但重新解释，并在 release notes 写明。
- **风险**：规则重载时 `effects` 队列未清理（`ConversionRuntime.java:94-106,147-162`）属于既有不一致，阶段 2 引入 `CancelReason.RULE_RELOAD` 时需一并修正，否则「延后不丢」的验收会受污染。
