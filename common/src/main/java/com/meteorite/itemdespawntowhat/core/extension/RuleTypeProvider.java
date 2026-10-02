package com.meteorite.itemdespawntowhat.core.extension;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;

import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;
import com.mojang.serialization.Codec;

/** 第三方类型 SPI；通过 META-INF/services 中的本接口全名登记实现类，冻结前分两阶段调用。 */
public interface RuleTypeProvider {
    // 先注册条件，随后才构建能解码第三方条件的表达式 Codec。
    default void registerConditions(TypeRegistry<ConditionType<?>> registry) {}

    // 效果注册时可以复用完整的条件表达式 Codec。
    default void registerEffects(TypeRegistry<EffectType<?>> registry, Codec<ConditionExpression> expressionCodec) {}
}
