package com.meteorite.itemdespawntowhat.core.model;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 转化规则：后端唯一的配置单元。
 * 一条规则 = 源匹配 + 消失方式集合 + 条件表达式 + 固定成本 + 组合模式 + 候选结果集合；
 * 同一掉落物只执行优先级最高的那一条规则。
 * <p>effects 是平铺效果列表，为兼容既有数据包与第三方效果类型而保留；声明了 outcomes 时以候选结果为准，
 * 两者不得同时声明（由 {@link RuleValidation} 拒绝）。未声明 outcomes 时，顶层 effects 由
 * {@link #effectiveOutcomes()} 隐式映射为唯一候选 id=default。
 * displayName 是可选展示名：规范化后为空串视为未设置（null），长度上限由 RuleValidation 按 code point 校验。
 */
public record Rule(
        ResourceLocation id,
        boolean enabled,
        int priority,
        @Nullable String displayName,
        @Nullable String notes,
        SourceMatcher source,
        ConditionExpression conditions,
        int triggerAfterSeconds,
        List<Effect> effects,
        Set<TriggerKind> triggers,
        @Nullable SourceCost sourceCost,
        @Nullable CatalystCost catalystCost,
        boolean consumeFluid,
        CombinationMode combination,
        List<OutcomeCandidate> outcomes,
        int schemaVersion
) {

    public Rule {
        displayName = normalizeDisplayName(displayName);
        conditions = conditions == null ? ConditionExpression.EMPTY : conditions;
        effects = List.copyOf(effects);
        // 消失方式保留声明顺序（编码回 JSON 时顺序稳定）；空集合表示仅自然消失
        triggers = triggers == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(triggers));
        combination = combination == null ? CombinationMode.ROUND_ROBIN : combination;
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
    }

    // 展示名规范化：去首尾空白，空串归一为 null
    private static @Nullable String normalizeDisplayName(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 特异性兜底：条件叶总数（同优先级时叶数多者优先） */
    public int complexity() {
        return conditions.leafCount();
    }

    /** 是否具备参与运行时匹配的资格：启用且至少有一个可执行候选（顶层 effects 或显式 outcomes） */
    public boolean isRunnable() {
        return enabled && !effectiveOutcomes().isEmpty();
    }

    /** 生效的消失方式：未声明时按 natural 处理 */
    public Set<TriggerKind> effectiveTriggers() {
        return triggers.isEmpty() ? Set.of(TriggerKind.NATURAL) : triggers;
    }

    /** 生效的候选结果：未声明 outcomes 时由顶层 effects 隐式映射为唯一候选 default（供顶层 effects 写法使用） */
    public List<OutcomeCandidate> effectiveOutcomes() {
        if (!outcomes.isEmpty()) {
            return outcomes;
        }
        return effects.isEmpty() ? List.of() : List.of(OutcomeCandidate.implicit(effects));
    }

    /** 规则声明的全部效果（平铺 effects + 各候选内 effects，按声明顺序） */
    public List<Effect> allEffects() {
        List<Effect> all = new ArrayList<>(effects);
        for (OutcomeCandidate candidate : outcomes) {
            all.addAll(candidate.effects());
        }
        return List.copyOf(all);
    }

    /**
     * 以新的生效启用值重建规则，其余字段与标识保持不变。
     * 加载链路用它把「本服启用状态 ?? 默认启用」注入运行时对象，不修改规则内容的 JSON 副本。
     *
     * @param value 生效启用值
     * @return 启用值不同时为重建的新实例；相同时返回自身
     */
    public Rule withEnabled(boolean value) {
        if (enabled == value) {
            return this;
        }
        return new Rule(id, value, priority, displayName, notes, source, conditions, triggerAfterSeconds, effects,
                triggers, sourceCost, catalystCost, consumeFluid, combination, outcomes, schemaVersion);
    }

}
