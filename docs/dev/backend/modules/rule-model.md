# 功能模块：规则模型与契约（`core/api` + `core/model`）

> 事实来源：`core/api/**`（17 个文件）、`core/model/**`（15 个文件）。
> 这是后端最稳定的"契约面"：模型不可变，世界操作不在这里发生。

## 1. 定位与依赖方向

- `core/api`：定义**全部对外契约**——类型定义契约、执行/求值上下文、解码契约、类型分发工具、问题模型与一批值/常量。它**不依赖** `model`/`registry`/`runtime`，是全项目的依赖底座（被 80+ 文件引用）。
- `core/model`：`api` 契约的具体落地——规则的数据形态、Codec 组装、语义校验。它依赖 `api`，**不直接依赖** `registry`（只引用 `TypeRegistry` 接口）。
- 加载层（`core/load`）通过 `RuleDecoder` 契约与模型解耦，见 [rule-loading.md](rule-loading.md)。

```text
core/api  ←──────────── core/model ────────→ core/load(仅经 RuleDecoder)
   ▲                                              ▲
   └────────── runtime / type / service / command / network / debug ──┘
```

## 2. `core/api` 类清单

### 2.1 类型注册契约

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `TypeDefinition<P>` | interface | 可注册类型的公共形态 | `id()`、`MapCodec<P> codec()`、`default boolean validateParams(P, IssueCollector, String fieldPath)`（默认 true） |
| `TypeRegistry<T>` | interface | 注册表契约 | `register` / `find` / `require` / `all` / `ids` / `getOrNull` |
| `TypeDispatch` | util | 扁平式类型分发 MapCodec | `flat(TypeRegistry<? extends TypeDefinition<?>>, Function<A,ResourceLocation>)` |

### 2.2 执行 / 求值契约

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `ConditionEvaluator<P>` | `@FunctionalInterface` | 条件求值器（**纯谓词**，返回未取反结果） | `boolean test(P, ConditionContext)` |
| `EffectExecutor<P>` | `@FunctionalInterface` | 效果执行器（只做世界操作） | `void execute(P, EffectContext)` |
| `ConditionContext` | interface | 条件求值的**只读**上下文 | `level()` / `source()` / `pos()` / `random()` / `tags()` / `climate()` |
| `EffectContext` | interface | 效果执行上下文（可产生副作用） | `level()` / `source()` / `sourceStack()` / `position()` / `random()` / `ruleId()` / `rounds()` / `coveredSourceItems()` / `schedule(int,Runnable)` |
| `ClimateSampler` / `ClimateSample` | interface / record | 6 参数气候采样（Δ 温度/湿度/大陆性/侵蚀/深度/怪异度） | `sample(BlockPos)` |
| `TagLookup` | interface | 6 类标签成员查询；标签不存在返回 false | `itemInTag` / `blockInTag` / `entityInTag` / `biomeInTag` / `fluidInTag` / `mobEffectInTag` |

### 2.3 解码与问题模型

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `RuleDecoder<T>` | `@FunctionalInterface` | 单条规则 JSON→模型 的解码契约（加载层持有、模型层实现） | `DataResult<T> decode(JsonObject, IssueCollector)` |
| `Issue` | record | 一条问题记录（严重级 + 信息 + 来源 + 字段路径） | `error(...)` / `warn(...)` / `format()` |
| `IssueCollector` | class | 问题累积器（**非线程安全**，单次加载流程内传递） | `error` / `warn` / `addAll` / `issues` / `errors` / `warnings` / `hasErrors` / `format` |
| `IssueSeverity` | enum | `ERROR`（拒载该条）/ `WARN`（不影响加载） | — |

### 2.4 值常量与校验支撑

| 类 | 类型 | 职责 |
|---|---|---|
| `RuleFields` | util | **全部 JSON 字段名常量的唯一来源**（规则级 / 源匹配 / 效果通用 / 条件叶 / 覆盖层控制 `disabled`·`delete`）。模型、加载与命令输出统一引用，禁止散落字面量。 |
| `TaggedId` | record | 通用引用：注册表 id 或 `#标签`。`CODEC`、`parse(String)`、`serialized()`、`id()`、`tag()` |
| `ParamChecks` | util | 内置类型 `validateParams` 的公共助手，统一错误文案与字段路径。`required` / `inRange(int|double)` / `positive` / `notEmpty` / `orderedRange` / `child` / `index` |

