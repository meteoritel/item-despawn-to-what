# 功能模块：调试与开发场景（`core/debug`）

> 事实来源：`core/debug/**`（9 个文件）。仅在**加载器 development 环境**启用，发布环境不注册任何 debug 入口。
> 操作步骤与性能对比方案见 [Debug 实机验证与反馈指南](../../debug-validation-guide.md)。

## 1. 类清单

| 类 | 职责 | 关键成员 |
|---|---|---|
| `DebugMode` | 环境判定 | `ENABLED = Services.PLATFORM.isDevelopmentEnvironment()` |
| `DebugSessionManager` | 两端已有事件的开发诊断入口 | 首 tick 打 READY、转发 `DebugScenarioManager.tick`；`shutdown` 先清场景 |
| `DebugScenarioManager` | 每服务器一轮场景的编排/登记/观察/调度/收尾；UUID → (run, source) 绑定 | `schedule`、`observe`、`converted`、`outputAdded`、`prepareOutput`、`allowsRuntimeLogging` |
| `DebugScenarioDefinition` | 用**真实** Codec / 校验 / `RuleIndex` 构建场景规则与预期量 | `FUNCTIONAL` 场景名集合；规则 origin 标为 `development-memory` |
| `DebugScenarioRun` | 单轮有界负载状态机与校对（SETUP / WARMUP / MEASURE / CLEANUP） | `verify()`、计数键 |
| `DebugMeasurements` | 有界原始微秒样本，结束时 nearest-rank 分位 | `MAX_SAMPLES = 12000` |
| `DebugPerformanceWindow` | 测量窗口：服务端 tick 耗时 + checks/effects 两条队列分别计量 | 采集 `server.getTickTimesNanos()` |
| `DebugLog` | IDEA 控制台稳定单行日志（INFO，`IDTW.Debug`，`[IDTW_DEBUG]` 前缀） | — |
| `RuleDebugCommands` | `/idtw debug` 命令树 | `build(context)` |

## 2. 场景如何复用真实链路

- 场景规则用**真实** `RuleCodecs.codec(...)` 解析 + `RuleValidation.validate` + `RuleIndex.build`，**不是旁路实现**；只是规则 origin 标为 `development-memory`。
- 观察的是真实后端入口：`ConversionRuntime` 在正常链路里回调 `observe`/`converted`/`schedule`/`prepareOutput`/`outputAdded`。
- **候选规则隔离**：仅对本轮登记 UUID 使用场景索引，其它实体回落到用户普通索引；测试产物在加入前登记，**不进入用户的普通规则链**。
- `expiry` 场景用**真实**寿命提供器 `runtime.lifespanTicks(...)`，直接设原版 NBT `Age`。
- 测量窗口只在准备 + 20 tick 预热后开始；服务端 tick 耗时直接读 `server.getTickTimesNanos()`。

计数键（`DebugScenarioRun`）：`TRACKED` / `AGE_NOT_READY` / `CONDITION_FALSE` / `CONDITION_TRUE` / `RETRY` / `CONVERT` / `OUTPUT_ITEMS` / `NATURAL_EXPIRY_DEFERRED` / `EXCLUDED` / `ERROR` / `DUPLICATE_CONVERSION` / `UNEXPECTED_OUTPUT`。

## 3. 命令

均要求 `DebugMode.ENABLED && hasAccess`。

| 命令 | 用途 |
|---|---|
| `/idtw debug` / `help` | 帮助（4 行 i18n） |
| `/idtw debug run <场景>` | 功能场景；阶段固定，1 实体 / 12 秒默认；`<场景>` ∈ FUNCTIONAL（如 convert / retry / delay / expiry / stack / priority / excluded / reload） |
| `/idtw debug bench baseline [秒数 10..300]` | 无新增负载基线（默认 60 秒） |
| `/idtw debug bench convert\|retry [实体数 1..10000] [秒数]` | 转化 / 失败重试负载（默认 1000 / 60） |
| `/idtw debug bench normal\|converted [实体数] [秒数]` | 10000 实体对照：不追踪 vs 被追踪并转化（默认 10000 / 10） |
| `/idtw debug status` | 场景进度 |
| `/idtw debug mark <描述>` | 记录肉眼观察（每轮封顶 64 条，单条 ≤160 UTF-16） |
| `/idtw debug stop` | 停止并清理本轮负载（结果为 INCOMPLETE） |

## 4. 生命周期与清理

- 两端在 `END_SERVER_TICK`/`ServerTickEvent.Post` 调 `DebugSessionManager.tick`，并每 20 tick 调 `RuleEditServerHandler.expireIdle()`。
- 停服时**先** `DebugSessionManager.shutdown` **再** `RuleRuntimeHost.shutdown`，保证先取消本轮延迟任务。
- 每服务器同时只允许一轮场景（重复启动拒绝）；场景停止时精确取消本轮任务并清理源与产物，**不清空普通队列**。发起者离线后结果仍写入控制台。
- 测试实体带 `idtw_debug_fixture` 标签与唯一 `custom_data`（防合并），关闭重力与拾取，用于保持可比较实体数量。

## 5. 约束与踩坑

- 场景/性能日志用 INFO，**不依赖** `server.json.debug_logging`；性能场景关闭逐实体过程日志（即使用户把 `debug_logging` 设为 true）。
- 样本有界：`DebugMeasurements.MAX_SAMPLES=12000`，满则停止，不伪造更长窗口。
- `expiry` 场景要求实际寿命 ≤32767，否则明确拒绝该场景。
- 诊断必须复用**真实平台寿命提供器**，不能用通用兜底冒充 NeoForge 当前寿命。
- 队列成本包含诊断计数与任务管理开销；性能前后对比须使用**相同诊断版本**，不能直接相减 p95/p99 推断纯模组分位。

## 6. 相关

- 端到端链路：[../flows/debug-scenario-flow.md](../flows/debug-scenario-flow.md)
- 实机验证与性能对比：[../../debug-validation-guide.md](../../debug-validation-guide.md)
- 调度预算：[../systems/scheduling-budget.md](../systems/scheduling-budget.md)
