# 横向系统：模组级配置（`server.json`）

> 事实来源：`core/config/ServerConfig.java`。与规则配置完全分离；两端共用同一实现与同一份文件。

## 1. 字段

文件路径固定为 `config/itemdespawntowhat/server.json`（**不随 `overlay_directory` 变化**，避免引导期自引用）。

| JSON key | 字段 | 类型 | 默认 | 区间 | 作用 |
|---|---|---|---|---|---|
| `check_interval_ticks` | `checkIntervalTicks` | int | 20 | 1..1200 | 失败退避基数；暂不 ticking 时的重查间隔 |
| `backoff_max_ticks` | `backoffMaxTicks` | int | 100 | 1..72000 | 退避上限（5 秒） |
| `max_checks_per_tick` | `maxChecksPerTick` | int | 512 | 1..100000 | **每维度、每类队列**每 tick 任务访问上限 |
| `overlay_directory` | `overlayDirectory` | string | `"itemdespawntowhat"` | — | 覆盖层目录名（相对 config/） |
| `fabric_lifespan_fallback_ticks` | `fabricLifespanFallbackTicks` | int | 6000 | 1..72000 | Fabric 端 lifespan 兜底（NeoForge 读实体当前 `lifespan`） |
| `debug_logging` | `debugLogging` | bool | false | — | 常规运行日志开关；开发场景由加载器 development 环境启用，**不依赖此项** |

性能预算相关的三项是 `check_interval_ticks` / `backoff_max_ticks` / `max_checks_per_tick`；语义见 [scheduling-budget.md](scheduling-budget.md)。

## 2. 加载与落盘

| 方法 | 行为 |
|---|---|
| `loadOrCreate(file, issues)` | `file == null` 返回 `DEFAULT`；文件缺失时**写出默认值**（方便用户直接编辑）并返回默认；解析失败记 ERROR 并回退 `DEFAULT`；`IOException`/`RuntimeException` 记 ERROR 回退 |
| `save(file)` | 编码后写回；父目录缺失自动创建 |
| `DEFAULT` | 全部默认值组成的常量实例，也是缺失字段的兜底 |

调用方：平台 `RuleRuntimeHost.start()` 在引导时读取；`server.json` 本身位置固定，`overlay_directory` 只影响规则覆盖层目录。

## 3. 退避算法

```text
backoffTicks(failureCount):
  base  = max(1, checkIntervalTicks)
  shift = min(max(0, failureCount - 1), 20)
  ticks = (long) base << shift
  return min(ticks, backoffMaxTicks)
```

即 `checkIntervalTicks × 2^(n-1)` 后按 `backoff_max_ticks` 封顶。注释给出的直觉是"1s→2s→4s→封顶"，但**基数是 `checkIntervalTicks`（默认恰为 20）而非写死 20**，改基准会整体平移序列。

## 4. 扩展点与踩坑

- 新增模组级参数 → 加 record 分量 + `CODEC` 的 `optionalFieldOf` + `DEFAULT`；**不能**只改 Java 而漏改 Codec（会导致解析失败回退默认）。
- 踩坑：`server.json` 路径固定，不要让它受 `overlay_directory` 控制；`debug_logging` 与开发场景开关是两回事。

## 5. 相关

- 预算与退避机制：[scheduling-budget.md](scheduling-budget.md)
- 引导读取位置：[../modules/platform.md](../modules/platform.md)
