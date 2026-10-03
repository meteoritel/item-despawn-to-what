package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.RefChecks;
import com.meteorite.itemdespawntowhat.core.type.EnumCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ArrowRainExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * arrow_rain：在触发位置上方生成一阵箭雨。
 * potion_effects 可空，元素为箭矢携带的药水效果（效果 id + 持续刻数 + 等级）。本类只承载参数模型与参数校验（阶段②）。
 */
public record ArrowRainEffect(
        int count,
        Pickup pickup,
        @Nullable List<PotionEffectSpec> potionEffects,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "arrow_rain");

    // 类型专属参数字段名
    public static final String COUNT_FIELD = "count";
    public static final String PICKUP_FIELD = "pickup";
    public static final String POTION_EFFECTS_FIELD = "potion_effects";

    // 参数默认值与取值区间
    public static final int DEFAULT_COUNT = 16;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 256;
    public static final Pickup DEFAULT_PICKUP = Pickup.DISALLOWED;

    // 紧凑构造器：列表字段做防御性拷贝，保持记录不可变
    public ArrowRainEffect {
        potionEffects = potionEffects == null ? null : List.copyOf(potionEffects);
    }

    // 箭矢拾取模式
    public enum Pickup {
        DISALLOWED,
        ALLOWED,
        CREATIVE_ONLY;

        // JSON 取值：小写下划线，解析大小写不敏感
        public static final Codec<Pickup> CODEC = EnumCodecs.lowerCase(Pickup.class);
    }

    /**
     * 箭矢携带的一项药水效果。
     * effect 为 TaggedId（支持 #tag 形式的药水效果标签）。
     */
    public record PotionEffectSpec(
            TaggedId effect,
            int durationTicks,
            int amplifier
    ) {

        // 参数字段名
        public static final String EFFECT_FIELD = "effect";
        public static final String DURATION_TICKS_FIELD = "duration_ticks";
        public static final String AMPLIFIER_FIELD = "amplifier";

        // 参数默认值与取值区间
        public static final int DEFAULT_DURATION_TICKS = 100;
        public static final int MIN_DURATION_TICKS = 1;
        public static final int MAX_DURATION_TICKS = 1000000;
        public static final int DEFAULT_AMPLIFIER = 0;
        public static final int MIN_AMPLIFIER = 0;
        public static final int MAX_AMPLIFIER = 255;

        // 药水效果编解码器
        public static final MapCodec<PotionEffectSpec> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.fieldOf(EFFECT_FIELD).forGetter(PotionEffectSpec::effect),
                Codec.intRange(MIN_DURATION_TICKS, MAX_DURATION_TICKS)
                        .optionalFieldOf(DURATION_TICKS_FIELD, DEFAULT_DURATION_TICKS)
                        .forGetter(PotionEffectSpec::durationTicks),
                Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER)
                        .optionalFieldOf(AMPLIFIER_FIELD, DEFAULT_AMPLIFIER)
                        .forGetter(PotionEffectSpec::amplifier)
        ).apply(instance, PotionEffectSpec::new));

        // 单项药水效果的语义校验；问题写入 issues，不抛异常
        public static boolean validateParams(PotionEffectSpec params, IssueCollector issues, String fieldPath) {
            String effectPath = ParamChecks.child(fieldPath, EFFECT_FIELD);
            boolean valid = ParamChecks.required(params.effect(), EFFECT_FIELD, issues, effectPath);
            valid &= RefChecks.check(params.effect(), BuiltInRegistries.MOB_EFFECT, EFFECT_FIELD,
                    issues, effectPath);
            valid &= ParamChecks.inRange(params.durationTicks(), MIN_DURATION_TICKS, MAX_DURATION_TICKS,
                    DURATION_TICKS_FIELD, issues, ParamChecks.child(fieldPath, DURATION_TICKS_FIELD));
            valid &= ParamChecks.inRange(params.amplifier(), MIN_AMPLIFIER, MAX_AMPLIFIER, AMPLIFIER_FIELD,
                    issues, ParamChecks.child(fieldPath, AMPLIFIER_FIELD));
            return valid;
        }
    }

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<ArrowRainEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(ArrowRainEffect::count),
                Pickup.CODEC.optionalFieldOf(PICKUP_FIELD, DEFAULT_PICKUP).forGetter(ArrowRainEffect::pickup),
                PotionEffectSpec.CODEC.codec().listOf().optionalFieldOf(POTION_EFFECTS_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.potionEffects())),
                CommonFields.delayTicks(ArrowRainEffect::delayTicks),
                CommonFields.chance(ArrowRainEffect::chance),
                CommonFields.optionalConditions(ArrowRainEffect::conditions, expressionCodec)
        ).apply(instance, (count, pickup, potionEffects, delayTicks, chance, conditions) ->
                new ArrowRainEffect(count, pickup, potionEffects.orElse(null), delayTicks, chance,
                        conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器
    public static EffectType<ArrowRainEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), ArrowRainEffect::validateParams, ArrowRainExecutor::execute, true);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(ArrowRainEffect params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
        valid &= ParamChecks.required(params.pickup(), PICKUP_FIELD, issues,
                ParamChecks.child(fieldPath, PICKUP_FIELD));
        if (params.potionEffects() != null) {
            String effectsPath = ParamChecks.child(fieldPath, POTION_EFFECTS_FIELD);
            for (int index = 0; index < params.potionEffects().size(); index++) {
                PotionEffectSpec spec = params.potionEffects().get(index);
                String specPath = ParamChecks.index(effectsPath, index);
                valid &= ParamChecks.required(spec, POTION_EFFECTS_FIELD, issues, specPath);
                if (spec != null) {
                    valid &= PotionEffectSpec.validateParams(spec, issues, specPath);
                }
            }
        }
        return valid;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
