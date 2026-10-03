# 阶段 0-C 调研笔记：规则领域模型与契约现状核实

- 任务：task-3（owner: domain-contract），对应 `docs/plan/backend-round-2/PLAN.md` 阶段 0（L238-244）与阶段 1（L246-252）。
- 证据方式：**纯静态代码阅读**（read / grep / glob），部分原版与依赖通过 IDEA MCP 反编译读取。本阶段不修改任何 Java/JSON/构建文件，不运行 `gradlew` / `tools/dsh-build.ps1`，不启动游戏。
- 引用格式：`文件相对路径:行号`。源码前缀统一记作 `common/.../` = `common/src/main/java/com/meteorite/itemdespawntowhat/`；资源前缀 `common/src/main/resources/`。
- 未核实项集中列在第 8 节，正文不重复声明。

---

## 1. 当前规则契约的实际字段与 JSON 形状

### 1.1 顶层九字段（Rule 记录 + RuleCodecs.codec）

| JSON 键名 | Java 成员 | 类型 | 必填 | 默认值 | 证据 |
| --- | --- | --- | --- | --- | --- |
| `id` | `Rule.id` | ResourceLocation | 是 | 无 | `common/.../core/model/RuleCodecs.java:116`；`core/model/Rule.java:13-23` |
| `enabled` | `Rule.enabled` | boolean | 否 | `true` | `RuleCodecs.java:117` |
| `priority` | `Rule.priority` | int | 否 | `0` | `RuleCodecs.java:118` |
| `display_name` | `Rule.displayName` | String（可空） | 否 | `null` | `RuleCodecs.java:119-120`；`Rule.java:32-38` normalizeDisplayName：strip 后空串→null |
| `notes` | `Rule.notes` | String（可空） | 否 | `null` | `RuleCodecs.java:121-122` |
| `source` | `Rule.source` | SourceMatcher | 是 | 无 | `RuleCodecs.java:123` |
| `conditions` | `Rule.conditions` | ConditionExpression | 否 | 空表达式（恒真） | `RuleCodecs.java:124-125`；紧凑构造器 `Rule.java:25-29` 空→`ConditionExpression.EMPTY` |
| `trigger_after_seconds` | `Rule.triggerAfterSeconds` | int | 否 | `300` | `RuleCodecs.java:126-127,41` |
| `effects` | `Rule.effects` | List<Effect> | 是 | 无 | `RuleCodecs.java:128` |

- 覆盖层控制字段 `disabled` / `delete`（布尔，控制条目语义）只在 `KNOWN_RULE_FIELDS` 中登记，不在 codec 九字段内：`RuleCodecs.java:44-56`；消费在 `core/service/RuleSubmissionValidator.java:20-28`。
- 一元派生方法：`Rule.complexity()`=`conditions.leafCount()`（`Rule.java:41-43`）；`isRunnable()`=`enabled && !effects.isEmpty()`（`:46-48`）；`declaresAnyConsumption()` / `declaresSourceConsumption()` / `usesImplicitSourceConsumption()`（`:51-73`，经 `ConsumptionDefaults.isConsumption` 判定）。
- **现状没有**：消失方式（触发）集合、组合模式、候选结果列表、结构版本、固定成本字段。

### 1.2 顶层未知字段白名单与行为

- `RuleCodecs.KNOWN_RULE_FIELDS = Set.of(id, enabled, priority, display_name, notes, source, conditions, trigger_after_seconds, effects, disabled, delete)`：`RuleCodecs.java:44-56`。
- `warnUnknownFields`（`RuleCodecs.java:147-153`）：不在白名单的顶层键只 `issues.warn("未知字段: " + key, null, key)`，**不阻断解码**。
- ⇒ 阶段 1 新增任何顶层键，必须同步 `KNOWN_RULE_FIELDS`，否则新旧字段混用时只会产生 WARN 而非拒绝；反之，白名单登记后 codec 未接字段会变成 `DataResult.error`（未知字段被 `TypeDispatch` 之外的路由忽略与否需按实现确认，见 8. 未核实 3）。

### 1.3 效果级通用字段（CommonFields）

- `delay_ticks`：`Codec.INT.optionalFieldOf("delay_ticks", 0)`，默认 `0`：`core/model/CommonFields.java:21,30-32`。
- `chance`：`Codec.doubleRange(0,1).optionalFieldOf("chance", 1.0)`，默认 `1.0`：`CommonFields.java:23,35-37`。
- `conditions`：可选条件表达式，codec 层禁止产出 null：`CommonFields.java:40-46`。
- 12 个内置效果 codec 全部调用这三个方法（每文件各 3 次），证据见 2.1。

### 1.4 效果类型分发（字段白名单的真正来源）

