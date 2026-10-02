package com.meteorite.itemdespawntowhat.core.model;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 转化规则：后端唯一的配置单元。
 * 一条规则 = 源匹配 + 条件表达式 + 有序效果列表；同一掉落物只执行优先级最高的那一条规则。
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
        List<Effect> effects
) {

    public Rule {
        displayName = normalizeDisplayName(displayName);
        conditions = conditions == null ? ConditionExpression.EMPTY : conditions;
        effects = List.copyOf(effects);
    }

    // 展示名规范化：去首尾空白，空串归一为 null
    private static @Nullable String normalizeDisplayName(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // 特异性兜底：条件叶总数（同优先级时叶数多者优先）
    public int complexity() {
        return conditions.leafCount();
    }

    // 是否具备参与运行时匹配的资格（启用且至少有一个效果）
    public boolean isRunnable() {
        return enabled && !effects.isEmpty();
    }

    // 是否显式声明了任意消耗效果（consume_source / consume_catalyst / consume_fluid）
    public boolean declaresAnyConsumption() {
        for (Effect effect : effects) {
            if (ConsumptionDefaults.isConsumption(effect.type())) {
                return true;
            }
        }
        return false;
    }

    // 是否显式声明了消耗源物品效果
    public boolean declaresSourceConsumption() {
        for (Effect effect : effects) {
            if (ConsumptionDefaults.CONSUME_SOURCE_ID.equals(effect.type())) {
                return true;
            }
        }
        return false;
    }

    // 未显式声明任何消耗效果时，规则按默认语义隐式消耗源物品
    public boolean usesImplicitSourceConsumption() {
        return !declaresAnyConsumption();
    }
}
