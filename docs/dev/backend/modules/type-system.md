# 功能模块：类型系统与内置类型（`core/registry` + `core/extension` + `core/type`）

> 事实来源：`core/registry/**`、`core/extension/**`、`core/type/**`（含 `condition/`、`condition/eval/`、`effect/`、`effect/exec/`）。
> 效果与条件的可扩展注册体系；决策见 [ADR-0013](../../../adr/0013-dfu-codec-and-flat-type-dispatch.md)。

## 1. 定位

- `core/registry`：`TypeRegistry` 的默认实现 `SimpleTypeRegistry`（**注册期可变、`freeze()` 后只读**）+ 两个语义异常。
- `core/extension`：第三方 SPI `RuleTypeProvider`，经 `META-INF/services` 加载。
- `core/type`：10 个内置条件、12 个内置效果，以及公共工具 `EnumCodecs`、`RefChecks`。

## 2. 类清单

### 2.1 `core/registry`

| 类 | 职责 | 关键成员 |
|---|---|---|
| `SimpleTypeRegistry<T>` | 默认注册表：注册期用 `LinkedHashMap` 保序；`freeze()` 构造不可变视图并用 volatile 发布 | `register` / `find` / `require` / `all` / `ids` / `freeze` / `isFrozen` / `size` |
| `DuplicateTypeException` | 重复 id 注册（**绝不静默覆盖**） | `typeId()`、`existingDescription()`、`incomingDescription()` |
| `RegistryFrozenException` | 冻结后再注册 | `typeId()`、`definitionDescription()` |

> 接口 `TypeRegistry` 本身**不承诺只读**；冻结语义由 `SimpleTypeRegistry` 保证。`all()`/`ids()` 在注册期也返回快照（`List.copyOf`/`unmodifiableSet`）。

### 2.2 `core/extension`

| 类 | 职责 |
|---|---|
| `RuleTypeProvider` | 第三方类型注册 SPI，两阶段调用：`registerConditions(TypeRegistry<ConditionType<?>>)` 先、`registerEffects(TypeRegistry<EffectType<?>>, Codec<ConditionExpression>)` 后 |

### 2.3 `core/type` 公共工具

| 类 | 职责 |
|---|---|
| `EnumCodecs` | `lowerCase(Class<E>)`：枚举取 JSON 统一小写下划线、解析**大小写不敏感**，未知值报错时附可选值列表 |
| `RefChecks` | 动态引用校验助手：非标签引用未命中 → ERROR；标签引用仅在标签数据已绑定时校验，未命中 → WARN（数据包标签可能后加载，不能据此拒载） |
| `BuiltinConditionTypes` | 注册 10 个内置条件类型，`create()`/`createMutable()` |
| `BuiltinEffectTypes` | 注册 12 个内置效果类型，`create(expressionCodec)`（效果记录含效果级 `conditions`，需先有表达式 Codec） |

### 2.4 内置实现的组织模式

每个内置类型 = 一个**参数 record**（实现 `Effect`/`Condition`）+ `static final ResourceLocation ID` + 静态 `CODEC`/`codec(...)` + 静态 `validateParams(...)` + 静态工厂 `effectType(...)`/`conditionType()`；执行/求值实现位于 `exec/`、`eval/` 子包的 `*Executor`/`*Evaluator`。

> 命名坑：静态工厂**不能叫 `type()`**——接口已有无参实例方法 `type()`，同签名静态方法在 Java 中非法。

## 3. 注册生命周期（顺序固定）

`BuiltinTypeRegistries.create()`（`core/service`）固化的装配顺序：

```text
条件类型(内置 + provider) → freeze → 构建条件表达式 Codec → 效果类型(内置 + provider) → freeze
```

原因：效果记录带**效果级 `conditions`** 字段，需要完整条件表达式 Codec 才能编解码。`ServiceLoader<RuleTypeProvider>` 按 provider 类名排序保证跨端确定性。任一重复 id、provider 构造或登记异常都使启动失败，不静默忽略。详见 [type-registry-dispatch.md](../systems/type-registry-dispatch.md)。

