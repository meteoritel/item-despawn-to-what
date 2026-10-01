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
import com.meteorite.itemdespawntowhat.core.type.effect.exec.PlaceBlockExecutor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * place_block：在触发位置按指定形状放置方块。
 * block（TaggedId，可空）与 use_source_block 至少需要其一：前者放置指定方块，后者放置源物品对应的方块。
 * 本类只承载参数模型与参数校验（阶段②）。
 */
public record PlaceBlockEffect(
        @Nullable TaggedId block,
        boolean useSourceBlock,
        Shape shape,
        int count,
        int radius,
        @Nullable Integer limit,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "place_block");

    // 类型专属参数字段名
    public static final String BLOCK_FIELD = "block";
    public static final String USE_SOURCE_BLOCK_FIELD = "use_source_block";
    public static final String SHAPE_FIELD = "shape";
    public static final String COUNT_FIELD = "count";
    public static final String RADIUS_FIELD = "radius";
    public static final String LIMIT_FIELD = "limit";

    // 参数默认值与取值区间
    public static final boolean DEFAULT_USE_SOURCE_BLOCK = false;
    public static final Shape DEFAULT_SHAPE = Shape.SQUARE;
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;
    public static final int DEFAULT_RADIUS = 6;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 32;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 4096;

    // 放置形状
    public enum Shape {
        SQUARE,
        CIRCLE,
        CROSS;

        // JSON 取值：小写下划线，解析大小写不敏感
        public static final Codec<Shape> CODEC = EnumCodecs.lowerCase(Shape.class);
    }

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<PlaceBlockEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.optionalFieldOf(BLOCK_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.block())),
                Codec.BOOL.optionalFieldOf(USE_SOURCE_BLOCK_FIELD, DEFAULT_USE_SOURCE_BLOCK)
                        .forGetter(PlaceBlockEffect::useSourceBlock),
                Shape.CODEC.optionalFieldOf(SHAPE_FIELD, DEFAULT_SHAPE).forGetter(PlaceBlockEffect::shape),
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(PlaceBlockEffect::count),
                Codec.intRange(MIN_RADIUS, MAX_RADIUS).optionalFieldOf(RADIUS_FIELD, DEFAULT_RADIUS)
                        .forGetter(PlaceBlockEffect::radius),
                Codec.intRange(MIN_LIMIT, MAX_LIMIT).optionalFieldOf(LIMIT_FIELD)
                        .forGetter(effect -> Optional.ofNullable(effect.limit())),
                CommonFields.delayTicks(PlaceBlockEffect::delayTicks),
                CommonFields.chance(PlaceBlockEffect::chance),
                CommonFields.optionalConditions(PlaceBlockEffect::conditions, expressionCodec)
        ).apply(instance, (block, useSourceBlock, shape, count, radius, limit, delayTicks, chance, conditions) ->
                new PlaceBlockEffect(block.orElse(null), useSourceBlock, shape, count, radius,
                        limit.orElse(null), delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器 + 服务端执行器
    public static EffectType<PlaceBlockEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), PlaceBlockEffect::validateParams, PlaceBlockExecutor::execute);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(PlaceBlockEffect params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.required(params.shape(), SHAPE_FIELD, issues,
                ParamChecks.child(fieldPath, SHAPE_FIELD));
        if (params.block() == null && !params.useSourceBlock()) {
            issues.error("block 与 use_source_block 至少需要其一，否则没有可放置的方块", null,
                    ParamChecks.child(fieldPath, BLOCK_FIELD));
            valid = false;
        }
        valid &= RefChecks.check(params.block(), BuiltInRegistries.BLOCK, BLOCK_FIELD, issues,
                ParamChecks.child(fieldPath, BLOCK_FIELD));
        valid &= ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
        valid &= ParamChecks.inRange(params.radius(), MIN_RADIUS, MAX_RADIUS, RADIUS_FIELD, issues,
                ParamChecks.child(fieldPath, RADIUS_FIELD));
        if (params.limit() != null) {
            valid &= ParamChecks.inRange(params.limit(), MIN_LIMIT, MAX_LIMIT, LIMIT_FIELD, issues,
                    ParamChecks.child(fieldPath, LIMIT_FIELD));
        }
        return valid;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
