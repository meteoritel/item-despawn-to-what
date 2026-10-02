# ADR-0018：条件表达式改为递归条件树与四态求值

- 状态：已实施（P2，2026-10-02）；实现见 `core/model/ConditionNode.java`、`core/model/ConditionExpression.java`、`core/model/ConditionTrees.java`、`core/model/ConditionLimits.java`、`core/runtime/ExpressionTreeEvaluator.java`。
- 依据：[实施契约 §2](../plan/plan-frontend-rewrite-contract.md)（P2 冻结）、[规划书 §5](../plan/plan-frontend-rewrite.md)（§5.1 新模型与规范格式、§5.2 求值与异常、§5.3 校验与限额）。
- 替代范围：取代 [ADR-0007](0007-condition-expression-dnf.md) 的析取范式（DNF 二维数组）与叶级 `negated` 设计。

> **局部已被取代（2026-10-03，P8 结论）**：第 10 条决策与「后果」中关于 `RuleConvertService`「暂停」的表述已失效——旧格式转换链路已**整类删除**（退役 `/idtw config convert`），不再计划按新格式重写映射逻辑。见 [迁移评估 §5](../plan/v1.2.1-migration-evaluation.md) 与 [更新说明](../guide/update-notes.md)。本文其余结论不受影响。

## 背景

旧条件表达式是「二维数组」：外层是条件组的析取（OR），内层是条件叶的合取（AND）。它有两个硬伤：

1. **表达力与编辑体验不足**：OR/AND 只能固定嵌套两层，无法表达 `(A 且 (B 或 C)) 且 !D` 这类结构；前端「加一个 OR 组」的交互也因此被钉死在两层上。
2. **叶级 `negated` 让取反吞掉异常**：旧求值器在条件抛异常时捕获后判 `false`，再连同 `negated` 一起取反，结果是 `NOT(ERROR)` 会变成「通过」，把「无法判定/求值失败」误当成「条件不成立」，最终可能错误触发转化。

规划书 §5 要求条件系统彻底重构：改为任意嵌套的条件树，并把「取反」从叶字段提升为树节点。

## 决策

1. **递归节点模型**（契约 §2.1）：`public sealed interface ConditionNode permits AllOf, AnyOf, Inverted, Leaf`，四个 record 分别是 `AllOf(List<ConditionNode> terms)`、`AnyOf(List<ConditionNode> terms)`、`Inverted(ConditionNode term)`、`Leaf(Condition condition)`。紧凑构造器只做 `terms = List.copyOf(terms)`，**不做非空校验**。
2. **表达式只剩根节点**：`public record ConditionExpression(@Nullable ConditionNode root)`，`EMPTY = new ConditionExpression(null)`；`ConditionGroup` 类删除，`groups()` 访问器删除。统计方法为 `isEmpty()` / `leafCount()` / `nodeCount()` / `depth()`（根深度 = 1，空表达式 = 0）/ `isStructurallyValid()`。
3. **叶级取反删除**：`Condition` 只剩 `ResourceLocation type()`；`CommonFields.negated(...)` 与 `DEFAULT_NEGATED` 删除；10 个内置条件 record 去掉 `boolean negated` 字段。取反只能写成 `inverted` 节点。解码时叶内出现 `negated` **必须报错**（提示改用 inverted），禁止静默忽略。
4. **规范 JSON 形状**（契约 §2.5）：`{"op":"all_of","terms":[...]}` / `{"op":"any_of","terms":[...]}` / `{"op":"inverted","term":{...}}` / `{"op":"leaf","condition":{"type":"...",<类型专属字段平铺>}}`。无条件时**省略** `conditions` 字段，不得输出 `null`；未知 `op` 报错并列出允许值。
5. **空组合节点可构造、可序列化**：空 `all_of`/`any_of` 是编辑中间态，构造器与编码都必须允许；非空性只在 `isStructurallyValid()` 与解码期强制（`terms` 缺失或为空即报错）。
6. **限额统一**（契约 §2.3）：`ConditionLimits` 定义 `MAX_LEAVES=128`、`MAX_NODES=256`、`MAX_DEPTH=16`、`MAX_EFFECTS=32`、`MAX_SOURCE_ENTRIES=256`、`MAX_DISPLAY_NAME_CODEPOINTS=128`；`RuleValidation` 里写死的 128/32 改为引用常量。限额**逐表达式**计算，规则级与每个效果级条件树各自受限，不跨表达式累加。
7. **四态求值**（契约 §2.6）：`ConditionResult{MATCH, NO_MATCH, UNAVAILABLE, ERROR}` 与 `Evaluability{AVAILABLE, UNAVAILABLE}`。`ConditionType` 增加 `default Evaluability evaluability(P params, ConditionContext context) { return AVAILABLE; }`，供「当前上下文不足以判定」（例如区块未加载）使用。求值按契约 §2.6 的 10 条规则短路聚合：`inverted` 只交换 MATCH/NO_MATCH，`UNAVAILABLE`/`ERROR` **原样穿透**；组合节点聚合时 `UNAVAILABLE` 优先于 `ERROR`；空 `all_of` → MATCH、空 `any_of` → NO_MATCH。
8. **可求值性门禁迁移**：`surrounding_blocks` 的 `LoadedChunks.containsArea(level, pos, 1)` 从 `ExpressionEvaluator` 移入该条件的 `evaluability(...)`，求值器不再硬编码具体条件类型。
9. **只有 `MATCH` 通过根**：`UNAVAILABLE`/`ERROR` 一律不触发效果；`matches(...)` 保留为 `evaluate(...) == MATCH` 的便捷入口，供旧调用点过渡。
10. **旧格式零兼容**：`conditions` 为数组、对象含 `groups`、叶内 `negated` 一律返回明确错误信息，不做静默迁移；旧格式的一次性转换链路（`RuleConvertService`）已随之**退役**（2026-10-03，P8 结论：不再重写映射逻辑，见文首取代注记）。

