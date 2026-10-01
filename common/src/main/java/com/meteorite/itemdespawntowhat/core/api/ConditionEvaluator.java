package com.meteorite.itemdespawntowhat.core.api;

/**
 * 条件求值器契约：一种条件类型对应一个求值器。
 * 求值器必须是纯谓词：只读取上下文，不得修改世界（消耗由效果承担）。
 */
@FunctionalInterface
public interface ConditionEvaluator<P> {

    // 返回条件是否成立（未取反的原始判定）
    boolean test(P condition, ConditionContext context);
}
