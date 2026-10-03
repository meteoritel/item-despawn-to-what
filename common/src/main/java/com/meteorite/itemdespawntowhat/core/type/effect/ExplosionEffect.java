package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ExplosionExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * explosion：在触发位置产生爆炸。
 * visual_only 为真时只播放爆炸视觉与音效，不破坏方块、不伤害实体。本类只承载参数模型与参数校验（阶段②）。
 */
public record ExplosionEffect(
        float power,
        boolean fire,
        boolean visualOnly,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "explosion");

    // 类型专属参数字段名
    public static final String POWER_FIELD = "power";
    public static final String FIRE_FIELD = "fire";
    public static final String VISUAL_ONLY_FIELD = "visual_only";

    // 参数默认值与取值区间
    public static final float DEFAULT_POWER = 3.0F;
    public static final float MIN_POWER = 0.0F;
    public static final float MAX_POWER = 16.0F;
    public static final boolean DEFAULT_FIRE = false;
    public static final boolean DEFAULT_VISUAL_ONLY = false;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<ExplosionEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.floatRange(MIN_POWER, MAX_POWER).optionalFieldOf(POWER_FIELD, DEFAULT_POWER)
                        .forGetter(ExplosionEffect::power),
                Codec.BOOL.optionalFieldOf(FIRE_FIELD, DEFAULT_FIRE).forGetter(ExplosionEffect::fire),
                Codec.BOOL.optionalFieldOf(VISUAL_ONLY_FIELD, DEFAULT_VISUAL_ONLY)
                        .forGetter(ExplosionEffect::visualOnly),
                CommonFields.delayTicks(ExplosionEffect::delayTicks),
                CommonFields.chance(ExplosionEffect::chance),
                CommonFields.optionalConditions(ExplosionEffect::conditions, expressionCodec)
        ).apply(instance, (power, fire, visualOnly, delayTicks, chance, conditions) ->
                new ExplosionEffect(power, fire, visualOnly, delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器
    public static EffectType<ExplosionEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), ExplosionEffect::validateParams, ExplosionExecutor::execute, true);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(ExplosionEffect params, IssueCollector issues, String fieldPath) {
        return ParamChecks.inRange(params.power(), MIN_POWER, MAX_POWER, POWER_FIELD, issues,
                ParamChecks.child(fieldPath, POWER_FIELD));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
