# 扩展指南：注册效果类型与条件类型

> 目标读者：想新增一种效果或一种条件的开发者（含第三方模组作者）。
> 本文以真实的内置实现为模板，字段名与调用方式都可在仓库中逐个核对。

## 1. 改动点速览

新增**一种效果类型**（core 侧最少 2 处 + 客户端 1 处）：

| # | 位置 | 内容 |
|---|---|---|
| 1 | `core/type/effect/<Xxx>Effect.java` | 参数记录 + 字段常量 + `codec` + `validateParams` + `effectType` |
| 2 | `core/type/effect/exec/<Xxx>Executor.java` | 执行器（实现 `EffectExecutor`） |
| 3 | `core/type/BuiltinEffectTypes.java` | 一行 `registry.register(XxxEffect.effectType(expressionCodec))` |
| 4 | `client/ui/view/EffectParams.java`（可选） | 登记声明式表单规格；不登记则 GUI 不提供该类型的编辑表单 |

新增**一种条件类型**：把上面的 1/2 换成 `core/type/condition/<Xxx>Condition.java` 与 `core/type/condition/eval/<Xxx>Evaluator.java`，第 3 步改为 `core/type/BuiltinConditionTypes.java`，第 4 步改为 `client/ui/view/RuleConditionInputs.java`。

**框架代码无需改动**：分发、校验调度、注册表、GUI 表单渲染都由既有抽象承担。

## 2. 前置概念

| 概念 | 契约 | 说明 |
|---|---|---|
| `TypeDefinition<P>` | `id()` / `codec()` / `validateParams()` | 可注册类型定义的公共形态 |
| `EffectType<P>` | 继承 `TypeDefinition`，加 `executor()` | 效果类型定义；参数对象 `P` 同时实现 `Effect` |
| `ConditionType<P>` | 继承 `TypeDefinition`，加 `evaluator()` | 条件类型定义；参数对象 `P` 同时实现 `Condition` |
| `TypeRegistry<T>` | `register` / `find` / `require` / `all` / `ids` | 注册表契约；重复 id 抛异常，`freeze()` 后再注册抛 `RegistryFrozenException` |
| `SimpleEffectType` / `SimpleConditionType` | `(id, codec, validator, executor/evaluator)` | 内置类型统一使用的一行式定义 |
| 扁平类型分发 | `TypeDispatch.flat(registry, typeIdGetter)` | 类型专属字段与通用字段处在**同一个 JSON 对象**中，不产生嵌套 `value` |

**通用字段**由 `core/model/CommonFields` 提供编解码片段，所有效果共享：`delay_ticks` / `chance` / 效果级 `conditions`；所有条件共享 `negated`。

## 3. 新增效果类型（完整步骤）

### 3.1 参数记录

```java
package com.meteorite.itemdespawntowhat.core.type.effect;

/**
 * <类型 id 的 path>：一句话说明这个效果做什么。
 */
public record MyEffect(
        int amount,                 // 类型专属参数
        int delayTicks,             // 通用字段（必须原样保留）
        double chance,              // 通用字段
        @Nullable ConditionExpression conditions   // 通用字段，可空
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "my_effect");
    // 类型专属参数字段名（snake_case）
    public static final String AMOUNT_FIELD = "amount";
    // 默认值与取值区间常量
    public static final int DEFAULT_AMOUNT = 1;
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 64;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<MyEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(MIN_AMOUNT, MAX_AMOUNT).optionalFieldOf(AMOUNT_FIELD, DEFAULT_AMOUNT)
                        .forGetter(MyEffect::amount),
                CommonFields.delayTicks(MyEffect::delayTicks),
                CommonFields.chance(MyEffect::chance),
                CommonFields.optionalConditions(MyEffect::conditions, expressionCodec)
        ).apply(instance, (amount, delayTicks, chance, conditions) ->
                new MyEffect(amount, delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器
    public static EffectType<MyEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), MyEffect::validateParams, MyExecutor::execute);
    }

    // 参数语义校验；问题写入 issues，返回 false 表示该条规则拒载
    public static boolean validateParams(MyEffect params, IssueCollector issues, String fieldPath) {
        return ParamChecks.inRange(params.amount(), MIN_AMOUNT, MAX_AMOUNT, AMOUNT_FIELD, issues,
                ParamChecks.child(fieldPath, AMOUNT_FIELD));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
```

