package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.OutcomeCandidate;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import com.meteorite.itemdespawntowhat.core.type.effect.ConsumeCatalystEffect;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 催化剂门槛投影：在规则索引构建时补齐 counts / count 均未声明的引用数量。
 * 显式门槛不改写；缺省数量优先采用同作用域成本，其次全规则唯一匹配成本，否则为 1。
 * 规则级成本按引用匹配，效果级及跨作用域成本按 items 集合匹配；数量使用 countFor。
 * 投影只重建受影响的运行期对象，保存仍保持原声明；编辑器按相同函数展示缺省数量。
 */
public final class CatalystThresholdProjection {

    // 没有任何可匹配消耗配置时的兜底门槛，与 CatalystPresentCondition 的默认值同源
    private static final int FALLBACK_THRESHOLD = CatalystPresentCondition.DEFAULT_COUNT;

    private CatalystThresholdProjection() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 投影整条规则：规则级条件与全部效果级条件（含候选结果内效果）里的留空门槛都被解析成有效值
    public static Rule project(Rule rule) {
        if (!hasUnfilledThreshold(rule)) {
            // 快速路径：没有任何留空门槛时不重建任何对象
            return rule;
        }
        ConditionExpression conditions = projectExpression(rule.conditions(), rule, null);
        List<Effect> effects = projectEffects(rule.effects(), rule);
        List<OutcomeCandidate> outcomes = projectOutcomes(rule.outcomes(), rule);
        if (conditions == rule.conditions() && effects == rule.effects() && outcomes == rule.outcomes()) {
            return rule;
        }
        // 只替换条件树与效果列表，其余字段（id/优先级/成本/组合模式等）原样保留，排序键不受影响
        return new Rule(rule.id(), rule.enabled(), rule.priority(), rule.displayName(), rule.notes(), rule.source(),
                conditions, rule.triggerAfterSeconds(), effects, rule.triggers(), rule.sourceCost(),
                rule.catalystCost(), rule.combination(), outcomes, rule.schemaVersion());
    }

    /**
     * 解析一个留空门槛的有效数量：先看同作用域消耗配置，再看整条规则范围内是否恰好只有一个 items 相同的配置。
     * 纯函数、无副作用，运行期投影与编辑器「默认：N」提示共用同一份判定。
     *
     * @param rule        门槛所在规则（提供 catalyst_cost 与全部效果）
     * @param items       条件叶声明的候选物品引用（item id 或 #tag）
     * @param scopeEffect 承载该条件叶的效果；null 表示条件叶位于规则级条件中
     * @return 有效门槛，找不到唯一可匹配的消耗配置时返回 DEFAULT_COUNT
     */
    public static int resolveThreshold(Rule rule, List<TaggedId> items, @Nullable Effect scopeEffect) {
        return items.isEmpty() ? FALLBACK_THRESHOLD : resolveThreshold(rule, items, scopeEffect, items.getFirst());
    }

    public static int resolveThreshold(Rule rule, List<TaggedId> items, @Nullable Effect scopeEffect, TaggedId reference) {
        // 同作用域优先：效果级条件跟随所在效果自身的消耗量
        if (scopeEffect instanceof ConsumeCatalystEffect consume && sameItems(items, consume.items())) {
            return positive(consume.countFor(reference));
        }
        // 规则级条件跟随规则级 catalyst_cost
        if (scopeEffect == null && rule.catalystCost() != null && rule.catalystCost().items().contains(reference)) {
            return positive(rule.catalystCost().countFor(reference));
        }
        // 同作用域无匹配：全规则范围内恰好只有一个 items 相同的消耗配置才敢沿用它的数量；
        // 多个不同对象或不同作用域的消耗配置不得互相覆盖，此时退回默认值
        int matched = 0;
        int resolved = FALLBACK_THRESHOLD;
        if (rule.catalystCost() != null && sameItems(items, rule.catalystCost().items())) {
            matched++;
            resolved = positive(rule.catalystCost().countFor(reference));
        }
        for (Effect effect : rule.effects()) {
            if (effect instanceof ConsumeCatalystEffect consume && sameItems(items, consume.items())) {
                matched++;
                resolved = positive(consume.countFor(reference));
            }
        }
        for (OutcomeCandidate outcome : rule.outcomes()) {
            for (Effect effect : outcome.effects()) {
                if (effect instanceof ConsumeCatalystEffect consume && sameItems(items, consume.items())) {
                    matched++;
                    resolved = positive(consume.countFor(reference));
                }
            }
        }
        return matched == 1 ? resolved : FALLBACK_THRESHOLD;
    }

    // items 集合语义比较（顺序无关）；两侧都必须非空，空列表不是合法的催化剂引用
    private static boolean sameItems(@Nullable List<TaggedId> left, @Nullable List<TaggedId> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return new HashSet<>(left).equals(new HashSet<>(right));
    }

    // 消耗配置的数量越界由校验层拒载；这里只保证不把 0 / 负数当成有效门槛（会让求值器 fail-closed）
    private static int positive(int count) {
        return count > 0 ? count : FALLBACK_THRESHOLD;
    }

    // 快速判定：整条规则是否存在留空门槛（不存在时投影无任何分配）
    private static boolean hasUnfilledThreshold(Rule rule) {
        if (containsUnfilledThreshold(rule.conditions())) {
            return true;
        }
        for (Effect effect : rule.effects()) {
            if (containsUnfilledThreshold(effect.conditions())) {
                return true;
            }
        }
        for (OutcomeCandidate outcome : rule.outcomes()) {
            for (Effect effect : outcome.effects()) {
                if (containsUnfilledThreshold(effect.conditions())) {
                    return true;
                }
            }
        }
        return false;
    }

