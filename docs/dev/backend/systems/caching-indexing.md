# 横向系统：索引与缓存

> 事实来源：`core/runtime/{RuleIndex, RuntimeTagLookup, RuntimeClimateSampler, ConversionRuntime}.java`、`core/type/effect/exec/PlaceBlockExecutor.java`。
> 相关模块：[conversion-runtime.md](../modules/conversion-runtime.md)。

## 1. 候选规则索引（`RuleIndex`）

把规则集合整理成"物品 → 候选规则"的查询结构：

| 结构 | 说明 |
|---|---|
| `directIndex` | 直接物品项建索引 |
| `tagRules` + `tagCache` | 标签项**懒展开**并缓存成员 |
| `queryCache` | 按 itemId 缓存候选列表 |

**排序键**（`build`，稳定排序）：`优先级 desc → 条件叶数 desc → 定义序`。`build` 会剔除不可运行规则（`Rule.isRunnable` = enabled 且有有效果）。"条件叶数降序"是同优先级下的**特异性兜底**。

`onItemAdded` 只对 `index.candidates(itemId)` 非空的掉落物建追踪——**没有候选规则的掉落物零成本**。

## 2. 缓存失效：整体重建，不做单点失效

`ConversionRuntime.replaceRules(...)`：

- 用新规则集重建 `RuleIndex`（旧对象整体替换）；
- 清空各维度 `tracked` / `scheduler`；
- 把各维度的 `tags` / `climate` 置 **null**（强制下次使用时按新数据包重建，**防止读到旧数据包标签**）。

即所有缓存**随 reload 整体丢弃**，不单独失效。这保证 reload 后条件求值不会命中旧数据。

## 3. 标签缓存（`RuntimeTagLookup`）

- 键 `kind:tagId`（item/block/entity/biome/fluid/mob_effect 六类），值 `Set<ResourceLocation>`。
- **标签不存在时缓存空集**（避免重复查询同一个空标签）。
- 求值器不直接访问注册表，统一经 `ConditionContext.tags()`；缓存与 reload 失效由运行时管理。

## 4. 气候缓存（`RuntimeClimateSampler`）

- 按 **quart 坐标**采样，访问序 `LinkedHashMap`，**上限 4096 项**，超限淘汰最旧。
- 仅对 `MultiNoiseBiomeSource`（主世界/下界）有效，其它群系源返回 null。
- 条件求值不允许自己起缓存，统一经 `ConditionContext.climate()`。

## 5. 静态预计算（`PlaceBlockExecutor.OFFSETS`）

- 方块形状偏移按 `ShapeKey(shape, radius)`（**不含维度**）预计算，首次计算后 `List.copyOf` 复用。
- 这是跨调用共享的静态缓存，不随 reload 失效（只依赖形状与半径）。

## 6. 有界性总览

| 缓存 | 上限 | 失效时机 |
|---|---|---|
| `RuleIndex.tagCache` / `queryCache` | 随注册表大小 | reload（整体重建） |
| `RuntimeTagLookup` | 随标签数量 | reload（置 null） |
| `RuntimeClimateSampler` | **4096**（淘汰最旧） | reload（置 null） |
| `PlaceBlockExecutor.OFFSETS` | 随 (shape, radius) 组合 | 进程内静态，不失效 |
| `DebugMeasurements` | **12000** 样本 | 每轮场景 |

## 7. 扩展点与踩坑

- 新增按物品/标签查询的结构 → 扩 `RuleIndex`（保持排序键与懒展开）。
- 新增依赖数据包的缓存 → 必须在 `replaceRules` 中一并置空，否则 reload 后读到旧数据。
- 踩坑：缓存"整体重建"是刻意设计，不要为单条规则加局部失效路径。

## 8. 相关

- 运行时总控：[../modules/conversion-runtime.md](../modules/conversion-runtime.md)
- 加载后重建索引：[../modules/rule-loading.md](../modules/rule-loading.md)、[../flows/rule-loading-flow.md](../flows/rule-loading-flow.md)