## 3. `core/model` 类清单

### 3.1 规则骨架

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `Rule` | record | **后端唯一配置单元** | 字段：`id` / `enabled` / `priority` / `notes` / `source` / `conditions` / `triggerAfterSeconds` / `effects`；派生：`complexity()`（条件叶数）、`isRunnable()`（enabled 且有效果）、`declaresAnyConsumption()` / `declaresSourceConsumption()` / `usesImplicitSourceConsumption()` |
| `SourceMatcher` | record | 源匹配：`items` 匹配、`exclude` 排除（排除优先） | `CODEC`、`of`、`isEmpty`、`matches(ResourceLocation, Function<ResourceLocation,Set<ResourceLocation>>)` |
| `SourceEntry` | record | 源匹配项（物品 id 或物品标签），经 `TaggedId.CODEC.xmap` 复用形状 | `CODEC`、`fromTagged` / `toTagged` / `parse` / `serialized` |
| `ConditionExpression` | record | **DNF 表达式**：组间 OR、组内 AND，空=恒真 | `EMPTY`、`isEmpty()`、`leafCount()`、`isStructurallyValid()`、`groups()` |
| `ConditionGroup` | record | DNF 的一个合取子句 | `conditions()`、`isEmpty()`、`leafCount()` |
| `Condition` | interface | 条件叶 | `type()`、`negated()`；常量 `TYPE_FIELD` / `NEGATED_FIELD` |
| `Effect` | interface | 规则触发后执行的一个动作 | `type()`、`delayTicks()`、`chance()`、`@Nullable conditions()`、`default effectiveConditions()` |

### 3.2 类型定义形态

| 类 | 类型 | 职责 |
|---|---|---|
| `EffectType<P extends Effect>` | interface | 效果类型定义：继承 `TypeDefinition`，加 `executor()` |
| `ConditionType<P extends Condition>` | interface | 条件类型定义：继承 `TypeDefinition`，加 `evaluator()` |
| `SimpleEffectType<P>` | record | `(id, MapCodec<P>, Validator<P>, EffectExecutor<P>)` 一行式实现 |
| `SimpleConditionType<P>` | record | `(id, MapCodec<P>, Validator<P>, ConditionEvaluator<P>)` 一行式实现 |

> 约定：**参数对象 `P` 同时实现 `Effect`/`Condition`**，分发解码后可直接当规则里的效果/条件叶使用。新增类型必须让参数 record 实现对应接口。

### 3.3 编解码与校验

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `RuleCodecs` | util | 规则/条件/效果编解码组装入口 | `DEFAULT_TRIGGER_AFTER_SECONDS=300`、`KNOWN_RULE_FIELDS`、`conditionExpressionCodec`、`effectCodec`、`codec`、`decoder(...)` |
| `CommonFields` | util | 效果/条件共用字段 codec 片段与默认值 | `DEFAULT_DELAY_TICKS=0`、`DEFAULT_CHANCE=1.0`、`DEFAULT_NEGATED=false`；`delayTicks` / `chance` / `optionalConditions` / `negated` |
| `RuleValidation` | util | 规则语义/参数校验（两档入口） | `validate(Rule, IssueCollector, origin)`、`validate(Rule, effectTypes, conditionTypes, issues, origin)`、`validateEffect`、`validateSourceEntries`、`validateConsumptionEffects`、`matchesDirect`、`describe` |
| `ConsumptionDefaults` | util | 消耗类效果 id 常量与判定 | `CONSUME_SOURCE_ID` / `CONSUME_CATALYST_ID` / `CONSUME_FLUID_ID`、`isConsumption(ResourceLocation)` |

## 4. 关键机制

### 4.1 规则 JSON 形状与字段

规则顶层字段（唯一来源 `RuleFields`；默认值见 `RuleCodecs` / `CommonFields`）：

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | ResourceLocation | 否 | 由文件路径推导 | 稳定标识；覆盖/删除/日志/命令以它为准 |
| `enabled` | bool | 否 | `true` | false 不进运行时索引 |
| `priority` | int | 否 | `0` | 越大越优先 |
| `notes` | string | 否 | 无 | 注释，不参与判定 |
| `source` | object | **是** | — | `items`（id 或 `#tag` 数组，至少一项）+ `exclude` |
| `conditions` | 二维数组（DNF） | 否 | `[]`（恒真） | 外层 OR、内层 AND |
| `trigger_after_seconds` | int（秒） | 否 | `300` | 触发时刻 = min(该值×20 刻, lifespan−1) |
| `effects` | 数组 | **是** | — | 有序；为空则规则不可运行 |

