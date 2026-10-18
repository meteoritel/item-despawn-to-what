# ADR-0012：规则模型与效果列表

- 状态：已实施；实现见 `core/model/Rule.java`、`core/model/Effect.java`、`core/model/ConditionExpression.java`。

## 背景
旧链路是"一个转化类型 = 一个 Config 子类 = 一个 Executor = 一个结果"：9 个类型要维护 9 套 DTO、9 个校验入口与 2N 个类，新增一种效果要改约 8 处（注册表 / Config 子类 / Executor / 客户端 FormDefinition / Presenter / i18n / 文档 / 示例），且"结果"与"消耗"被拆在配置与执行两侧，无法表达"一次触发做多件事"。

## 决策
后端只保留两个领域概念：

1. **规则 (Rule)** 是唯一的配置单元：`id`（ResourceLocation）/ `enabled` / `priority` / `display_name` / `notes` / `source`（源匹配：物品与 `#tag` 列表 + 排除列表）/ `conditions`（**递归条件树**，见 [ADR-0018](0018-condition-tree-contract.md)）/ `trigger_after_seconds` / `effects`（**有序**效果列表）/ `triggers` / `source_cost` / `catalyst_cost` / `consume_fluid` / `combination` / `outcomes` / `schema_version`。
2. **效果 (Effect)** 是唯一的执行单元：`type` + 类型专属参数 + 通用字段 `delay_ticks` / `chance` / `conditions`。7 个内置效果。
3. **选择语义**：同一掉落物只执行**优先级最高的一条**命中规则（priority desc → 条件叶数 desc → 定义序）；规则内效果**顺序全执行**、不回滚。
4. **输入支付统一结算**：源门槛与扣除量、催化剂门槛与实际消耗、流体源消耗由规则输入配置声明，见 [ADR-0041](0041-input-thresholds-and-consumption.md)。
5. **异常隔离**：单个效果抛异常 → 记录 ERROR（规则 id + 效果类型 + 位置）+ 继续后续效果，整体转化仍视为已发生。

## 考虑过的替代方案
- **保留 ConversionType，仅重构字段**：改动面小，但"类型即结果"的结构无法表达多效果与效果级延迟/概率，扩展成本依旧。否决。
- **多条规则叠加执行**：允许同一掉落物命中多条规则，各自执行。语义复杂且顺序不可预测，冲突时难以解释"为什么这个物品变成这样"。否决（Q39）。
- **效果执行失败整体回滚**：需要给每个世界操作做补偿，1.21.1 原版 API 不提供，成本与风险都过高。否决（Q44）。

## 后果
- 新增一种效果类型 = 一个参数记录 + 一个执行器 + 注册表一行（可选客户端规格一行），框架代码零改动。
- `Rule` 与全部效果/条件参数都是不可变 record，可直接被索引、缓存与跨线程读取。
- 规则文件天然支持"一次触发多效果"；编辑器可编辑效果列表与候选结果。
- 旧字段全部重设计、不兼容；旧 v1.2.1 配置不再加载，也不提供自动转换。
