package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.ConditionContext;
import com.meteorite.itemdespawntowhat.core.api.ConditionResult;
import com.meteorite.itemdespawntowhat.core.api.Evaluability;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.model.Condition;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 条件树求值：把 ConditionExpression 的递归结构求成四态结果（MATCH / NO_MATCH / UNAVAILABLE / ERROR）。
 * 三条关键约定（契约 §2.6）：
 * 1) inverted 只互换 MATCH 与 NO_MATCH，UNAVAILABLE / ERROR 原样保留，杜绝「判不了」被取反成「成立」；
 * 2) 组合节点按序短路，但 UNAVAILABLE / ERROR 只记标记继续，且 UNAVAILABLE 优先于 ERROR；
 * 3) 空 all_of 恒真、空 any_of 恒假（编辑器中间态），不抛异常。
 */
public final class ExpressionTreeEvaluator {

    private static final Logger LOGGER = LogManager.getLogger();

    private ExpressionTreeEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 求值整个表达式：null 与空表达式恒为 MATCH
    public static ConditionResult evaluate(@Nullable ConditionExpression expression,
                                           ConditionContext context,
                                           TypeRegistry<ConditionType<?>> conditionTypes) {
        if (expression == null || expression.isEmpty()) {
            return ConditionResult.MATCH;
        }
        return evaluateNode(expression.root(), context, conditionTypes);
    }

    // 递归求值单个节点
    public static ConditionResult evaluateNode(@Nullable ConditionNode node,
                                               ConditionContext context,
                                               TypeRegistry<ConditionType<?>> conditionTypes) {
        if (node == null) {
            // 结构非法（结构校验已在保存期拦截，这里兜底）
            return ConditionResult.ERROR;
        }
        if (node instanceof ConditionNode.Leaf leaf) {
            return evaluateLeaf(leaf.condition(), context, conditionTypes);
        }
        if (node instanceof ConditionNode.Inverted inverted) {
            return invert(evaluateNode(inverted.term(), context, conditionTypes));
        }
        if (node instanceof ConditionNode.AllOf allOf) {
            return evaluateAllOf(allOf.terms(), context, conditionTypes);
        }
        if (node instanceof ConditionNode.AnyOf anyOf) {
            return evaluateAnyOf(anyOf.terms(), context, conditionTypes);
        }
        return ConditionResult.ERROR;
    }

    // 合取：NO_MATCH 立即返回；UNAVAILABLE / ERROR 记标记继续；否则 MATCH
    private static ConditionResult evaluateAllOf(List<ConditionNode> terms,
                                                 ConditionContext context,
                                                 TypeRegistry<ConditionType<?>> conditionTypes) {
        boolean sawUnavailable = false;
        boolean sawError = false;
        for (ConditionNode term : terms) {
            ConditionResult result = evaluateNode(term, context, conditionTypes);
            if (result == ConditionResult.NO_MATCH) {
                return ConditionResult.NO_MATCH;
            }
            if (result == ConditionResult.UNAVAILABLE) {
                sawUnavailable = true;
            } else if (result == ConditionResult.ERROR) {
                sawError = true;
            }
        }
        if (sawUnavailable) {
            return ConditionResult.UNAVAILABLE;
        }
        if (sawError) {
            return ConditionResult.ERROR;
        }
        return ConditionResult.MATCH;
    }

    // 析取：MATCH 立即返回；UNAVAILABLE / ERROR 记标记继续；否则 NO_MATCH
    private static ConditionResult evaluateAnyOf(List<ConditionNode> terms,
                                                 ConditionContext context,
                                                 TypeRegistry<ConditionType<?>> conditionTypes) {
        boolean sawUnavailable = false;
        boolean sawError = false;
        for (ConditionNode term : terms) {
            ConditionResult result = evaluateNode(term, context, conditionTypes);
            if (result == ConditionResult.MATCH) {
                return ConditionResult.MATCH;
            }
            if (result == ConditionResult.UNAVAILABLE) {
                sawUnavailable = true;
            } else if (result == ConditionResult.ERROR) {
                sawError = true;
            }
        }
        if (sawUnavailable) {
            return ConditionResult.UNAVAILABLE;
        }
        if (sawError) {
            return ConditionResult.ERROR;
        }
        return ConditionResult.NO_MATCH;
    }

    // 取反：只互换 MATCH 与 NO_MATCH
    private static ConditionResult invert(ConditionResult result) {
        if (result == ConditionResult.MATCH) {
            return ConditionResult.NO_MATCH;
        }
        if (result == ConditionResult.NO_MATCH) {
            return ConditionResult.MATCH;
        }
        return result;
    }

    // 条件叶求值：可求值性门禁 -> 求值器 -> 四态结果
    private static ConditionResult evaluateLeaf(@Nullable Condition condition,
                                                ConditionContext context,
                                                TypeRegistry<ConditionType<?>> conditionTypes) {
        if (condition == null || condition.type() == null) {
            return ConditionResult.ERROR;
        }
        ConditionType<?> definition = conditionTypes.getOrNull(condition.type());
        if (definition == null) {
            // 未注册类型：加载期已被拒载，运行期按「判不了」兜底；绝不能按不成立处理，否则 inverted 会反过来放行
            observe(context, "CONDITION_UNAVAILABLE", condition);
            return ConditionResult.UNAVAILABLE;
        }
        try {
            if (bridge(definition).evaluability(condition, context) == Evaluability.UNAVAILABLE) {
                observe(context, "CONDITION_UNAVAILABLE", condition);
                return ConditionResult.UNAVAILABLE;
            }
            boolean value = bridge(definition).evaluator().test(condition, context);
            observe(context, value ? "CONDITION_TRUE" : "CONDITION_FALSE", condition);
            return value ? ConditionResult.MATCH : ConditionResult.NO_MATCH;
        } catch (RuntimeException failure) {
            if (DebugMode.ENABLED) {
                DebugScenarioManager.observe(context.source(), "ERROR", "condition", condition.type(), "error", failure.toString());
            }
            LOGGER.error("条件求值失败：类型={} 位置={}", condition.type(), context.pos(), failure);
            return ConditionResult.ERROR;
        }
    }

    // 开发环境下的条件求值观察点
    private static void observe(ConditionContext context, String event, Condition condition) {
        if (DebugMode.ENABLED) {
            DebugScenarioManager.observe(context.source(), event,
                    "condition", condition.type(), "evaluated_position", context.pos());
        }
    }

    // 泛型桥接：条件叶即类型定义声明的 P，求值器与可求值性检查都只读参数
    @SuppressWarnings("unchecked")
    private static ConditionType<Condition> bridge(ConditionType<?> definition) {
        return (ConditionType<Condition>) definition;
    }
}
