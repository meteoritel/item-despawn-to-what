package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.ConsumeSourceExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * consume_source：消耗来源掉落物的物品堆叠。
 * 规则未显式声明任何 consume_* 效果时按隐式消耗处理（见 core/model/Rule#usesImplicitSourceConsumption）。本类只承载参数模型与参数校验（阶段②）。
 */
public record ConsumeSourceEffect(
        int count,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "consume_source");

    // 类型专属参数字段名
    public static final String COUNT_FIELD = "count";

    // 参数默认值与取值区间
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<ConsumeSourceEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(ConsumeSourceEffect::count),
                CommonFields.delayTicks(ConsumeSourceEffect::delayTicks),
                CommonFields.chance(ConsumeSourceEffect::chance),
                CommonFields.optionalConditions(ConsumeSourceEffect::conditions, expressionCodec)
        ).apply(instance, (count, delayTicks, chance, conditions) ->
                new ConsumeSourceEffect(count, delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器
    public static EffectType<ConsumeSourceEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), ConsumeSourceEffect::validateParams, ConsumeSourceExecutor::execute);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(ConsumeSourceEffect params, IssueCollector issues, String fieldPath) {
        return ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }

    // 效果级条件替换：其余参数原样保留，供运行期门槛投影重建真实效果类型
    @Override
    public ConsumeSourceEffect withConditions(@Nullable ConditionExpression conditions) {
        return new ConsumeSourceEffect(count, delayTicks, chance, conditions);
    }
}
