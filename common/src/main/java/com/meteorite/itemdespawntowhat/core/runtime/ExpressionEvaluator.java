package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.ConditionResult;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import org.jetbrains.annotations.Nullable;

/**
 * 条件表达式求值入口：对外只保留 matches 与 evaluate 两个入口，递归逻辑与四态规则在 {@link ExpressionTreeEvaluator}。
 * matches 等价于「evaluate 的结果是 MATCH」：UNAVAILABLE 与 ERROR 都不会触发效果。
 */
public final class ExpressionEvaluator {

    private ExpressionEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 条件是否成立：仅 MATCH 为真
    public static boolean matches(@Nullable ConditionExpression expression,
                                  ConditionContext context,
                                  TypeRegistry<ConditionType<?>> conditionTypes) {
        return evaluate(expression, context, conditionTypes).isMatch();
    }

    // 四态求值入口
    public static ConditionResult evaluate(@Nullable ConditionExpression expression,
                                           ConditionContext context,
                                           TypeRegistry<ConditionType<?>> conditionTypes) {
        return ExpressionTreeEvaluator.evaluate(expression, context, conditionTypes);
    }
}
