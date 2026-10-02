# 纵向系统：调试场景链路

> 从 `/idtw debug` 命令到场景准备、观测、测量、清理的端到端流程。
> 类职责见 [debug.md](../modules/debug.md)；操作与性能对比方案见 [Debug 实机验证指南](../../debug-validation-guide.md)。

## 1. 全景

```text
/idtw debug run|bench <场景>
  → RuleDebugCommands（DebugMode.ENABLED && hasAccess）
  → DebugScenarioManager 登记本轮（每服务器一轮）
  → DebugScenarioDefinition 用真实 Codec/校验/RuleIndex 建内存场景规则 + 预期
  → DebugScenarioRun 状态机：SETUP → WARMUP(20 tick) → MEASURE → CLEANUP
  → ConversionRuntime 在真实链路回调 observe/converted/schedule/prepareOutput/outputAdded
  → DebugLog 输出 START/过程事件/FRAME/END；verify() 判定 PASS/FAIL/INCOMPLETE
```

## 2. 分步

| 阶段 | 动作 | 关键点 |
|---|---|---|
| 命令 | `RuleDebugCommands.build` | 仅在 development 环境挂载；带宽 `help` / `run` / `bench` / `status` / `mark` / `stop` |
| 登记 | `DebugScenarioManager` | 每服务器同时只允许一轮；UUID → (run, source) 绑定 |
| 场景规则 | `DebugScenarioDefinition` | 用**真实** `RuleCodecs` / `RuleValidation` / `RuleIndex`；origin=`development-memory`；FUNCTIONAL 场景名集合 |
| 准备 SETUP | 生成测试源 | 自动分批（每 tick ≤128 源、2ms 软预算）；不强制加载区块；实体带 `idtw_debug_fixture` + 唯一 `custom_data`，关闭重力与拾取 |
| 预热 WARMUP | 20 世界 tick | 生成/预热成本不混入测量窗口 |
| 测量 MEASURE | 采集窗口 | `DebugPerformanceWindow` 采服务端 tick 耗时 + checks/effects 两队列分位；`DebugMeasurements` 有界样本（≤12000） |
| 校对 | `DebugScenarioRun.verify()` | 依据实际计数键（TRACKED/CONVERT/OUTPUT_ITEMS/…）判 PASS/FAIL |
| 清理 CLEANUP | 取消本轮任务、清理源与产物 | 精确取消本轮任务，**不清空普通队列**；不清理用户规则链 |

## 3. 隔离机制（关键）

- **候选规则隔离**：仅对本轮登记 UUID 使用场景索引，其它实体回落到用户普通索引。
- **产物隔离**：测试产物在加入前登记，不触发用户规则链。
- **诊断复用真实链路**：观察真实后端事件，不重新求值模拟游戏结果。
- **寿命真实**：`expiry` 场景用 `runtime.lifespanTicks(...)`，不用通用兜底。

## 4. 结果与清理语义

- 正常结束自动清理；**手动 `stop` 结果为 INCOMPLETE**（不能当 PASS）。
- 正常停服与维度卸载结束场景；`DebugSessionManager.shutdown` 在 `RuleRuntimeHost.shutdown` 之前，保证先取消本轮延迟任务。
- 进程强杀不保证清理；测试世界若有残留 fixture，用 `/kill @e[type=minecraft:item,tag=idtw_debug_fixture]` 清理（不匹配普通掉落物）。
- 日志用 INFO，可在 IDEA 按 `[IDTW_DEBUG]` 与 `run=<UUID>` 校对；命令不读取日志或导出文件。

## 5. 相关

- 模块细节：[../modules/debug.md](../modules/debug.md)
- 转化链路：[conversion-lifecycle.md](conversion-lifecycle.md)
- 操作步骤与性能对比：[../../debug-validation-guide.md](../../debug-validation-guide.md)
