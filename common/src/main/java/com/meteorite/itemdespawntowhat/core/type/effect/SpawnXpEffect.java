package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.meteorite.itemdespawntowhat.core.type.effect.exec.SpawnXpExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * spawn_xp：在触发位置生成经验球。
 * per_source_item 为真时，实际经验量按源物品堆叠数量倍增。本类只承载参数模型与参数校验（阶段②）。
 */
public record SpawnXpEffect(
        int amount,
        boolean perSourceItem,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "spawn_xp");

    // 类型专属参数字段名
    public static final String AMOUNT_FIELD = "amount";
    public static final String PER_SOURCE_ITEM_FIELD = "per_source_item";

    // 参数默认值与取值区间
    public static final int DEFAULT_AMOUNT = 1;
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 65536;
    public static final boolean DEFAULT_PER_SOURCE_ITEM = false;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<SpawnXpEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(MIN_AMOUNT, MAX_AMOUNT).optionalFieldOf(AMOUNT_FIELD, DEFAULT_AMOUNT)
                        .forGetter(SpawnXpEffect::amount),
                Codec.BOOL.optionalFieldOf(PER_SOURCE_ITEM_FIELD, DEFAULT_PER_SOURCE_ITEM)
                        .forGetter(SpawnXpEffect::perSourceItem),
                CommonFields.delayTicks(SpawnXpEffect::delayTicks),
                CommonFields.chance(SpawnXpEffect::chance),
                CommonFields.optionalConditions(SpawnXpEffect::conditions, expressionCodec)
        ).apply(instance, (amount, perSourceItem, delayTicks, chance, conditions) ->
                new SpawnXpEffect(amount, perSourceItem, delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器
    public static EffectType<SpawnXpEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), SpawnXpEffect::validateParams, SpawnXpExecutor::execute);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(SpawnXpEffect params, IssueCollector issues, String fieldPath) {
        return ParamChecks.inRange(params.amount(), MIN_AMOUNT, MAX_AMOUNT, AMOUNT_FIELD, issues,
                ParamChecks.child(fieldPath, AMOUNT_FIELD));
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
