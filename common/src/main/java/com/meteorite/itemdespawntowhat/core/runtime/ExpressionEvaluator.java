package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.api.ConditionEvaluator;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionGroup;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;

/**
 * DNF 条件表达式求值器：组间 OR、组内 AND、叶级 NOT，全部短路。
 * 空表达式恒真；未注册的条件类型按 false 处理（加载期已被拒载，这里是防御性兜底）。
 */
public final class ExpressionEvaluator {

    private ExpressionEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 求值整个表达式
    public static boolean matches(ConditionExpression expression,
                                  ConditionContext context,
                                  TypeRegistry<ConditionType<?>> conditionTypes) {
        if (expression == null || expression.isEmpty()) {
            return true;
        }
        for (ConditionGroup group : expression.groups()) {
            boolean matched = true;
            for (Condition leaf : group.conditions()) {
                ConditionType<?> definition = conditionTypes.getOrNull(leaf.type());
                boolean value;
                if (definition == null || leaf instanceof com.meteorite.itemdespawntowhat.core.type.condition.SurroundingBlocksCondition
                        && !LoadedChunks.containsArea(context.level(), context.pos(), 1)) {
                    // 未注册类型：直接判否且不参与取反（加载期已拒载，这里是防御路径）
                    value = false;
                } else {
                    try {
                        value = evaluatorOf(definition).test(leaf, context);
                        if (leaf.negated()) { value = !value; }
                    } catch (RuntimeException failure) {
                        if (DebugMode.ENABLED) { DebugScenarioManager.observe(context.source(), "ERROR", "condition", leaf.type(), "error", failure.toString()); }
                        org.apache.logging.log4j.LogManager.getLogger().error("条件求值失败：类型={} 位置={}", leaf.type(), context.pos(), failure);
                        value = false;
                    }
                }
                if (DebugMode.ENABLED) { DebugScenarioManager.observe(context.source(), value ? "CONDITION_TRUE" : "CONDITION_FALSE",
                        "condition", leaf.type(), "negated", leaf.negated(), "evaluated_position", context.pos()); }
                if (!value) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    // 泛型桥接：条件叶即类型定义声明的 P，求值器只读参数
    @SuppressWarnings("unchecked")
    private static ConditionEvaluator<Condition> evaluatorOf(ConditionType<?> definition) {
        return ((ConditionType<Condition>) definition).evaluator();
    }
}
