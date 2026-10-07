# 功能模块：类型系统与内置类型（`core/registry` + `core/extension` + `core/type`）

> 事实来源：`core/registry/**`、`core/extension/**`、`core/type/**`（含 `condition/`、`condition/eval/`、`effect/`、`effect/exec/`），以及它们依赖的契约 `core/api/{TypeDefinition, TypeRegistry, EffectExecutor, ConditionEvaluator, EffectResult, ConditionResult, Evaluability, TaggedId}.java`、`core/model/{EffectType, ConditionType, SimpleEffectType, SimpleConditionType, Condition, Effect, CommonFields}.java`、装配点 `core/service/BuiltinTypeRegistries.java`。
> 效果与条件的可扩展注册体系和内置类型；条件树形状见 [ADR-0018](../../../adr/0018-condition-tree-contract.md)，注册/分发机制见 [ADR-0013](../../../adr/0013-dfu-codec-and-flat-type-dispatch.md)。

## 1. 定位

- `core/registry`：`TypeRegistry` 的默认实现 `SimpleTypeRegistry`（**注册期可变、`freeze()` 后只读**）+ 两个语义异常。
- `core/extension`：第三方 SPI `RuleTypeProvider`，经 `META-INF/services` 加载。
- `core/type`：10 个内置条件、10 个内置效果，以及公共工具 `EnumCodecs`、`RefChecks`。
- 类型定义形态（`EffectType`/`ConditionType`/`Simple*Type`）与 `Effect`/`Condition` 接口定义在 `core/model`，执行/求值上下文与回执定义在 `core/api`；本文只覆盖注册表、SPI 与内置实现。

## 2. 类清单

### 2.1 `core/registry`

| 类 | 职责 | 关键成员 |
|---|---|---|
| `SimpleTypeRegistry<T>` | 默认注册表：注册期用 `LinkedHashMap` 保序；`freeze()` 构造不可变 `FrozenView` 并用 volatile 发布 | `register` / `find` / `require` / `all` / `ids` / `freeze` / `isFrozen` / `size` |
| `DuplicateTypeException` | 重复 id 注册（**绝不静默覆盖**，`putIfAbsent` 保证冲突时不写入） | `typeId()`、`existingDescription()`、`incomingDescription()` |
| `RegistryFrozenException` | 冻结后再注册 | `typeId()`、`definitionDescription()` |

> 接口 `TypeRegistry<T>` 本身**不承诺只读**（`register` 始终在契约内），冻结语义完全由 `SimpleTypeRegistry` 保证。接口成员：`register` / `find` / `require` / `all` / `ids` + 默认 `getOrNull(id)`。`all()`/`ids()` 在注册期也返回快照（`List.copyOf`/`unmodifiableSet`），调用方拿到的集合不随之后的注册变化。
> 并发约定：注册期只允许**单线程**调用 `register`，`freeze()` 必须由注册线程调用；冻结后任意线程可并发读。

### 2.2 `core/extension`

| 类 | 职责 |
|---|---|
| `RuleTypeProvider` | 第三方类型注册 SPI，两阶段调用：`registerConditions(TypeRegistry<ConditionType<?>>)` 先、`registerEffects(TypeRegistry<EffectType<?>>, Codec<ConditionExpression>)` 后；两者均为 `default` 空实现 |

### 2.3 `core/type` 公共工具

| 类 | 职责 |
|---|---|
| `EnumCodecs` | `lowerCase(Class<E>)`：枚举取 JSON 统一小写下划线、解析**大小写不敏感**，未知或空取值报错时附可选值列表 |
| `RefChecks` | 动态引用校验助手：非标签引用未命中 → ERROR；标签引用仅在标签数据已绑定时校验，未命中 → WARN（数据包标签可能后加载，不能据此拒载）。`check`（单项）/`checkAll`（列表，路径形如 `path[0]`） |
| `BuiltinConditionTypes` | 注册 10 个内置条件类型；`create()`（构建并冻结）/`createMutable()`（交由装配器统一冻结） |
| `BuiltinEffectTypes` | 注册 10 个内置效果类型；`create(expressionCodec)`/`createMutable(expressionCodec)`（效果记录含效果级 `conditions`，需先有表达式 Codec） |

