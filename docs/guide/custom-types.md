# 注册自定义条件与效果类型

本文说明 common 侧的类型 SPI。客户端表单描述见 [client-editor-spi.md](client-editor-spi.md)。

## 1. 注册入口：RuleTypeProvider

`com.meteorite.itemdespawntowhat.core.extension.RuleTypeProvider` 是唯一的第三方类型注册入口，两个方法都有默认空实现：

`@java
public interface RuleTypeProvider {
    // 先注册条件，随后才构建能解码第三方条件的表达式 Codec。
    default void registerConditions(TypeRegistry<ConditionType<?>> registry) {}
    // 效果注册时可以复用完整的条件表达式 Codec。
    default void registerEffects(TypeRegistry<EffectType<?>> registry, Codec<ConditionExpression> expressionCodec) {}
}
`@

**登记方式**：在自己 mod 的 jar 内放置服务文件，一行一个实现类全限定名：

`@text
META-INF/services/com.meteorite.itemdespawntowhat.core.extension.RuleTypeProvider
`@

**两阶段顺序**（重要）：先调用全部 provider 的 `registerConditions`，再构建条件表达式 `Codec`，最后调用全部 provider 的 `registerEffects`。因此：

- 条件类型拿不到表达式 Codec（它不需要）。
- 效果类型能拿到完整表达式 Codec，用于效果级 `conditions` 字段（`CommonFields.optionalConditions`）。

注册表在装配完成后会 `freeze()`，此后调用 `register` 会失败——请只在 provider 里注册，不要在运行时动态注册。

## 2. 条件类型模板

条件类型由「id + 参数 codec + 参数校验器 + 求值器 +（可选）可求值性检查」组成。

`@java
package com.example.examplemod.condition;

public record HeightCondition(Integer min, Integer max) implements Condition {

    // namespace 用你自己的 modid，不要占用 itemdespawntowhat
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("example_mod", "height");

    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";

    // 参数 codec：不要包含 type 字段，它由分发层统一读写
    public static final MapCodec<HeightCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf(FIELD_MIN).forGetter(value -> Optional.ofNullable(value.min())),
            Codec.INT.optionalFieldOf(FIELD_MAX).forGetter(value -> Optional.ofNullable(value.max()))
    ).apply(instance, (min, max) -> new HeightCondition(min.orElse(null), max.orElse(null))));

    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 静态工厂不叫 type()，因为 Condition 接口已有无参实例方法 type()
    public static ConditionType<HeightCondition> conditionType() {
        return new SimpleConditionType<>(ID, CODEC, HeightCondition::validateParams, HeightCondition::test);
    }

    public static boolean validateParams(HeightCondition params, IssueCollector issues, String fieldPath) {
        boolean valid = true;
        if (params.min() != null) {
            valid &= ParamChecks.inRange(params.min(), -2048, 2048, FIELD_MIN, issues,
                    ParamChecks.child(fieldPath, FIELD_MIN));
        }
        if (params.max() != null) {
            valid &= ParamChecks.inRange(params.max(), -2048, 2048, FIELD_MAX, issues,
                    ParamChecks.child(fieldPath, FIELD_MAX));
        }
        valid &= ParamChecks.orderedRange(params.min(), params.max(), FIELD_MIN + "/" + FIELD_MAX, issues, fieldPath);
        return valid;
    }

    public static boolean test(HeightCondition params, ConditionContext context) {
        int y = context.pos().getY();
        return (params.min() == null || y >= params.min()) && (params.max() == null || y <= params.max());
    }
}
`@

要点：

- `Condition#type()` 返回类型 id，也就是 JSON 里 `type` 字段的取值。
- 参数 `MapCodec` 只描述**类型专属字段**；通用字段（`type`）由分发层 `TypeDispatch.flat` 统一读写，自己再写一次会被判为「存在未知字段」。
- `validateParams` 用 `ParamChecks` 助手统一错误文案与字段路径；返回 false 表示参数非法，必须同时向 `issues` 写入具体问题（框架对「返回 false 但没有问题」的情况会补一条兜底错误）。
- `ConditionEvaluator#test` 只回答「当前上下文是否命中断言」；异常不要自己吞，运行时统一捕获并计为 ERROR。

