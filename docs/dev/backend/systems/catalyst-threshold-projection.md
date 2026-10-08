# 横向系统：催化剂门槛运行投影

> 事实来源：`core/runtime/CatalystThresholdProjection.java`、`core/runtime/RuleIndex.java`、`core/type/condition/CatalystPresentCondition.java`、`core/model/Effect.java`、`core/type/effect/ConsumeCatalystEffect.java`。
> 行为依据：[ADR-0027](../../../adr/0027-catalyst-threshold-runtime-projection.md)、[编辑器四页职责规格](../../../spec/editor-page-responsibilities-2026-10-07.md)（门槛跟随消耗量）。
> 相关：[conversion-runtime.md](../modules/conversion-runtime.md)、[caching-indexing.md](caching-indexing.md)、[type-system.md](../modules/type-system.md)、[rule-loading-flow.md](../flows/rule-loading-flow.md)、[编辑页人工验收清单](../../../guide/editor-page-acceptance-2026-10-07.md) EA-10～EA-13。

## 1. 一句话

`catalyst_present` 的 `count` **可以留空**（未填写）；运行期把它解析成当前消耗配置下的**有效门槛**。保存与序列化**始终保留「未填写」**，投影出的派生值**不写回声明**。

## 2. 声明侧语义（`CatalystPresentCondition`）

| 项 | 说明 |
|---|---|
| 形状 | `record CatalystPresentCondition(List<TaggedId> items, @Nullable Integer count)`（`CatalystPresentCondition.java:31`） |
| 编解码 | `Codec.INT.optionalFieldOf(FIELD_COUNT)` → 字段缺失即 `null`，编码时同样省略该字段（`:46-49`） |
| 取值域 | `MIN_COUNT=1`、`MAX_COUNT=64`；`count != null` 时才校验范围，留空合法（`:68-74`） |
| `DEFAULT_COUNT=1` | **不是解码默认值**，只是「门槛留空且无匹配消耗配置」时的兜底（`:40-43`） |
| `items` | 必填非空；`count` 与 `items` 无关，语义由求值器按有效门槛比较「方块格 1×1×1 内命中堆叠数 ≥ 门槛」 |

JSON 最小写法：`{ "type": "itemdespawntowhat:catalyst_present", "items": ["minecraft:blaze_powder"] }`——没有 `count` 即「未填写」。

## 3. 有效门槛解析规则（`resolveThreshold`）

纯函数、无副作用；编辑器的「默认：N」提示与运行期投影**共用这一份判定**。

| 优先级 | 门槛所在位置 | 取谁的 `count` |
|---|---|---|
| 1 | 规则级条件叶 | 同规则的 `catalyst_cost`（`items` 集合相同才匹配） |
| 1 | 效果级条件叶 | **承载该叶的效果自身**，且必须是 `consume_catalyst`（`scopeEffect instanceof ConsumeCatalystEffect`） |
| 2 | 同作用域无匹配 | 在**整条规则**内收集 `items` 相同的催化剂消耗配置（`catalyst_cost` + 顶层 `effects` + `outcomes` 内 effects 的 `consume_catalyst`）；**恰好只有一个**才采用它的 `count` |
| 3 | 其余情况 | `DEFAULT_COUNT = 1` |

- `items` 相同 = `HashSet` 集合相等，**顺序无关**；两侧都必须非空（`:104-109`）。
- 多匹配（多个不同对象/不同作用域）或零匹配一律退回 1，**绝不挑「任意第一条」**（`:78-100`）。
- 非 `consume_catalyst` 效果的本地叶（如 `explosion` 的本地条件）只走第 2/3 条，不会去认「附近某条消耗动作」。
- `positive(count)`：`count <= 0` 视为 1（`:112-114`）——**正数化兜底**，避免 `count<=0` 把门槛误判成「永不通过」；越界配置**应由校验层拒载**，此处仅作防御。