**三条硬约束**：

1. **codec 不得产出 null**：DFU 的 `DataResult` 内部用 `Optional.of`，任何 null 都会在解码期抛 NPE。可空字段一律用 `optionalFieldOf` + `Optional` 承载，在 `apply` 最后一步 `orElse(null)`。
2. **静态工厂不能叫 `type()`**：`Effect#type()` 是无参实例方法，同签名静态方法在 Java 中非法。约定 `effectType(expressionCodec)` / `conditionType()`。
3. **引用字段用 `TaggedId`**（支持 `#tag`），只有确实没有标签语义的才用纯 `ResourceLocation`（如 `loot_table.loot_table`、`dimension.dimensions`）。

### 3.2 执行器

```java
package com.meteorite.itemdespawntowhat.core.type.effect.exec;

/** MyEffect 的服务端执行器；只做"做什么"，异常交给运行时隔离。 */
public final class MyExecutor {

    private MyExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 执行效果：从 context 取维度/源掉落物/位置/随机源，产出世界变化
    public static void execute(MyEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        Vec3 pos = context.position();
        // ... 世界操作
    }
}
```

`EffectContext` 提供：`level()` / `source()` / `sourceStack()` / `position()` / `random()` / `ruleId()` / `schedule(delayTicks, task)`。

- 需要延迟时**不要自己起线程或计时器**，用 `context.schedule(...)`；延迟任务绑定「维度 + 位置」，不要求源实体存活。
- 随机一律用 `context.random()`（维度随机），保证联机一致。
- 不要吞异常：运行时统一捕获、记 ERROR（规则 id + 效果类型 + 位置）并继续执行后续效果。

### 3.3 注册

`core/type/BuiltinEffectTypes.java` 增加一行（顺序即注册顺序）：

```java
registry.register(MyEffect.effectType(expressionCodec));
```

注册表在 `create()` 末尾 `freeze()`，之后 `register` 抛 `RegistryFrozenException`；重复 id 抛 `DuplicateTypeException`。

### 3.4 客户端表单（可选）

`client/ui/view/EffectParams.java` 的 `SPECS` 中登记声明式规格：

```java
id("my_effect"), List.of(
        ParamSpec.integer("amount", EDIT + "effect.amount", "1")),
```

`ParamSpec` 的控件种类：`TEXT / INTEGER / DECIMAL / BOOLEAN / ENUM / LIST`；`labelKey` 是 i18n key（需同步 `en_us.json` 与 `zh_cn.json`）。表单由 `RuleFormBuilder` + `FormRenderer` 自动渲染，不需要写界面代码。

**不登记规格的后果**：GUI 仍能只读展示该类型的规则（多效果规则只读），但不提供该效果的参数编辑表单——这是有意的最小实现边界（Q35）。

## 4. 新增条件类型（完整步骤）

### 4.1 参数记录

```java
package com.meteorite.itemdespawntowhat.core.type.condition;

/**
 * 条件类型 my_condition：一句话说明判定语义。
 * JSON 示例：{ "type": "itemdespawntowhat:my_condition", "value": 3 }
 */
public record MyCondition(boolean negated, int value) implements Condition {

    // 类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "my_condition");
    // 参数 JSON 字段名
    private static final String FIELD_VALUE = "value";
    // 取值区间
    private static final int MIN_VALUE = 1;
    private static final int MAX_VALUE = 10;

    // 参数编解码器：取反字段复用通用片段；value 必填
    public static final MapCodec<MyCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CommonFields.negated(MyCondition::negated),
            Codec.intRange(MIN_VALUE, MAX_VALUE).fieldOf(FIELD_VALUE).forGetter(MyCondition::value)
    ).apply(instance, MyCondition::new));

    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 条件类型定义，供注册表登记；静态工厂不能叫 type()
    public static ConditionType<MyCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, MyCondition::validateParams, MyEvaluator::test);
    }

    // 参数语义校验
    public static boolean validateParams(MyCondition params, IssueCollector issues, String fieldPath) {
        return ParamChecks.inRange(params.value(), MIN_VALUE, MAX_VALUE, FIELD_VALUE, issues,
                ParamChecks.child(fieldPath, FIELD_VALUE));
    }
}
```

