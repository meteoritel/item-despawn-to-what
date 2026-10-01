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
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ConsumeFluidExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * consume_fluid：消耗触发位置处的流体。
 * fluid 可空表示"任意流体"；非空时为流体 id 或 #tag。require_source 为真时要求同位置存在源掉落物。
 * 本类只承载参数模型与参数校验（阶段②）。
 */
public record ConsumeFluidEffect(
        @Nullable TaggedId fluid,
        boolean requireSource,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "consume_fluid");

    // 类型专属参数字段名
    public static final String FLUID_FIELD = "fluid";
    public static final String REQUIRE_SOURCE_FIELD = "require_source";

    // 参数默认值：true 表示必须在源掉落物所在位置消耗流体
    public static final boolean DEFAULT_REQUIRE_SOURCE = true;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<ConsumeFluidEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.optionalFieldOf(FLUID_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.fluid())),
                Codec.BOOL.optionalFieldOf(REQUIRE_SOURCE_FIELD, DEFAULT_REQUIRE_SOURCE)
                        .forGetter(ConsumeFluidEffect::requireSource),
                CommonFields.delayTicks(ConsumeFluidEffect::delayTicks),
                CommonFields.chance(ConsumeFluidEffect::chance),
                CommonFields.optionalConditions(ConsumeFluidEffect::conditions, expressionCodec)
        ).apply(instance, (fluid, requireSource, delayTicks, chance, conditions) ->
                new ConsumeFluidEffect(fluid.orElse(null), requireSource, delayTicks, chance,
                        conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器
    public static EffectType<ConsumeFluidEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), ConsumeFluidEffect::validateParams, ConsumeFluidExecutor::execute);
    }

    // 参数语义校验；fluid 为 null 表示任意流体，不做存在性判定
    public static boolean validateParams(ConsumeFluidEffect params, IssueCollector issues, String fieldPath) {
        return RefChecks.check(params.fluid(), BuiltInRegistries.FLUID, FLUID_FIELD, issues,
                ParamChecks.child(fieldPath, FLUID_FIELD));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