    // 条件表达式内是否存在留空门槛；expression 为 null 表示效果未声明效果级条件
    private static boolean containsUnfilledThreshold(@Nullable ConditionExpression expression) {
        return expression != null && containsUnfilledThreshold(expression.root());
    }

    // 条件节点内是否存在留空门槛
    private static boolean containsUnfilledThreshold(@Nullable ConditionNode node) {
        if (node instanceof ConditionNode.Leaf(var condition)) {
            return condition instanceof CatalystPresentCondition(var items, var count, var counts) && count == null
                    && items.stream().anyMatch(item -> !counts.containsKey(item));
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

    // 重建条件表达式：无变化时返回原实例；效果级条件为 null 时保持 null
    private static @Nullable ConditionExpression projectExpression(@Nullable ConditionExpression expression,
                                                                   Rule rule, @Nullable Effect scopeEffect) {
        if (expression == null || expression.root() == null) {
            return expression;
        }
        ConditionNode root = projectNode(expression.root(), rule, scopeEffect);
        return root == expression.root() ? expression : new ConditionExpression(root);
    }

    // 重建单个条件节点：只有留空门槛的叶会被替换，其余节点无变化时原样返回
    private static ConditionNode projectNode(ConditionNode node, Rule rule, @Nullable Effect scopeEffect) {
        if (node instanceof ConditionNode.Leaf(var condition)) {
            if (condition instanceof CatalystPresentCondition(var leafItems, var leafCount, var leafCounts)
                    && leafCount == null && leafItems.stream().anyMatch(item -> !leafCounts.containsKey(item))) {
                var counts = new java.util.LinkedHashMap<>(leafCounts);
                for (TaggedId item : leafItems) {
                    int fallback = resolveThreshold(rule, leafItems, scopeEffect, item);
                    counts.putIfAbsent(item, fallback);
                }
                return new ConditionNode.Leaf(new CatalystPresentCondition(leafItems,
                        FALLBACK_THRESHOLD, counts));
            }
            return node;
        }
        if (node instanceof ConditionNode.Inverted(var term)) {
            ConditionNode projected = projectNode(term, rule, scopeEffect);
            return projected == term ? node : new ConditionNode.Inverted(projected);
        }
        if (node instanceof ConditionNode.AllOf(var terms)) {
            List<ConditionNode> projected = projectTerms(terms, rule, scopeEffect);
            return projected == terms ? node : new ConditionNode.AllOf(projected);
        }
        if (node instanceof ConditionNode.AnyOf(var terms)) {
            List<ConditionNode> projected = projectTerms(terms, rule, scopeEffect);
            return projected == terms ? node : new ConditionNode.AnyOf(projected);
        }
        return node;
    }

    // 重建子节点列表：至少一个子节点发生变化时才新建列表，避免整树复制
    private static List<ConditionNode> projectTerms(List<ConditionNode> terms, Rule rule, @Nullable Effect scopeEffect) {
        List<ConditionNode> projected = null;
        for (int index = 0; index < terms.size(); index++) {
            ConditionNode original = terms.get(index);
            ConditionNode replacement = projectNode(original, rule, scopeEffect);
            if (projected == null && replacement != original) {
                projected = new ArrayList<>(terms.subList(0, index));
            }
            if (projected != null) {
                projected.add(replacement);
            }
        }
        return projected == null ? terms : List.copyOf(projected);
    }

    // 重建顶层效果列表：每个效果的条件作用域就是它自身
    private static List<Effect> projectEffects(List<Effect> effects, Rule rule) {
        List<Effect> projected = null;
        for (int index = 0; index < effects.size(); index++) {
            Effect original = effects.get(index);
            Effect replacement = projectEffect(original, rule);
            if (projected == null && replacement != original) {
                projected = new ArrayList<>(effects.subList(0, index));
            }
            if (projected != null) {
                projected.add(replacement);
            }
        }
        return projected == null ? effects : List.copyOf(projected);
    }

    // 重建候选结果列表：只替换内含留空门槛的效果，其余候选原样复用
    private static List<OutcomeCandidate> projectOutcomes(List<OutcomeCandidate> outcomes, Rule rule) {
        List<OutcomeCandidate> projected = null;
        for (int index = 0; index < outcomes.size(); index++) {
            OutcomeCandidate original = outcomes.get(index);
            List<Effect> effects = projectEffects(original.effects(), rule);
            OutcomeCandidate replacement = effects == original.effects() ? original
                    : new OutcomeCandidate(original.id(), effects, original.safeSpawn(), original.fillOrigin());
            if (projected == null && replacement != original) {
                projected = new ArrayList<>(outcomes.subList(0, index));
            }
            if (projected != null) {
                projected.add(replacement);
            }
        }
        return projected == null ? outcomes : List.copyOf(projected);
    }

    // 重建单个效果：条件未变化时原样返回；否则由实现类重建真实效果类型（执行器按具体类型强转，不能用包装类型替代）
    private static Effect projectEffect(Effect effect, Rule rule) {
        ConditionExpression conditions = effect.conditions();
        ConditionExpression projected = projectExpression(conditions, rule, effect);
        return projected == conditions ? effect : effect.withConditions(projected);
    }
}
