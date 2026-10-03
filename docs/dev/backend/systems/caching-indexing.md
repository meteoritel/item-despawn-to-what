# 横向系统：索引与缓存

> 事实来源：`core/runtime/{RuleIndex, RuntimeTagLookup, RuntimeClimateSampler, ConversionRuntime}.java`、`core/type/effect/exec/PlaceBlockExecutor.java`、`core/catalog/**`、`core/network/catalog/RuleCatalogService.java`。
> 相关模块：[conversion-runtime.md](../modules/conversion-runtime.md)；候选目录的编辑协议见 [edit-protocol.md](../modules/edit-protocol.md)。

## 1. 候选规则索引（`RuleIndex`）

把规则集合整理成「物品 → 候选规则」的查询结构：

| 结构 | 说明 |
|---|---|
| `directIndex` | 直接物品项建索引 |
| `tagRules` + `tagCache` | 标签项**懒展开**并缓存成员 |
| `queryCache` | 按 itemId 缓存候选列表 |

**排序键**（`build`，稳定排序）：`优先级 desc → 条件叶数 desc → 定义序`。其中「条件叶数」= `Rule.complexity()` = `conditions.leafCount()`；`build` 会剔除不可运行规则（`Rule.isRunnable()` = `enabled` 且 `effectiveOutcomes()` 非空）。同优先级下的「条件叶数降序」是**特异性兜底**。

`ConversionRuntime.onItemAdded` 只对 `index.candidates(itemId)` 非空的掉落物建追踪——**没有候选规则的掉落物零成本**。

## 2. 缓存失效：整体重建，不做单点失效

`ConversionRuntime.replaceRules(...)` 在 reload 时：

- 用新规则集重建 `RuleIndex`（旧对象整体替换）；
- 清空各维度 `tracked`；
- 把各维度的 `tags` / `climate` 置 **null**（强制下次使用时按新数据包重建，**防止读到旧数据包标签**）；
- 对每个维度 `scheduler.cancelRealm(key, RULE_RELOAD)`——**检查与效果任务一起带原因取消**（旧实现只清检查队列，效果队列会跨 reload 残留）。

即所有与数据包相关的缓存**随 reload 整体丢弃**，不单独失效。规则索引 `RuleIndex` 自身的 `tagCache` / `queryCache` 也随对象替换一并丢弃。

## 3. 标签缓存（`RuntimeTagLookup`）

- 键 `kind:tagId`（item / block / entity / biome / fluid / mob_effect 六类），值 `Set<ResourceLocation>`。
- 物品 / 方块 / 实体 / 流体 / 药水效果走静态注册表；**生物群系走当前服务端的动态注册表**（`level.registryAccess()`）。
- **标签不存在时缓存空集**（避免重复查询同一个空标签）。
- 求值器不直接访问注册表，统一经 `ConditionContext.tags()`；缓存与 reload 失效由运行时管理。

## 4. 气候缓存（`RuntimeClimateSampler`）

- 按 **quart 坐标**采样，访问序 `LinkedHashMap`，**上限 4096 项**，超限淘汰最旧。
- 仅对 `MultiNoiseBiomeSource`（主世界 / 下界）有效，其它群系源返回 null。
- 条件求值不允许自己起缓存，统一经 `ConditionContext.climate()`。

## 5. 静态预计算（`PlaceBlockExecutor.OFFSETS`）

- 方块形状偏移按 `ShapeKey(shape, radius, fillOrigin)` 预计算（**不含维度**）；首次计算后 `List.copyOf` 复用。
- 缓存键含 `fillOrigin`：起点填充开关会改变候选集合，必须参与键，否则会串用不同候选集。
- 这是跨调用共享的**进程内静态缓存，不随 reload 失效**（只依赖形状、半径与起点开关）。

## 6. 候选来源目录（`core/catalog`）与网络目录（`core/network/catalog`）

编辑器要能枚举「可选的物品 / 方块 / 实体 ……」，这部分与规则索引无关，分两层：

**`core/catalog`——数据源（全量 + 修订号）**