## 4. 内置条件类型（10 个）

所有条件叶通用字段：`type`（必填）、`negated`（默认 false）。求值器为纯谓词，**不承担消耗**。

| # | type | 参数 | 语义 |
|---|---|---|---|
| 1 | `dimension` | `dimensions: List<ResourceLocation>`（必填非空） | 维度 id 命中任意一项 |
| 2 | `biome` | `mode: exact\|climate`（必填）；`biomes: List<TaggedId>`（exact 必填）；6 个 `ClimateRange{min,max}`（域 `[-1,1]`，可省略端） | exact 按群系 id/`#tag`；climate 采样 6 气候参数，已填区间须全满足 |
| 3 | `weather` | `weather: clear\|rain\|thunder`（必填） | clear=无雨无雷；rain=有雨无雷；thunder=仅看雷暴 |
| 4 | `outdoor` | 无 | `MOTION_BLOCKING_NO_LEAVES` 高度图 ≤ y+1 |
| 5 | `surrounding_blocks` | `up/down/north/south/east/west: TaggedId`（可空，**六向不可全空**） | 逐方向比对相邻方块，已填方向须全命中 |
| 6 | `catalyst_present` | `items: List<TaggedId>`（必填非空）；`count: int[1,64]`（默认 1） | 所在方块格 1×1×1 内命中物品堆叠数 ≥ count（排除源自身） |
| 7 | `fluid_present` | `fluid: TaggedId`（可空=任意）；`require_source: bool`（默认 true） | 非源模式接受同族流动变体；`#minecraft:empty` 恒不命中 |
| 8 | `time_of_day` | `from: int[0,23999]`、`to: int[0,23999]`（均必填） | `dayTime mod 24000` 区间；`from>to` 表示跨零点 |
| 9 | `y_level` | `min/max: Integer[-2048,2048]`（可空=不限） | Y 坐标区间 |
| 10 | `light_level` | `min/max: Integer[0,15]`（可空=不限） | `getMaxLocalRawBrightness`（天光衰减后与方块光取大）区间 |

`surrounding_blocks` 有一条**特殊门禁**：目标区块未加载时直接判 false 且**不取反**（避免把"未知"当成"不满足"来取反）。

## 5. 内置效果类型（12 个 = 9 生效 + 3 消耗）

所有效果通用字段：`type`（必填）、`delay_ticks`（默认 0）、`chance`（默认 1.0，域 `[0,1]`）、`conditions`（可选）。

### 5.1 生效效果（9）

| # | type | 参数 | 语义 |
|---|---|---|---|
| 1 | `spawn_item` | `item: TaggedId`（必填，支持 `#tag`）；`count: int[1,64]`/1；`limit: Integer[1,4096]`/不限；`radius: Integer[1,32]`/不限 | 触发位置生成物品，受邻域 `limit` 收敛 |
| 2 | `spawn_entity` | `entity: TaggedId`（必填）；`count: int[1,64]`/1；`age: int`/0（负=幼体）；`limit: Integer[1,4096]`；`radius: Integer[1,32]` | 生成实体；`AgeableMob` 按 age 设幼体 |
| 3 | `place_block` | `block: TaggedId`（与 `use_source_block` **至少其一**）；`use_source_block: bool`/false；`shape: square\|circle\|cross`/square；`count: int[1,64]`/1；`radius: int[1,32]`/6；`limit: Integer[1,4096]` | 按形状扩散放置方块 |
| 4 | `spawn_xp` | `amount: int[1,65536]`/1；`per_source_item: bool`/false | 生成经验球；`per_source_item` 用 `coveredSourceItems` 倍率 |
| 5 | `loot_table` | `loot_table: ResourceLocation`（必填，**不支持 `#tag`**）；`luck: float[-100,100]`/0 | 以位置为原点开战利品表（CHEST 参数集，`THIS_ENTITY`=源） |
| 6 | `lightning` | `count: int[1,16]`/1 | 第 1 道落原点，后续散布 r=5，间隔 8 刻 |
| 7 | `explosion` | `power: float[0,16]`/3.0；`fire: bool`/false；`visual_only: bool`/false | `visual_only` 只粒子+音效；否则 TNT 交互 |
| 8 | `arrow_rain` | `count: int[1,256]`/16；`pickup: disallowed\|allowed\|creative_only`/disallowed；`potion_effects: List<{effect: TaggedId, duration_ticks: int[1,1000000]/100, amplifier: int[0,255]/0}>` | 上方 +80 高度落箭雨，间隔 2 刻 |
| 9 | `weather` | `mode: rain\|clear`（必填）；`duration_ticks: int[1,24000]`/6000；`thundering: bool`/false | 切换维度天气；无天空光维度跳过 |

