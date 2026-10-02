package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.ConditionEvaluator;
import com.meteorite.itemdespawntowhat.core.api.Evaluability;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;

/**
 * 条件类型定义：一种已注册的条件类别。
 * 参数对象 P 同时实现 {@link Condition}，因此分发解码后可直接作为条件叶使用；
 * 求值器与可求值性检查都由类型自带，运行时按 type 字段一次查表即可拿到。
 */
public interface ConditionType<P extends Condition> extends TypeDefinition<P> {

    // 该条件类型的服务端求值器
    ConditionEvaluator<P> evaluator();

    // 可求值性前置门禁：默认恒可判定；需要上下文前提（如周围区块已加载）的类型覆盖本方法
    default Evaluability evaluability(P params, ConditionContext context) {
        return Evaluability.AVAILABLE;
    }
}
