# 功能模块：规则加载与装配（`core/load` + `core/service` 装配部分）

> 事实来源：`core/load/**`（14 个文件）、`core/service/{RuleLoadContext, RuleLoadingService, RuleReferenceValidator, BuiltinTypeRegistries}.java`。
> 写入/会话相关的 service 类（`RuleOverlayWriter` 等）与快照装配（`RuleSnapshotAssembler`）见 [edit-protocol.md](edit-protocol.md)。

## 1. 定位与依赖方向

一条**无状态流水线**：把内置数据包、世界数据包、config 覆盖层三处规则文件读成 `RawRuleEntry`，按 id 合并，再逐条交给注入的 `RuleDecoder` 解码。`core/load` **不 import `core/model`**（泛型 `T`），模型经 `RuleDecoder` 注入；`RuleLoadingService` 是加载链路的装配点（load 与 model 的桥接）。

> 同属 `core/load` 的 `RuleSourceIndex` **不参与**「读取 → 合并 → 解码」主流水线：它把**合并前**的原始条目按 id 归并为 base / overlay / control 三层视图，供快照装配与编辑判定使用（见 §2.1 与 [edit-protocol.md](edit-protocol.md)）。`RuleSnapshotAssembler` 同样同时依赖 load 与 model，因此「唯一跨两者」的表述已不再成立。

```text
内置/世界数据包 data/<ns>/idtw/rules/**/*.json
config 覆盖层     config/<overlay_directory>/rules/**/*.json
   → RuleFileParser → RuleMerger → RuleCodecs(经 RuleDecoder)
   → RuleValidation + RuleReferenceValidator → RuleIndex（见 runtime）

旁路（合并前原始条目）→ RuleSourceIndex → RuleSnapshotAssembler（快照，见 edit-protocol）
```

决策：[ADR-0014](../../../adr/0014-three-layer-scope-and-overlay-merge.md)；来源目录/控制条目：[ADR-0020](../../../adr/0020-source-catalog-and-overlay-control-entries.md)。

## 2. 类清单

