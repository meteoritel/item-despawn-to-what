# 功能模块：规则模型与契约（`core/api` + `core/model`）

> 事实来源：`core/api/**`、`core/model/**`。
> 这是后端最稳定的"契约面"：模型不可变，世界操作不在这里发生。

## 1. 定位与依赖方向

- `core/api`：定义**全部对外契约**——类型定义契约、执行/求值上下文与回执、解码契约、类型分发工具、问题模型与一批值/常量。它**不依赖** `model`/`registry`/`runtime`，是全项目的依赖底座（被 120+ 文件引用）。
- `core/model`：`api` 契约的具体落地——规则的数据形态、条件树算法、Codec 组装、语义校验。项目内只依赖 `api`，包括引用校验助手 `RefChecks`；**不直接依赖** `type` 或 `registry`（只引用 `TypeRegistry` 接口）。
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
| `EffectExecutor<P>` | `@FunctionalInterface` | 效果执行器（只做世界操作，返回真实回执） | `EffectResult execute(P, EffectContext)` |
| `ConditionContext` | interface | 条件求值的**只读**上下文 | `level()` / `source()` / `pos()` / `random()` / `tags()` / `climate()` |
| `EffectContext` | interface | 效果执行上下文（可产生副作用） | `level()` / `source()` / `sourceStack()` / `position()` / `random()` / `ruleId()` / `rounds()` / `coveredSourceItems()` / `outcomeId()` / `groupIndex()` / `groupCount()` / `groupSourceCost()` / `schedule(int,Runnable)` / `reportProgress(int)` / `safeSpawn()` / `fillOrigin()` / `positionSearchChecksPerTick()` |
| `ConditionResult` | enum | 条件求值四态 | `MATCH` / `NO_MATCH` / `UNAVAILABLE` / `ERROR`；`isMatch()`（仅 MATCH 为真） |
| `Evaluability` | enum | 可求值性前置门禁 | `AVAILABLE` / `UNAVAILABLE` |
| `EffectResult` | record | 效果执行回执（结算记账唯一依据） | `outcome`（`APPLIED`/`DEFERRED`/`SKIPPED`/`FAILED`）+ `appliedUnits` + `pendingUnits` + `oneShot` + `detail`；`applied(...)` / `deferred(...)` / `skipped(...)` / `failed(...)` / `asOneShot()` / `counted()` |
| `ClimateSampler` / `ClimateSample` | interface / record | 6 参数气候采样（温度/湿度/大陆性/侵蚀/深度/怪异度） | `sample(BlockPos)` |
| `TagLookup` | interface | 6 类标签成员查询；标签不存在返回 false | `itemInTag` / `blockInTag` / `entityInTag` / `biomeInTag` / `fluidInTag` / `mobEffectInTag` |

### 2.3 解码与问题模型

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `RuleDecoder<T>` | `@FunctionalInterface` | 单条规则 JSON→模型 的解码契约（加载层持有、模型层实现） | `DataResult<T> decode(JsonObject, IssueCollector)` |
| `Issue` | record | 一条问题记录（严重级 + 信息 + 来源 + 字段路径） | `error(...)` / `warn(...)` / `format()` |
| `IssueCollector` | class | 问题累积器（**非线程安全**，单次加载流程内传递） | `add` / `error` / `warn` / `addAll` / `issues` / `errors` / `warnings` / `hasErrors` / `isEmpty` / `format` |
| `IssueSeverity` | enum | `ERROR`（拒载该条）/ `WARN`（不影响加载） | — |

### 2.4 值常量与校验支撑

| 类 | 类型 | 职责 |
|---|---|---|
| `RuleFields` | util | **全部 JSON 字段名常量的唯一来源**：规则级（含 `display_name` / `triggers` / `source_cost` / `catalyst_cost` / `combination` / `outcomes` / `schema_version`）、候选结果内部、催化剂成本内部、源匹配、效果通用、条件树节点（`op` / `terms` / `term` / `condition` 与四个 op 取值）、旧格式 `negated`、覆盖层控制 `disabled`·`delete`。模型、加载与命令输出统一引用，禁止散落字面量。 |
| `TaggedId` | record | 通用引用：注册表 id 或 `#标签`。`CODEC`、`parse(String)`、`serialized()`、`id()`、`tag()` |
| `ParamChecks` | util | 内置类型 `validateParams` 的公共助手，统一错误文案与字段路径。`required` / `inRange(int|double)` / `positive` / `notEmpty` / `orderedRange` / `child` / `index` |
| `RefChecks` | util | 注册表引用存在性校验，供模型、服务与内置类型共用。非标签引用未命中 → ERROR；标签数据已绑定时，标签未命中 → WARN。`check`（单项）/`checkAll`（列表）；规则见 [issue-validation.md](../systems/issue-validation.md)。 |