效果通用字段：`type`（必填）、`delay_ticks`（默认 0，相对规则触发时刻）、`chance`（默认 1.0，域 `[0,1]`）、`conditions`（可选 DNF）。条件叶通用字段：`type`（必填）、`negated`（默认 false）。

### 4.2 扁平类型分发在模型层的接入

`RuleCodecs.conditionExpressionCodec` = `TypeDispatch.flat(conditionTypes, Condition::type).codec()` 生成叶 codec，再经两级 `listOf().xmap` 组成"组"与"表达式"；`effectCodec` 同理。规则本体用 `RecordCodecBuilder` 组装八字段。机制细节见 [type-registry-dispatch.md](../systems/type-registry-dispatch.md)。

严格度差异：**类型内未知字段是硬错误**（`TypeDispatch` 用类型 `codec().keys()` 白名单），**规则顶层未知字段只 WARN**（`RuleCodecs.warnUnknownFields`）。

### 4.3 不可变与 null 约束

- `Rule` / `SourceMatcher` / `ConditionExpression` / `ConditionGroup` / `TaggedId` / `Simple*Type` 均为 record，紧凑构造器对集合做 `List.copyOf` 防御性拷贝；对外集合返回不可变视图。
- **Codec 层不得产出 null**：可选字段一律 `Optional` 承载，记录构造最后一步 `orElse(null)`。`CommonFields` 的片段返回 `RecordCodecBuilder`（`forGetter` 绑定所属记录类型）。

### 4.4 隐式消耗（语义链起点）

`ConsumptionDefaults` 定义三个消耗效果 id。**规则未声明任何 `consume_*` 时运行时隐式消耗 1 个源物品**；一旦显式声明任意消耗效果即以声明为准。判定链：`Rule.usesImplicitSourceConsumption()` → `BuiltinTypeRegistries.perRoundSourceConsumption(Rule)` → `EffectContext.rounds()`，详见 [conversion-runtime.md](conversion-runtime.md)。

### 4.5 两档校验与工作量上限

| 档 | 入口 | 是否需要注册表 | 内容 |
|---|---|---|---|
| 结构档 | `validate(rule, issues, origin)` | 否 | id 合法、`triggerAfterSeconds≥0`、source/effects 非空、DNF 无空组、detach 消耗重复、静态物品引用存在 |
| 参数档 | `validate(rule, effectTypes, conditionTypes, issues, origin)` | **是** | 在结构档之上，逐效果/逐叶做类型专属 `validateParams`，未注册类型报错 |

上限：`effects≤32`、规则级条件叶 `≤128`、`source.items≤256`、每个效果级表达式 `≤128` 叶。**装配层必须用带注册表的重载**，否则未注册类型与区间类非法参数会被静默放行（见 [rule-loading.md](rule-loading.md)）。

## 5. 扩展点

- **新增规则级字段**：在 `RuleFields` 加常量 → `RuleCodecs` 的 `RecordCodecBuilder` 组里加 `.optionalFieldOf(...)` → `Rule` record 加分量 → 必要时 `RuleValidation` 加校验 → `KNOWN_RULE_FIELDS` 同步。
- **新增校验收紧/放宽**：改 `RuleValidation`，问题写入 `IssueCollector`，返回布尔表示该条是否干净。
- **新增通用效果/条件字段**：改 `CommonFields`（保持返回 `RecordCodecBuilder`、不产 null）。

## 6. 相关

- 类型分发机制：[../systems/type-registry-dispatch.md](../systems/type-registry-dispatch.md)
- 问题与校验机制：[../systems/issue-validation.md](../systems/issue-validation.md)
- 内置类型参数全表：[type-system.md](type-system.md)
- 决策：[ADR-0012](../../../adr/0012-rule-model-and-effect-list.md)、[ADR-0013](../../../adr/0013-dfu-codec-and-flat-type-dispatch.md)、[ADR-0007](../../../adr/0007-condition-expression-dnf.md)