### 2.4 类型定义契约（`core/model`，本文引用）

| 类型 | 形态 | 关键成员 |
|---|---|---|
| `TypeDefinition<P>` | interface | `id()`、`MapCodec<P> codec()`、`default boolean validateParams(P, IssueCollector, String fieldPath)`（默认 true） |
| `EffectType<P extends Effect>` | interface | 继承 `TypeDefinition`，加 `executor()` 与 `default boolean oneShot()`（默认 false） |
| `ConditionType<P extends Condition>` | interface | 继承 `TypeDefinition`，加 `evaluator()` 与 `default Evaluability evaluability(P, ConditionContext)`（默认 `AVAILABLE`） |
| `SimpleEffectType<P>` | record | `(id, MapCodec<P>, Validator<P>, EffectExecutor<P>, boolean oneShot)`；四参构造缺省按非一次性效果 |
| `SimpleConditionType<P>` | record | `(id, MapCodec<P>, Validator<P>, ConditionEvaluator<P>, EvaluabilityCheck<P>)`；四参构造缺省 `evaluability` 恒为 `AVAILABLE` |

> 约定：**参数对象 `P` 同时实现 `Effect`/`Condition`**，分发解码后可直接作为规则里的效果/条件叶使用。`Effect` 接口只剩 `type()`/`delayTicks()`/`chance()`/`conditions()`（`conditions()` 为 `@Nullable`，`effectiveConditions()` 空值回落 `ConditionExpression.EMPTY`）；`Condition` 接口只剩 `type()`。

### 2.5 内置实现的组织模式

每个内置类型 = 一个**参数 record**（实现 `Effect`/`Condition`）+ `static final ResourceLocation ID` + 静态 `CODEC`/`codec(expressionCodec)` + 静态 `validateParams(...)` + 静态工厂 `effectType(...)`/`conditionType()`；执行/求值实现位于 `exec/`、`eval/` 子包的 `*Executor`/`*Evaluator`。

> 命名坑：静态工厂**不能叫 `type()`**——接口已有无参实例方法 `type()`，同签名静态方法在 Java 中非法，故统一用 `effectType(...)`/`conditionType()`。

### 2.6 `core/type/effect/exec` 共享助手

| 类 | 职责 | 关键成员 |
|---|---|---|
| `EffectTargets` | 执行器共享的引用解析与空间查询工具；**不捕获、不吞异常**，硬错误直接抛出交给运行时 | `resolve`（未命中返回 null）/`resolveOrThrow`、`blockBox(center, radius)`、`matchesAny(items, stack)`（催化剂固定成本与效果式消耗同口径）、`addEntity`、`addConversionProduct`、`saturatedAdd`/`saturatedMultiply`、`forEachStep(...)` |
| `ReturnItemSpawner` | 掉落物生成统一入口：转化产物与返还物共用「创建 + 授予实体状态 + 加入世界」路径 | `Kind{CONVERSION_PRODUCT, PERMANENT_RETURN}`、`applyGrant`、`spawn`、`deliver`、内部 `PositionSearch`（三级位置搜索：起点附近 → 向上扫描 → 限高以上，跨刻分步） |

## 3. 注册生命周期（顺序固定）

`BuiltinTypeRegistries.create()`（`core/service`）固化的装配顺序：

```text
内置条件类型(10) → provider 条件类型 → freeze
→ 构建条件表达式 Codec
→ 内置效果类型(10) → provider 效果类型 → freeze
```

原因：效果记录带**效果级 `conditions`** 字段，需要完整条件表达式 Codec 才能编解码。`ServiceLoader<RuleTypeProvider>` 按 provider 类名排序保证跨端确定性。任一重复 id、provider 构造或登记异常都使启动失败，不静默忽略。详见 [type-registry-dispatch.md](../systems/type-registry-dispatch.md)。