- `RuleCodecs.effectCodec(effectTypes) = TypeDispatch.flat(effectTypes, Effect::type).codec()`：`RuleCodecs.java:106-108`。
- `core/api/TypeDispatch.java:29-75`：按 `type` 字段查表；未知字段检查基于 `codec.keys(ops)` 与 `type` 的并集（`:43-50`），错误文案 `"类型 <id> 存在未知字段: <key>"`，参数错误前缀 `"类型 <id> 参数错误: "`；encode 未注册类型抛 `IllegalStateException`。
- ⇒ 效果对象出现未知键会被拒绝，这是「固定成本不得带 chance/conditions/delay」可落地的基础机制。

### 1.5 条件树形状

- 节点：`{"op":"all_of|any_of","terms":[...]}` / `{"op":"inverted","term":{...}}` / `{"op":"leaf","condition":{...}}`：`RuleCodecs.java:62-64,79-103`；`RuleFields.java` 提供 `op/terms/term/condition` 与取值常量。
- 旧格式（条件二维数组 / `groups` / 叶级 `negated`）**明确报错**，不做静默兼容：`RuleCodecs.java:58-59,70-72,87-93,270-287`。
- 校验：`RuleValidation.validate` 结构档校验空 terms / inverted 无 term / 上限（`core/model/RuleValidation.java:31-74`）。

### 1.6 消耗类型常量

- `core/model/ConsumptionDefaults.java:12-16`：`itemdespawntowhat:consume_source` / `consume_catalyst` / `consume_fluid`；`isConsumption` `:23-25` 三选一。
- 三种消耗效果的参数（`core/type/effect/`）：
  - `ConsumeSourceEffect`：`count`（1..64，默认 1）：`ConsumeSourceEffect.java:21-26,40-49`。
  - `ConsumeCatalystEffect`：`items`（必填非空）、`count`（1..64）、`radius`（1..8，默认 1）：`ConsumeCatalystEffect.java:26-33,77-93`。
  - `ConsumeFluidEffect`：`fluid`（可空=任意流体）、`require_source`（默认 true）：`ConsumeFluidEffect.java:27-33`。

### 1.7 现有校验项清单（RuleValidation.java，238 行）

| 档位 | 校验内容 | 证据 |
| --- | --- | --- |
| 结构档 `validate(rule,issues,origin)` | id==null、trigger_after_seconds<0、source 为空、effects 为空、display_name 码点上限、条件结构非法 | `RuleValidation.java:31-74` |
| 工作量上限 | effects<=MAX_EFFECTS、条件叶/节点/深度、source 项<=MAX_SOURCE_ENTRIES | `:58-67` |
| 源项 | 重复项、既匹配又排除、非标签且 `BuiltInRegistries.ITEM` 不含→「未知源物品」 | `:165-186` |
| 逐效果 | type==null、delay_ticks<0、chance 有限且 [0,1]、效果级条件上限与结构 | `:189-213` |
| 消耗唯一性 | 同规则内同一 `ConsumptionDefaults` 消耗类型出现 >1 次→「同一规则内重复声明消耗效果 <type> 共 N 次」 | `:150-162` |
| 注册表档 `validate(rule,effectTypes,conditionTypes,issues,origin)` | 先结构档；规则级条件参数（路径 `conditions`）；逐效果参数（路径 `effects[i]`）+ 效果级条件（路径 `effects[i].conditions`）；未注册类型报「未注册的效果类型: <id>」 | `:77-147` |

- **明确不存在**：源成本必须为正数、固定成本禁止 chance/conditions/delay、候选引用/候选为空、结构版本。

### 1.8 动态引用校验（RuleReferenceValidator.java）

- `validate(rule,server,issues,origin)`（`:19-33`）：条件树内维度必须存在于 `server.levelKeys()`；`BiomeCondition.EXACT` 经 `RefChecks.checkAll` 校验生物群系注册表；逐效果只校验 `LootTableEffect.lootTable` 是否在 `Registries.LOOT_TABLE` 加载成功（`:25-28`）；效果级条件递归（`:36-56`）。
- **没有**候选引用 / 成本引用校验。

### 1.9 内置样本实际出现过的全部 JSON 键名（阶段 1 新增键不得冲突）

`id / enabled / display_name / notes / trigger_after_seconds / source / source.items / source.exclude / conditions / effects / type / count / radius / limit / delay_ticks / chance / item / entity / age / block / use_source_block / shape / amount / per_source_item / loot_table / luck / power / fire / visual_only / mode / duration_ticks / thundering / pickup / potion_effects / fluid / require_source / items / op / terms / term / condition / dimensions / weather / from / to / min / max / biomes / down`

---

## 2. 现有效果列表 + chance/conditions/delay 语义 vs 规划书「候选结果」

### 2.1 12 个内置效果与字段（`core/type/BuiltinEffectTypes.java:40-55` 注册顺序）

