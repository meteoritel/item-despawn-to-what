# 横向系统：问题模型与校验

> 事实来源：`core/api/{Issue, IssueCollector, IssueSeverity, ParamChecks, RefChecks, RuleDecodeBudget}.java`、`core/extension/{DynamicReferenceCheck, DynamicReferenceContext}.java`、`core/model/RuleValidation.java`、`core/model/ConditionLimits.java`、`core/model/ConditionTrees.java`、`core/service/{RuleReferenceValidator, RuleSubmissionValidator, RuleLoadingService}.java`、`core/load/RuleLoader.java`。
> 相关模块：[rule-model.md](../modules/rule-model.md)、[rule-loading.md](../modules/rule-loading.md)。

## 1. 问题模型

| 类 | 说明 |
|---|---|
| `Issue`（record） | `severity` + `message` + `origin`（来源标识）+ `fieldPath`（字段路径）；`format()` 输出 `[SEVERITY] origin#fieldPath: message` |
| `IssueSeverity` | `ERROR`（导致**该条/该文件**拒载）、`WARN`（不影响加载） |
| `IssueCollector` | 可变累积器，提供 `add`/`error`/`warn`/`addAll`/`issues()`/`errors()`/`warnings()`/`hasErrors()`/`isEmpty()`/`format()`，以及派生来源继承子收集器的 `originScoped(origin)` |

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
| 结构档 | `RuleValidation.validate(rule, issues, origin)` | 否 | id 合法、`trigger_after_seconds≥0`、`schema_version` 受支持、source 非空、effects/outcomes 至少一个且互斥、`source_cost` 各数量为正且引用已选源物品、`catalyst_cost` 合法、`source_cost` 与 `consume_source` 不得同时声明、`catalyst_cost` 与 `consume_catalyst` 不得同时声明、`display_name` 码点上限、条件树结构合法、消耗效果不重复、静态物品引用存在、候选 id 非空唯一且各候选 effects 非空 |
| 参数档 | `RuleValidation.validate(rule, effectTypes, conditionTypes, issues, origin)` | **是** | 结构档之上，逐效果/逐叶做类型专属 `validateParams`，未注册类型报错 |

**装配层必须使用带注册表的重载**：`RuleLoadingService.loadAndValidate` 特意用注册表感知重载，否则未注册引用与区间/结构类非法参数会被**静默放行**。

工作量上限分两处，条件是**同源常量**：

- **解码预算**（`core/api/RuleDecodeBudget.java`）：文本嵌套深度 `MAX_NESTING_DEPTH=64`、条件树 `MAX_CONDITION_DEPTH=16` / `MAX_CONDITION_NODES=256`。所有 Gson 入口都走同一个有界解码入口：先做 token 级嵌套深度预检（跳过字符串字面量），再解析；随后用显式栈盘点全部条件子树的深度与节点数，超限即作为结构化 ISSUE 报错。**超限整条拒载、不落盘**；`StackOverflowError`（是 `Error` 而非 `RuntimeException`）同样降级为结构化 ISSUE 并整批拒绝，不让栈溢出逃逸。
- **模型层限额**（`core/model/ConditionLimits.java`）：`MAX_EFFECTS=32`、`MAX_LEAVES=128`、`MAX_SOURCE_ENTRIES=256`（匹配 + 排除合计）、`MAX_DISPLAY_NAME_CODEPOINTS=128`。条件树的 `MAX_NODES` / `MAX_DEPTH` 引用同一份解码预算常量；模型层的深度 / 节点校验保留为**解码后的纵深防御**。

限额**逐表达式**计算：规则级条件树与每个效果级条件树各自受限，不跨表达式累加；候选结果数量也受 `MAX_EFFECTS` 约束。

### 类型参数校验的两个细节

- **局部收集器**：`validateTypeParams` 先把类型校验问题收进局部 `IssueCollector`，补齐 `origin` 后一次性并入父收集器，避免"边遍历边追加导致重复 Issue"。
- **兜底错误**：若某类型 `validateParams` 只返回 false 却没写问题，补一条兜底 ERROR，避免错误静默。

## 4. 字段路径约定

用 `ParamChecks.child(path, field)` / `ParamChecks.index(path, i)` 拼接，统一错误文案与路径格式。条件树沿 `ConditionTrees.forEachLeaf(...)` 深度优先展开，路径与 JSON 形状一致，最终形如：

- 规则级条件叶：`conditions.terms[0].condition`（单叶根则为 `conditions.condition`）；
- 效果级条件叶：`outcomes[0].effects[1].conditions.terms[0].condition.dimensions`；
- 效果参数：`effects[0].count`、`outcomes[0].effects[1].count`；
- 催化剂成本：`catalyst_cost.items[0]`、`catalyst_cost.count`。

## 5. 引用的两类校验

| 类型 | 何时 | 规则 | 实现 |
|---|---|---|---|
| **静态物品引用** | 加载/提交/校验时 | 非标签引用未命中注册表 → **ERROR** | 源匹配项走 `RuleValidation.validateSourceReference`（查 `BuiltInRegistries.ITEM`）；`catalyst_cost.items` 走 `RuleValidation.validateCatalystCost` → `RefChecks.checkAll`（同查 ITEM） |
| **标签引用** | 仅在标签数据已绑定（`hasBoundTags`）时 | 未命中 → **WARN**（数据包标签可能后加载，不能据此拒载） | `RefChecks.warnMissingTag` |
| **动态引用** | 有 `MinecraftServer` 时 | 维度 / 群系 / **已加载**战利品表 | 核心校验器只做遍历与汇总，具体类型判定由各类型的**参数对象**经动态引用校验扩展点实现 |

动态引用校验是一个**扩展点**（见 [ADR-0036](../../../adr/0036-dynamic-reference-check-extension-point.md)）：带动态引用的类型在自己的参数对象上实现 `checkDynamicReferences(DynamicReferenceContext)`，上下文携带服务端、问题累积器与字段路径；核心校验器对遍历到的每个对象做一次接口分派，不含任何具体类型的引用规则。内置三类——战利品表效果、维度条件、群系条件——各自在参数对象上实现。问题经 `IssueCollector.originScoped(origin)` 派生的子收集器记录，来源继承一致。

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