### 2.1 `core/load`

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleSourceLayer` | 三层枚举与优先级/可写性 | `BUILTIN(0)` < `WORLD(1)` < `OVERLAY(2)`；`priority()` / `displayName()` / `writable()`（仅 OVERLAY 为 true） |
| `RuleOrigin` | 每条规则的来源坐标（进 `Issue.origin`） | `datapack(layer, packId, location)` / `overlay(relativePath)` / `display()` |
| `RulePaths` | 路径与字段路径常量 | `DATAPACK_RULES_DIRECTORY="idtw/rules"`、`OVERLAY_RULES_DIRECTORY="rules"`、`RULE_FILE_EXTENSION=".json"`、`stripExtension()`、`joinFieldPath()` |
| `PackLayerResolver` | 包 id → 层的可注入判定 | `resolve(String packId)`；静态 `allWorld()`（覆盖层/无数据包场景） |
| `RawRuleEntry` | 解析后、解码前的中间载体（保留原始 JsonObject） | record(id, body, origin, disabled, delete, fieldPath)；`isControl()` |
| `LoadedRule<T>` | 解码成功条目 + 最终来源 | record(id, origin, value) |
| `RuleLoadRequest<T>` | 一次加载的输入 | record(5 字段)；工厂 `datapacks()`、`overlay()` |
| `RuleLoadResult<T>` | 一次加载的输出 | record(rules, issues)；`hasErrors()` / `hasWarnings()` / `formatIssues()` / `allIssues()` |
| `RuleFileParser` | JSON 文本 → 原始条目（严格策略、id 推导消费、控制字段校验） | `parse(json, origin, derivedId, issues)` |
| `DatapackRuleReader` | 读内置 + 世界数据包层全部条目并排序 | `read(resourceManager, layerResolver, issues)`；私有 `deriveId(location)` |
| `OverlayRuleReader` | 读 config 覆盖层条目 | `read(overlayRoot, defaultNamespace, issues)`；私有 `deriveId(relativePath, ns)` |
| `RuleMerger` | 三层按 id 合并（含 disabled/delete 控制） | `merge(entries, issues)` |
| `RuleLoader` | 加载入口：读取 → 合并 → 解码 | `load(request)`、`loadRaw(request, issues)` |
| `RuleSourceIndex` | **合并前**原始条目按 id 归并为 base / overlay / control 三层视图（只归并，不读文件/解码/校验），供快照判定 origin/status 与编辑使用 | `build(rawEntries)`、`empty()`、`entries()`、`byId(id)`、`rawEntries()`；`Entry(id, base, overlay, control, controlWins, controlFromOverlay)` + `deleted()`/`disabled()`/`hasBody()`/`baseBody()`/`overlayBody()` |

> `RuleSourceIndex` 与 `RuleMerger` 共用同一「层内后者胜」顺序语义，但用途不同：`RuleMerger` 产出可解码的合并结果，`RuleSourceIndex` 保留被覆盖/停用的原始基底以便可恢复（见 [edit-protocol.md](edit-protocol.md)）。其 `byId()` / `rawEntries()` 目前无调用方（保留为查询入口）。

### 2.2 `core/service`（装配）

| 类 | 职责 | 关键成员 |
|---|---|---|
| `RuleLoadContext` | 一次加载的全部外部依赖容器 | record(8 字段，含可选 `server`)；`withServer()`、`full()`、`overlayOnly()` |
| `RuleLoadingService` | 装配层：加载、加载+语义校验、来源分层索引、统计/描述 | `load(ctx)`、`loadAndValidate(ctx)`、`sourceIndex(ctx, issues)`、`countByLayer()`、`describeRules()`、`summarize()` |
| `RuleReferenceValidator` | 服务端**动态**引用校验（维度/群系/战利品表），覆盖顶层 effects 与 outcomes 内 effects | `validate(rule, server, issues, origin)` |
| `BuiltinTypeRegistries` | 内置类型注册表 + 表达式 Codec 的**规范装配点** | `create()`、`perRoundSourceConsumption(Rule)`、`implicitSourceConsumption()` |

## 3. 三层作用域与合并语义

优先级 **OVERLAY > WORLD > BUILTIN**（同 id 后者覆盖前者）。

| 层 | 位置 | 可写 |
|---|---|---|
| ① 内置数据包 | `data/<ns>/idtw/rules/**/*.json`（mod jar 内） | 否 |
| ② 世界数据包 | 存档 `datapacks/*/data/<ns>/idtw/rules/**/*.json` | 否 |
| ③ config 覆盖层 | `config/<overlay_directory>/rules/**/*.json` | **是**（GUI/命令写入目标） |

**层内排序**：`DatapackRuleReader` 按 `(层优先级, 包优先级, 文件路径, 文件内顺序)` 升序处理，合并"后写胜"。包优先级取自 `listPacks()` 序号（未登记包 rank=-1 并每包告警一次；取表失败时 WARN 并退化为按文件路径排序）。

> 命名坑：包排序比较器变量名 `byPackRankDesc` 实为**自然升序**，凭名字理解会得到相反的优先级结论。

**普通条目先合并、控制条目后应用**：`RuleMerger` 用 `TreeMap`（按层优先级）分层，层内稳定重排为 `normals + controls`，因此 `disabled`/`delete` 语义**不受文件名字典序影响**。因为控制字段只在 OVERLAY 合法，实际等价于"覆盖层内先全部 upsert、后统一停用/删除"。

| 控制字段 | 语义 |
|---|---|
| `disabled: true` | 保留基底 `body`，最终状态标为停用、来源改为控制条目 origin；找不到基底 → WARN 并忽略 |
| `delete: true` | `merged.remove(id)`，幂等（基底不存在=no-op） |

约束：`disabled`/`delete` **只能出现在覆盖层**，出现在数据包层即 ERROR 拒载；同一条同时写二者 → 拒载；文件控制条目只识别 `id`/`disabled`/`delete`，夹带其它字段 → WARN。想让**新规则**停用请用规则级 `enabled:false`，不要用 `disabled`。

> 控制条目的判定必须**判取值而非存在性**：`"disabled": false` 是普通规则（`RuleFileParser.readFlag` 与快照侧的 `isTrueFlag` 都遵守）。

## 4. 文件形状与 id 推导

一个文件是**单条规则对象**或**规则数组**。

| 场景 | id 取值 |
|---|---|
| 含 `id` 字段 | 取该字段（须为合法 `ResourceLocation`） |
| 单条规则文件省略 `id` | 数据包：`<文件命名空间>:<去 idtw/rules/ 前缀与 .json 的相对路径>`；覆盖层：`<overlay_directory>:<去扩展名的相对路径>` |
| 多条规则文件某条省略 `id` | **该条拒载**（多条目文件必须显式声明 id） |
| 同一文件内 id 重复 | 后者拒载（ERROR） |

> id 推导分散在 `DatapackRuleReader.deriveId`、`OverlayRuleReader.deriveId`（读取侧）与 `RuleOverlayWriter.locate`（写入侧）**三处，必须与解析层对齐**——不同步会导致写入/下发落到错误目标。`RuleFileParser` 只消费调用方传入的 `derivedId`，自身不推导。

## 5. 读取与解码细节

- **同名文件全部参与合并**：`DatapackRuleReader` 用 `getResourceStack()` 取回所有数据包的同名副本，不只取最高优先级的那一份。
- **解码器异常不吞**：`RuleLoader.decodeEntry` 捕获 `RuntimeException` 时完整堆栈进日志，Issue 里保留"类名 @ 首个栈帧"，避免折叠成不可定位字符串。
- **disabled 注入**：解码前剔除 `disabled`/`delete` 控制字段、补齐推导 id；`disabled=true` 的条目在解码时注入 `enabled=false`。
- **失败保留旧索引**：枚举资源或覆盖层目录失败以 `IllegalStateException`/`UncheckedIOException` 明确表达，调用方保留上一版规则——见 [rule-loading-flow.md](../flows/rule-loading-flow.md)。

## 6. 装配与校验

`RuleLoadingService.loadAndValidate(ctx)` 的固定动作：

1. 用注册表与解码器构造 `RuleCodecs.decoder(context.registryAccess(), effectTypes, conditionTypes)`；
2. `RuleLoader.load(request)`：读取 → 合并 → 解码；
3. 逐条 `RuleValidation.validate(rule, effectTypes, conditionTypes, issues, origin)`（**必须用带注册表的重载**，否则未注册类型与区间类非法参数会被静默放行）+ `RuleReferenceValidator.validate(rule, server, issues, origin)`（`server == null` 时跳过）。

### 6.1 契约字段与版本

`RuleCodecs.decoder` 在真正解码前先做三层字段检查：

- **顶层字段白名单** `RuleCodecs.KNOWN_RULE_FIELDS` 共 **17 项**：`id`、`enabled`、`priority`、`display_name`、`notes`、`source`、`conditions`、`trigger_after_seconds`、`effects`、`triggers`、`source_cost`、`catalyst_cost`、`combination`、`outcomes`、`schema_version`、`disabled`、`delete`。顶层未知字段仅 **WARN**，不阻断加载。
- **`outcomes[i]` 元素未知字段为 ERROR**（允许 `id`/`effects`/`safe_spawn`/`fill_origin`）；**`catalyst_cost` 未知字段、非对象写法（旧的整数写法）为 ERROR**。
- **`schema_version`**：缺省值为 `RuleCodecs.DEFAULT_SCHEMA_VERSION = 1`，当前**仅支持 1**；其它值在 `RuleValidation.validate` 中报 ERROR 拒载。
- **旧格式不兼容**：条件数组 / `conditions.groups` / 叶级 `negated` 由条件树解码器明确报错，不做静默兼容。

### 6.2 动态引用校验范围

`RuleReferenceValidator` 依据**当前实际加载内容**校验，且覆盖**顶层 `effects` 与 `outcomes[i].effects[j]` 内效果**（路径与 JSON 形状一致，如 `outcomes[0].effects[1].loot_table`）；效果级条件沿条件树逐叶展开：

- 维度：`server.levelKeys()`；
- 群系：`server.registryAccess().registryOrThrow(Registries.BIOME)`，**仅 `EXACT` 模式**做引用校验；
- 战利品表：`server.reloadableRegistries().lookup()`；
- 引用存在性统一走 `RefChecks`：**非标签引用未命中 → ERROR**；**标签未命中 → WARN**（标签可能后加载，不能据此拒载）。

### 6.3 每轮源消耗

`BuiltinTypeRegistries.perRoundSourceConsumption(Rule)` 决定每轮消耗量，**固定成本优先**：

1. 显式声明 `source_cost` → 取其值（加载期已保证为正数）；
2. 未声明 `source_cost` 且规则无任何 `consume_*` 效果 → 隐式消耗 1；
3. 未声明 `source_cost` 但声明 `consume_source` → 累加其 `count`；
4. 只声明其它消耗（如 `consume_fluid`）→ 0（不按堆叠轮次展开）。

这是 `rounds` 语义的来源，见 [conversion-runtime.md](conversion-runtime.md)。

### 6.4 上下文工厂

`RuleLoadContext` 工厂：`full(...)` 不设 server，需 `.withServer(server)`；`overlayOnly()` 用 `PackLayerResolver.allWorld()`，同样不设 server。

## 7. 扩展点

- **新增来源层**：改 `RuleSourceLayer` 与 `RuleMerger` 分层逻辑（很少见）。
- **改合并策略**：改 `RuleMerger`，注意"普通先、控制后"不变量。
- **改 id 推导**：三处必须同步（`DatapackRuleReader.deriveId` / `OverlayRuleReader.deriveId` / `RuleOverlayWriter.locate`）。
- **改动态引用校验范围**：改 `RuleReferenceValidator`，新增的第三方动态引用由第三方自己的校验器处理。
- **改来源分层/快照形态**：改 `RuleSourceIndex`（归并语义）与 `RuleSnapshotAssembler`（快照装配，见 [edit-protocol.md](edit-protocol.md)）。

## 8. 相关

- 端到端链路：[../flows/rule-loading-flow.md](../flows/rule-loading-flow.md)
- 校验与问题模型：[../systems/issue-validation.md](../systems/issue-validation.md)
- 写入与快照：[edit-protocol.md](edit-protocol.md)
- 规则模型与契约：[rule-model.md](rule-model.md)
- 决策：[ADR-0014](../../../adr/0014-three-layer-scope-and-overlay-merge.md)、[ADR-0020](../../../adr/0020-source-catalog-and-overlay-control-entries.md)