| 效果 id（缩写） | 类型专属 JSON 键 | 通用三字段 | 专属文件 |
| --- | --- | --- | --- |
| `spawn_item` | item / count / limit / radius | 是 | `core/type/effect/SpawnItemEffect.java` |
| `spawn_entity` | entity / count / age / limit / radius | 是 | `SpawnEntityEffect.java` |
| `place_block` | block / use_source_block / shape / count / radius / limit | 是 | `PlaceBlockEffect.java` |
| `spawn_xp` | amount / per_source_item | 是 | `SpawnXpEffect.java` |
| `loot_table` | loot_table / luck | 是 | `LootTableEffect.java` |
| `lightning` | count | 是 | `LightningEffect.java` |
| `explosion` | power / fire / visual_only | 是 | `ExplosionEffect.java` |
| `arrow_rain` | count / pickup / potion_effects / effect / duration_ticks / amplifier | 是 | `ArrowRainEffect.java` |
| `weather` | mode / duration_ticks / thundering | 是 | `WeatherEffect.java` |
| `consume_source` | count | 是 | `ConsumeSourceEffect.java` |
| `consume_catalyst` | items / count / radius | 是 | `ConsumeCatalystEffect.java` |
| `consume_fluid` | fluid / require_source | 是 | `ConsumeFluidEffect.java` |

「通用三字段」= 该文件 codec 调用 `CommonFields.delayTicks()/chance()/optionalConditions()`（grep：12 个文件各 3 次命中）。`Effect` 接口本身即 `type()/delayTicks()/chance()/conditions()`：`core/model/Effect.java:11-35`。

### 2.2 执行语义（chance / conditions / delay 全在单效果维度）

- 转化主流程 `core/runtime/ConversionRuntime.performConversion`（`:357-387`）：`:364` `perRound = types.perRoundSourceConsumption(rule)`；`:366` `rounds = perRound > 0 ? max(1, available / perRound) : 1`；`:370` `covered = min(rounds*perRound, available)`；`:371` 构造 `EffectContext(rounds, covered)`；`:373-375` 仅隐式消耗时执行 `implicitSourceConsumption()`；`:376-378` 逐条 `dispatchEffect`。
- `dispatchEffect`（`:390-395`）：`delay = max(0, effect.delayTicks())` → `base.schedule(delay, () -> runEffect(...))`。**每效果独立调度，无候选分组**。
- `runEffect`（`:398-425`）：先效果级 `conditions`（`ExpressionEvaluator.matches`），再 `:407` `if (effect.chance() < 1.0D && base.random().nextDouble() >= effect.chance()) return;`，未注册类型记 ERROR 日志后返回，执行异常被捕获隔离。
- **概率落空不做任何补偿/回退记账**：`:407` 直接 `return`。
- 规则选择：`select(...)`（`:329-344`）按优先级取第一条条件成立者，同一掉落物只执行一条规则；`dueAge = min(triggerAfterSeconds*20, lifespan-1)`（`:351-354`）。

### 2.3 与规划书「候选结果」的差距

| 规划书要求（PLAN 4.2 / 行为契约） | 现状 | 阶段 1 需新增的字段层级 |
| --- | --- | --- |
| 消失方式集合（自然/火/岩浆/仙人掌并列，一条规则可多选；未声明仅自然消失） | 无任何触发字段；运行时只有物品自然到期转化 | 规则级「触发集合」数组（取值枚举 + 默认 `["natural"]`），并在运行时按触发来源过滤 |
| 组合模式（轮询默认 / 优先） | 无；效果按数组顺序无条件全执行 | 规则级组合模式枚举（默认轮询），候选内维护轮询游标 |
| 候选结果列表（每候选含稳定标识 + 效果列表；每转化组只选一个候选） | `effects` 是扁平列表，全部执行 | 规则级 `outcomes`：候选对象数组，每项 `id` + `effects` |
| 候选稳定标识 | 无 id 概念，效果靠数组下标 | 候选 `id`（同规则内唯一） |
| 结构版本 | 无 | 规则级 schema/结构版本整数，版本不可识别即拒绝 |
| 一次性效果分类（天气/爆炸/闪电不参与数量上限；仅含一次性效果的候选每源最多一组） | 无分类；所有效果同等对待 | `EffectType` 元数据（如 `oncePerSource`）或内置分类表；运行时据此改变组数结算 |
| 固定成本（正数源成本 + 催化剂数量成本；无概率/条件/延迟） | 由 `effects` 里的 `consume_source` / `consume_catalyst` 承担，且带通用三字段 | 规则级固定成本字段（源成本正整数 + 催化剂 {items,count}），与 `effects` 分离；消耗效果禁止携带 chance/conditions/delay |
| 安全生成开关（默认关闭，沿用原点行为） / 起点填充（默认开启） | 无字段，位置策略散在执行器内 | 候选级（PLAN 4.2 描述为候选内可选）布尔开关，默认 false / true；归属层级待确认（第 8 节） |
| 完整账目（实际成功量、失败原因） | `EffectExecutor.execute` 返回 `void`（`core/api/EffectExecutor.java:8-11`），无回执 | 阶段 4 范围；阶段 1 只需保证契约能表达 |

---

## 3. 消耗与隐式消耗的实际扣费路径

### 3.1 每轮源消耗估算（BuiltinTypeRegistries）

