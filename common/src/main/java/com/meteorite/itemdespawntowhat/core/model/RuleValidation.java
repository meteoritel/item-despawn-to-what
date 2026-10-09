package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeDefinition;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.api.RefChecks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

        if (rule.id() == null) {
            issues.error("规则缺少 id", origin, RuleFields.ID);
        }
        if (rule.triggerAfterSeconds() < 0) {
            issues.error("trigger_after_seconds 不能为负数: " + rule.triggerAfterSeconds(), origin, RuleFields.TRIGGER_AFTER_SECONDS);
        }
        if (rule.source().isEmpty()) {
            issues.error("source.items 至少需要一个物品或标签", origin, SourceMatcher.fieldPath(SourceMatcher.ITEMS_FIELD));
        }
        boolean hasFlatEffects = !rule.effects().isEmpty();
        boolean hasOutcomes = !rule.outcomes().isEmpty();
        if (!hasFlatEffects && !hasOutcomes) {
            issues.error("effects 与 outcomes 至少需要一个", origin, RuleFields.EFFECTS);
        }
        if (hasFlatEffects && hasOutcomes) {
            issues.error("effects 与 outcomes 不能同时声明（二选一）", origin, RuleFields.OUTCOMES);
        }
        if (rule.schemaVersion() != RuleCodecs.DEFAULT_SCHEMA_VERSION) {
            issues.error("不支持的 schema_version: " + rule.schemaVersion()
                    + "（当前支持 " + RuleCodecs.DEFAULT_SCHEMA_VERSION + "）", origin, RuleFields.SCHEMA_VERSION);
        }
        // 固定成本契约：source_cost 按已选引用配置正整数；catalyst_cost 必须是合法对象
        // （items 非空且引用存在、count 1..64、radius 1..8）；两者与同名消耗效果互斥，避免重复记账
        if (rule.sourceCost() != null) {
            for (var amount : rule.sourceCost().counts().entrySet()) {
                if (amount.getValue() <= 0 || rule.source().entries().stream()
                        .noneMatch(entry -> entry.toTagged().equals(amount.getKey()))) {
                    issues.error("source_cost 必须引用已选择的源物品且数量为正数", origin, RuleFields.SOURCE_COST);
                }
            }
        }
        if (rule.catalystCost() != null) {
            validateCatalystCost(rule.catalystCost(), issues);
        }
        if (rule.sourceCost() != null && rule.declaresSourceConsumption()) {
            issues.error("source_cost 与 consume_source 效果不能同时声明（避免重复记账）", origin, RuleFields.SOURCE_COST);
        }
        if (rule.catalystCost() != null && rule.declaresConsumption(ConsumptionDefaults.CONSUME_CATALYST_ID)) {
            issues.error("catalyst_cost 与 consume_catalyst 效果不能同时声明（避免重复记账）", origin, RuleFields.CATALYST_COST);
        }
        if (rule.displayName() != null
                && rule.displayName().codePointCount(0, rule.displayName().length()) > ConditionLimits.MAX_DISPLAY_NAME_CODEPOINTS) {
            issues.error("display_name 超过 " + ConditionLimits.MAX_DISPLAY_NAME_CODEPOINTS + " 个码点",
                    origin, RuleFields.DISPLAY_NAME);
        }
        if (!rule.conditions().isStructurallyValid()) {
            issues.error("conditions 结构非法：all_of / any_of 的 terms 不能为空、inverted 必须有 term，"
                    + "且条件叶<=" + ConditionLimits.MAX_LEAVES + "、节点<=" + ConditionLimits.MAX_NODES
                    + "、深度<=" + ConditionLimits.MAX_DEPTH + "（空条件组请直接省略 conditions 字段）",
                    origin, RuleFields.CONDITIONS);
        }

        List<EffectSlot> slots = effectSlots(rule);
        if (slots.size() > ConditionLimits.MAX_EFFECTS
                || rule.conditions().leafCount() > ConditionLimits.MAX_LEAVES
                || rule.conditions().nodeCount() > ConditionLimits.MAX_NODES
                || rule.conditions().depth() > ConditionLimits.MAX_DEPTH
                || rule.source().entries().size() + rule.source().exclude().size() > ConditionLimits.MAX_SOURCE_ENTRIES) {
            issues.error("规则超出工作量上限：effects<=" + ConditionLimits.MAX_EFFECTS
                    + "、条件叶<=" + ConditionLimits.MAX_LEAVES + "、条件节点<=" + ConditionLimits.MAX_NODES
                    + "、条件深度<=" + ConditionLimits.MAX_DEPTH
                    + "、source 项<=" + ConditionLimits.MAX_SOURCE_ENTRIES, origin, null);
        }
        validateSourceEntries(rule.source(), issues, origin);
        for (EffectSlot slot : slots) {
            validateEffect(slot.effect(), slot.path(), issues, origin);
            if (slot.effect() instanceof com.meteorite.itemdespawntowhat.core.type.effect.ConsumeSourceEffect consume) {
                for (TaggedId reference : consume.counts().keySet()) {
                    if (rule.source().entries().stream().noneMatch(entry -> entry.toTagged().equals(reference))) {
                        issues.error("数量配置引用了未选择的源物品: " + reference.serialized(), origin,
                                slot.path() + "." + RuleFields.ITEM_COUNTS);
                    }
                }
            }
        }
        validateOutcomes(rule, issues, origin);
        validateConsumptionEffects(slots, issues, origin);
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
        // 类型专属参数校验覆盖顶层 effects 与候选结果内 effects，路径与 JSON 形状一致
        for (EffectSlot slot : effectSlots(rule)) {
            Effect effect = slot.effect();
            String path = slot.path();
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

    // 条件叶类型专属参数校验：沿条件树逐叶展开，路径与 JSON 形状一致（如 conditions.terms[0].condition）
    private static void validateConditionParams(ConditionExpression expression,
                                                TypeRegistry<ConditionType<?>> conditionTypes,
                                                IssueCollector issues, @Nullable String origin, String basePath) {
        ConditionTrees.forEachLeaf(expression.root(), basePath, (path, condition) -> {
            if (condition == null || condition.type() == null) {
                issues.error("条件叶缺少条件对象或 type", origin, path);
                return;
            }
            ConditionType<?> definition = conditionTypes.getOrNull(condition.type());
            if (definition == null) {
                issues.error("未注册的条件类型: " + condition.type(), origin, path);
                return;
            }
            validateTypeParams(definition, condition, issues, origin, path);
        });
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

    // 消耗类效果重复检测：同一规则内同一消耗效果类型只允许出现一次（跨候选与顶层合并计数）
    private static void validateConsumptionEffects(List<EffectSlot> slots, IssueCollector issues, @Nullable String origin) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        Map<ResourceLocation, String> firstPath = new HashMap<>();
        for (EffectSlot slot : slots) {
            Effect effect = slot.effect();
            if (ConsumptionDefaults.isConsumption(effect.type())) {
                counts.merge(effect.type(), 1, Integer::sum);
                firstPath.putIfAbsent(effect.type(), slot.path());
            }
        }
        counts.forEach((type, count) -> {
            if (count > 1) {
                issues.error("同一规则内重复声明消耗效果 " + type + " 共 " + count + " 次", origin, firstPath.get(type));
            }
        });
    }

    // 效果槽位：把顶层 effects 与候选结果内 effects 统一成「路径 + 效果」，供所有校验遍历
    private record EffectSlot(String path, Effect effect) {
    }

    // 展开规则声明的全部效果槽位：顶层 effects[i] 在前，候选 outcomes[i].effects[j] 在后
    private static List<EffectSlot> effectSlots(Rule rule) {
        List<EffectSlot> slots = new ArrayList<>(rule.effects().size());
        for (int index = 0; index < rule.effects().size(); index++) {
            slots.add(new EffectSlot(RuleFields.EFFECTS + "[" + index + "]", rule.effects().get(index)));
        }
        for (int candidate = 0; candidate < rule.outcomes().size(); candidate++) {
            OutcomeCandidate outcome = rule.outcomes().get(candidate);
            for (int index = 0; index < outcome.effects().size(); index++) {
                slots.add(new EffectSlot(
                        RuleFields.OUTCOMES + "[" + candidate + "]." + RuleFields.CANDIDATE_EFFECTS + "[" + index + "]",
                        outcome.effects().get(index)));
            }
        }
        return List.copyOf(slots);
    }

    // 候选结果的结构校验：标识非空且唯一、效果列表非空、候选数量不超过效果上限
    private static void validateOutcomes(Rule rule, IssueCollector issues, @Nullable String origin) {
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < rule.outcomes().size(); index++) {
            OutcomeCandidate candidate = rule.outcomes().get(index);
            String path = RuleFields.OUTCOMES + "[" + index + "]";
            if (candidate.id().isEmpty()) {
                issues.error("候选结果缺少 id", origin, path);
            } else if (!ids.add(candidate.id())) {
                issues.error("候选结果 id 重复: " + candidate.id(), origin, path + "." + RuleFields.CANDIDATE_ID);
            }
            if (candidate.effects().isEmpty()) {
                issues.error("候选结果 effects 不能为空", origin, path + "." + RuleFields.CANDIDATE_EFFECTS);
            }
        }
        if (rule.outcomes().size() > ConditionLimits.MAX_EFFECTS) {
            issues.error("候选结果数量超过上限: " + rule.outcomes().size() + " > " + ConditionLimits.MAX_EFFECTS,
                    origin, RuleFields.OUTCOMES);
        }
    }

    // 源匹配项校验：同一项不得同时出现在匹配与排除中，且不得重复
    private static void validateSourceEntries(SourceMatcher source, IssueCollector issues, @Nullable String origin) {
        Set<String> entries = new HashSet<>();
        for (SourceEntry entry : source.entries()) {
            validateSourceReference(entry, issues, origin, SourceMatcher.ITEMS_FIELD);
            if (!entries.add(entry.serialized())) {
                issues.error("source.items 中存在重复项: " + entry.serialized(), origin, SourceMatcher.fieldPath(SourceMatcher.ITEMS_FIELD));
            }
        }
        for (SourceEntry entry : source.exclude()) {
            validateSourceReference(entry, issues, origin, SourceMatcher.EXCLUDE_FIELD);
            if (entries.contains(entry.serialized())) {
                issues.error("source 中同一项既匹配又排除: " + entry.serialized(), origin, SourceMatcher.fieldPath(SourceMatcher.EXCLUDE_FIELD));
            }
        }
    }

    // 静态物品引用必须存在，标签可为空但不能把拼错物品静默装入索引。
    private static void validateSourceReference(SourceEntry entry, IssueCollector issues, String origin, String field) {
        if (!entry.tag() && !BuiltInRegistries.ITEM.containsKey(entry.id())) {
            issues.error("未知源物品: " + entry.id(), origin, SourceMatcher.fieldPath(field));
        }
    }

    // 规则级催化剂固定成本校验：items 必填非空且逐个引用存在（复用效果侧 RefChecks 语义，
    // 非标签引用未注册即 ERROR、标签缺失只 WARN），count / radius 取值域与 consume_catalyst 一致。
    // 说明：这里复用 ParamChecks / RefChecks，问题条目的 origin 为 null（与效果参数校验一致）。
    private static void validateCatalystCost(CatalystCost cost, IssueCollector issues) {
        String itemsPath = ParamChecks.child(RuleFields.CATALYST_COST, RuleFields.CATALYST_ITEMS);
        ParamChecks.notEmpty(cost.items(), RuleFields.CATALYST_ITEMS, issues, itemsPath);
        RefChecks.checkAll(cost.items(), BuiltInRegistries.ITEM, RuleFields.CATALYST_ITEMS, issues, itemsPath);
        for (TaggedId item : cost.counts().keySet()) {
            if (!cost.items().contains(item)) {
                issues.error("数量配置引用了未选择的催化剂: " + item.serialized(), null,
                        ParamChecks.child(RuleFields.CATALYST_COST, RuleFields.ITEM_COUNTS));
            }
        }
        ParamChecks.inRange(cost.count(), CatalystCost.MIN_COUNT, CatalystCost.MAX_COUNT, RuleFields.CATALYST_COUNT,
                issues, ParamChecks.child(RuleFields.CATALYST_COST, RuleFields.CATALYST_COUNT));
        ParamChecks.inRange(cost.radius(), CatalystCost.MIN_RADIUS, CatalystCost.MAX_RADIUS,
                RuleFields.CATALYST_RADIUS, issues, ParamChecks.child(RuleFields.CATALYST_COST, RuleFields.CATALYST_RADIUS));
    }

    // 效果通用字段校验；类型专属参数由注册表感知入口负责
    private static void validateEffect(Effect effect, String path, IssueCollector issues,
                                       @Nullable String origin) {
        if (effect.type() == null) {
            issues.error("效果缺少 type", origin, path);
            return;
        }
        if (effect.delayTicks() < 0) {
            issues.error("delay_ticks 不能为负数: " + effect.delayTicks(), origin, path + "." + RuleFields.DELAY_TICKS);
        }
        if (!Double.isFinite(effect.chance()) || effect.chance() < 0.0D || effect.chance() > 1.0D) {
            issues.error("chance 必须在 [0,1] 内: " + effect.chance(), origin, path + "." + RuleFields.CHANCE);
        }
        ConditionExpression conditions = effect.conditions();
        if (conditions != null && (conditions.leafCount() > ConditionLimits.MAX_LEAVES
                || conditions.nodeCount() > ConditionLimits.MAX_NODES
                || conditions.depth() > ConditionLimits.MAX_DEPTH)) {
            issues.error("效果级条件超出上限：叶<=" + ConditionLimits.MAX_LEAVES + "、节点<=" + ConditionLimits.MAX_NODES
                    + "、深度<=" + ConditionLimits.MAX_DEPTH, origin, path + "." + RuleFields.CONDITIONS);
        }
        if (conditions != null && !conditions.isStructurallyValid()) {
            issues.error("效果级 conditions 结构非法：all_of / any_of 的 terms 不能为空、inverted 必须有 term",
                    origin, path + "." + RuleFields.CONDITIONS);
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
