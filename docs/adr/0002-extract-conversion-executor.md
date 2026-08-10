# 转化行为从 config 数据类抽离为 ConversionExecutor 策略

## 背景
`BaseConversionConfig` 长期作为 god class，同时承担序列化数据、缓存、校验、复杂度、消耗逻辑与执行（`performConversion` / `countNearbyResult` / `isResultLimitExceeded`）。5 个子类各自把 `performConversion` 焊死在数据类上。唯一例外 `WorldEffectType` 已验证"数据接口(`SideEffectConfig`) + 行为策略(`SideEffectExecutor`)"的分离模式，但仅用于世界效果。为支持第三方提供新转化类型，需要一套干净、可独立实现的执行 SPI。

## 决策
推广 `WorldEffectType` 模式至全部转化类型：config 降为 DTO，仅保留数据字段 + 与数据形状 intrinsic 的 `validate()` / `complexity()` / 引用解析 `resolve()`；执行相关（`performConversion` / `countNearbyResult` / `isResultLimitExceeded` / 结果容量 / 消耗 / 返还）移至 `ConversionExecutor<C extends ConversionConfig>` 策略，随转化类型注册。`CompiledConversionRule` 持有执行器。共享的消耗 / 轮数计算 / 返还逻辑下沉到共享基类或工具供各执行器复用。

## 后果
- 5 个内置类型的 `performConversion` 及相关执行方法需迁出至各自执行器，迁移量较大但机械。
- config 成为可在运行时快照 / 编辑快照 / 网络传输间安全共享的纯数据对象（不再承载执行）。
- 第三方 SPI 形状锁定：实现新类型 = 提供 `ConversionConfig` DTO + `ConversionExecutor` 策略 + 注册到 `ConversionType`。