## 4. 内置条件类型（10 个）

所有条件叶通用字段：`type`（必填）。**叶级取反（`negated`）已删除**：取反一律由条件树的 `inverted` 节点承担，叶内出现 `negated` 由解码器明确报错（`Condition.NEGATED_FIELD` 仅作常量保留供识别旧格式）。条件叶 JSON 形状为 `{"op":"leaf","condition":{"type":...,<类型专属字段平铺>}}`，取反为 `{"op":"inverted","term":{...}}`。

求值器 `ConditionEvaluator.test(P, ConditionContext)` 是**纯谓词**，返回 `boolean`（未取反的原始判定），**不承担消耗**。运行时由条件树求值器把谓词结果与可求值性门禁聚合为四态 `ConditionResult{MATCH, NO_MATCH, UNAVAILABLE, ERROR}`：只有 `MATCH` 通过；`UNAVAILABLE`（上下文不足）与 `ERROR`（求值抛异常）一律不触发，且 `inverted` 节点**不改变**（原样穿透）。

| # | type | 参数 | 语义 |
|---|---|---|---|
| 1 | `dimension` | `dimensions: List<ResourceLocation>`（必填非空） | 维度 id 命中任意一项 |
| 2 | `biome` | `mode: exact\|climate`（必填）；`biomes: List<TaggedId>`（exact 必填）；6 个 `ClimateRange{min,max}`（域 `[-1,1]`，可省略端，climate 至少一个区间） | exact 按群系 id/`#tag`；climate 采样 6 气候参数，已填区间须全满足 |
| 3 | `weather` | `weather: clear\|rain\|thunder`（必填） | clear=无雨无雷；rain=有雨无雷；thunder=仅看雷暴 |
| 4 | `outdoor` | 无 | `MOTION_BLOCKING_NO_LEAVES` 高度图 ≤ y+1 |
| 5 | `surrounding_blocks` | `up/down/north/south/east/west: TaggedId`（可空，**六向不可全空**） | 逐方向比对相邻方块，已填方向须全命中 |
| 6 | `catalyst_present` | `items: List<TaggedId>`（必填非空）；`count: int[1,64]`（默认 1） | 所在方块格 1×1×1 内命中物品堆叠数 ≥ count（排除源自身） |
| 7 | `fluid_present` | `fluid: TaggedId`（可空=任意）；`require_source: bool`（默认 true） | 非源模式接受同族流动变体；`#minecraft:empty` 恒不命中 |
| 8 | `time_of_day` | `from: int[0,23999]`、`to: int[0,23999]`（均必填） | `dayTime mod 24000` 区间；`from>to` 表示跨零点 |
| 9 | `y_level` | `min/max: Integer[-2048,2048]`（可空=不限） | Y 坐标区间；min 不得大于 max |
| 10 | `light_level` | `min/max: Integer[0,15]`（可空=不限） | `getMaxLocalRawBrightness`（天光衰减后与方块光取大）区间；min 不得大于 max |

> `surrounding_blocks` 有一条**特殊门禁**：周围区块未全部加载时通过 `evaluability(...)` 返回 `Evaluability.UNAVAILABLE`（而非 `false`），由求值层映射为 `ConditionResult.UNAVAILABLE`，`inverted` 也不会把它当作「不成立」翻转。
> `dimension`、`biome` 属动态注册表，`validateParams` 只做语法与数量校验，引用存在性复核留给运行时/命令层（那里能拿到 `RegistryAccess`）。

## 5. 内置效果类型（10 个 = 7 生效 + 3 消耗）

所有效果通用字段：`type`（必填）、`delay_ticks`（默认 0）、`chance`（默认 1.0，域 `[0,1]`）、`conditions`（可选，单根条件树，缺省省略而非 `null`）。