| 声明（`C` = 消耗量，`T` = 门槛） | 有效门槛 |
|---|---|
| `C=2`，`T` 留空 | 2 |
| 接着把 `C` 改成 4 | 4（`T` 仍未填写，跟随新的 `C`） |
| 显式填 `T=6`，`C=2` | 6（门槛只用于存在检查，通过后每轮仍只扣 2 件） |
| 随后改 `C` 或编辑 `T` | 改 `C` 不变（仍 6）；改 `T` 不反向改 `C` |
| 清空 `T` | 重新跟随当前 `C` |
| 只有 `catalyst_present`、没有匹配的消耗配置 | 1（不足 1 件不通过、够 1 件通过） |

## 4. 投影的执行时机与失效

| 项 | 结论 |
|---|---|
| 唯一入口 | `RuleIndex.build`（`RuleIndex.java:53-63`）：入索引前对每条可运行规则调用 `CatalystThresholdProjection.project(entry.value())`，返回同一实例则不换 `LoadedRule` |
| 共用方 | 普通运行 `ConversionRuntime.replaceRules`（`ConversionRuntime.java:131`）与 debug 场景 `DebugScenarioDefinition.create`（`DebugScenarioDefinition.java:80`）**都只经这一处**；不新增第二套规则索引 |
| 快速路径 | `hasUnfilledThreshold(rule)` 为假时原样返回同一个 `Rule`——**零分配**，普通规则完全不受影响（`:44-47`） |
| 重建粒度 | 只替换条件树与效果列表；`id`/`enabled`/`priority`/`displayName`/`notes`/`source`/`triggers`/成本/组合模式等原样保留，排序键不受影响（`:55-57`）。子节点列表仅在确有变化时新建 + `List.copyOf`（`:203-216`） |
| 失效 | 投影结果只存在于 `RuleIndex` 内那份 `Rule` 上；reload（数据包 / `/idtw config reload` / 保存后重建）→ `replaceRules` 重建索引 → 按新消耗配置重算。**没有独立缓存，也不做单点失效**（与 [caching-indexing.md](caching-indexing.md) §2 的整体重建一致） |
| 编辑器提示 | `resolveThreshold(rule, items, scopeEffect)` 是同一份判定的纯函数；界面「默认：N」提示必须与其结果一致（提示的显示时机见 [ADR-0027](../../../adr/0027-catalyst-threshold-runtime-projection.md) 与编辑页规格） |

## 5. 公开 API 扩展：`Effect.withConditions`

`core/model/Effect.java:26-27` 新增：

```java
// 返回仅替换效果级条件、其余参数原样保留的新实例；运行期门槛投影据此重建真实效果类型
Effect withConditions(@Nullable ConditionExpression conditions);
```

- 10 个内置效果全部实现：`spawn_entity`（`SpawnEntityEffect.java:47`）、`place_block`（`:129`）、`loot_table`（`:75`）、`lightning`（`:69`）、`explosion`（`:78`）、`arrow_rain`（`:162`）、`weather`（`:91`）、`consume_source`（`:69`）、`consume_catalyst`（`:102`）、`consume_fluid`（`:78`）。
- **理由**：执行器 `EffectExecutor<P>` 按**具体效果类型**强转/分发，且 `oneShot` 等语义挂在实现类上；用包装 record 顶替会破坏类型判定与结算语义。因此选择扩展公开接口，由实现类重建真实类型。
- 未扩展 `ConditionContext`，运行期条件求值不承担「查当前规则消耗配置」的职责（ADR-0027 的既定取舍）。

## 6. 不变量与边界

- **不反写声明**：投影只影响运行期对象；保存/快照仍按声明序列化「未填写」，因此改 `C` 后即使不重新保存规则，reload 重建索引即生效。
- **不动世界、不动求值语义**：条件求值仍是纯谓词；投影只改条件叶的 `count`。
- **只有一处索引、一处投影**：新增依赖消耗配置的派生值时扩 `CatalystThresholdProjection` + 在 `RuleIndex.build` 挂接，不要另建索引或运行期缓存。
- **不做旧数据迁移**（项目未 release）：旧声明若显式写了 `count` 就按显式值处理。
- 相关验收：`EA-10`（T 留空跟随 C）、`EA-11`（显式 T 与清空 T）、`EA-12`（无匹配消耗配置默认 1）、`EA-13`（普通运行 / 保存重开 / debug 同一声明同一门槛 + 配置更新后失效）。