- `core/service/BuiltinTypeRegistries.java:35-46` `perRoundSourceConsumption(Rule)`：隐式消耗（规则未声明任何消耗效果）→ `1`；否则累加 `ConsumeSourceEffect.count()` 的 `Math.max(0, count)`；只声明其它消耗效果（如仅 consume_fluid）→ 返回 `0`（不按堆叠轮次展开）。
- `implicitSourceConsumption()`（`:49-51`）返回 `new ConsumeSourceEffect(1, 0, 1.0D, null)`。
- `create()`（`:54-66`）通过 `ServiceLoader<RuleTypeProvider>` 注册第三方（按类名排序）后冻结；条件类型先于效果类型。
- ⇒ 「源成本」目前没有独立字段，是效果 `count` 的派生量；**没有正数约束**（`count` 由 codec 限 1..64，但语义上并无「必须声明固定成本」的要求，因为隐式路径允许 0 声明）。

### 3.2 三个消耗执行器的真实扣费

| 执行器 | 扣费行为 | 证据 |
| --- | --- | --- |
| `ConsumeSourceExecutor` | 源不存活直接 return（销毁路径源已死亡即不扣）；`requested = saturatedMultiply(count, context.rounds())`；`consumeFromStack = min(max(0,requested), stack.getCount())` 并 shrink；`consumed>0 && stack.isEmpty()` 才 `discard()`；不足只 debug 日志收敛，不报错 | `core/type/effect/exec/ConsumeSourceExecutor.java:24-41,44-50` |
| `ConsumeCatalystExecutor` | `AABB = EffectTargets.blockBox(center, radius)`；`remaining = saturatedMultiply(count, rounds)`；`getEntitiesOfClass(ItemEntity)` 排除源、按距离由近及远扣；不足只 debug「催化剂不足」，不生成不补偿 | `exec/ConsumeCatalystExecutor.java:35-67` |
| `ConsumeFluidExecutor` | `while(consumed<rounds && consumeOne(...))`；`consumeOne` 检查 `fluidState.isEmpty()/matches/requireSource→isSource()`；含水方块只清 `WATERLOGGED`，否则 `setBlock AIR`；消耗后同位置立即变空，实际通常只消耗 1 格，未消耗轮次被剔除并 debug 记录 | `exec/ConsumeFluidExecutor.java:38-73` |

### 3.3 账目性质

- `performConversion` 先按堆叠数估算 `rounds`/`covered`（`:366-371`），再逐效果执行；**没有「组」实体、没有已开始组、没有实际成功量与返还**。PLAN 阶段 4「完整组与实际账目替代预估 rounds」正是替换此处。

---

## 4. 客户端可编译面真实依赖 + 阶段 1 必须保留的 common 适配点

### 4.1 结论：没有任何客户端类使用 `Rule` / `Effect` 的成员

- grep `core.model.Rule;` / `RuleValidation` / `ConsumptionDefaults` 在 `common/.../client/` 下**命中 0**（用分号限定 import 语句）。
- 客户端只在 JSON 层工作（`JsonObject`）；数据入口是 `RuleSnapshotEntry` 的三个 JSON 视图 `effective`/`base`/`overlay`（`client/net/RuleSnapshotEntry.java:112 行` record 定义，序列化键 `:34-41`）。
- ⇒ 增删 `Rule` 记录字段**不会破坏客户端编译**；真正的耦合面是下面 4.2 的常量/静态方法/协议类型与 JSON 键名。

### 4.2 客户端依赖清单（类 + 使用的 common 成员）

| 客户端类（相对 `common/.../client/`） | 使用的 common 成员 | 证据 |
| --- | --- | --- |
| `edit/BuiltinEditorDefaults.java` | `core.api.RuleFields`（写新建规则默认体）：id/enabled/priority/source.items/trigger_after_seconds/effects | `BuiltinEditorDefaults.java:22-33` |
| `edit/BuiltinEditorDescriptors.java` | `RuleFields` | import 命中（grep `core.api.RuleFields`） |
| `edit/EditorChangeSet.java` | `RuleFields.ID`；协议 `RuleEdit`/`RuleEditChangeSet` | `EditorChangeSet.java:54-63,75-80` |
| `edit/RuleDraft.java` | `RuleCodecs.conditionExpressionCodec`（`:226,251,261`）、`TypeRegistry`（import `:8`）、`RuleFields`、`ConditionExpression/ConditionNode/ConditionType` | `RuleDraft.java:168,185,202`（未识别字段保留策略） |
| `edit/ClientTypeRegistries.java` | `BuiltinConditionTypes.create()`、`BuiltinEffectTypes.create(conditionExpressionCodec(conditions()))`、`RuleCodecs.conditionExpressionCodec`（`:51,65`）、`TypeRegistry`（`:20,22,30,45,51`） | `ClientTypeRegistries.java:30-73` |
| `edit/TypeEditorDescriptor.java` / `TypeEditorRegistry.java` / `EffectEditorRegistry.java` / `ConditionEditorRegistry.java` | `EffectType<?>` / `ConditionType<?>` 仅作泛型参数 | `ClientTypeRegistries.java:20,22` |
| `ui/screen/RuleEditorScreen.java` | `RuleFields`（含 `OP_*`、`CONDITION`、路径 `"effects[i].conditions"` `:1472`）、`TypeRegistry`（import `:43`）、`ConditionLimits` | `RuleEditorScreen.java:1451-1505, 986-998` |
| `ui/screen/form/UiConditionTreeEditor.java` / `FormView.java` / `NaturalSummary.java` | `RuleFields` | import 命中（grep） |
| `ui/screen/form/RuleTemplates.java` | 不 import core；读 `assets/itemdespawntowhat/idtw/templates/` 8 个模板 | `RuleTemplates.java:19-32` |
| `net/*`、`edit/EditorWorkspaceView.java` 等 | 协议类型 `RuleCatalog/RuleCatalogType/RuleCatalogEntry/RuleIssue/RuleSaveStatus/RuleSnapshot/RuleSnapshotEntry/RuleEdit/RuleEditChangeSet/RuleEditProtocol` | import 命中（grep） |

