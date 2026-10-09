# 横向系统：问题模型与校验

> 事实来源：`core/api/{Issue, IssueCollector, IssueSeverity, ParamChecks, RefChecks}.java`、`core/model/RuleValidation.java`、`core/model/ConditionLimits.java`、`core/model/ConditionTrees.java`、`core/service/{RuleReferenceValidator, RuleSubmissionValidator, RuleLoadingService}.java`、`core/load/RuleLoader.java`。
> 相关模块：[rule-model.md](../modules/rule-model.md)、[rule-loading.md](../modules/rule-loading.md)。

## 1. 问题模型

| 类 | 说明 |
|---|---|
| `Issue`（record） | `severity` + `message` + `origin`（来源标识）+ `fieldPath`（字段路径）；`format()` 输出 `[SEVERITY] origin#fieldPath: message` |
| `IssueSeverity` | `ERROR`（导致**该条/该文件**拒载）、`WARN`（不影响加载） |
| `IssueCollector` | 可变累积器，提供 `add`/`error`/`warn`/`addAll`/`issues()`/`errors()`/`warnings()`/`hasErrors()`/`isEmpty()`/`format()` |

**`IssueCollector` 非线程安全**：只在同一加载流程内传递。`issues()` 返回不可变视图。

## 2. 拒载粒度

- **文件级**：非法 JSON / 顶层非对象或数组 → 整个文件拒载。
- **条目级**：单条规则语义非法 → **只拒载该条**，其余条目照常加载。
- **启动与 reload 行为一致**，且**不做任何隐式写回**。

判定实现：`RuleValidation.validate` 用 `int before = issues.errors().size()` 记录前值，最后 `errors().size() == before` 表示该条干净。调用方据此决定拒载。

**加载失败保留旧索引**：枚举资源或覆盖层目录失败以 `IllegalStateException`/`UncheckedIOException` 表达，异常不外抛到 `/reload`，保留上一版规则（见 [../flows/rule-loading-flow.md](../flows/rule-loading-flow.md)）。

## 3. 两档校验

| 档 | 入口 | 需要注册表 | 内容 |
|---|---|---|---|
| 结构档 | `RuleValidation.validate(rule, issues, origin)` | 否 | id 合法、`trigger_after_seconds≥0`、`schema_version` 受支持、source 非空、effects/outcomes 至少一个且互斥、`source_cost` 为正、`catalyst_cost` 合法、`display_name` 码点上限、条件树结构合法、消耗效果不重复、静态物品引用存在、候选 id 非空唯一且各候选 effects 非空 |
| 参数档 | `RuleValidation.validate(rule, effectTypes, conditionTypes, issues, origin)` | **是** | 结构档之上，逐效果/逐叶做类型专属 `validateParams`，未注册类型报错 |

**装配层必须使用带注册表的重载**：`RuleLoadingService.loadAndValidate` 特意用注册表感知重载，否则未注册引用与区间/结构类非法参数会被**静默放行**。

工作量上限集中在 `ConditionLimits`（唯一常量来源）：`MAX_EFFECTS=32`、条件树 `MAX_LEAVES=128` / `MAX_NODES=256` / `MAX_DEPTH=16`、`MAX_SOURCE_ENTRIES=256`（匹配 + 排除合计）、`MAX_DISPLAY_NAME_CODEPOINTS=128`。限额**逐表达式**计算：规则级条件树与每个效果级条件树各自受限，不跨表达式累加；候选结果数量也受 `MAX_EFFECTS` 约束。

### 类型参数校验的两个细节

- **局部收集器**：`validateTypeParams` 先把类型校验问题收进局部 `IssueCollector`，补齐 `origin` 后一次性并入父收集器，避免"边遍历边追加导致重复 Issue"。
- **兜底错误**：若某类型 `validateParams` 只返回 false 却没写问题，补一条兜底 ERROR，避免错误静默。

## 4. 字段路径约定

用 `ParamChecks.child(path, field)` / `ParamChecks.index(path, i)` 拼接，统一错误文案与路径格式。条件树沿 `ConditionTrees.forEachLeaf(...)` 深度优先展开，路径与 JSON 形状一致，最终形如：

- 规则级条件叶：`conditions.terms[0].condition.value`（单叶根则为 `conditions.condition.value`）；
- 效果级条件叶：`outcomes[0].effects[1].conditions.terms[0].condition.dimensions`；
- 效果参数：`effects[0].count`、`outcomes[0].effects[1].count`；
- 催化剂成本：`catalyst_cost.items[0]`、`catalyst_cost.count`。

## 5. 引用的两类校验

| 类型 | 何时 | 规则 | 实现 |
|---|---|---|---|
| **静态物品引用** | 加载/提交/校验时 | 非标签引用未命中注册表 → **ERROR** | 源匹配项走 `RuleValidation.validateSourceReference`（查 `BuiltInRegistries.ITEM`）；`catalyst_cost.items` 走 `RuleValidation.validateCatalystCost` → `RefChecks.checkAll`（同查 ITEM） |
| **标签引用** | 仅在标签数据已绑定（`hasBoundTags`）时 | 未命中 → **WARN**（数据包标签可能后加载，不能据此拒载） | `RefChecks.warnMissingTag` |
| **动态引用** | 有 `MinecraftServer` 时 | 维度 / 群系 / **已加载**战利品表 | `RuleReferenceValidator.validate` |

`RuleReferenceValidator` 依据当前实际加载内容：维度用 `server.levelKeys()`、群系用 `registryAccess().registryOrThrow(Registries.BIOME)`、战利品表用 `server.reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)`。它遍历顶层 effects、各候选 `outcomes[i].effects[j]` 以及这两处的效果级条件树（维度/群系叶）。`server == null`（离线装配）时**由装配层在调用点跳过本步**（`RuleLoadingService.loadAndValidate`），只能做静态校验；校验器入口通过 `Objects.requireNonNull` 明确要求非空 server，直接传入 null 会立即抛出 `NullPointerException`。

> 踩坑：标签**不校验存在性**（`hasBoundTags` 为假时完全跳过）——拼错标签不会报错，运行时静默不匹配。

## 6. 提交前校验（网络保存）

`RuleSubmissionValidator.validate` 是写入前唯一闸门：

- `DELETE` 动作直接跳过；其余编辑把变更体深拷贝并补齐 `id`；
- 控制条目必须是"**恰好 `id` + 一个值为 true 的控制字段**"（`disabled` 或 `delete`，且对象大小恰为 2），否则拒绝该批；
- 普通条目走完整 `RuleCodecs.decoder` + `RuleValidation` + `RuleReferenceValidator`；
- 第三方 Codec/校验器抛异常也**拒绝整批**；
- 返回 `issues.errors().isEmpty()`，即**无部分通过**。

## 7. 相关

- 规则字段、条件树与上限：[../modules/rule-model.md](../modules/rule-model.md)
- 加载链路中的校验位置：[../modules/rule-loading.md](../modules/rule-loading.md)、[../flows/rule-loading-flow.md](../flows/rule-loading-flow.md)
- 保存链路：[../flows/edit-save-protocol.md](../flows/edit-save-protocol.md)
