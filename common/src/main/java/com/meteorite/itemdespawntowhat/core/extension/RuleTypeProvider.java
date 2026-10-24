package com.meteorite.itemdespawntowhat.core.extension;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;

import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;

/** 第三方类型 SPI；通过 META-INF/services 中的本接口全名登记实现类，冻结前分两阶段调用。 */
public interface RuleTypeProvider {
    // 第一阶段登记条件类型，规则级条件树的编解码器在冻结后才构建。
    default void registerConditions(TypeRegistry<ConditionType<?>> registry) {}

    // 第二阶段登记效果类型；效果参数不引用条件表达式，注册时不再需要条件 Codec。
    default void registerEffects(TypeRegistry<EffectType<?>> registry) {}
}
