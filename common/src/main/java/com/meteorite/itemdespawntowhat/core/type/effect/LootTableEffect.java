package com.meteorite.itemdespawntowhat.core.type.effect;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.model.CommonFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.SimpleEffectType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * loot_table：在触发位置按战利品表产出掉落物。
 * loot_table 为纯 ResourceLocation（无 tag 语义，故不使用 TaggedId）。本类只承载参数模型与参数校验（阶段②）。
 */
public record LootTableEffect(
        ResourceLocation lootTable,
        float luck,
        int delayTicks,
        double chance,
        @Nullable ConditionExpression conditions
) implements Effect {

    // 效果类型 id，同时是 JSON 中 type 字段的取值
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "loot_table");

    // 类型专属参数字段名
    public static final String LOOT_TABLE_FIELD = "loot_table";
    public static final String LUCK_FIELD = "luck";

    // 参数默认值与取值区间
    public static final float DEFAULT_LUCK = 0.0F;
    public static final float MIN_LUCK = -100.0F;
    public static final float MAX_LUCK = 100.0F;

    // 类型专属参数编解码器；通用字段复用 CommonFields 片段（codec 层不得产出 null）
    public static MapCodec<LootTableEffect> codec(Codec<ConditionExpression> expressionCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf(LOOT_TABLE_FIELD).forGetter(LootTableEffect::lootTable),
                Codec.floatRange(MIN_LUCK, MAX_LUCK).optionalFieldOf(LUCK_FIELD, DEFAULT_LUCK)
                        .forGetter(LootTableEffect::luck),
                CommonFields.delayTicks(LootTableEffect::delayTicks),
                CommonFields.chance(LootTableEffect::chance),
                CommonFields.optionalConditions(LootTableEffect::conditions, expressionCodec)
        ).apply(instance, (lootTable, luck, delayTicks, chance, conditions) ->
                new LootTableEffect(lootTable, luck, delayTicks, chance, conditions.orElse(null))));
    }

    // 效果类型定义：id + 参数编解码器 + 参数校验器
    public static EffectType<LootTableEffect> effectType(Codec<ConditionExpression> expressionCodec) {
        return new SimpleEffectType<>(ID, codec(expressionCodec), LootTableEffect::validateParams);
    }

    // 参数语义校验；战利品表是否存在由数据包机制保证，此处只校验必填与区间
    public static boolean validateParams(LootTableEffect params, IssueCollector issues, String fieldPath) {
        boolean valid = ParamChecks.required(params.lootTable(), LOOT_TABLE_FIELD, issues,
                ParamChecks.child(fieldPath, LOOT_TABLE_FIELD));
        valid &= ParamChecks.inRange(params.luck(), MIN_LUCK, MAX_LUCK, LUCK_FIELD, issues,
                ParamChecks.child(fieldPath, LUCK_FIELD));
        return valid;
    }

    @Override
    public ResourceLocation type() {
        return ID;
    }
}
