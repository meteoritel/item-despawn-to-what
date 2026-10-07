# 横向系统：类型注册与扁平分发

> 事实来源：`core/api/{TypeDefinition, TypeRegistry, TypeDispatch, RuleFields}.java`、`core/registry/{SimpleTypeRegistry, DuplicateTypeException, RegistryFrozenException}.java`、`core/model/{RuleCodecs, Condition, ConditionNode, ConditionExpression, Effect}.java`、`core/service/BuiltinTypeRegistries.java`。
> 相关模块：[rule-model.md](../modules/rule-model.md)、[type-system.md](../modules/type-system.md)。决策：[ADR-0013](../../../adr/0013-dfu-codec-and-flat-type-dispatch.md)、[ADR-0018](../../../adr/0018-condition-tree-contract.md)。

## 1. 注册表生命周期：注册期可变、冻结后只读

`SimpleTypeRegistry` 内部两个结构：注册期 `LinkedHashMap`（**保留注册顺序**）+ 冻结后 `volatile FrozenView`。是否冻结以 `frozen != null` 判定。

| 操作 | 行为 |
|---|---|
| `register` | 空校验 → 已冻结抛 `RegistryFrozenException` → `putIfAbsent`，冲突抛 `DuplicateTypeException`（**绝不静默覆盖**） |
| `freeze()` | `synchronized`、幂等；构造 `byId`/`ordered`/`ids` 三类不可变视图后用 volatile 安全发布 |
| `all()` / `ids()` | 注册期也返回快照（`List.copyOf` / `unmodifiableSet`），避免调用方见到后续注册 |

并发约定：**注册表单线程注册**（注册期不保证并发读安全），`freeze()` 必须由注册线程调用；冻结后任意线程可并发读。

> 接口 `TypeRegistry` 本身**不承诺只读**；只有 `SimpleTypeRegistry` 保证冻结语义。换实现可能失去该保护。

## 2. 装配顺序固定

`BuiltinTypeRegistries.create()` 固化的顺序：

```text
内置条件类型 → provider 条件类型 → freeze
→ 构建条件表达式 Codec
→ 内置效果类型 → provider 效果类型 → freeze
```

原因：效果记录带**效果级 `conditions`** 字段，需要完整条件表达式 Codec 才能编解码。`ServiceLoader<RuleTypeProvider>` 按 provider 类名排序保证跨端确定性。**任何重复 id、provider 构造或登记异常都使启动失败，不静默忽略。** 第三方若颠倒顺序会导致效果级 conditions 无法解码。

## 3. 扁平类型分发（`TypeDispatch.flat`）与递归条件树

核心机制仍以 `TypeDispatch.flat(registry, typeIdGetter)` 为准：它是自定义匿名 `MapCodec`，把类型专属字段与通用字段放在**同一个 JSON 对象**里（不产生嵌套 `value` 字段）：

```json
// 采用（扁平）
{ "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:stone", "count": 2, "delay_ticks": 20 }
// 拒绝（DFU 默认 dispatch）
{ "type": "...:spawn_entity", "value": { "variant": "item", "item": "minecraft:stone", "count": 2, "delay_ticks": 20 } }
```

- **decode**：读 `type` 字段（`RuleFields.TYPE`）→ 查注册表（非法 id / 未注册返回可读 `DataResult.error`）→ 取该定义 `codec()` 强转 `MapCodec<A>`；
- **未知字段检测**：用 `codec.keys(ops)` 收集本类型允许的键 + `type`，遍历输入对象键，遇不在集合内者立即报错；
- **错误前缀**：未知字段报 `类型 <id> 存在未知字段: <key>`；参数错误报 `类型 <id> 参数错误: <message>`（解决 DFU `intRange/doubleRange` 错误不含字段名的问题）；
- **encode**：由 `typeIdGetter` 写回 `type`；**未注册类型直接抛 `IllegalStateException`**（暴露编程错误，避免写出无法回读的 JSON）。

**条件树接入**：`RuleCodecs.conditionExpressionCodec(conditionTypes)` 不再用「两级 `listOf().xmap` 组组」，而是构造一个**递归条件节点 codec**——叶子 codec 来自 `TypeDispatch.flat(conditionTypes, Condition::type).codec()`；`all_of` / `any_of` / `inverted` 组合节点在一个匿名 `MapCodec<ConditionNode>` 中按 `op` 分派，并通过内部 `NodeCodecHolder` 在构造完成后回填自引用，实现 `terms` / `term` 的递归解析（构造期不能自引用，否则无限递归）。表达式外壳是一个 `Codec<ConditionExpression>`：解码**只接受 JSON 对象**（数组即旧二维 DNF，明确拒绝），编码空表达式时写成恒真的空 `all_of` 以保证可回读。`effectCodec(effectTypes)` 仍直接由 `TypeDispatch.flat` 生成。

### 严格度差异（重要）

| 层级 | 未知字段 | 结果 |
|---|---|---|
| **类型内**（效果/条件叶） | 不在该类型 `keys()` 内 | **ERROR**，拒载该条 |
| **条件树节点** | 不在 `op` / `terms` / `term` / `condition` 内 | **ERROR**（可见 `RuleCodecs` 的 `TERMS_FIELDS` / `INVERTED_FIELDS` / `LEAF_FIELDS` 白名单） |
| **候选结果元素** | 不在 `OUTCOME_FIELDS` 内 | **ERROR** |
| **催化剂成本对象** | 不在 `CATALYST_COST_FIELDS` 内（含 `chance`/`conditions`/`delay_ticks`） | **ERROR** |
| **规则顶层** | 不在 `KNOWN_RULE_FIELDS` 内 | 仅 **WARN**，不阻断加载 |

## 4. Codec 铁律

1. **不得产出 null**：DFU 的 `DataResult` 内部用 `Optional.of`，null 在解码期抛 NPE。可空字段一律 `Optional` 承载，记录构造末步 `orElse(null)`。
2. **`keys()` 必须完备**：新增自定义 `MapCodec` 若 `keys()` 不全，会把合法字段误判为未知字段。递归条件节点 codec 的 `keys()` 返回全部四个字段名。
3. **`CommonFields` 片段返回 `RecordCodecBuilder`**（而非自由 `MapCodec`），因为 `forGetter` 绑定在所属记录类型上。注意：已不含 `negated` 片段，取反由 `inverted` 节点承担。
4. **递归自引用回填**：条件节点 codec 需要引用自身来解析 `terms` / `term`，通过持有者对象在构造后回填 `ref`，避免构造期无限递归。

## 5. 扩展点与踩坑

- 新增类型定义 → [type-system.md](../modules/type-system.md) 第 6 节；第三方 SPI → 同文第 7 节。
- 新增条件节点类型 → 改 `ConditionNode`、`ConditionTrees`、`RuleCodecs.nodeMapCodec` 三处，并同步 `ConditionExpression.isStructurallyValid()` 的形状判断（见 [rule-model.md](../modules/rule-model.md) 扩展点）。
- 踩坑：注册表接口不承诺只读；注册顺序不可颠倒；类型内/条件节点/候选元素/催化剂成本对象未知字段都是硬错误、只有规则顶层才 WARN；编码未注册类型会抛异常（GUI 保存路径必须捕获）；条件树自引用必须在构造后回填。

## 6. 相关

- 类型定义形态与内置类型：[../modules/type-system.md](../modules/type-system.md)
- 规则 Codec 组装与条件树形状：[../modules/rule-model.md](../modules/rule-model.md)
