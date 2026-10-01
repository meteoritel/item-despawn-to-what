package com.meteorite.itemdespawntowhat.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecoder;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeDispatch;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;

/**
 * 规则及其条件表达式的编解码入口。
 * 效果与条件叶均通过注册表做扁平类型分发：类型专属字段与通用字段位于同一个 JSON 对象中。
 * 硬约束：codec 层不得产出 null（DFU 的 DataResult 内部使用 Optional.of，null 会在解码期抛 NPE）；
 * 可选字段一律以 Optional 承载，在记录构造的最后一步 orElse(null) 落回可空字段。
 */
public final class RuleCodecs {

    // 触发时间默认值：对齐原版掉落物 300 秒的消失直觉
    public static final int DEFAULT_TRIGGER_AFTER_SECONDS = 300;

    // 规则文件允许出现的顶层字段（含覆盖层控制字段）
    public static final Set<String> KNOWN_RULE_FIELDS = Set.of(
            RuleFields.ID,
            RuleFields.ENABLED,
            RuleFields.PRIORITY,
            RuleFields.NOTES,
            RuleFields.SOURCE,
            RuleFields.CONDITIONS,
            RuleFields.TRIGGER_AFTER_SECONDS,
            RuleFields.EFFECTS,
            RuleFields.DISABLED,
            RuleFields.DELETE
    );

    private RuleCodecs() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 条件表达式编解码器（二维数组：外层 OR、内层 AND；空数组表示恒真）
    public static Codec<ConditionExpression> conditionExpressionCodec(TypeRegistry<ConditionType<?>> conditionTypes) {
        Codec<Condition> leafCodec = TypeDispatch.flat(conditionTypes, Condition::type).codec();
        Codec<ConditionGroup> groupCodec = leafCodec.listOf()
                .xmap(ConditionGroup::new, ConditionGroup::conditions);
        return groupCodec.listOf().xmap(ConditionExpression::new, ConditionExpression::groups);
    }

    // 效果列表编解码器
    public static Codec<Effect> effectCodec(TypeRegistry<EffectType<?>> effectTypes) {
        return TypeDispatch.flat(effectTypes, Effect::type).codec();
    }

    // 规则编解码器
    public static Codec<Rule> codec(TypeRegistry<EffectType<?>> effectTypes,
                                    TypeRegistry<ConditionType<?>> conditionTypes) {
        Codec<ConditionExpression> expressionCodec = conditionExpressionCodec(conditionTypes);
        Codec<Effect> effectCodec = effectCodec(effectTypes);
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf(RuleFields.ID).forGetter(Rule::id),
                Codec.BOOL.optionalFieldOf(RuleFields.ENABLED, Boolean.TRUE).forGetter(Rule::enabled),
                Codec.INT.optionalFieldOf(RuleFields.PRIORITY, 0).forGetter(Rule::priority),
                Codec.STRING.optionalFieldOf(RuleFields.NOTES)
                        .forGetter(rule -> Optional.ofNullable(rule.notes())),
                SourceMatcher.CODEC.fieldOf(RuleFields.SOURCE).forGetter(Rule::source),
                expressionCodec.optionalFieldOf(RuleFields.CONDITIONS, ConditionExpression.EMPTY)
                        .forGetter(Rule::conditions),
                Codec.INT.optionalFieldOf(RuleFields.TRIGGER_AFTER_SECONDS, DEFAULT_TRIGGER_AFTER_SECONDS)
                        .forGetter(Rule::triggerAfterSeconds),
                effectCodec.listOf().fieldOf(RuleFields.EFFECTS).forGetter(Rule::effects)
        ).apply(instance, (id, enabled, priority, notes, source, conditions, triggerAfterSeconds, effects) ->
                new Rule(id, enabled, priority, notes.orElse(null), source, conditions, triggerAfterSeconds, effects)));
    }

    // 构造加载层可用的解码器；未知顶层字段不阻断加载，但会作为 WARN 进入 IssueCollector
    public static RuleDecoder<Rule> decoder(RegistryAccess registryAccess,
                                            TypeRegistry<EffectType<?>> effectTypes,
                                            TypeRegistry<ConditionType<?>> conditionTypes) {
        Codec<Rule> ruleCodec = codec(effectTypes, conditionTypes);
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        return (body, issues) -> {
            warnUnknownFields(body, issues);
            return ruleCodec.decode(ops, body).map(Pair::getFirst);
        };
    }

    // 顶层未知字段告警
    private static void warnUnknownFields(JsonObject body, IssueCollector issues) {
        for (String key : body.keySet()) {
            if (!KNOWN_RULE_FIELDS.contains(key)) {
                issues.warn("未知字段: " + key, null, key);
            }
        }
    }
}
