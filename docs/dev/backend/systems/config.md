# 横向系统：模组级配置（`server.json`）

> 事实来源：`core/config/ServerConfig.java`。与规则配置完全分离；两端共用同一实现与同一份文件。
> 调用方：两端 `runtime/RuleRuntimeHost.start()` 在引导时读取；预算兑现见 [scheduling-budget.md](scheduling-budget.md)。

## 1. 文件与加载

文件路径固定为 `config/itemdespawntowhat/server.json`（**不随 `overlay_directory` 变化**，避免引导期自引用）。

| 方法 | 行为 |
|---|---|
| `loadOrCreate(file, issues)` | `file == null` 返回 `DEFAULT`；文件缺失时**写出默认值**（方便用户直接编辑）并返回默认；解析失败时经 `issues.error` 记 ERROR 并回退 `DEFAULT`；`IOException` / `RuntimeException` 记 ERROR 回退 |
| `save(file)` | Codec 编码后写回；父目录缺失自动创建 |
| `DEFAULT` | 全部默认值组成的常量实例，也是缺失字段的兜底 |

Codec 用 `optionalFieldOf`：**未知键忽略、缺失键取默认**。因此新增键必须同时加 record 分量、`CODEC` 的 `optionalFieldOf` 与 `DEFAULT`——只改 Java 会解码失败并整体回退默认。

## 2. 字段（16 键）

| JSON key | 字段 | 类型 | 默认 | 区间 | 含义 |
|---|---|---|---|---|---|
| `check_interval_ticks` | `checkIntervalTicks` | int | 20 | 1..1200 | 失败退避基数；区块未 ticking 时的重查间隔 |
| `backoff_max_ticks` | `backoffMaxTicks` | int | 100 | 1..72000 | 退避上限（5 秒） |
| `max_checks_per_tick` | `maxChecksPerTick` | int | 512 | 1..100000 | 旧语义的每维度每队列访问上限；现作为 `max_work_units_per_tick` 缺失时的**回退值** |
| `overlay_directory` | `overlayDirectory` | string | `"itemdespawntowhat"` | — | 规则覆盖层目录名（相对 `config/`） |
| `fabric_lifespan_fallback_ticks` | `fabricLifespanFallbackTicks` | int | 6000 | 1..72000 | Fabric 端 lifespan 兜底（NeoForge 读实体当前 `lifespan`） |
| `debug_logging` | `debugLogging` | bool | false | — | 常规运行日志开关；开发场景由加载器 development 环境启用，**不依赖此项** |
| `server_budget_us` | `serverBudgetUs` | int | 2000 | 100..100000 | 每 tick 服务器**共享**软时间预算（微秒，默认 2ms） |
| `max_work_units_per_tick` | `maxWorkUnitsPerTick` | int（可选） | 缺失（回退 512） | 1..100000 | 全局每 tick 工作量上限；**缺省为空**表示回退 `max_checks_per_tick` |
| `effects_work_units_per_tick` | `effectsWorkUnitsPerTick` | int | 64 | 1..100000 | 效果类每 tick 工作量单独限额（不饿死条件检查） |
| `check_spread_window_ticks` | `checkSpreadWindowTicks` | int | 20 | 1..1200 | 到期检查的平滑窗口（tick） |
| `dispatch_batch_size` | `dispatchBatchSize` | int | 64 | 1..100000 | 每 lane 每 tick 搬运上限，兼效果派发 / 返还交付的每步批量 |
| `new_product_protection_seconds` | `newProductProtectionSeconds` | int | 2 | **0**..3600 | 新产物临时保护（秒）；0 表示不授予 |
| `conversion_cooldown_seconds` | `conversionCooldownSeconds` | int | 5 | **0**..3600 | 转化冷却（秒）；0 表示不授予 |
| `position_search_checks_per_tick` | `positionSearchChecksPerTick` | int | 16 | 1..4096 | 每刻候选位置检查上限（安全生成与返还位置搜索共用） |
| `debug_scenario_prepare_batch_size` | `debugScenarioPrepareBatchSize` | int | 128 | 1..100000 | 阶段 7 debug 场景每 tick 准备源数量上限 |
| `debug_scenario_prepare_budget_us` | `debugScenarioPrepareBudgetUs` | int | 2000 | 1..100000 | 阶段 7 debug 场景准备软预算（微秒） |