### 4.3 阶段 1 必须保留的 common 访问适配点

1. `core.api.RuleFields` 中客户端用到的 22 个键常量**不得改名/删除**（改名即编译失败）：ID/ENABLED/PRIORITY/DISPLAY_NAME/NOTES/SOURCE/SOURCE_ITEMS/CONDITIONS/TRIGGER_AFTER_SECONDS/EFFECTS/TYPE/DELAY_TICKS/CHANCE/OP/OP_LEAF/OP_INVERTED/OP_ALL_OF/OP_ANY_OF/TERMS/TERM/CONDITION/DELETE。
2. `RuleCodecs.conditionExpressionCodec(TypeRegistry<ConditionType<?>>)` 必须保持 public static 且签名不变（4 处调用）。
3. `BuiltinConditionTypes.create()` / `BuiltinEffectTypes.create(Codec<ConditionExpression>)` 签名不变；`TypeRegistry<EffectType<?>>` / `TypeRegistry<ConditionType<?>>` 泛型形状不变。
4. `EffectType` / `ConditionType` 保留无参泛型可用（客户端只作类型参数）。
5. 协议类型 `RuleSnapshotEntry` 的三视图 JSON 与 `RuleEditChangeSet`（键 `expected_version`/`edits`）结构不变或向后兼容。
6. `RuleDraft` 依赖的「路径级读写 + 未识别字段原样保留」语义（`RuleDraft.java:168,185,202`、`FormControl.java:1109`、`FormView.java:32`）必须维持——否则新字段会被 GUI 静默抹掉（PLAN 阶段 1 验收项）。

### 4.4 「旧 GUI 不无声抹掉新字段」的现状与新增字段的连带改动

- 已成立：未注册编辑器走只读 JSON 摘要且不改写字段（`TypeEditorDescriptor.java:12,33`、`TypeEditorRegistry.java:15`、`BuiltinEditorDescriptors.java:14`）；`RuleDraft` 按路径合并，只覆盖 patch 中出现的字段。
- **风险点**：`BuiltinEditorDefaults.ruleBody`（`:22-33`）构造的新规则默认体只有 9 个旧字段；`RuleEditorScreen.java:986-998` 直接 `createSession(key, body)`。若阶段 1 让服务端默认补齐新字段则无碍；若服务端要求显式声明，则旧 GUI 新建的规则会被服务端拒绝（VALIDATION_FAILED），需在客户端默认体同步补字段。
- 客户端本地校验 `RuleEditorScreen.localIssues`（`:1451-1505`）目前只拦 effects 非空/<=32、条件树结构、display_name 码点、trigger 非负、重复 consume 类型；阶段 1 的「源成本>0 / 候选非空 / 候选 id 唯一」需在此补本地拦截或接受服务端报错。

---

## 5. 内置数据包与模板的实际结构与阶段 1 同步范围

### 5.1 文件清单与同源关系

- `common/src/main/resources/data/itemdespawntowhat/idtw/rules/` 与 `common/src/main/resources/assets/itemdespawntowhat/idtw/templates/` **各 8 个同名文件**；逐一 SHA256 比对全部 `same=True`（模板 = data 样本的副本）。
- 文件与大小：`builtin_arrow_rain.json`(921B)、`builtin_conditions.json`(2288B)、`builtin_item_to_block.json`(1074B)、`builtin_item_to_entity.json`(922B)、`builtin_item_to_item.json`(614B)、`builtin_loot_and_chance.json`(548B)、`builtin_multi_effect.json`(947B)、`builtin_weather_and_light.json`(2159B)。
- 成因：`client/ui/screen/form/RuleTemplates.java:19-32` 注释「模板内容是 `data/itemdespawntowhat/idtw/rules/` 下内置样本的副本：客户端资源管理器只暴露 `assets/` 下的资源」，`:32` `DIRECTORY="idtw/templates"`。
- 加载目录常量：`core/load/RulePaths.java:12` `DATAPACK_RULES_DIRECTORY="idtw/rules"`；三层来源 `core/load/RuleSourceLayer.java:9-11`：mod 内置数据包（只读）、世界数据包、覆盖层。

