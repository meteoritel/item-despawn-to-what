# 功能模块：调试与开发场景（`core/debug`）

> 事实来源：`core/debug/**`（19 个文件）。仅在**加载器 development 环境**启用，发布环境不注册任何 debug 入口。
> 操作步骤见 [实机测试流水线](../../debug-test-pipeline.md)，场景统一声明与扩展方式见 [统一技术路线](../../debug-scenario-extension.md)。

## 1. 类清单

| 类 | 职责 | 关键成员 |
|---|---|---|
| `DebugMode` | 环境判定 | `ENABLED = Services.PLATFORM.isDevelopmentEnvironment()` |
| `DebugSessionManager` | 两端已有事件的开发诊断入口 | beginRuntimeTick与tick配对计时；首tick打READY；停服释放计时及场景状态 |
| `DebugScenarioManager` | 每服务器一轮场景的编排/登记/观察/调度/收尾；UUID → (run, source) 绑定 | `schedule`、`observe`、`converted`、`outputAdded`、`prepareOutput`、`allowsRuntimeLogging` |
| `DebugScenarioDefinition` | 用**真实** Codec / 校验 / `RuleIndex` 构建场景规则与预期量 | 读取 `DebugScenarioSpec`；规则 origin 标为 `development-memory` |
| `DebugScenarioRun` | 单轮有界负载状态机与校对（SETUP / WARMUP / MEASURE / CLEANUP） | `finish()`、统一断言和已完成报告 |
| `DebugScenarioCatalog` / `DebugScenarioSpec` | 场景manifest及严格资源声明 | 名称、默认输入、动作与预期的唯一来源 |
| `DebugScenarioActions` | 准备与声明动作 | expiry / excluded、移动源、真实reload |
| `DebugScenarioProbe` / `DebugScenarioChecks` | 后端事件、实物及统一断言 | END.expected / actual / checks |
| `DebugPipelineCatalog` | 开发阶段manifest和有限计划 | quantity、rounds、steps；展开上限64 |
| `DebugPipelineCommands` | 一键阶段命令注册与帮助 | /idtw debug pipeline p0～p4 |
| `DebugPipelineManager` / `DebugPipelineRun` | 串行阶段、清理回调、双时钟冷却、失败停止 | PIPELINE_START / STEP_END / COOLDOWN / END |
| `DebugDistribution` | 有界非耗时整数样本分位数 | 就绪延迟、预算比例与搬运分布 |
| `DebugMeasurements` | 有界原始微秒样本，结束时 nearest-rank 分位 | `MAX_SAMPLES = 12000` |
| `DebugPerformanceWindow` | 测量窗口：同tick原版＋IDTW运行时成本、分项及队列分别计量 | server_tick_cost、vanilla_tick_cost、idtw_runtime_cost；计时缺失显式标记 |
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
| `/idtw debug` / `help` | 帮助（i18n） |
| `/idtw debug run <场景>` | 功能场景；目录注册18个，输入由声明提供，默认12秒 |
| `/idtw debug bench baseline [秒数 10..300]` | 无新增负载基线（默认 60 秒） |
| `/idtw debug bench convert\|retry [实体数 1..1000] [秒数]` | 转化 / 失败重试负载（默认 1000 / 60） |
| `/idtw debug bench normal\|converted [实体数] [秒数]` | 1000 实体对照：不追踪 vs 被追踪并转化（默认 1000 / 10） |
| `/idtw debug pipeline p0\|p1\|p2 [间隔秒]` | 环境基线、4轮冒烟、其余14轮功能 |
| `/idtw debug pipeline p3 [最高实体数] [间隔秒]` | 逐级100/250/500/1000，默认最高1000 |
| `/idtw debug pipeline p4 [实体数] [间隔秒]` | 固定档3组重复，默认100源 |
| `/idtw debug pipeline status\|stop` | 阶段状态或中止后续计划 |
| `/idtw debug status` | 场景或活动阶段进度 |
| `/idtw debug mark <描述>` | 记录肉眼观察（每轮封顶 64 条，单条 ≤160 UTF-16） |
| `/idtw debug stop` | 停止本轮；活动流水线同时取消后续计划（INCOMPLETE） |

## 4. 生命周期与清理

- 两端在 `END_SERVER_TICK`/`ServerTickEvent.Post` 中先beginRuntimeTick，再推进RuleRuntimeHost.tickServer，最后DebugSessionManager.tick封存运行时成本并推进场景；每20 tick调用expireIdle。
- 停服时**先** `DebugSessionManager.shutdown` **再** `RuleRuntimeHost.shutdown`，保证先取消本轮延迟任务。
- 每服务器同时只允许一轮场景（重复启动拒绝）；场景停止时精确取消本轮任务并清理源与产物，**不清空普通队列**。发起者离线后结果仍写入控制台。
- 流水线等待真实 END、清理及解绑回调后默认冷却5秒（可调3～30秒），墙钟与世界时间均满足才继续；最后一轮同样冷却并检查已加载测试残留。冷却期间锁住场景入口；失败停止后续计划，离线、切维度、tick rate变化或停服记INCOMPLETE。
- P0/P3/P4额外检查TPS、tick p95、完整窗口、样本截断和索引变化，P1/P2检查功能断言；汇总关联阶段与每轮UUID。
- 场景与阶段JSON位于 `idtw-debug/` 类路径目录，独立于builtin datapack，发布环境不加载。
- 测试实体带 `idtw_debug_fixture` 标签与唯一 `custom_data`（防合并），关闭重力与拾取，用于保持可比较实体数量。

## 5. 约束与踩坑

- 场景/性能日志用 INFO，**不依赖** `server.json.debug_logging`；性能场景关闭逐实体过程日志（即使用户把 `debug_logging` 设为 true）。
- 样本有界：`DebugMeasurements.MAX_SAMPLES=12000`，满则停止，不伪造更长窗口。
- v3的server_tick_cost按每tick原版数组成本与IDTW运行时实测成本相加后求分位数，排除后续开发场景推进及其它结束事件监听器；缺失计时不能通过阶段性能门槛。旧v2口径不可直接混比。
- pending_scene_effects排除正常CONDITION_CHECK，pending_scene_checks独立记录；清理取消全部句柄，scheduler pending按实时车道汇总，避免上一tick快照误报。
- `expiry` 场景要求实际寿命 ≤32767，否则明确拒绝该场景。
- 诊断必须复用**真实平台寿命提供器**，不能用通用兜底冒充 NeoForge 当前寿命。
- 队列成本包含诊断计数与任务管理开销；性能前后对比须使用**相同诊断版本**，不能直接相减 p95/p99 推断纯模组分位。

## 6. 相关

- 端到端链路：[../flows/debug-scenario-flow.md](../flows/debug-scenario-flow.md)
- 实机验证与性能对比：[../../debug-validation-guide.md](../../debug-validation-guide.md)
- 调度预算：[../systems/scheduling-budget.md](../systems/scheduling-budget.md)
