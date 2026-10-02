# 将世界效果拆为独立转化类型

> 历史记录：本文绑定的旧实现已退役；当前后端契约与前端占位决定以 [ADR-0017](0017-backend-cutover-and-budgeted-effects.md) 和 [当前架构](../dev/architecture.md) 为准。

旧 `item_to_world_effect` 以 `WorldEffectType` 和一组互斥字段承载天气、闪电、爆炸与箭雨，导致新增效果必须修改封闭 enum，且配置中长期保留与所选效果无关的字段。现将四种效果注册为独立 `ConversionType` 并按文件存储，旧联合文件在加载前按 `side_effect` 安全分流；同时将战利品表作为独立 `item_to_loot` 类型，而不是继续扩大联合结果模型，从而保持第三方扩展边界与每种配置的数据形状一致。
