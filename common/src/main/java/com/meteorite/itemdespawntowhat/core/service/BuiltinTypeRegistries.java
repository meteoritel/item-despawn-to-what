package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.type.BuiltinConditionTypes;
import com.meteorite.itemdespawntowhat.core.type.BuiltinEffectTypes;
import com.mojang.serialization.Codec;

/**
 * 内置类型注册表的规范装配点。
 * 依赖顺序固定：条件类型 → 条件表达式编解码器 → 效果类型（效果记录带有效果级 conditions 字段）。
 * 运行时引导层（阶段③）直接使用本记录，避免各处重复拼装。
 */
public record BuiltinTypeRegistries(
        TypeRegistry<ConditionType<?>> conditionTypes,
        TypeRegistry<EffectType<?>> effectTypes,
        Codec<ConditionExpression> conditionExpressionCodec
) {

    // 构建并冻结全部内置类型注册表
    public static BuiltinTypeRegistries create() {
        TypeRegistry<ConditionType<?>> conditionTypes = BuiltinConditionTypes.create();
        Codec<ConditionExpression> expressionCodec = RuleCodecs.conditionExpressionCodec(conditionTypes);
        TypeRegistry<EffectType<?>> effectTypes = BuiltinEffectTypes.create(expressionCodec);
        return new BuiltinTypeRegistries(conditionTypes, effectTypes, expressionCodec);
    }
}