> 一次性世界效果（lightning / explosion / arrow_rain / weather）**不乘 `rounds()`**；产出类效果（spawn_item / spawn_entity / place_block / spawn_xp）按 `rounds()` 展开并受 `limit` 收敛。见 [conversion-runtime.md](conversion-runtime.md)。

### 5.2 消耗效果（3）

| # | type | 参数 | 语义 |
|---|---|---|---|
| 10 | `consume_source` | `count: int[1,64]`/1 | 从**实时**堆叠扣 `count×rounds`，扣空则 discard（用实时 stack 而非快照） |
| 11 | `consume_catalyst` | `items: List<TaggedId>`（必填非空）；`count: int[1,64]`/1；`radius: int[1,8]`/1 | 半径内由近及远消耗命中催化剂，总量 ≤ `count×rounds` |
| 12 | `consume_fluid` | `fluid: TaggedId`/任意；`require_source: bool`/true | 每轮消耗 1 格；含水方块只去 waterlogged，否则整块置 AIR |

同一条规则内**同一消耗类型只允许一次**，否则拒载。规则未声明任何 `consume_*` 时隐式消耗 1 个源物品。

## 6. 新增一种类型（步骤摘要）

以新增效果为例：

| # | 位置 | 内容 |
|---|---|---|
| 1 | `core/type/effect/<Xxx>Effect.java` | 参数 record（实现 `Effect`）+ 字段常量 + `codec(expressionCodec)` + `validateParams` + `effectType(...)` |
| 2 | `core/type/effect/exec/<Xxx>Executor.java` | 实现 `EffectExecutor`，只做世界操作 |
| 3 | `core/type/BuiltinEffectTypes.java` | 一行 `registry.register(XxxEffect.effectType(expressionCodec))` |

条件类型同理（`condition/` + `condition/eval/` + `BuiltinConditionTypes`）。**框架代码无需改动**：分发、校验调度、注册表都由既有抽象承担。

三条硬约束：① Codec 不产 null（`Optional` 承载、末步 `orElse(null)`）；② 静态工厂不叫 `type()`；③ 引用字段用 `TaggedId`（支持 `#tag`），只有确无标签语义才用裸 `ResourceLocation`。

执行器/求值器约定：延迟用 `context.schedule(...)`（绑定"维度+位置"，不要求源存活）；随机用 `context.random()`；**不吞异常**，交给运行时隔离记录。

## 7. 第三方 Java 扩展 SPI

实现 `RuleTypeProvider`，在第三方 jar 登记 `META-INF/services/com.meteorite.itemdespawntowhat.core.extension.RuleTypeProvider`（一行一个实现类全名）。`registerEffects` 拿到的 `expressionCodec` **已包含全部 provider 注册的条件**，效果级 `conditions` 因此可用自己的条件。

约束：类型 id 用自有命名空间；Codec 必须暴露完整 `keys(ops)`；效果与条件不得注册到其它阶段；扩展 jar 必须在服务端安装并满足 loader 依赖声明。

## 8. 相关

- 分发与冻结机制：[../systems/type-registry-dispatch.md](../systems/type-registry-dispatch.md)
- 参数校验与问题模型：[../systems/issue-validation.md](../systems/issue-validation.md)
- 规则字段全表：[rule-model.md](rule-model.md)
- 决策：[ADR-0013](../../../adr/0013-dfu-codec-and-flat-type-dispatch.md)
