package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.EnumCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.WeatherExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * weather：切换触发维度（或其所在区域）的天气。
 * mode 必填（rain / clear）；duration_ticks 为持续刻数（上限对齐原版一天的 24000 刻）。本类只承载参数模型与参数校验（阶段②）。
 */
public record WeatherEffect(
        Mode mode,
        int durationTicks,
        boolean thundering,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "weather");

    // 类型专属参数字段名
    public static final String MODE_FIELD = "mode";
    public static final String DURATION_TICKS_FIELD = "duration_ticks";
    public static final String THUNDERING_FIELD = "thundering";

    // 参数默认值与取值区间
    public static final int DEFAULT_DURATION_TICKS = 6000;
    public static final int MIN_DURATION_TICKS = 1;
    public static final int MAX_DURATION_TICKS = 24000;
    public static final boolean DEFAULT_THUNDERING = false;

    // 天气模式
    public enum Mode {
        RAIN,
        CLEAR;

        // JSON 取值：小写下划线，解析大小写不敏感
        public static final Codec<Mode> CODEC = EnumCodecs.lowerCase(Mode.class);
    }

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<WeatherEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Mode.CODEC.fieldOf(MODE_FIELD).forGetter(WeatherEffect::mode),
                Codec.intRange(MIN_DURATION_TICKS, MAX_DURATION_TICKS)
                        .optionalFieldOf(DURATION_TICKS_FIELD, DEFAULT_DURATION_TICKS)
                        .forGetter(WeatherEffect::durationTicks),
                Codec.BOOL.optionalFieldOf(THUNDERING_FIELD, DEFAULT_THUNDERING)
                        .forGetter(WeatherEffect::thundering),
                CommonFields.delayTicks(WeatherEffect::delayTicks),
                CommonFields.chance(WeatherEffect::chance),
                CommonFields.optionalConditions(WeatherEffect::conditions, expressionCodec)
        ).apply(instance, (mode, durationTicks, thundering, delayTicks, chance, conditions) ->
                new WeatherEffect(mode, durationTicks, thundering, delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器
    public static EffectType<WeatherEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), WeatherEffect::validateParams, WeatherExecutor::execute);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(WeatherEffect params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.required(params.mode(), MODE_FIELD, issues,
                ParamChecks.child(fieldPath, MODE_FIELD));
        valid &= ParamChecks.inRange(params.durationTicks(), MIN_DURATION_TICKS, MAX_DURATION_TICKS,
                DURATION_TICKS_FIELD, issues, ParamChecks.child(fieldPath, DURATION_TICKS_FIELD));
        return valid;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