## 3. `core/model` 类清单

### 3.1 规则骨架

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `Rule` | record | **后端唯一配置单元** | 字段：`id` / `enabled` / `priority` / `displayName`(可空) / `notes`(可空) / `source` / `conditions` / `triggerAfterSeconds` / `effects` / `triggers` / `sourceCost`(可空) / `catalystCost`(可空) / `combination` / `outcomes` / `schemaVersion`；派生：`complexity()`（条件叶数）、`isRunnable()`（enabled 且有可执行候选）、`effectiveTriggers()`（空→`{natural}`）、`effectiveOutcomes()`（无 outcomes 时把顶层 effects 隐式映射为唯一候选 `default`）、`allEffects()`、`declaresConsumption` / `declaresAnyConsumption` / `declaresSourceConsumption` / `usesImplicitSourceConsumption` |
| `SourceMatcher` | record | 源匹配：`items` 匹配、`exclude` 排除（排除优先） | `CODEC`、`of`、`isEmpty`、`matches(ResourceLocation, Function<...>)` |
| `SourceEntry` | record | 源匹配项（物品 id 或物品标签），经 `TaggedId.CODEC.xmap` 复用形状 | `CODEC`、`fromTagged` / `toTagged` / `parse` / `serialized` |
| `TriggerKind` | enum | 消失方式：`NATURAL` / `FIRE` / `LAVA` / `CACTUS` | `key()`（小写下划线）、`CODEC`（大小写不敏感，未知报错，无静默回退） |
| `CombinationMode` | enum | 候选组合模式：`ROUND_ROBIN`（缺省）/ `PRIORITY` | `key()`、`CODEC`（大小写不敏感，未知报错） |
| `SourceCost` | record | 按源物品引用配置每轮消耗 | `counts: Map<TaggedId,Integer>`；`CODEC` 直接编码为数量对象；`forStack` 按当前堆叠取数量，直接物品优先，再按源列表匹配标签，缺省 1 |
| `CatalystCost` | record | 规则级催化剂固定成本 | `items`(必填非空 `List<TaggedId>`)、`counts`(每引用 1..64)、`count`(缺省引用的数量，默认 1)、`radius`(1..8，默认 1)；`CODEC`；常量 `DEFAULT_COUNT` / `MIN_COUNT` / `MAX_COUNT` / `DEFAULT_RADIUS` / `MIN_RADIUS` / `MAX_RADIUS` |
| `OutcomeCandidate` | record | 候选结果（内含多个共同执行的效果） | `id`（规则内唯一）、`effects`、`safeSpawn`(默认 false)、`fillOrigin`(默认 true)；`codec(Codec<Effect>)`；`IMPLICIT_ID="default"`；`implicit(...)`（把顶层 effects 映射为候选，不写回 JSON） |

### 3.2 条件树

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `ConditionExpression` | record | 条件表达式：整棵树只有一个根节点，`root=null` 表示无条件 | `EMPTY`、`isEmpty()`、`leafCount()`、`nodeCount()`、`depth()`、`isStructurallyValid()` |
| `ConditionNode` | sealed interface | 递归条件节点 | permits `AllOf(List<ConditionNode>)` / `AnyOf(List<ConditionNode>)` / `Inverted(ConditionNode term)` / `Leaf(Condition condition)` |
| `ConditionTrees` | util | 条件树遍历、统计与不可变结构变换（求值/校验/编辑器共用） | `forEachLeaf(...)`（带 JSON 路径）、`leafCount` / `nodeCount` / `depth`、`replaceAt(...)`、`updateTermsAt(...)` |
| `ConditionLimits` | util | 条件树与规则规模上限的**唯一常量来源** | `MAX_LEAVES=128`、`MAX_NODES=256`、`MAX_DEPTH=16`、`MAX_EFFECTS=32`、`MAX_SOURCE_ENTRIES=256`、`MAX_DISPLAY_NAME_CODEPOINTS=128` |
| `Condition` | interface | 条件叶（实现类本身即参数对象） | `type()`；常量 `TYPE_FIELD` / `NEGATED_FIELD`（后者仅用于解码期识别旧格式并报错） |

