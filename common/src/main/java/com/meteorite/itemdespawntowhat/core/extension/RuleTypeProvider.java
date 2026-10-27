package com.meteorite.itemdespawntowhat.core.extension;

import com.meteorite.itemdespawntowhat.core.api.TypeRegistrar;

import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.EffectType;

/**
 * 第三方类型 SPI；通过 META-INF/services 中的本接口全名登记实现类，冻结前分两阶段调用。
 * <p>服务端启动与客户端首次使用类型表时分别装配 provider；实现只注册类型定义，
 * 默认实现不登记类型；
 * 可在两侧重复装配，不依赖当前世界或服务端实例。客户端安装同一 provider 后，非空 params 自动投影为表单。</p>
 */
public interface RuleTypeProvider {

    /** 第一阶段登记条件类型；装配器封口后才构建规则级条件树 Codec。 */
    default void registerConditions(@SuppressWarnings("unused") TypeRegistrar<ConditionType<?>> registry) {}

    /** 第二阶段登记效果类型；注册窗口由装配器关闭。 */
    default void registerEffects(@SuppressWarnings("unused") TypeRegistrar<EffectType<?>> registry) {}
}
