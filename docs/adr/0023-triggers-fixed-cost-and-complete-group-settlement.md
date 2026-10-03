# ADR-0023：消失方式触发、固定成本与完整转化组结算

- 状态：已实施（2026-10-03，第二轮后端改造；提交 `63eea8b`），已通过用户实机验收。
- 实现见：`core/model/TriggerKind`、`CombinationMode`、`CatalystCost`、`OutcomeCandidate`、`Rule`；`core/runtime/ConversionSettlement`、`CatalystReservations`、`SettlementLedger`、`SettlementRecord`、`SettlementRecovery`、`RoundRobinCursors`；`core/state/DropState`。
- 替代范围：扩展 [ADR-0012](0012-rule-model-and-effect-list.md) 的规则模型（新增 `triggers` / `source_cost` / `catalyst_cost` / `combination` / `outcomes` / `schema_version`）；取代 [ADR-0015](0015-runtime-scheduling-and-tracking.md) 决策 3 的「按堆叠预估 rounds」式结算。
- 依据：历史规划 `docs/archive/backend-round-2/PLAN.md` 与其 `0001-conversion-commitment`（本地归档，不纳入版本控制）。

## 背景

旧模型只有「自然消失」一种触发，且按 `max(1, 源堆叠 / 每轮消耗)` **预估**轮数执行：产出不足不会回减消耗，源实体一旦提交就带永久 `converted` 标记，也没有候选结果与中断返还的概念。这既无法表达「被火烧 / 岩浆 / 仙人掌销毁」这类即时转化，也无法在冷却、容量或空间受限时给出可解释、可守恒的账目。

## 决策

1. **消失方式集合 `triggers`**：`natural`（默认，未声明时）/ `fire` / `lava` / `cactus`。火 / 岩浆 / 仙人掌由两端**最小 Mixin** 接 `ItemEntity` 致死分支（原版物品销毁回调之前）接管，**即时触发、不等待**；按**最终致死伤害**归因（岩浆优先于火，未知伤害不归为火）。接触危险源、受伤但存活、原版免疫均不触发。Mixin 只做入口，规则匹配与结算仍在 common。
2. **固定成本**：`source_cost` 必须是**正整数**（取消 0 成本）；`catalyst_cost` 为对象 `{items, count, radius}`，整数写法明确报错。源与催化剂作为**固定成本**支付，不携带 `chance` / `conditions` / `delay_ticks`。流体只作**存在条件** + 独立移除动作，不计数量、不预留份数。
3. **完整转化组**：每轮 = 一组，组数以 `floor(源量 / 每组源成本)` 为上界；**不按比例拆半组**，不足一组成本不强制产出。组内多个产出效果受**共同的最小完整容量**约束；容量、催化剂或半径内位置不足在**该组开始之前**处理。效果条件不满足或概率落空属于正常结果，仍支付该组，不改选候选、不返还——概率落空不是免费重试。
4. **候选结果 `outcomes` + 组合模式 `combination`**：一轮一组只选**一个**候选；`round_robin`（默认，按游标依次选取）或 `priority`（取第一个可执行完整一组的候选）。轮询游标按**规则 id + 维度**共享并持久化，不随换源清空。未声明 `outcomes` 时，顶层 `effects` 由 `Rule.effectiveOutcomes()` 隐式映射为唯一候选 `default`（兼容旧格式）。`effects` 与 `outcomes` 不得同时声明。
5. **一次性世界效果**（lightning / explosion / arrow_rain / weather）不参与容量计算；仅含一次性效果的候选对同一源最多尝试一组。
6. **数量账目守恒**：设源量 N、每组源成本 c、实际开始组数 g，则 `N = c×g + 已交付返还 + 待交付返还`。已开始组支付完整成本；突发动作失败**记录真实成功量**、不补做、不重放，允许「已付成本但产出不完整」。
7. **中断与持久返还**：区块 / 维度卸载或停服时终止已开始组的剩余动作，只返还**尚未开始部分**；一时无法加入世界的返还保存为待返还记录（`SettlementRecord`），由 `SettlementRecovery` 在重载后交付；已开始组不恢复执行。
8. **产物与返还物状态**（`core/state/DropState`）：新产物默认 **2 秒**保护（仅防火 / 岩浆 / 仙人掌）与 **5 秒**转化冷却；未消耗源返还物在本实体生命周期内**永久**防上述三类伤害且**永久禁转**。状态存**实体层**（不随 `ItemStack` 进背包），服务端 tick 计时、重启保留；拾取后重丢是普通掉落物，不继承永久状态；状态不兼容的掉落物禁止合并。
9. **位置策略**：候选级 `safe_spawn`（生物安全生成，水平 5×5、上下各 2 格，找不到回原点）与 `fill_origin`（方块起点填充，默认开启）开关；返还位置搜索受公共调度预算分步约束，必要时在限高以上生成。

## 考虑过的替代方案

- **回滚已执行的世界效果 / 恢复未完成组**：需要为每个世界操作做补偿，原版 API 不提供，复杂度与风险都过高。否决。
- **保留 0 源成本**：会让「不消耗输入」和「消耗但概率跳过后免费重试」都变得可表达，账目无法守恒。否决（不希望消耗的输入应作为催化剂）。
- **把流体按数量换算可执行组数**：会把「存在条件」和「数量成本」两类语义混在一起，且需要扫描流体总量。否决（流体只作存在条件与移除动作）。

## 后果

- 规则 JSON 契约 **breaking**：旧规则未声明 `triggers` 时仅自然消失；`source_cost` 必须为正、`catalyst_cost` 改为对象写法、`effects` 与 `outcomes` 互斥、新增 `schema_version`（当前仅支持 1）。内置数据包已同步更新，**不提供自动迁移**（`/idtw config convert` 已退役，见 [ADR-0018](0018-condition-tree-contract.md) 文首注记）。
- 后续 GUI 需能表达触发、固定成本、候选与组合模式；旧 GUI 无法表达新结构时**拒绝可能丢字段的保存**（见 [edit-protocol.md](../dev/backend/modules/edit-protocol.md)）。
- 数量守恒与「已开始组不恢复」成为可观测行为，调试场景与结算示例以此为准。