> 旧结构已删除：`ConditionGroup` 类、`ConditionExpression.groups()`、`Condition.negated()` 与 `CommonFields` 的 `negated` 片段都不再存在（决策见 [ADR-0018](../../../adr/0018-condition-tree-contract.md)）。

### 3.3 类型定义形态

| 类 | 类型 | 职责 |
|---|---|---|
| `EffectType<P extends Effect>` | interface | 效果类型定义：继承 `TypeDefinition`，加 `executor()`，以及 `default boolean oneShot()`（默认 false，一次性世界效果不参与数量上限） |
| `ConditionType<P extends Condition>` | interface | 条件类型定义：继承 `TypeDefinition`，加 `evaluator()`，以及 `default Evaluability evaluability(P, ConditionContext)`（默认 `AVAILABLE`） |
| `SimpleEffectType<P>` | record | `(id, MapCodec<P>, Validator<P>, EffectExecutor<P>, boolean oneShot)` 一行式实现；四参构造默认非一次性 |
| `SimpleConditionType<P>` | record | `(id, MapCodec<P>, Validator<P>, ConditionEvaluator<P>, EvaluabilityCheck<P>)` 一行式实现；四参构造可求值性恒 `AVAILABLE` |

> 约定：**参数对象 `P` 同时实现 `Effect`/`Condition`**，分发解码后可直接当规则里的效果/条件叶使用。新增类型必须让参数 record 实现对应接口。
>
> `Effect` 接口除 `type()` / `delayTicks()` / `chance()` / `conditions()` 外，还要求实现 `withConditions(@Nullable ConditionExpression)`——返回仅替换效果级条件、其余参数原样保留的新实例。这是为运行期门槛投影扩展的公开 API：执行器按**具体效果类型**强转分发，不能用包装 record 顶替，故由实现类重建真实效果类型（10 个内置效果全部实现），见 [催化剂门槛运行投影](../systems/catalyst-threshold-projection.md)。

### 3.4 编解码与校验

| 类 | 类型 | 职责 | 关键成员 |
|---|---|---|---|
| `RuleCodecs` | util | 规则/条件树/效果编解码组装入口 | `DEFAULT_TRIGGER_AFTER_SECONDS=300`、`DEFAULT_SCHEMA_VERSION=1`、`KNOWN_RULE_FIELDS`（17 项）、`OUTCOME_FIELDS`、`CATALYST_COST_FIELDS`、`conditionExpressionCodec(...)`、`effectCodec(...)`、`codec(...)`、`decoder(...)` |
| `CommonFields` | util | 效果/条件共用字段 codec 片段与默认值 | `DEFAULT_DELAY_TICKS=0`、`DEFAULT_CHANCE=1.0`；`delayTicks` / `chance` / `optionalConditions` |
| `RuleValidation` | util | 规则语义/参数校验（两档入口） | `validate(Rule, IssueCollector, origin)`、`validate(Rule, effectTypes, conditionTypes, issues, origin)`、`validateEffect` / `validateEffectParams`、`validateConditionParams`、`validateSourceEntries` / `validateSourceReference`、`validateCatalystCost`、`validateOutcomes`、`validateConsumptionEffects`、`warnLargeSource`、`describe`、`matchesDirect` |
| `ConsumptionDefaults` | util | 消耗类效果 id 常量与判定 | `CONSUME_SOURCE_ID` / `CONSUME_CATALYST_ID` / `CONSUME_FLUID_ID`、`isConsumption(ResourceLocation)` |

## 4. 关键机制

### 4.1 规则 JSON 形状与字段

规则顶层字段（唯一来源 `RuleFields`；默认值见 `RuleCodecs` / `CommonFields`）：

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | ResourceLocation | 否 | 由文件路径/加载层补齐 | 稳定标识；覆盖/删除/日志/命令以它为准 |
| `enabled` | bool | 否 | `true` | false 不进运行时索引 |
| `priority` | int | 否 | `0` | 越大越优先 |
| `display_name` | string | 否 | 无 | 展示名；去首尾空白，空串归一为未设置；码点上限 128 |
| `notes` | string | 否 | 无 | 注释，不参与判定 |
| `source` | object | **是** | — | `items`（id 或 `#tag` 数组，至少一项）+ `exclude` |
| `conditions` | 条件树对象 | 否 | 无（恒真） | 见 4.2；无条件时**省略**该字段 |
| `trigger_after_seconds` | int（秒） | 否 | `300` | 触发时刻 = min(该值×20 刻, lifespan−1) |
| `effects` | 数组 | 否 | `[]` | 平铺效果；与 `outcomes` 二选一 |
| `triggers` | 字符串数组 | 否 | `[]`（视为 `{natural}`） | `natural` / `fire` / `lava` / `cactus` |
| `source_cost` | object | 否 | 无 | `{物品或 #tag 引用: 每轮数量}`；数量为正整数，引用必须属于 `source.items`，缺省引用为 1 |
| `catalyst_cost` | object | 否 | 无 | `{items, counts?, count?, radius?}`，整数写法已废弃（解码报错） |
| `combination` | 字符串 | 否 | `round_robin` | `round_robin` / `priority` |
| `outcomes` | 数组 | 否 | `[]` | 候选结果；与顶层 `effects` 二选一 |
| `schema_version` | int | 否 | `1` | 当前仅支持 1，其它值拒绝 |