## 3. 可求值性（evaluability）

默认条件下恒可判定（`AVAILABLE`）。当上下文中缺少判定所需前提时，重写 `evaluability` 返回 `UNAVAILABLE`，例如需要周围区块已加载：

`@java
public static ConditionType<SurroundingBlocksCondition> conditionType() {
    return new SimpleConditionType<>(ID, CODEC, SurroundingBlocksCondition::validateParams,
            SurroundingBlocksEvaluator::test,
            // 第 5 个组件：上下文不足时不判定（而不是判 false）
            (params, context) -> LoadedChunks.containsArea(context.level(), context.pos(), 1)
                    ? Evaluability.AVAILABLE
                    : Evaluability.UNAVAILABLE);
}
`@

`UNAVAILABLE` 会沿条件树向上传播：`inverted` 不改变它，`all_of` / `any_of` 用「UNAVAILABLE 优先于 ERROR」的规则汇总，且绝不触发效果。用它表达「暂时算不出来」而不是「不成立」。

## 4. 效果类型模板

效果必须带三个通用字段：`delay_ticks`、`chance`、`conditions`（效果级条件树）。用 `CommonFields` 复用编解码片段。

`@java
package com.example.examplemod.effect;

public record SoundEffect(ResourceLocation sound, int delayTicks, double chance,
                          @Nullable ConditionExpression conditions) implements Effect {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("example_mod", "sound");

    public static MapCodec<SoundEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("sound").forGetter(SoundEffect::sound),
                CommonFields.delayTicks(SoundEffect::delayTicks),
                CommonFields.chance(SoundEffect::chance),
                CommonFields.optionalConditions(SoundEffect::conditions, expressionCodec)
        ).apply(instance, (sound, delay, chance, conditions) ->
                new SoundEffect(sound, delay, chance, conditions.orElse(null))));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

    public static EffectType<SoundEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), SoundEffect::validateParams, SoundEffect::execute);
    }

    public static boolean validateParams(SoundEffect params, IssueCollector issues, String fieldPath) {
        return ParamChecks.required(params.sound(), "sound", issues, ParamChecks.child(fieldPath, "sound"));
    }

    public static void execute(SoundEffect effect, EffectContext context) {
        // 只负责"做什么"；延迟、概率、条件命中与异常隔离由运行时负责
    }
}
`@

注意 `optionalConditions` 的返回值是 `Optional<ConditionExpression>`，必须在 `apply` 里 `orElse(null)`——直接把 `Optional` 存进字段或让 codec 产出 null 都会在解码期抛 NPE。

## 5. 在规则 JSON 中的形态

自定义条件作为叶被条件树引用，组合节点负责逻辑：

`@json
{
  "conditions": {
    "op": "all_of",
    "terms": [
      { "op": "leaf", "condition": { "type": "example_mod:height", "min": 60 } },
      { "op": "inverted", "term": { "op": "leaf", "condition": { "type": "itemdespawntowhat:weather", "weather": "rain" } } }
    ]
  },
  "effects": [
    { "type": "example_mod:sound", "sound": "minecraft:block.note_block.bell", "delay_ticks": 20, "chance": 0.5 }
  ]
}
`@

限额按**逐表达式**计算（规则级一份、每个效果级各一份，不累加）：最多 128 个叶、256 个节点、深度 16。

## 6. 常见错误

| 现象 | 原因 |
| --- | --- |
| 解码期 NPE（`Optional.of`） | codec 或 `apply` 产出了 null；可选字段用 `Optional` + `orElse(null)` |
| 「存在未知字段: type」 | 类型自己的 `MapCodec` 里又写了一遍 `type` 字段 |
| 「重复注册」或 freeze 后注册失败 | 同一 id 注册两次，或绕开 provider 在运行时注册 |
| 加载期报未知条件类型 | provider 未生效：检查 `META-INF/services` 文件路径与全限定名 |
| 条件恒不命中 | 该类型未注册时叶求值为 UNAVAILABLE（不触发效果）；请核对命名空间与 path |
| 保存被校验拦截 | 违反限额、空 `all_of`/`any_of`、`inverted` 缺 `term`，或用了旧格式 |