- 接口 `RuleCatalogSource`：`type()`（对应 `RuleCatalogType`）、`revision()`（内容修订号）、`all()`（全量候选，已按展示顺序排序）。**接口形状已冻结**，分页 / 过滤不在这里。
- `RuleCatalogSources.builtin(server)`：按 `RuleCatalogType` 声明顺序建 **9 类**数据源（物品 / 方块 / 实体 / 战利品表 / 群系 / 维度 / 标签 / 流体 / 状态效果），并按**服务端实例缓存**整份源列表（换服 / 换存档整体重建，旧服务端可回收）。
- `RegistryCatalogSource`：基于注册表 id 集合的通用实现（静态注册表取 `keySet()`，数据包 / 动态注册表取可重载查找或 `registryAccess()`）。条目按 id 字典序排序并**按修订号缓存**；`revision()` 每次现场按 id 集合的**内容哈希**（`31 × size + hashCode`，与顺序无关）计算、**不缓存**——内容未变时稳定，reload 后内容变化即改变。
- `TagCatalogSource`：合并物品 / 方块 / 实体 / 流体 / 群系五个注册表的标签 id，去重保留首次命中的注册表短名；同样按修订号缓存。

**`core/network/catalog/RuleCatalogService`——传输层（分页 + 过滤 + 缓存）**

- **消费** `core/catalog` 的数据源，负责**大小写不敏感的子串过滤（id / label / subLabel）、分页切分、按「类型 + 来源修订号」缓存全量候选与修订失效**，产出可下发的 `RuleCatalog`。
- 默认每页 `DEFAULT_PAGE_SIZE = 50`，单页上限 `RequestRuleCatalogPayload.MAX_PAGE_SIZE`；越界页码夹到最后一页。
- `invalidate()` / `invalidate(type)` 在保存、重载或来源内容变化后清缓存。

**两者关系**：`core/catalog` 提供「有哪些、变没变」；`core/network/catalog` 提供「怎么分页下发、何时失效」；编辑端经 `RequestRuleCatalogPayload` / `RuleCatalogPayload` 取分页目录（见 [edit-protocol.md](../modules/edit-protocol.md)）。

## 7. 有界性总览

| 缓存 | 上限 | 失效时机 |
|---|---|---|
| `RuleIndex.tagCache` / `queryCache` | 随注册表大小 | reload（索引整体重建） |
| `RuntimeTagLookup` | 随标签数量 | reload（置 null） |
| `RuntimeClimateSampler` | **4096**（淘汰最旧） | reload（置 null） |
| `PlaceBlockExecutor.OFFSETS` | 随 (shape, radius, fillOrigin) 组合 | 进程内静态，不失效 |
| `RuleCatalogSources` 源列表 | 每服务端一份 | 换服务端实例 |
| `RegistryCatalogSource` / `TagCatalogSource` 条目 | 每修订号一份 | 修订号变化即重建 |
| `RuleCatalogService.CACHE` | 每 `RuleCatalogType` 一份 | 修订号变化 / `invalidate` |
| `DebugMeasurements` | **12000** 样本 | 每轮场景 |

## 8. 扩展点与踩坑

- 新增按物品 / 标签查询的结构 → 扩 `RuleIndex`（保持排序键与懒展开）。
- 新增依赖数据包的缓存 → 必须在 `replaceRules` 中一并置空，否则 reload 后读到旧数据。
- 新增候选类别 → 扩 `RuleCatalogType` + 在 `RuleCatalogSources.builtin` 增加一个数据源；分页 / 过滤复用 `RuleCatalogService`，不要在两处各写一份。
- 踩坑：缓存「整体重建」是刻意设计，不要为单条规则加局部失效路径；`revision()` 必须与顺序无关（用 `Set.hashCode`），否则每 tick 都会误判内容变化；`PlaceBlockExecutor.OFFSETS` 是进程内静态、跨 reload 保留。

## 9. 相关

- 运行时总控：[../modules/conversion-runtime.md](../modules/conversion-runtime.md)
- 加载后重建索引：[../modules/rule-loading.md](../modules/rule-loading.md)、[../flows/rule-loading-flow.md](../flows/rule-loading-flow.md)
- 编辑协议与目录下发：[../modules/edit-protocol.md](../modules/edit-protocol.md)、[edit-save-protocol.md](../flows/edit-save-protocol.md)
