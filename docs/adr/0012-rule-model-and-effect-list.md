# 规则模型与效果列表

> 实施补充：本文的最新调度、区块生命周期、保存与前端边界以 [ADR-0017](0017-backend-cutover-and-budgeted-effects.md) 和 [当前架构](../dev/backend/README.md) 为准。
> **局部已被取代（2026-10-03，P8 结论）**：本文「后果」中「迁移走 `/idtw config convert`」已失效——该转换入口与 `RuleConvertService` 已**整类删除**，旧 v1.2.1 配置不再加载、也不再提供自动或显式转换。见 [迁移评估 §5](../plan/v1.2.1-migration-evaluation.md) 与 [更新说明](../guide/update-notes.md)。本文其余结论不受影响。
> **已扩展（2026-10-03，第二轮后端）**：决策 1 的 Rule 字段集已扩展——新增 `display_name`（见 [ADR-0021](0021-display-name-and-stable-ids.md)）与 `triggers` / `source_cost` / `catalyst_cost` / `combination` / `outcomes` / `schema_version`（见 [ADR-0023](0023-triggers-fixed-cost-and-complete-group-settlement.md)）；`conditions` 已从「DNF 表达式」改为**递归条件树**（见 [ADR-0018](0018-condition-tree-contract.md)）。「规则 + 效果两个领域概念」与扩展性结论仍现行。

## 背景
旧链路是"一个转化类型 = 一个 Config 子类 = 一个 Executor = 一个结果"：9 个类型要维护 9 套 DTO、9 个校验入口与 2N 个类，新增一种效果要改约 8 处（注册表 / Config 子类 / Executor / 客户端 FormDefinition / Presenter / i18n / 文档 / 示例），且"结果"与"消耗"被拆在配置与执行两侧，无法表达"一次触发做多件事"。

## 决策
后端只保留两个领域概念：

1. **规则 (Rule)** 是唯一的配置单元：`id`（ResourceLocation）/ `enabled` / `priority` / `notes` / `source`（源匹配：物品与 `#tag` 列表 + 排除列表）/ `conditions`（DNF 表达式）/ `trigger_after_seconds` / `effects`（**有序**效果列表）。
2. **效果 (Effect)** 是唯一的执行单元：`type` + 类型专属参数 + 通用字段 `delay_ticks` / `chance` / `conditions`。12 个内置效果（9 生效 + 3 消耗）。
3. **选择语义**：同一掉落物只执行**优先级最高的一条**命中规则（priority desc → 条件叶数 desc → 定义序）；规则内效果**顺序全执行**、不回滚。
4. **消耗是效果**：`consume_source` / `consume_catalyst` / `consume_fluid`；规则未声明任何 `consume_*` 时隐式消耗 1 个源物品。
5. **异常隔离**：单个效果抛异常 → 记录 ERROR（规则 id + 效果类型 + 位置）+ 继续后续效果，整体转化仍视为已发生。
6. 后端取消 `ConversionType`；前端保留「模板」这一 UI 概念（一个模板 = 一种效果类型）。

## 考虑过的替代方案
- **保留 ConversionType，仅重构字段**：改动面小，但"类型即结果"的结构无法表达多效果与效果级延迟/概率，扩展成本依旧。否决。
- **多条规则叠加执行**：允许同一掉落物命中多条规则，各自执行。语义复杂且顺序不可预测，冲突时难以解释"为什么这个物品变成这样"。否决（Q39）。
- **效果执行失败整体回滚**：需要给每个世界操作做补偿，1.21.1 原版 API 不提供，成本与风险都过高。否决（Q44）。

## 后果
- 新增一种效果类型 = 一个参数记录 + 一个执行器 + 注册表一行（可选客户端规格一行），框架代码零改动。
- `Rule` 与全部效果/条件参数都是不可变 record，可直接被索引、缓存与跨线程读取。
- 规则文件天然支持"一次触发多效果"，GUI 首版只支持单效果编辑，多效果规则只读展示。
- 旧字段全部重设计、不兼容；迁移走 `/idtw config convert`（见 0016 与迁移指南）。 〔2026-10-03 取代：该转换入口已随 P8 结论删除，见文首〕