执行器 `EffectExecutor.execute(P, EffectContext)` 返回 **`EffectResult`**（不再是 `void`）：`outcome ∈ {APPLIED, DEFERRED, SKIPPED, FAILED}` + `appliedUnits`（实际完成量：件数/个数/方块数/经验点/世界效果次数）+ `pendingUnits`（已受理待完成量），`oneShot` 标记一次性世界效果。异步批次经 `EffectContext.reportProgress(int)` 把 `pendingUnits` 逐步收敛为 `appliedUnits`；**结算层只按回执记账，禁止用计划数量冒充成功量**。`counted()` 表示该回执计入本组产出（`APPLIED`/`DEFERRED`）。

### 5.1 生效效果（7）

| # | type | 参数 | 语义 |
|---|---|---|---|
| 1 | `spawn_entity` | `variant: item/entity/experience` 必填；各子类参数见下表 | 统一生成掉落物、通用实体或经验；共用概率/延迟/条件 |
| 2 | `place_block` | `block: TaggedId`（与 `use_source_block` **至少其一**）；`use_source_block: bool`/false；`shape: square\|circle\|cross`/square；`count: int[1,64]`/1；`radius: int[1,32]`/6；`limit: Integer[1,4096]` | 按形状由内向外扩散放置方块，只替换可替换位置、不突破自身半径 |
| 3 | `loot_table` | `loot_table: ResourceLocation`（必填，**不支持 `#tag`**）；`luck: float[-100,100]`/0 | 以位置为原点开战利品表（CHEST 参数集，`THIS_ENTITY`=源），逐轮开表并分批生成 |
| 4 | `lightning` | `count: int[1,16]`/1 | 第 1 道落原点，后续散布 r=5，间隔 8 刻 |
| 5 | `explosion` | `power: float[0,16]`/3.0；`fire: bool`/false；`visual_only: bool`/false | `visual_only` 只粒子+音效；否则 TNT 交互爆炸 |
| 6 | `arrow_rain` | `count: int[1,256]`/16；`pickup: disallowed\|allowed\|creative_only`/disallowed；`potion_effects: List<{effect: TaggedId, duration_ticks: int[1,1000000]/100, amplifier: int[0,255]/0}>` | 上方 +80 高度落箭雨，间隔 2 刻，可携带药水效果 |
| 7 | `weather` | `mode: rain\|clear`（必填）；`duration_ticks: int[1,24000]`/6000；`thundering: bool`/false | 切换维度天气；无天空光维度跳过；已处于目标天气则跳过 |

实体生成的 JSON 字段仍与 `type` 同级，不增加 `product` 包装：

| `variant` | 专用参数 | 单位与校验 |
|---|---|---|
| `item` | `item: TaggedId` 必填，`count: int[1,64]` 默认 1 | 物品件数；支持物品标签 |
| `entity` | `entity: TaggedId` 必填，`count: int[1,64]` 默认 1，`age: int` 默认 0 | 实体个数，生物及其他实体；age 仅 AgeableMob 生效；拒绝 item / experience_orb 及含它们的标签 |
| `experience` | `amount: int[1,65536]` 默认 1，`per_source_item: bool` 默认 false | 经验点数；true 按本组实扣源件数计算，false 按组；总点数按原版拆分并保留原版合并 |

子类外字段、规则中的邻近 `limit/radius`、旧 `spawn_item/spawn_xp` 以及缺少 variant 的旧 spawn_entity 均不兼容。邻近阈值移至 [服务端配置](../systems/config.md)；开组后不因阈值截断。`place_block` 的 limit/radius 保留。

> **一次性世界效果**（lightning / explosion / arrow_rain / weather）：注册时 `oneShot=true`，执行器回执带 `asOneShot()` 标记，**不乘 `rounds()`**、不参与容量计算，仅含一次性效果的候选对同一源最多尝试一组。实体产出按组展开并使用共享配置做开组准入；place_block 保留自身规则容量。见 [conversion-runtime.md](conversion-runtime.md)。
> 候选级字段 `safe_spawn`、`fill_origin` 属于**候选结果**（`core/model/OutcomeCandidate.java`），不在任何单个效果的参数里；执行时经 `EffectContext.safeSpawn()`/`fillOrigin()` 透传给 `spawn_entity` / `place_block`。

