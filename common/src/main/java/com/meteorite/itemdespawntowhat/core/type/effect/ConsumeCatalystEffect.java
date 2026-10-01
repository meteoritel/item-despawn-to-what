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

import java.util.List;

/**
 * consume_catalyst：消耗触发位置附近作为触媒的物品（如祭坛上的献祭物）。
 * items 为物品 id 或 #tag 的混合列表，必填且不得为空；本类只承载参数模型与参数校验（阶段②）。
 */
public record ConsumeCatalystEffect(
        List<TaggedId> items,
        int count,
        int radius,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "consume_catalyst");

    // 类型专属参数字段名
    public static final String ITEMS_FIELD = "items";
    public static final String COUNT_FIELD = "count";
    public static final String RADIUS_FIELD = "radius";

    // 参数默认值与取值区间
    public static final int DEFAULT_COUNT = 1;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 64;
    public static final int DEFAULT_RADIUS = 1;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 8;

    // 紧凑构造器：列表字段做防御性拷贝，保持记录不可变
    public ConsumeCatalystEffect {
        items = items == null ? null : List.copyOf(items);
    }

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<ConsumeCatalystEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                TaggedId.CODEC.listOf().fieldOf(ITEMS_FIELD).forGetter(ConsumeCatalystEffect::items),
                Codec.intRange(MIN_COUNT, MAX_COUNT).optionalFieldOf(COUNT_FIELD, DEFAULT_COUNT)
                        .forGetter(ConsumeCatalystEffect::count),
                Codec.intRange(MIN_RADIUS, MAX_RADIUS).optionalFieldOf(RADIUS_FIELD, DEFAULT_RADIUS)
                        .forGetter(ConsumeCatalystEffect::radius),
                CommonFields.delayTicks(ConsumeCatalystEffect::delayTicks),
                CommonFields.chance(ConsumeCatalystEffect::chance),
                CommonFields.optionalConditions(ConsumeCatalystEffect::conditions, expressionCodec)
        ).apply(instance, (items, count, radius, delayTicks, chance, conditions) ->
                new ConsumeCatalystEffect(items, count, radius, delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器
    public static EffectType<ConsumeCatalystEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), ConsumeCatalystEffect::validateParams);
    }

    // 参数语义校验；问题写入 issues，不抛异常
    public static boolean validateParams(ConsumeCatalystEffect params, IssueCollector issues, String fieldPath) {
        String itemsPath = ParamChecks.child(fieldPath, ITEMS_FIELD);
        boolean valid = ParamChecks.notEmpty(params.items(), ITEMS_FIELD, issues, itemsPath);
        if (params.items() != null) {
            for (int index = 0; index < params.items().size(); index++) {
                TaggedId entry = params.items().get(index);
                String entryPath = ParamChecks.index(itemsPath, index);
                valid &= ParamChecks.required(entry, ITEMS_FIELD, issues, entryPath);
                valid &= RefChecks.check(entry, BuiltInRegistries.ITEM, ITEMS_FIELD, issues, entryPath);
            }
        }
        valid &= ParamChecks.inRange(params.count(), MIN_COUNT, MAX_COUNT, COUNT_FIELD, issues,
                ParamChecks.child(fieldPath, COUNT_FIELD));
        valid &= ParamChecks.inRange(params.radius(), MIN_RADIUS, MAX_RADIUS, RADIUS_FIELD, issues,
                ParamChecks.child(fieldPath, RADIUS_FIELD));
        return valid;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
