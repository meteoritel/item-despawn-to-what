# ADR-0018：条件表达式改为递归条件树与四态求值

- 状态：已实施；实现见 `core/model/ConditionNode.java`、`core/model/ConditionExpression.java`、`core/model/ConditionTrees.java`、`core/model/ConditionLimits.java`、`core/runtime/ExpressionTreeEvaluator.java`。
- 替代范围：取代此前的析取范式（DNF 二维数组与叶级 `negated`）设计。

## 背景

旧条件表达式是「二维数组」：外层是条件组的析取（OR），内层是条件叶的合取（AND）。它有两个硬伤：

1. **表达力与编辑体验不足**：OR/AND 只能固定嵌套两层，无法表达 `(A 且 (B 或 C)) 且 !D` 这类结构；前端「加一个 OR 组」的交互也因此被钉死在两层上。
2. **叶级 `negated` 让取反吞掉异常**：取反绑在条件叶上，就把「条件不成立」与「条件无法判定」压成同一个布尔值——取反一旦落在「求值失败」上，失败会被当成「通过」，最终可能错误触发转化。

条件系统需要彻底重构：改为任意嵌套的条件树，并把「取反」从叶字段提升为树节点。

## 决策

1. **递归节点模型**：`public sealed interface ConditionNode permits AllOf, AnyOf, Inverted, Leaf`，四个 record 分别是 `AllOf(List<ConditionNode> terms)`、`AnyOf(List<ConditionNode> terms)`、`Inverted(ConditionNode term)`、`Leaf(Condition condition)`。紧凑构造器只做 `terms = List.copyOf(terms)`，**不做非空校验**。
2. **表达式只剩根节点**：`public record ConditionExpression(@Nullable ConditionNode root)`，`EMPTY = new ConditionExpression(null)`；`ConditionGroup` 类删除，`groups()` 访问器删除。统计方法为 `isEmpty()` / `leafCount()` / `nodeCount()` / `depth()`（根深度 = 1，空表达式 = 0）/ `isStructurallyValid()`。
3. **叶级取反删除**：`Condition` 只保留 `ResourceLocation type()` 与字段名常量 `TYPE_FIELD` / `NEGATED_FIELD`；`CommonFields.negated(...)` 与 `DEFAULT_NEGATED` 删除；内置条件 record 不再有 `boolean negated` 字段。取反只能写成 `inverted` 节点。解码时叶内出现 `negated` **必须报错**（提示改用 inverted），禁止静默忽略。
4. **规范 JSON 形状**：`{"op":"all_of","terms":[...]}` / `{"op":"any_of","terms":[...]}` / `{"op":"inverted","term":{...}}` / `{"op":"leaf","condition":{"type":"...",<类型专属字段平铺>}}`。无条件时 `conditions` 可**省略**（解码回落空表达式）；编码空表达式写成 `{"op":"all_of","terms":[]}`，不输出 `null`；未知 `op` 报错并列出允许值。
5. **空组合节点可构造、可序列化**：空 `all_of`/`any_of` 是编辑中间态，构造器与编码都必须允许；非空性只在 `isStructurallyValid()` 与解码期强制（`terms` 缺失或为空即报错）。
6. **限额统一**：`ConditionLimits` 定义叶数、节点数、深度、效果数、源条目数与展示名长度上限 `MAX_LEAVES=128`、`MAX_NODES=256`、`MAX_DEPTH=16`、`MAX_EFFECTS=32`、`MAX_SOURCE_ENTRIES=256`、`MAX_DISPLAY_NAME_CODEPOINTS=128`，其中节点数与深度引用契约层解码预算的同一常量；`RuleValidation` 与编辑器引用同一组常量。限额**逐表达式**计算，规则级与每个效果级条件树各自受限，不跨表达式累加。
7. **四态求值**：`ConditionResult{MATCH, NO_MATCH, UNAVAILABLE, ERROR}` 与 `Evaluability{AVAILABLE, UNAVAILABLE}`。`ConditionType` 提供 `default Evaluability evaluability(P params, ConditionContext context) { return AVAILABLE; }`，供「当前上下文不足以判定」（例如区块未加载）使用。求值按下列规则短路聚合：`inverted` 只交换 MATCH/NO_MATCH，`UNAVAILABLE`/`ERROR` **原样穿透**；组合节点聚合时 `UNAVAILABLE` 优先于 `ERROR`；空 `all_of` → MATCH、空 `any_of` → NO_MATCH。
8. **可求值性门禁**：`surrounding_blocks` 的可求值性由该类型的 `evaluability(...)` 承担（以 `ChunkPresence.containsArea(level, pos, 1)` 判定区块是否加载），求值器不硬编码具体条件类型。
9. **只有 `MATCH` 通过根**：`UNAVAILABLE`/`ERROR` 一律不触发效果；`matches(...)` 是 `evaluate(...) == MATCH` 的便捷入口。
10. **旧格式零兼容**：`conditions` 为数组、对象含 `groups`、叶内 `negated` 一律返回明确错误信息，不做静默迁移，也不提供自动转换。

## 理由

- 任意嵌套树同时解决了表达力与前端交互问题：编辑器只需递归渲染 `all_of`/`any_of`/`inverted`/`leaf` 四种节点，`ConditionExpression` 成为唯一数据模型。
- 取反变成显式节点后，`inverted` 只在 MATCH/NO_MATCH 之间交换，`UNAVAILABLE`/`ERROR` 原样穿透，异常与「无法判定」不再被取反吞掉。
- 区分 `UNAVAILABLE` 与 `ERROR` 让「区块未加载」这类暂时不可判定与「条件实现坏了」分开处理：前者不当作失败、也不触发效果，后者记录日志并可被观测。
- 空组必须能构造与序列化，否则编辑器在「刚点了加组、还没加子节点」的中间态无法保存草稿；把非空性收敛到结构校验与解码期，避免构造器抛异常打断编辑。
- `leaf` 内部沿用既有 `TypeDispatch.flat` 扁平分发，第三方条件类型的参数格式与旧版一致，迁移成本只在「节点外壳」上。

## 后果

- `RuleValidation`、`RuleReferenceValidator` 走 `ConditionTrees.forEachLeaf(...)` 定位字段（字段路径形如 `conditions.terms[0].condition.dimensions`）；调试场景的规则与条件树来自场景 JSON，同样使用条件树格式；`common/src/main/resources/data/itemdespawntowhat/idtw/` 下的样本使用新树格式。
- 旧格式 JSON 在解码期显式报错，不做静默迁移；旧 JSON 原样保留但不再加载。
- 第三方条件类型不受影响：`evaluability` 有默认实现，未实现即为「恒可求值」；但任何条件类型都不得再返回叶级取反语义。
- 深度/节点/叶上限（16/256/128）成为保存前必须拦截的硬规则，编辑器与校验共用同一组常量。
- 组合节点的聚合顺序（`UNAVAILABLE` 优先 `ERROR`）成为可观测行为，日志里会看到被跳过的 ERROR 标记，便于排查「条件树为什么没通过」。
