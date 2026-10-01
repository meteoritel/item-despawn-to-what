package com.meteorite.itemdespawntowhat.core.type.condition.eval;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.type.condition.DimensionCondition;

/**
 * dimension 条件的求值器：判定掉落物所在维度是否命中候选列表。
 * 纯谓词：只读取上下文，不修改世界；不处理 negated（叶级取反由运行时统一应用）。
 */
public final class DimensionEvaluator {

    private DimensionEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 命中维度列表中的任意一项即成立
    public static boolean test(DimensionCondition condition, ConditionContext context) {
        return condition.dimensions().contains(context.level().dimension().location());
    }
}
