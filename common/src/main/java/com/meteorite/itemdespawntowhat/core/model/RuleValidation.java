package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则语义校验。
 * 统一严格策略：语义非法即由调用方拒载该条规则，其余条目照常加载；所有问题进入 IssueCollector。
 * 两档入口：
 * - validate(rule, issues, origin)：只做结构校验（不依赖注册表）；
 * - validate(rule, effectTypes, conditionTypes, issues, origin)：结构校验 + 类型专属参数校验。
 */
public final class RuleValidation {

    private RuleValidation() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 校验单条规则（结构维度）；origin 用于问题定位（文件路径或数据包标识）
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

    // 校验单条规则（结构 + 类型专属参数）；参数非法同样按「该条拒载」处理
    public static boolean validate(Rule rule,
                                   TypeRegistry<EffectType<?>> effectTypes,
                                   TypeRegistry<ConditionType<?>> conditionTypes,
                                   IssueCollector issues,
                                   @Nullable String origin) {
        int before = issues.errors().size();
        validate(rule, issues, origin);
        validateConditionParams(rule.conditions(), conditionTypes, issues, origin, RuleFields.CONDITIONS);
        for (int index = 0; index < rule.effects().size(); index++) {
            Effect effect = rule.effects().get(index);
            String path = RuleFields.EFFECTS + "[" + index + "]";
            validateEffectParams(effect, effectTypes, issues, origin, path);
            ConditionExpression conditions = effect.conditions();
            if (conditions != null) {
                validateConditionParams(conditions, conditionTypes, issues, origin, path + "." + RuleFields.CONDITIONS);
            }
        }
        return issues.errors().size() == before;
    }

    // 效果类型专属参数校验：类型未注册时给出可读错误
    private static void validateEffectParams(Effect effect, TypeRegistry<EffectType<?>> effectTypes,
                                             IssueCollector issues, @Nullable String origin, String path) {
        if (effect.type() == null) {
            return;
        }
        EffectType<?> definition = effectTypes.getOrNull(effect.type());
        if (definition == null) {
            issues.error("未注册的效果类型: " + effect.type(), origin, path);
            return;
        }
        validateTypeParams(definition, effect, issues, origin, path);
    }

    // 条件叶类型专属参数校验：逐组逐叶，路径形如 conditions[g][l]
    private static void validateConditionParams(ConditionExpression expression,
                                                TypeRegistry<ConditionType<?>> conditionTypes,
                                                IssueCollector issues, @Nullable String origin, String basePath) {
        for (int groupIndex = 0; groupIndex < expression.groups().size(); groupIndex++) {
            ConditionGroup group = expression.groups().get(groupIndex);
            for (int leafIndex = 0; leafIndex < group.conditions().size(); leafIndex++) {
                Condition condition = group.conditions().get(leafIndex);
                String path = basePath + "[" + groupIndex + "][" + leafIndex + "]";
                ConditionType<?> definition = conditionTypes.getOrNull(condition.type());
                if (definition == null) {
                    issues.error("未注册的条件类型: " + condition.type(), origin, path);
                    continue;
                }
                validateTypeParams(definition, condition, issues, origin, path);
            }
        }
    }

    // 泛型桥接：参数对象即类型定义所声明的 P，运行时安全（validateParams 只读参数）
    @SuppressWarnings("unchecked")
    private static <P> void validateTypeParams(TypeDefinition<P> definition, Object params,
                                               IssueCollector issues, @Nullable String origin, String path) {
        if (params == null) {
            return;
        }
        // 先收进局部收集器，补好来源后一次性并入；避免边遍历边追加导致重复 Issue
        IssueCollector local = new IssueCollector();
        boolean valid = definition.validateParams((P) params, local, path);
        // 若实现只返回 false 而未写入问题，补一条兜底错误，避免错误静默
        if (!valid && local.errors().isEmpty()) {
            local.error("类型 " + definition.id() + " 的参数非法", null, path);
        }
        for (Issue issue : local.issues()) {
            issues.add(new Issue(issue.severity(), issue.message(),
                    issue.origin() == null ? origin : issue.origin(), issue.fieldPath()));
        }
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

    // 效果通用字段校验；类型专属参数由注册表感知入口负责
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
    }

    // 便捷入口：无来源信息的结构校验
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
