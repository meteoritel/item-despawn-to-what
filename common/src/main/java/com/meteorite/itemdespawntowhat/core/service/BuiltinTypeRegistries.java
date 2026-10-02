package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeSourceEffect;
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

    /**
     * 该规则每轮消耗的源物品数量：
     * - 未声明任何 consume_* 效果 → 隐式消耗 1（Q8）；
     * - 显式声明 consume_source → 取其 count（多条时累加，加载期已禁止同类型重复）；
     * - 只声明了其它消耗效果（如 consume_fluid）→ 0，表示不按堆叠轮次展开。
     */
    public int perRoundSourceConsumption(Rule rule) {
        if (rule.usesImplicitSourceConsumption()) {
            return 1;
        }
        int declared = 0;
        for (Effect effect : rule.effects()) {
            if (effect instanceof ConsumeSourceEffect consume) {
                declared += Math.max(0, consume.count());
            }
        }
        return declared;
    }

    // 规则的默认隐式消耗效果（Q8：规则未声明任何 consume_* 时隐式消耗 1 个源物品）
    public Effect implicitSourceConsumption() {
        return new ConsumeSourceEffect(1, 0, 1.0D, null);
    }

    // 构建并冻结全部内置类型注册表
    public static BuiltinTypeRegistries create() {
        TypeRegistry<ConditionType<?>> conditionTypes = BuiltinConditionTypes.create();
        Codec<ConditionExpression> expressionCodec = RuleCodecs.conditionExpressionCodec(conditionTypes);
        TypeRegistry<EffectType<?>> effectTypes = BuiltinEffectTypes.create(expressionCodec);
        return new BuiltinTypeRegistries(conditionTypes, effectTypes, expressionCodec);
    }
}
