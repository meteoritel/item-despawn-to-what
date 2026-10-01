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
 * spawn_entity：在触发位置生成指定实体。
 * entity 为 TaggedId（支持 #tag）；age 为负表示幼体（原版 Babyable 语义）。本类只承载参数模型与参数校验（阶段②）。
 */
public record SpawnEntityEffect(
        TaggedId entity,
        int count,
        int age,
        @Nullable Integer limit,
        @Nullable Integer radius,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "spawn_entity");

    // 类型专属参数字段名
    public static final String ENTITY_FIELD = "entity";
    public static final String COUNT_FIELD = "count";
    public static final String AGE_FIELD = "age";
    public static final String LIMIT_FIELD = "limit";
    public static final String RADIUS_FIELD = "radius";

    // 参数默认值与取值区间
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;
    public static final int DEFAULT_AGE = 0;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 4096;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 32;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<SpawnEntityEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.fieldOf(ENTITY_FIELD).forGetter(SpawnEntityEffect::entity),
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(SpawnEntityEffect::count),
                Codec.INT.optionalFieldOf(AGE_FIELD, DEFAULT_AGE).forGetter(SpawnEntityEffect::age),
                Codec.intRange(MIN_LIMIT, MAX_LIMIT).optionalFieldOf(LIMIT_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.limit())),
                Codec.intRange(MIN_RADIUS, MAX_RADIUS).optionalFieldOf(RADIUS_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.radius())),
                CommonFields.delayTicks(SpawnEntityEffect::delayTicks),
                CommonFields.chance(SpawnEntityEffect::chance),
                CommonFields.optionalConditions(SpawnEntityEffect::conditions, expressionCodec)
        ).apply(instance, (entity, count, age, limit, radius, delayTicks, chance, conditions) ->
                new SpawnEntityEffect(entity, count, age, limit.orElse(null), radius.orElse(null),
                        delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器
    public static EffectType<SpawnEntityEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), SpawnEntityEffect::validateParams);
    }

    // 参数语义校验；age 不做区间限制（负数即幼体，其余取值由实体自身语义决定）
    public static boolean validateParams(SpawnEntityEffect params, IssueCollector issues, String fieldPath) {
        String entityPath = ParamChecks.child(fieldPath, ENTITY_FIELD);
        boolean valid = ParamChecks.required(params.entity(), ENTITY_FIELD, issues, entityPath);
        valid &= RefChecks.check(params.entity(), BuiltInRegistries.ENTITY_TYPE, ENTITY_FIELD, issues, entityPath);
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
