package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.ConditionEvaluator;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;

/**
 * 条件类型定义：一种已注册的条件类别。
 * 参数对象 P 同时实现 {@link Condition}，因此分发解码后可直接作为条件叶使用；
 * 求值器由类型自带，运行时按 type 字段一次查表即可拿到编解码器与求值器。
 */
public interface ConditionType<P extends Condition> extends TypeDefinition<P> {

    // 该条件类型的服务端求值器
    ConditionEvaluator<P> evaluator();
}