> `new_product_protection_seconds` / `conversion_cooldown_seconds` 的下限是 **0**（允许显式关闭），其余键下限均为 1。`max_work_units_per_tick` 是唯一**可选**键：缺失时读作空 `Optional`。

分组的观测/运算归属：

- **调度预算组**（→ `SchedulerConfig`）：`server_budget_us`、`max_work_units_per_tick`、`effects_work_units_per_tick`、`check_spread_window_ticks`、`dispatch_batch_size`、`position_search_checks_per_tick`。
- **检查与寿命组**：`check_interval_ticks`、`backoff_max_ticks`、`fabric_lifespan_fallback_ticks`（语义见 [scheduling-budget.md](scheduling-budget.md)、[conversion-runtime.md](../modules/conversion-runtime.md)）。
- **实体状态组**：`new_product_protection_seconds`、`conversion_cooldown_seconds`（`start` 时经 `DropStateStore.configure` 注入）。
- **调试组**：`debug_logging`、`debug_scenario_prepare_batch_size`、`debug_scenario_prepare_budget_us`（只影响 debug 场景准备，**不参与** `server_budget_us`）。

## 3. 派生换算

| 方法 | 公式 | 用途 |
|---|---|---|
| `serverBudgetNanos()` | `max(1, serverBudgetUs) × 1000` | 软预算纳秒值 |
| `effectiveMaxWorkUnitsPerTick()` | `maxWorkUnitsPerTick.orElse(maxChecksPerTick)` | 全局工作量上限（缺省回退） |
| `newProductProtectionTicks()` | `max(0, seconds) × 20` | 秒 → 刻（20 刻/秒） |
| `conversionCooldownTicks()` | `max(0, seconds) × 20` | 秒 → 刻（20 刻/秒） |

`SchedulerConfig.from(config)` 把预算组换算成调度器参数并做下界保护（各值夹到 ≥ 1）；调度器在 `ConversionRuntime` 构造时读取一次，改预算参数需**重启服务端**（重载不重建调度器）。

## 4. 退避算法

```text
backoffTicks(failureCount):
  base  = max(1, checkIntervalTicks)
  shift = clamp(failureCount - 1, 0, 20)
  ticks = (long) base << shift
  return (int) min(ticks, backoffMaxTicks)
```

即 `checkIntervalTicks × 2^(failureCount-1)` 后按 `backoff_max_ticks` 封顶。直觉序列「1s → 2s → 4s → 封顶」，但**基数是 `checkIntervalTicks`（默认恰为 20）而非写死 20**，改基准会整体平移序列；`shift` 上限 20 防止长失败次数位移溢出。到自然消失前停止（`ConversionRuntime.attempt` 的年龄门槛修正见 [conversion-runtime.md](../modules/conversion-runtime.md) §2.4）。

## 5. 扩展点与踩坑

- 新增模组级参数 → 加 record 分量 + `CODEC` 的 `optionalFieldOf` + `DEFAULT`；需要派生换算时一并加方法。
- 踩坑：`server.json` 路径固定，不要让它受 `overlay_directory` 控制；`debug_logging` 与开发场景开关是两回事；预算组改动要重启而非重载；`max_work_units_per_tick` 留空才回退，写 0 会被区间校验拒绝。

## 6. 相关

- 调度预算机制：[scheduling-budget.md](scheduling-budget.md)
- 引导读取位置与两端差异：[../modules/platform.md](../modules/platform.md)、[platform-abstraction.md](platform-abstraction.md)
