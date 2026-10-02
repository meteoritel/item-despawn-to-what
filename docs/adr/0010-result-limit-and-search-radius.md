# 统一结果上限与搜索半径

> 历史记录：本文绑定的旧实现已退役；当前后端契约与前端占位决定以 [ADR-0017](0017-backend-cutover-and-budgeted-effects.md) 和 [当前架构](../dev/architecture.md) 为准。

旧配置把 `result_limit`、方块放置半径和世界效果批量次数混用，导致同名字段在不同类型中含义不同。现将 `result_limit` 定义为 item/mob/block/loot 类型在 `search_radius` 范围内的邻近结果单位上限；方块的放置半径保留为独立参数，XP 与世界效果只使用各自的单次安全阀。这样既统一了可配置的邻近累积语义，也避免对不产生可枚举邻近结果的效果强行引入无意义字段。

阶段 5 前的 XP 文件可能仍带有旧 `result_limit`。该字段只作为兼容输入保留，不参与 XP 执行或校验，新建 XP 配置不会默认生成它。
