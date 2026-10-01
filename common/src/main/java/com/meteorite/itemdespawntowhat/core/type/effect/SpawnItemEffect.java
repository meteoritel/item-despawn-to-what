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
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * spawn_item：在触发位置生成指定物品。
 * item 为 TaggedId，既可写具体物品 id，也可写 #tag 由运行时展开；本类只承载参数模型与参数校验（阶段②）。
 */
public record SpawnItemEffect(
        TaggedId item,
        int count,
        @Nullable Integer limit,
        @Nullable Integer radius,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "spawn_item");

    // 类型专属参数字段名
    public static final String ITEM_FIELD = "item";
    public static final String COUNT_FIELD = "count";
    public static final String LIMIT_FIELD = "limit";
    public static final String RADIUS_FIELD = "radius";

    // 参数默认值与取值区间
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 4096;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 32;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<SpawnItemEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.fieldOf(ITEM_FIELD).forGetter(SpawnItemEffect::item),
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(SpawnItemEffect::count),
                Codec.intRange(MIN_LIMIT, MAX_LIMIT).optionalFieldOf(LIMIT_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.limit())),
                Codec.intRange(MIN_RADIUS, MAX_RADIUS).optionalFieldOf(RADIUS_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.radius())),
                CommonFields.delayTicks(SpawnItemEffect::delayTicks),
                CommonFields.chance(SpawnItemEffect::chance),
                CommonFields.optionalConditions(SpawnItemEffect::conditions, expressionCodec)
        ).apply(instance, (item, count, limit, radius, delayTicks, chance, conditions) ->
                new SpawnItemEffect(item, count, limit.orElse(null), radius.orElse(null),
                        delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器
    public static EffectType<SpawnItemEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), SpawnItemEffect::validateParams);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(SpawnItemEffect params, IssueCollector issues, String fieldPath) {
        String itemPath = ParamChecks.child(fieldPath, ITEM_FIELD);
        boolean valid = ParamChecks.required(params.item(), ITEM_FIELD, issues, itemPath);
        valid &= RefChecks.check(params.item(), BuiltInRegistries.ITEM, ITEM_FIELD, issues, itemPath);
        valid &= ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
        if (params.limit() != null) {
            valid &= ParamChecks.inRange(params.limit(), MIN_LIMIT, MAX_LIMIT, LIMIT_FIELD, issues,
                    ParamChecks.child(fieldPath, LIMIT_FIELD));
        }
        if (params.radius() != null) {
            valid &= ParamChecks.inRange(params.radius(), MIN_RADIUS, MAX_RADIUS, RADIUS_FIELD, issues,
                    ParamChecks.child(fieldPath, RADIUS_FIELD));
        }
        return valid;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