### 5.2 消耗效果（3）

| # | type | 参数 | 语义 |
|---|---|---|---|
| 8 | `consume_source` | `count: int[1,64]`/1 | 从**实时**堆叠扣 `count×rounds`，扣空则 discard；同时是「每组源成本」的声明，结算层按规则成本统一扣减，本执行器按实际扣减量回执避免重复记账 |
| 9 | `consume_catalyst` | `items: List<TaggedId>`（必填非空）；`count: int[1,64]`/1；`radius: int[1,8]`/1 | 半径内由近及远消耗命中催化剂，总量 ≤ `count×rounds` |
| 10 | `consume_fluid` | `fluid: TaggedId`/任意；`require_source: bool`/true | 每轮消耗 1 格（同 tick 同位置通常只消耗 1 格）；含水方块只去 waterlogged，否则整块置 AIR；只作实时存在条件，不参与份数预留 |

同一条规则内**同一消耗类型只允许一次**，否则拒载。规则未声明任何 `consume_*` 时隐式消耗 1 个源物品（判定入口 `BuiltinTypeRegistries.perRoundSourceConsumption(Rule)`，详见 [conversion-runtime.md](conversion-runtime.md)）。

## 6. 新增一种类型（步骤摘要）

以新增效果为例：

| # | 位置 | 内容 |
|---|---|---|
| 1 | `core/type/effect/<Xxx>Effect.java` | 参数 record（实现 `Effect`）+ 字段常量 + `codec(expressionCodec)` + `validateParams` + `effectType(...)`（一次性效果传 `oneShot=true`） |
| 2 | `core/type/effect/exec/<Xxx>Executor.java` | 静态 `execute(P, EffectContext)`，返回 `EffectResult`，只做世界操作 |
| 2 | `core/type/BuiltinEffectTypes.java` | 一行 `registry.register(XxxEffect.effectType(expressionCodec))` |

条件类型同理（`condition/` + `condition/eval/` + `BuiltinConditionTypes`）；需要上下文门禁的条件在 `conditionType()` 里追加 `evaluability`。**框架代码无需改动**：分发、校验调度、注册表都由既有抽象承担。

三条硬约束：① 不产 null（`Optional` 承载、末步 `orElse(null)`）；② 静态工厂不叫 `type()`；③ 引用字段用 `TaggedId`（支持 `#tag`），只有确无标签语义才用裸 `ResourceLocation`。

执行器/求值器约定：延迟用 `context.schedule(...)`（绑定"维度+位置"，不要求源存活）；随机用 `context.random()`（维度随机）；大量产出用 `EffectTargets.forEachStep(...)` 分批；**不吞异常**，交给运行时隔离记录。

## 7. 第三方 Java 扩展 SPI

实现 `RuleTypeProvider`，在第三方 jar 登记 `META-INF/services/com.meteorite.itemdespawntowhat.core.extension.RuleTypeProvider`（一行一个实现类全名）。`registerEffects` 拿到的 `expressionCodec` **已包含全部 provider 注册的条件**，效果级 `conditions` 因此可用自己的条件。

约束：类型 id 用自有命名空间；Codec 必须暴露完整 `keys(ops)`；效果与条件不得注册到其它阶段；扩展 jar 必须在服务端安装并满足 loader 依赖声明。

## 8. 相关

- 分发与冻结机制：[../systems/type-registry-dispatch.md](../systems/type-registry-dispatch.md)
- 参数校验与问题模型：[../systems/issue-validation.md](../systems/issue-validation.md)
- 规则字段全表：[rule-model.md](rule-model.md)
- 运行的派发与结算：[conversion-runtime.md](conversion-runtime.md)
- 决策：[ADR-0013](../../../adr/0013-dfu-codec-and-flat-type-dispatch.md)、[ADR-0018](../../../adr/0018-condition-tree-contract.md)
