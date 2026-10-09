# 横向系统：催化剂门槛运行投影

> 事实来源：`CatalystThresholdProjection`、`RuleIndex`、`CatalystPresentCondition`、`CatalystPresentEvaluator`、`Effect.withConditions`。
> 相关：[规则模型](../modules/rule-model.md)、[转化运行时](../modules/conversion-runtime.md)、[编辑器职责](../../internals/editor-pages-contract.md)。

## 声明与有效数量

`catalyst_present.items` 是必填非空的物品或标签列表。每个引用分别累计所在方块格内的掉落物数量，排除源实体；全部达到各自门槛才通过。

`counts` 为各引用的门槛对象，数量范围为 1..64。引用未在 `counts` 中配置时，先采用显式的 `count`；两者均未配置时，由运行期投影解析。`count` 缺失解码为 `null`，编码仍省略。投影不会反写声明。

```json
{
  "type": "itemdespawntowhat:catalyst_present",
  "items": ["minecraft:blaze_powder", "minecraft:redstone"],
  "counts": {"minecraft:blaze_powder": 2, "minecraft:redstone": 4}
}
```

## 缺省门槛解析

`resolveThreshold(rule, items, scopeEffect, reference)` 按单个引用解析缺省门槛。已配置的 `counts` 值和显式 `count` 均保持声明值。

| 优先级 | 条件作用域 | 数量来源 |
|---|---|---|
| 1 | 规则级条件 | `catalyst_cost.items` 包含该引用时，采用 `countFor(reference)` |
| 1 | 效果级条件 | 承载效果为 `consume_catalyst` 且 items 集合与条件相同，采用效果的 `countFor(reference)` |
| 2 | 同作用域无匹配 | 全规则中 items 集合相同的催化剂消耗配置恰好只有一个，采用该配置的 `countFor(reference)` |
| 3 | 零匹配或多匹配 | 默认 1 |

集合比较忽略顺序，两侧均须非空。`countFor` 优先读取 `counts`，缺省采用配置的 `count`。三个参数的重载返回列表首个引用的缺省数量，供单值回退使用。

门槛与消耗相互独立：显式门槛为 6、每轮成本为 2 时，存在检查要求 6，支付每轮只扣 2。输入页首次开启消耗时，把各物品当前门槛复制为其成本；随后编辑成本不会改写门槛。

## 投影时机与保存

`RuleIndex.build` 在入索引前调用 `project`，覆盖规则级条件、顶层效果的条件和候选结果内效果的条件。只重建需要补齐数量的条件节点与承载对象，无变化时复用原实例。

普通运行和 debug 场景共用该投影。reload 或保存后重建规则索引时重新解析，运行期求值直接读取投影后的树，无独立缓存。保存与快照继续按原声明编解码，缺省门槛不会变成显式配置。

编辑器数量控件使用同一解析函数展示缺省值，未编辑时保留原始字段缺失状态。草稿无法解码时，展示显式 `count` 或默认 1。

## 效果条件重建

`Effect.withConditions(@Nullable ConditionExpression)` 返回只替换条件、保留其余参数的新实例。全部内置效果均实现该接口，逐引用 `counts` 也保留。执行器依赖具体效果类型，投影使用真实类型重建。

投影只处理派生数量；世界读取由条件求值器承担，库存分配、预留与支付由结算层承担。