规则必须至少声明 `effects` 或 `outcomes` 之一，且**两者不得同时声明**（`RuleValidation` 拒载）。效果通用字段：`type`（必填）、`delay_ticks`（默认 0，相对规则触发时刻）、`chance`（默认 1.0，域 `[0,1]`）、`conditions`（可选条件树）。条件叶通用字段已无 `negated`（取反见 4.2）。

源物品列表仍是替代匹配；催化剂列表中的每个引用都必须分别通过门槛并分别支付。示例：

```json
{
  "source_cost": {"minecraft:egg": 2, "minecraft:acacia_door": 3},
  "catalyst_cost": {
    "items": ["minecraft:blaze_powder", "minecraft:redstone"],
    "counts": {"minecraft:blaze_powder": 2, "minecraft:redstone": 4}
  }
}
```

`catalyst_present.counts` 用相同键分别设置门槛，数量范围为 1..64；未配置引用先采用显式 `count`，两者均未配置时由运行期门槛投影解析。`fluid_present.fluids` 是流体引用列表，命中任一种即通过，空列表回退到可选的 `fluid`，二者均空表示任意流体。

### 4.2 条件树 JSON 形状

`conditions`（规则级与效果级同形）是一个递归对象，`op` 取值四选一：

```json
{"op":"all_of","terms":[ ...子节点 ]}      // 全部成立才成立
{"op":"any_of","terms":[ ...子节点 ]}      // 任一成立即成立
{"op":"inverted","term":{ ...单个子节点 }} // 取反：MATCH/NO_MATCH 互换，UNAVAILABLE/ERROR 原样穿透
{"op":"leaf","condition":{"type":"...",<类型专属字段平铺>}}  // 原子条件
```

- **无条件请省略 `conditions`**，不要写空组；`all_of` / `any_of` 的 `terms` 解码期必须非空。
- 旧格式一律**明确报错、不做兼容**：`conditions` 为数组（旧二维 DNF）、对象含 `groups`、叶级内联 `type`、叶内 `negated`，都会返回可定位的错误信息。
- 空表达式编码为 `{"op":"all_of","terms":[]}`，保证往返可回读；空组只是编辑器中间态，`ConditionExpression` 允许构造与序列化，非空性只在 `isStructurallyValid()` 与解码期强制。

### 4.3 扁平类型分发在模型层的接入

`RuleCodecs.conditionExpressionCodec(conditionTypes)` 构造一个**递归条件节点 codec**：叶子 codec 由 `TypeDispatch.flat(conditionTypes, Condition::type).codec()` 提供，组合节点通过内部 `NodeCodecHolder` 在构造期回填自引用以解析 `terms` / `term`。`effectCodec(effectTypes)` 直接由 `TypeDispatch.flat` 生成。机制细节见 [type-registry-dispatch.md](../systems/type-registry-dispatch.md)。

严格度差异：

| 层级 | 未知字段 | 结果 |
|---|---|---|
| 类型内（效果/条件叶） | 不在该类型 `keys()` 内 | **ERROR**（拒载该条） |
| 条件树节点 | 不在 `op` / `terms` / `term` / `condition` 内 | **ERROR** |
| `outcomes` 元素 | 不在 `OUTCOME_FIELDS` 内 | **ERROR** |
| `catalyst_cost` 对象 | 不在 `{items,counts,count,radius}` 内（含 `chance`/`conditions`/`delay_ticks`） | **ERROR** |
| 规则顶层 | 不在 `KNOWN_RULE_FIELDS` 内 | 仅 **WARN**，不阻断加载 |

### 4.4 不可变与 null 约束

