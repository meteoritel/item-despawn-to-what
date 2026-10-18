# 横向系统：催化剂门槛运行投影

> 事实来源：`CatalystThresholdProjection`、`RuleIndex`、`CatalystPresentCondition`、`CatalystPresentEvaluator`、`Effect.withConditions`。
> 相关：[规则模型](../modules/rule-model.md)、[转化运行时](../modules/conversion-runtime.md)、[编辑器页面与页签](../../internals/ui-screen.md)。

## 声明与默认门槛

`catalyst_present.items` 为必填非空的物品或标签列表，每个引用均需达到门槛。叶级 `counts` 优先，其次显式 `count`；均未配置时，`resolveThreshold(rule, reference)` 读取规则 `catalyst_cost.counts` 对应引用，缺省为 1。实际消耗量 `consume_counts` 不参与门槛解析。

所有催化剂条件与支付使用同一规则半径：有 `catalyst_cost` 时取其 `radius`，否则取默认 1。半径是运行期投影属性，不写入条件叶 JSON。查询排除源实体；跨未加载区块的查询返回不可求值。

## 投影与保存

`RuleIndex.build` 在入索引前调用 `project`，覆盖规则级条件、顶层效果条件和候选结果内效果条件。投影重建受影响的运行期条件及承载对象，不反写保存声明；reload 与保存后重建索引时重新解析。

`Effect.withConditions` 重建真实效果类型，保留其他参数。编辑器使用同一默认门槛函数，未编辑字段保留原始缺失状态。

输入页由 `catalyst_cost` 编辑门槛、实际消耗与共享半径。开启消耗时将当前门槛复制到 `consume_counts`，之后两行分别编辑；共享半径位于卡片最下方。逐组库存分配、门槛复核与支付见转化运行时文档。
