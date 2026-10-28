package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.EffectExecutor;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;

/**
 * 效果类型定义：一种已注册的效果类别。
 * 参数对象 P 同时实现 {@link Effect}，因此分发解码后可直接作为效果使用；
 * 执行器由类型自带，运行时按 type 字段一次查表即可拿到编解码器与执行器。
 *
 * <p>产出类别是该类型的展示元数据：规则分类器据此判断含该效果的规则归入哪个分类组。
 * 分类只影响列表分组，不参与运行时匹配、结算或优先级。</p>
 *
 * <p>注册类型的 {@code params()} 必须包含 {@link CommonFields#EFFECT_PARAMS} 中的同一声明实例，
 * 类型表装配时检查该契约。类型自身的 codec 负责这些字段的编解码，并与
 * {@link Effect#delayTicks()}、{@link Effect#chance()} 保持一致。</p>
 */
public interface EffectType<P extends Effect> extends TypeDefinition<P> {

    // 该效果类型的服务端执行器
    EffectExecutor<P> executor();

    // 是否属于「一次性效果」（天气 / 闪电 / 爆炸等）：不参与数量上限，仅含一次性效果的候选每源最多一组
    default boolean oneShot() {
        return false;
    }

    /**
     * 该效果类型的产出类别；默认 {@link ProductKind#NONE}，表示非产出。
     *
     * <p>产出子类随参数变化的类型（如统一实体生成按 variant 生成物品 / 生物 / 其他实体 / 经验）
     * 重写本方法读取实例参数后返回；新增效果类型只需声明产出类别即可参与分类。</p>
     *
     * @param params 该类型已解码的参数对象
     * @return 产出类别；非产出返回 {@link ProductKind#NONE}
     */
    default ProductKind productKind(P params) {
        return ProductKind.NONE;
    }

    /**
     * 该效果类型每轮一组的容量需求声明；默认不占容量。
     *
     * <p>结算的计划期初始产出预算读取本声明：声明是每轮一组的最小产出下界，
     * 产物类型未知的产出（战利品表）按保守口径参与；一次性效果不参与容量计算，无需声明。</p>
     *
     * @param params 该类型已解码的参数对象
     * @return 每轮一组的最小产出需求；非产出返回 {@link CapacityDemand#NONE}
     */
    default CapacityDemand capacityDemand(P params) {
        return CapacityDemand.NONE;
    }
}