- `Rule` / `SourceMatcher` / `SourceEntry` / `ConditionExpression` / `CatalystCost` / `OutcomeCandidate` / `TaggedId` / `Simple*Type` 均为 record，紧凑构造器对集合做防御性拷贝；`triggers` 用 `LinkedHashSet` 保留声明顺序；对外集合返回不可变视图。
- **Codec 层不得产出 null**：可选字段一律 `Optional` 承载，记录构造最后一步 `orElse(null)`。`CommonFields` 的片段返回 `RecordCodecBuilder`（`forGetter` 绑定所属记录类型）。

### 4.5 固定成本与隐式消耗（语义链起点）

`ConsumptionDefaults` 定义三个消耗效果 id（source / catalyst / fluid）。**规则未声明任何 `consume_*` 时按隐式语义消耗 1 个源物品**。`BuiltinTypeRegistries.perRoundSourceConsumption(Rule, ItemStack)` 给出该规则每轮的源消耗量，优先级链：

1. 显式 `source_cost` → 根据当前源堆叠查数量：直接物品优先，其次 `source.items` 原序首个命中且配置数量的标签，未配置时为 1；
2. 未声明 `source_cost` 且未声明任何 `consume_*` → 隐式 `1`；
3. 未声明 `source_cost` 但显式声明 `consume_source` → 同样按当前源物品查 `counts`，未配置时采用该效果的 `count`；
4. 只声明了其它消耗效果（如 `consume_fluid`）→ `0`（不按堆叠轮次展开）。

判定入口：`Rule.usesImplicitSourceConsumption()` → `BuiltinTypeRegistries.perRoundSourceConsumption(Rule, ItemStack)` → `EffectContext`（`rounds()` / `groupSourceCost()` / `coveredSourceItems()`），详见 [conversion-runtime.md](conversion-runtime.md)。

### 4.6 两档校验与工作量上限

| 档 | 入口 | 是否需要注册表 | 内容 |
|---|---|---|---|
| 结构档 | `validate(rule, issues, origin)` | 否 | id 合法、`trigger_after_seconds≥0`、`schema_version` 受支持、source 非空、effects/outcomes 至少一个且互斥、`source_cost` 各数量为正且引用已选源物品、`catalyst_cost` 合法、`display_name` 码点上限、条件树结构、消耗效果不重复、静态物品引用存在、候选 id 非空唯一且 effects 非空 |
| 参数档 | `validate(rule, effectTypes, conditionTypes, issues, origin)` | **是** | 在结构档之上，逐效果/逐叶做类型专属 `validateParams`，未注册类型报错 |

上限集中在 `ConditionLimits`：`effects≤32`、规则级与每个效果级条件树各自 `叶≤128`、`节点≤256`、`深度≤16`、`source` 项（匹配+排除）`≤256`、`display_name≤128` 码点、候选数量 `≤32`。**装配层必须用带注册表的重载**，否则未注册类型与区间类非法参数会被静默放行（见 [rule-loading.md](rule-loading.md)）。

## 5. 扩展点

- **新增规则级字段**：在 `RuleFields` 加常量 → `RuleCodecs` 的 `RecordCodecBuilder` 组里加 `.optionalFieldOf(...)` → `Rule` record 加分量 → 必要时 `RuleValidation` 加校验 → `KNOWN_RULE_FIELDS` 同步。
- **新增条件节点类型**：改 `ConditionNode`（sealed permits）、`ConditionTrees`（统计/变换/遍历）、`RuleCodecs.nodeMapCodec`（编解码与字段白名单）——注意 `ConditionExpression.isStructurallyValid()` 也要同步形状判断。
- **新增校验收紧/放宽**：改 `RuleValidation`，问题写入 `IssueCollector`，返回布尔表示该条是否干净；新上限统一加到 `ConditionLimits`。
- **新增通用效果字段**：改 `CommonFields`（保持返回 `RecordCodecBuilder`、不产 null）。

## 6. 相关

- 类型分发机制：[../systems/type-registry-dispatch.md](../systems/type-registry-dispatch.md)
- 问题与校验机制：[../systems/issue-validation.md](../systems/issue-validation.md)
- 内置类型参数全表：[type-system.md](type-system.md)
- 决策：[ADR-0012](../../../adr/0012-rule-model-and-effect-list.md)、[ADR-0013](../../../adr/0013-dfu-codec-and-flat-type-dispatch.md)、[ADR-0018](../../../adr/0018-condition-tree-contract.md)、[ADR-0021](../../../adr/0021-display-name-and-stable-ids.md)