### 5.2 各样本结构要点（阶段 1 迁移输入）

- 单对象文件：
  - `builtin_arrow_rain`：enabled=false；`arrow_rain` count=32 pickup=allowed delay_ticks=10；conditions=all_of[dimension=overworld, weather=rain]；source.items=[minecraft:arrow]。
  - `builtin_item_to_block`：enabled=false；`place_block` use_source_block=true shape=square count=1 radius=1；conditions=all_of[surrounding_blocks.down=#minecraft:dirt, outdoor]；source.items=[#minecraft:saplings] + exclude=[minecraft:oak_sapling]。
  - `builtin_item_to_entity`：enabled=true；`spawn_entity` entity=minecraft:chicken count=1 age=-24000；conditions=all_of[outdoor, dimension=overworld]；source.items=[minecraft:egg]。
  - `builtin_item_to_item`：enabled=true；`spawn_item` item=minecraft:rotten_flesh count=1；省略 conditions；source.items=[minecraft:chicken]。
  - `builtin_loot_and_chance`：enabled=true；`loot_table` loot_table=minecraft:chests/simple_dungeon luck=0 chance=0.5；source.items=[minecraft:gold_nugget]。
  - `builtin_multi_effect`：enabled=true；effects=[spawn_xp amount=100, consume_source count=1, lightning count=1 delay_ticks=20]；conditions=inverted{y_level min=-64 max=40}；source.items=[minecraft:diamond]。
- 数组文件：
  - `builtin_conditions`：`builtin_catalyst_and_fluid`（enabled=true；all_of[fluid_present fluid=minecraft:water require_source=false, catalyst_present items=[minecraft:bone_meal] count=1]；effects=[spawn_item, consume_catalyst items=[minecraft:bone_meal] count=1 radius=1]；source.items=[minecraft:redstone]）、`builtin_biome_or_time`（enabled=false；any_of[biome mode=exact biomes=[#minecraft:is_forest], time_of_day 0-12000]；spawn_item golden_apple；source.items=[minecraft:apple]）。
  - `builtin_weather_and_light`：`builtin_thunder_condensation`（enabled=true；all_of[weather=thunder, y_level 60-320]；effects=[weather mode=rain duration_ticks=6000 thundering=true, consume_fluid fluid=minecraft:water require_source=true]；source.items=[minecraft:wet_sponge]）、`builtin_dark_blast`（enabled=false；any_of[light_level 0-7, inverted(weather=clear)]；explosion power=2 fire=true visual_only=false；source.items=[minecraft:gunpowder]）。

### 5.3 阶段 1 需同步更新的文件与需保留的兼容行为

- 必须同步：上述 8 个 `data/.../rules/*.json` 与 8 个 `assets/.../templates/*.json`（两者保持同源）；含 `consume_source` 的 `builtin_multi_effect`、`consume_catalyst` 的 `builtin_conditions`、`consume_fluid` 的 `builtin_weather_and_light` 需映射为固定成本字段。
- 需保留的兼容行为：
  1. 数组形态规则文件（一个文件多条规则）继续支持：`core/service/RuleOverlayWriter.java:123-140` 保留数组形态；`locate()` `:142-163` 支持无 id 的单条目文件按文件名推 id。
  2. 覆盖层写入是「整条 JSON 对象原样落盘」，不重新序列化模型（`RuleOverlayWriter.apply` `:55-100`）⇒ 新字段在未改动的规则上不会因 GUI 保存而丢失，但校验必须在写盘前完成。
  3. 旧字段兼容：`chance/delay_ticks/conditions` 在普通效果上继续有效；只有固定成本效果（`consume_source`/`consume_catalyst`，以及新增的规则级成本字段）禁止这三者。
  4. 未知顶层字段当前只 WARN（`RuleCodecs.java:147-153`）；阶段 1 是否升级为拒绝需在草案中定（见 7.3 与第 8 节）。

---

## 6. 保存链路中新契约校验的插入位置清单

链路（`core/service/RuleEditService.java`，577 行）：`submit()` `:247-312` → `RuleEditChangeSet.parse` → `beginApply` → `applyChangeSet` `:315-378` → 磁盘修订与 `versionMatches`（`:328-333`，VERSION_CONFLICT）→ registries 空→UNAVAILABLE → `RuleSubmissionValidator.validate`（`:341`，VALIDATION_FAILED）→ `overlayRoot` 空→UNAVAILABLE → `RuleOverlayWriter.apply`（`:351-352`）→ conflicts/errors→WRITE_FAILED → `writtenFiles==0`→NO_CHANGES → `rebuildAndRescan`（`:364-372`，失败回 SAVED_NOT_RELOADED + bumpVersion + `RuleCatalogService.invalidate`）→ SUCCESS。

| 插入点 | 位置 | 应加入的校验 | 理由 |
| --- | --- | --- | --- |
| P1（主） | `core/service/RuleSubmissionValidator.java:36` 之后（`RuleValidation.validate` → `RuleReferenceValidator.validate` 之后，同一 `IssueCollector`） | 源成本为正数、固定成本无 chance/conditions/delay、候选列表非空且 id 唯一、候选引用有效、结构版本可识别 | 与现有「整批拒绝」语义一致（`:38-41` RuntimeException 也整批拒绝） |
| P2 | `core/model/RuleValidation.java:150-162`（`validateConsumptionEffects`）旁新增方法，并由 `:69-72` 链式调用 | 固定成本/候选的结构级约束（不依赖注册表） | 加载期（数据包）与保存期共用同一校验 |
| P3 | `core/model/RuleCodecs.java:44-56` 白名单 + `:111-132` codec | 新顶层键登记 + 新字段解码；codec 层用未知字段机制拒绝固定成本带概率/条件/延迟 | 保证「不登记就只有 WARN」的坑被堵住 |
| P4 | `core/service/RuleReferenceValidator.java:19-33` | 候选引用（若候选可被规则内/跨规则引用） | 动态引用校验的既有归口 |
| P5 | `core/service/RuleLoadContext.java:29-35`（如需要） | 结构版本/候选引用的上下文（已知候选集合） | 该 record 已是加载/校验上下文容器；`withServer` `:38-41`、`full`/`overlayOnly` `:44-63` |
| P6 | `core/service/RuleOverlayWriter.java:55-100` 之前（即 P1 前置） | 无需新增；但必须保证校验在写盘前完成 | 写入是原样落盘，事后校验无法回滚已写文件（回滚只在 commit 失败时 `:166-210`） |

---

## 7. 阶段 1 目标契约草案（供 lead / ADR 审议）

> 命名原则：沿用现有蛇形 JSON 键、小写常量、`RuleFields` 集中登记；旧 GUI 未识别字段不受影响。

### 7.1 顶层新增字段

| JSON 键名 | Java 成员 | 类型 | 默认值 | 非法配置拒绝规则 |
| --- | --- | --- | --- | --- |
| `triggers` | `Rule.triggers` | `List<String>`（枚举 `natural/fire/lava/cactus`） | 缺省 = `["natural"]` | 空数组、含未知取值、重复取值、非数组 → 拒绝「未知的消失方式: X」 |
| `source_cost` | `Rule.sourceCost` | int | 缺省 = `1`（PLAN 4.2 阶段 1 默认值建议「未显式配置源成本时每组 1 个」） | `<=0` → 拒绝「源成本必须为正数」；非整数 → codec 拒绝 |
| `catalyst_cost` | `Rule.catalystCost` | 对象 `{items:[TaggedId], count:int}` | 缺省 = 无催化剂成本 | `items` 空、`count<=0` → 拒绝；出现 `chance/delay_ticks/conditions` → 由效果码 Unknown 字段机制拒绝「固定成本不得带概率/条件/延迟」 |
| `combination` | `Rule.combination` | 枚举 `round_robin`/`priority` | 缺省 = `round_robin` | 未知取值 → 拒绝 |
| `outcomes` | `Rule.outcomes` | 候选数组（见 7.2） | 无默认；为兼容旧文件，缺省时可将现有 `effects` 视为唯一候选（见第 8 节待确认） | 空数组 → 拒绝；候选 id 缺失/重复 → 拒绝；候选 effects 为空 → 拒绝 |
| `schema_version` | `Rule.schemaVersion` | int | 缺省 = `1` | 不支持/超出范围 → 拒绝「不支持的结构版本: N」 |

### 7.2 候选结构（`outcomes[i]`）

| 键 | 类型 | 默认 | 拒绝规则 |
| --- | --- | --- | --- |
| `id` | String（稳定标识） | 无（必填） | 空串、同规则内重复 → 拒绝 |
| `effects` | `List<Effect>`（沿用现有 12 类型与通用三字段） | 无（必填） | 空 → 拒绝 |
| `safe_spawn` | boolean | `false`（PLAN 4.2：安全生成默认关闭，沿用原点行为） | — |
| `fill_origin` | boolean | `true`（PLAN 4.2：起点填充默认开启） | — |

- 效果级通用三字段语义不变：`delay_ticks`(0)、`chance`(1.0)、`conditions`；`chance` 仍必须 [0,1] 且有限（`RuleValidation.java:189-213`）。
- 候选内多效果「共同执行、共同限制组数」（PLAN 行为契约）→ 组数上限由候选级结算，不由单效果决定。
- 一次性效果（`weather`/`lightning`/`explosion`，PLAN 行为契约）不参与数量上限；仅含一次性效果的候选每源最多一组 → 需要 `EffectType` 级分类元数据（如 `oncePerSource`，默认 false），而不是在运行时硬编码效果 id 列表。

### 7.3 非法配置拒绝规则汇总（阶段 1 验收对照）

1. `source_cost` 为 0/负数 → 拒绝（PLAN 验收「源成本 0 被拒绝」）。
2. 固定成本对象/效果出现 `chance` / `delay_ticks` / `conditions` → 拒绝（PLAN 验收「成本概率/条件/延迟等非法配置被拒绝」）。
3. `outcomes` 缺失（若不启用兼容映射）、为空、候选 `effects` 为空、候选 id 缺失/重复 → 拒绝。
4. `schema_version` 不可识别 → 拒绝。
5. `triggers`/`combination` 取值未知 → 拒绝。
6. 同规则内同时声明规则级 `source_cost` 与效果式 `consume_source` 且数值不一致 → 拒绝（避免双账目）。
7. `consume_fluid` 只作存在条件、不预留份数（ADR-0001）：阶段 1 只校验其不携带概率/条件/延迟，不参与成本计算。
8. 旧字段 `negated`/条件数组/`groups` 继续按现有文案拒绝（`RuleCodecs.java:87-93`）。

### 7.4 文件级改动面

| 文件（相对工作区） | 阶段 1 改动性质 |
| --- | --- |
| `common/.../core/model/Rule.java` | 新增 triggers/sourceCost/catalystCost/combination/outcomes/schemaVersion 字段与紧凑构造器默认值；调整 `isRunnable`/消耗派生方法 |
| `common/.../core/model/RuleFields.java` | 新增键常量（不动现有 22+ 个） |
| `common/.../core/model/RuleCodecs.java` | `KNOWN_RULE_FIELDS` 扩充；`codec()` 新增可选字段；候选对象编解码 |
| `common/.../core/model/RuleValidation.java` | 新增固定成本/候选/结构版本校验（结构档），注册表档追加一次性效果分类校验 |
| `common/.../core/model/EffectType.java` / `SimpleEffectType.java` / `Effect.java` | 一次性效果分类元数据（接口默认方法或记录组件） |
| `common/.../core/type/BuiltinEffectTypes.java` | 声明 weather/lightning/explosion 为一次性 |
| `common/.../core/type/effect/ConsumeSourceEffect.java` / `ConsumeCatalystEffect.java` | 复用为固定成本语义时限制通用三字段；或改为仅由规则级成本字段驱动 |
| `common/.../core/model/ConsumptionDefaults.java` | 保留常量（客户端无引用，但 `Rule` 与执行器使用） |
| `common/.../core/service/BuiltinTypeRegistries.java` | `perRoundSourceConsumption` 改读 `rule.sourceCost`；隐式消耗语义重定义 |
| `common/.../core/service/RuleReferenceValidator.java` | 候选引用校验（如需） |
| `common/.../core/service/RuleSubmissionValidator.java` | 在 `:36` 后挂接新校验（P1） |
| `common/.../core/service/RuleLoadContext.java` | 必要时新增上下文字段（P5） |
| `common/.../core/runtime/ConversionRuntime.java` | 按 `triggers`/`outcomes`/`sourceCost` 读取；阶段 1 保持现有执行语义，账目改造留阶段 4 |
| `common/.../core/network/protocol/*` | 能力描述/快照携带结构版本；保持向后兼容 |
| `common/src/main/resources/data/itemdespawntowhat/idtw/rules/*.json`（8 个） | 迁移到新字段（triggers/outcomes/固定成本），旧规则仅自然消失 |
| `common/src/main/resources/assets/itemdespawntowhat/idtw/templates/*.json`（8 个） | 与 data 样本保持同源同步 |
| `common/.../client/edit/BuiltinEditorDefaults.java` | 新建规则默认体补新字段（或依赖服务端默认） |
| `common/.../client/ui/screen/RuleEditorScreen.java` | `localIssues` 补源成本>0 / 候选非空 / 候选 id 唯一；GUI 候选编辑留后续阶段 |
| `common/.../client/edit/RuleDraft.java` 等 | 无需改动（路径级保留未识别字段已满足）；确认新字段不被表单重建时丢弃 |

---

## 8. 未核实项与待确认决策

1. `TypeDispatch.flat` 对「白名单登记了但 codec 未接的顶层字段」的行为，我只核实了 `warnUnknownFields` 白名单这一侧（`RuleCodecs.java:147-153`）；登记后是否会被 `RecordCodecBuilder` 忽略或报错**未核实**（阶段 1 引入新字段时需实测）。
2. PLAN 4.2 中「可选安全生成/起点填充」的归属层级（规则级 vs 候选级）**未核实**：原文列在候选结果描述内，但阶段 1 默认值建议又写在规则概念结构下。草案暂按候选级。
3. 效果式消耗（`consume_source`/`consume_catalyst`）在阶段 1 是「迁出 effects」还是「与规则级固定成本并存」**未核实**（PLAN 只写「区分固定成本与效果」）。草案给出并存 + 冲突拒绝的最小改动路径。
4. `outcomes` 缺失时是否自动把现有 `effects` 包成唯一候选以兼容未迁移文件，**待 lead / ADR 决定**（PLAN 说本轮只更新内置数据包、无历史迁移）。
5. 客户端 GUI 的候选编辑是否属于阶段 1 范围**未核实**（PLAN 阶段 1 提到「GUI API」，但未列候选编辑具体验收项）。