## 理由

- 任意嵌套树同时解决了表达力与前端交互问题：编辑器只需递归渲染 `all_of`/`any_of`/`inverted`/`leaf` 四种节点，`ConditionExpression` 成为唯一数据模型。
- 取反变成显式节点后，异常与「无法判定」不再被取反吞掉，运行期行为与校验结论一致（这是旧实现被判定为实现缺陷的直接原因）。
- 区分 `UNAVAILABLE` 与 `ERROR` 让「区块未加载」这类暂时不可判定与「条件实现坏了」分开处理：前者不当作失败、也不触发效果，后者记录日志并可被观测。
- 空组必须能构造与序列化，否则编辑器在「刚点了加组、还没加子节点」的中间态无法保存草稿；把非空性收敛到结构校验与解码期，避免构造器抛异常打断编辑。
- `leaf` 内部沿用既有 `TypeDispatch.flat` 扁平分发，第三方条件类型的参数格式与旧版一致，迁移成本只在「节点外壳」上。

## 后果

- 所有旧调用点必须改写：`RuleValidation`、`RuleReferenceValidator` 走 `ConditionTrees.forEachLeaf(...)` 定位字段（字段路径形如 `conditions.terms[0].condition.dimensions`）；`DebugScenarioDefinition` 动态生成的表达式改用 `leaf` 节点；`common/src/main/resources/data/itemdespawntowhat/idtw/` 下的样本已改写为新树格式。
- 旧格式 JSON（含用户磁盘上的历史文件）不会被静默兼容，必须显式报告；`RuleConvertService` 的「保持暂停」已变为**删除**——旧 JSON 原样保留但不再加载。 〔2026-10-03 取代，见文首〕
- 第三方条件类型不受影响：`evaluability` 有默认实现，未实现即为「恒可求值」；但任何条件类型都不得再返回叶级取反语义。
- 深度/节点/叶上限（16/256/128）成为保存前必须拦截的硬规则，编辑器与校验共用同一组常量。
- 组合节点的聚合顺序（`UNAVAILABLE` 优先 `ERROR`）成为可观测行为，日志里会看到被跳过的 ERROR 标记，便于排查「条件树为什么没通过」。
