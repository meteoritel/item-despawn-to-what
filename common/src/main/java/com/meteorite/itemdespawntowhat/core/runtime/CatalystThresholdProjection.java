package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 将条件叶未声明的门槛投影为规则 catalyst_cost 对应引用的门槛，缺省为 1。
 * 全部催化剂条件共享规则半径；运行期对象重建不反写保存声明。
 */
public final class CatalystThresholdProjection {

    // 没有任何可匹配门槛配置时的兜底门槛，与 CatalystPresentCondition 的默认值同源
    private static final int FALLBACK_THRESHOLD = CatalystPresentCondition.DEFAULT_COUNT;

    private CatalystThresholdProjection() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 补齐整条规则的条件门槛与共享半径，仅重建受影响的运行期对象。 */
    public static Rule project(Rule rule) {
        if (rule.catalystCost() == null && !hasUnfilledThreshold(rule)) {
            // 快速路径：没有任何留空门槛时不重建任何对象
            return rule;
        }
        ConditionExpression conditions = projectExpression(rule.conditions(), rule);
        if (conditions == rule.conditions()) {
            return rule;
        }
        // 只替换条件树，其余字段（id/优先级/效果列表/组合模式等）原样保留，排序键不受影响
        return new Rule(rule.id(), rule.enabled(), rule.priority(), rule.displayName(), rule.notes(), rule.source(),
                conditions, rule.triggerAfterSeconds(), rule.effects(), rule.triggers(), rule.sourceCost(),
                rule.catalystCost(), rule.consumeFluid(), rule.combination(), rule.outcomes(), rule.schemaVersion());
    }

    /**
     * 解析单个引用的默认门槛；实际消耗量不参与门槛计算。
     * @param rule 条件所在规则
     * @param reference 催化剂引用
     * @return 规则门槛，未声明该引用时为 1
     */
    public static int resolveThreshold(Rule rule, TaggedId reference) {
        return rule.catalystCost() != null && rule.catalystCost().items().contains(reference)
                ? rule.catalystCost().countFor(reference) : FALLBACK_THRESHOLD;
    }

    // 快速判定：规则条件树是否存在留空门槛（不存在时投影无任何分配）
    private static boolean hasUnfilledThreshold(Rule rule) {
        return containsUnfilledThreshold(rule.conditions());
    }

    // 条件表达式内是否存在留空门槛；expression 为 null 表示规则未声明条件
    private static boolean containsUnfilledThreshold(@Nullable ConditionExpression expression) {
        return expression != null && containsUnfilledThreshold(expression.root());
    }

    // 条件节点内是否存在留空门槛
    private static boolean containsUnfilledThreshold(@Nullable ConditionNode node) {
        if (node instanceof ConditionNode.Leaf(var condition)) {
            return condition instanceof CatalystPresentCondition leaf && leaf.count() == null
                    && leaf.items().stream().anyMatch(item -> !leaf.counts().containsKey(item));
        }
        if (node instanceof ConditionNode.Inverted(var term)) {
            return containsUnfilledThreshold(term);
        }
        if (node instanceof ConditionNode.AllOf(var terms)) {
            return containsUnfilledTerms(terms);
        }
        if (node instanceof ConditionNode.AnyOf(var terms)) {
            return containsUnfilledTerms(terms);
        }
        return false;
    }

    // 组合节点的子节点里是否存在留空门槛
    private static boolean containsUnfilledTerms(List<ConditionNode> terms) {
        for (ConditionNode term : terms) {
            if (containsUnfilledThreshold(term)) {
                return true;
            }
        }
        return false;
    }

    // 重建条件表达式：无变化时返回原实例；未声明条件时保持 null
    private static @Nullable ConditionExpression projectExpression(@Nullable ConditionExpression expression,
                                                                   Rule rule) {
        if (expression == null || expression.root() == null) {
            return expression;
        }
        ConditionNode root = projectNode(expression.root(), rule);
        return root == expression.root() ? expression : new ConditionExpression(root);
    }

    // 重建单个催化剂条件的有效数量与共享半径，其余节点按需复用。
    private static ConditionNode projectNode(ConditionNode node, Rule rule) {
        if (node instanceof ConditionNode.Leaf(var condition)) {
            if (condition instanceof CatalystPresentCondition leaf) {
                var counts = new java.util.LinkedHashMap<>(leaf.counts());
                for (TaggedId item : leaf.items()) {
                    counts.putIfAbsent(item, leaf.count() != null ? leaf.count()
                            : resolveThreshold(rule, item));
                }
                int radius = rule.catalystCost() == null ? leaf.radius() : rule.catalystCost().radius();
                return new ConditionNode.Leaf(new CatalystPresentCondition(leaf.items(), FALLBACK_THRESHOLD, counts, radius));
            }
            return node;
        }
        if (node instanceof ConditionNode.Inverted(var term)) {
            ConditionNode projected = projectNode(term, rule);
            return projected == term ? node : new ConditionNode.Inverted(projected);
        }
        if (node instanceof ConditionNode.AllOf(var terms)) {
            List<ConditionNode> projected = projectTerms(terms, rule);
            return projected == terms ? node : new ConditionNode.AllOf(projected);
        }
        if (node instanceof ConditionNode.AnyOf(var terms)) {
            List<ConditionNode> projected = projectTerms(terms, rule);
            return projected == terms ? node : new ConditionNode.AnyOf(projected);
        }
        return node;
    }

    // 重建子节点列表：至少一个子节点发生变化时才新建列表，避免整树复制
    private static List<ConditionNode> projectTerms(List<ConditionNode> terms, Rule rule) {
        List<ConditionNode> projected = null;
        for (int index = 0; index < terms.size(); index++) {
            ConditionNode original = terms.get(index);
            ConditionNode replacement = projectNode(original, rule);
            if (projected == null && replacement != original) {
                projected = new ArrayList<>(terms.subList(0, index));
            }
            if (projected != null) {
                projected.add(replacement);
            }
        }
        return projected == null ? terms : List.copyOf(projected);
    }

}
