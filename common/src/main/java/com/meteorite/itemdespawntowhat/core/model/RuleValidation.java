package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 规则语义校验。
 * 统一严格策略：语义非法即由调用方拒载该条规则，其余条目照常加载；所有问题进入 IssueCollector。
 */
public final class RuleValidation {

    private RuleValidation() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 校验单条规则；origin 用于问题定位（文件路径或数据包标识）
    public static boolean validate(Rule rule, IssueCollector issues, @Nullable String origin) {
        int before = issues.errors().size();
        String rulePath = rule.id() == null ? "<无 id>" : rule.id().toString();

        if (rule.id() == null) {
            issues.error("规则缺少 id", origin, RuleFields.ID);
        }
        if (rule.triggerAfterSeconds() < 0) {
            issues.error("trigger_after_seconds 不能为负数: " + rule.triggerAfterSeconds(), origin, RuleFields.TRIGGER_AFTER_SECONDS);
        }
        if (rule.source().isEmpty()) {
            issues.error("source.items 至少需要一个物品或标签", origin, SourceMatcher.fieldPath(SourceMatcher.ITEMS_FIELD));
        }
        if (rule.effects().isEmpty()) {
            issues.error("effects 不能为空", origin, RuleFields.EFFECTS);
        }
        if (!rule.conditions().isStructurallyValid()) {
            issues.error("conditions 中存在空条件组（空组合取恒真，视为非法）", origin, RuleFields.CONDITIONS);
        }

        validateSourceEntries(rule.source(), issues, origin);
        for (int index = 0; index < rule.effects().size(); index++) {
            validateEffect(rule.effects().get(index), index, issues, origin, rulePath);
        }
        validateConsumptionEffects(rule, issues, origin);
        return issues.errors().size() == before;
    }

    // 消耗类效果重复检测：同一规则内同一消耗效果类型只允许出现一次
    private static void validateConsumptionEffects(Rule rule, IssueCollector issues, @Nullable String origin) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (Effect effect : rule.effects()) {
            if (ConsumptionDefaults.isConsumption(effect.type())) {
                counts.merge(effect.type(), 1, Integer::sum);
            }
        }
        counts.forEach((type, count) -> {
            if (count > 1) {
                issues.error("同一规则内重复声明消耗效果 " + type + " 共 " + count + " 次", origin, RuleFields.EFFECTS);
            }
        });
    }

    // 源匹配项校验：同一项不得同时出现在匹配与排除中，且不得重复
    private static void validateSourceEntries(SourceMatcher source, IssueCollector issues, @Nullable String origin) {
        Set<String> entries = new HashSet<>();
        for (SourceEntry entry : source.entries()) {
            if (!entries.add(entry.serialized())) {
                issues.error("source.items 中存在重复项: " + entry.serialized(), origin, SourceMatcher.fieldPath(SourceMatcher.ITEMS_FIELD));
            }
        }
        for (SourceEntry entry : source.exclude()) {
            if (entries.contains(entry.serialized())) {
                issues.error("source 中同一项既匹配又排除: " + entry.serialized(), origin, SourceMatcher.fieldPath(SourceMatcher.EXCLUDE_FIELD));
            }
        }
    }

    // 效果通用字段校验；类型专属参数由各效果类型自行校验（阶段②）
    private static void validateEffect(Effect effect, int index, IssueCollector issues,
                                       @Nullable String origin, String rulePath) {
        String path = RuleFields.EFFECTS + "[" + index + "]";
        if (effect.type() == null) {
            issues.error("效果缺少 type", origin, path);
            return;
        }
        if (effect.delayTicks() < 0) {
            issues.error("delay_ticks 不能为负数: " + effect.delayTicks(), origin, path + "." + RuleFields.DELAY_TICKS);
        }
        if (effect.chance() < 0.0D || effect.chance() > 1.0D) {
            issues.error("chance 必须在 [0,1] 内: " + effect.chance(), origin, path + "." + RuleFields.CHANCE);
        }
        ConditionExpression conditions = effect.conditions();
        if (conditions != null && !conditions.isStructurallyValid()) {
            issues.error("效果级 conditions 中存在空条件组", origin, path + "." + RuleFields.CONDITIONS);
        }
        // 单个效果的字段校验到此为止；消耗类效果是否重复在 validate 末尾统一统计
    }

    // 便捷入口：无来源信息的校验
    public static boolean validate(Rule rule, IssueCollector issues) {
        return validate(rule, issues, null);
    }

    // 源匹配项数量上限的提示性校验，避免单条规则展开出过多标签
    public static void warnLargeSource(Rule rule, int threshold, IssueCollector issues, @Nullable String origin) {
        if (rule.source().entries().size() > threshold) {
            issues.warn("source.items 条目数 " + rule.source().entries().size() + " 超过建议上限 " + threshold,
                    origin, SourceMatcher.fieldPath(SourceMatcher.ITEMS_FIELD));
        }
    }

    // 供命令与日志输出使用的规则可读摘要
    public static String describe(Rule rule) {
        return rule.id() + " [priority=" + rule.priority() + ", leaves=" + rule.complexity()
                + ", effects=" + rule.effects().size() + ", trigger=" + rule.triggerAfterSeconds() + "s]";
    }

    // 判定规则是否属于指定物品（不含标签展开，由运行时调用）
    public static boolean matchesDirect(Rule rule, ResourceLocation itemId) {
        return rule.source().matches(itemId, tagId -> Set.of());
    }
}