### 4.2 求值器

```java
package com.meteorite.itemdespawntowhat.core.type.condition.eval;

/** MyCondition 的服务端求值器：纯谓词，只读上下文，不得修改世界。 */
public final class MyEvaluator {

    private MyEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 返回未取反的原始判定；叶级 negated 由求值框架统一处理
    public static boolean test(MyCondition condition, ConditionContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.pos();
        // ... 只读判定
        return true;
    }
}
```

`ConditionContext` 提供：`level()` / `source()` / `pos()` / `random()` / `tags()`（`TagLookup`，带缓存）/ `climate()`（`ClimateSampler`，按位置缓存）。

- **只读**：消耗属于效果（`consume_*`），条件不得产生副作用。
- **不要直接访问注册表**：标签经 `context.tags()`、气候经 `context.climate()`，这样缓存与 reload 失效由运行时统一管理。
- 返回未取反的原始结果；`negated` 由求值框架处理。

### 4.3 注册

`core/type/BuiltinConditionTypes.java` 增加一行：

```java
registry.register(MyCondition.conditionType());
```

**注册顺序固定**：条件类型 → 条件表达式编解码器 → 效果类型（效果记录带有效果级 `conditions` 字段）。装配点 `core/service/BuiltinTypeRegistries.create()` 已固化这个顺序，不要打乱。

### 4.4 客户端参数编辑器（可选）

`client/ui/view/RuleConditionInputs.java` 的 `ORDER` 中加入类型 id，并在其登记表中给出参数编辑实现；复杂嵌套参数可直接退化为 `ConditionJsonEditor`（biome 的气候区间就是这样处理的）。

## 5. 校验与错误约定

| 约定 | 说明 |
|---|---|
| 校验不抛异常 | `validateParams` 把问题写入 `IssueCollector` 并**返回 false**；返回 false 而未写问题时会补一条兜底 ERROR |
| 字段路径 | 用 `ParamChecks.child(path, field)` / `ParamChecks.index(path, i)` 拼接，最终形如 `effects[0].count`、`conditions[0][1].value` |
| 引用存在性 | 非标签引用未命中 → ERROR；标签引用仅在标签数据已绑定时校验，未命中记 WARN（`RefChecks`） |
| 未注册类型 | 校验与运行时都会给出可读错误；**编码**未注册类型会抛 `IllegalStateException`（编程错误，GUI 保存路径必须捕获） |
| 区间取值 | 优先用 `Codec.intRange/floatRange/doubleRange` 在解码期拦截，`validateParams` 只兜底程序化构造 |

## 6. 常见坑

1. **可空字段直接放进 codec getter** → 解码期 NPE。用 `Optional` 承载。
2. **静态工厂命名成 `type()`** → 编译失败（与实例方法签名冲突）。
3. **忘记注册** → 数据包写该 `type` 时该条规则拒载（"未注册的类型/效果类型/条件类型"）。
4. **在 `freeze()` 之后注册** → `RegistryFrozenException`。
5. **枚举取值** → 用 `core/type/EnumCodecs.lowerCase(...)`，JSON 统一小写下划线且解析大小写不敏感。
6. **列表字段可变** → 记录紧凑构造器里做 `List.copyOf` 防御性拷贝（内置类型均如此）。

## 7. 第三方扩展现状（重要）

Q25 承诺"Java API 可注册新的效果类型与条件类型"，但**当前新链路还没有对外的注册 SPI**：`BuiltinEffectTypes.create()` / `BuiltinConditionTypes.create()` 构建后立即 `freeze()`，没有"冻结前插入第三方注册"的引导钩子（旧链路的 `IPlatformHelper.registerConversionType` 只服务旧模型）。

因此现状是：

- **仓库内扩展**（本模组自己加类型）：按本文步骤直接改 `BuiltinEffectTypes` / `BuiltinConditionTypes`，可用。
- **第三方模组扩展**：暂不可用，属未交付能力；数据包侧也不应写未注册的 `type`。

## 8. 相关文档

- 字段全表：[config-reference.md](config-reference.md)
- 架构与数据流：[architecture.md](architecture.md)
- 类型注册表语义：[ADR-0013：DFU Codec 与扁平类型分发](../adr/0013-dfu-codec-and-flat-type-dispatch.md)
