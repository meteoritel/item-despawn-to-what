package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleRequirements;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端保存前校验：把草稿与服务端契约的可本地判定项收敛成问题清单。
 * <p>只读取会话、模型与页面视图，不写数据；本地只拦截可判定项，服务端始终是权威校验方。</p>
 */
final class RuleEditorValidation {

    /*** 宿主能力：校验只通过这些入口读取当前编辑现场。 */
    interface Host {

        // 当前编辑会话（未打开规则时为 null）
        @Nullable EditSession editingSession();

        // 当前编辑的规则 id
        @Nullable String editingId();

        // 规则模型（脏规则与恢复状态）
        RuleEditorModel model();

        // 编辑页组合视图（页面级问题）
        @Nullable RuleEditorEditPages pages();
    }

    private static final String UI = "gui.itemdespawntowhat.edit.";
    private static final String ISSUE = UI + "issue.";
    // 顶层效果数量上限
    private static final int MAX_EFFECTS = 32;
    // 展示名与备注按 Unicode 码点计数的上限（多字节字符不应被 UTF-16 单元数提前算超）
    private static final int MAX_DISPLAY_NAME_CODE_POINTS = 128;
    private static final int MAX_NOTES_CODE_POINTS = 1024;
    // 消耗效果的本地类型前缀；与固定成本同时声明属于重复记账
    private static final String CONSUME_PREFIX = TypeLabels.OWN_NAMESPACE + ":consume_";
    private static final String CONSUME_SOURCE = TypeLabels.OWN_NAMESPACE + ":consume_source";
    private static final String CONSUME_CATALYST = TypeLabels.OWN_NAMESPACE + ":consume_catalyst";

    private final Host host;
    private @Nullable String issueRuleId;

    RuleEditorValidation(Host host) {
        this.host = host;
    }

    // 最近一次阻塞问题所属规则（与当前编辑对象不同时用于跳转）
    @Nullable String issueRuleId() {
        return issueRuleId;
    }

    // 汇总当前编辑对象、其余脏规则与页面视图的问题；页面级问题已由页面自行判定
    List<FormIssue> collectIssues() {
        issueRuleId = host.editingId();
        List<FormIssue> issues = new ArrayList<>();
        EditSession session = host.editingSession();
        if (session != null && !session.draft().isDeleted() && !host.model().isRestoring(host.editingId())) {
            issues.addAll(localIssues(session.draft()));
            issues.addAll(session.pendingInputIssues());
        }
        for (String id : host.model().dirtyRuleIds()) {
            if (id.equals(host.editingId())) {
                continue;
            }
            EditSession other = host.model().session(id);
            if (other != null && !other.draft().isDeleted() && !host.model().isRestoring(id)) {
                List<FormIssue> otherIssues = new ArrayList<>(localIssues(other.draft()));
                otherIssues.addAll(other.pendingInputIssues());
                if (issues.stream().noneMatch(FormIssue::blocking) && otherIssues.stream().anyMatch(FormIssue::blocking)) {
                    issueRuleId = id;
                }
                issues.addAll(otherIssues);
            }
        }
        RuleEditorEditPages pages = host.pages();
        if (pages != null) {
            issues.addAll(pages.issues());
        }
        return issues;
    }

    // 本地保存前拦截
    private List<FormIssue> localIssues(RuleDraft draft) {
        List<FormIssue> issues = new ArrayList<>();
        for (var missing : RuleRequirements.find(draft))
            issues.add(FormIssue.error(missing.path(), Component.translatable(missing.labelKey()), Component.translatable(UI + "issue.required")));
        // 全部脏规则都需检查，避免切换规则或返回管理页后绕过触发方式校验。
        if (RuleTriggers.isExplicitlyEmpty(draft.root())) {
            issues.add(FormIssue.error(RuleFields.TRIGGERS, Component.translatable(UI + "rule.triggers"),
                    Component.translatable(UI + "issue.trigger_empty")));
        }
        RuleValue effects = listOf(draft.root().child(RuleFields.EFFECTS));
        RuleValue outcomes = listOf(draft.root().child(RuleFields.OUTCOMES));
        if (effects.size() == 0 && outcomes.size() == 0) {
            issues.add(issue(RuleFields.EFFECTS, UI + "issue.effects_empty"));
        } else if (effects.size() > MAX_EFFECTS) {
            issues.add(issue(RuleFields.EFFECTS, UI + "issue.effects_limit"));
        }
        // effects 与 outcomes 二选一：同时声明会被服务端拒绝，本地先拦
        if (effects.size() > 0 && outcomes.size() > 0) {
            issues.add(issue(RuleFields.OUTCOMES, UI + "issue.outcomes_conflict"));
        }
        // 规则级条件树
        validateConditionTree(draft, "", RuleFields.CONDITIONS, issues);
        // 效果级条件树：切到别的效果页签时也要能拦住，否则只在服务端报 VALIDATION_FAILED
        for (int i = 0; i < effects.size(); i++) {
            RuleValue element = effects.item(i);
            if (element == null || !element.isMapping()) {
                continue;
            }
            // 条件树路径需要指向 conditions 元素本身
            String path = RuleFields.EFFECTS + "[" + i + "]." + RuleFields.CONDITIONS;
            validateConditionTree(draft, path, path, issues);
        }
        // 候选结果：id 非空且唯一、effects 非空；候选内效果级条件树同样本地校验
        Set<String> candidateIds = new LinkedHashSet<>();
        for (int i = 0; i < outcomes.size(); i++) {
            RuleValue element = outcomes.item(i);
            String candidatePath = RuleFields.OUTCOMES + "[" + i + "]";
            if (element == null || !element.isMapping()) {
                issues.add(issue(candidatePath, UI + "issue.candidate_id_invalid"));
                continue;
            }
            RuleValue idElement = element.child(RuleFields.CANDIDATE_ID);
            if (idElement == null || !idElement.isScalar() || idElement.asText().isBlank()
                    || !candidateIds.add(idElement.asText())) {
                issues.add(issue(candidatePath + "." + RuleFields.CANDIDATE_ID, UI + "issue.candidate_id_invalid"));
            }
            RuleValue candidateEffects = listOf(element.child(RuleFields.CANDIDATE_EFFECTS));
            if (candidateEffects.size() == 0) {
                issues.add(issue(candidatePath + "." + RuleFields.CANDIDATE_EFFECTS, UI + "issue.candidate_effects_empty"));
                continue;
            }
            for (int j = 0; j < candidateEffects.size(); j++) {
                RuleValue effect = candidateEffects.item(j);
                if (effect == null || !effect.isMapping()) {
                    continue;
                }
                String conditionsPath = candidatePath + "." + RuleFields.CANDIDATE_EFFECTS + "[" + j + "]."
                        + RuleFields.CONDITIONS;
                validateConditionTree(draft, conditionsPath, conditionsPath, issues);
            }
        }
        RuleValue name = draft.root().child(RuleFields.DISPLAY_NAME);
        if (name != null && name.isScalar()
                && name.asText().codePointCount(0, name.asText().length()) > MAX_DISPLAY_NAME_CODE_POINTS) {
            issues.add(issue(RuleFields.DISPLAY_NAME, UI + "issue.display_name_too_long"));
        }
        RuleValue notes = draft.root().child(RuleFields.NOTES);
        if (notes != null && notes.isScalar()
                && notes.asText().codePointCount(0, notes.asText().length()) > MAX_NOTES_CODE_POINTS) {
            issues.add(issue(RuleFields.NOTES, UI + "issue.notes_too_long"));
        }
        RuleValue seconds = draft.root().child(RuleFields.TRIGGER_AFTER_SECONDS);
        if (seconds != null && seconds.isNumber() && seconds.asInteger(0) < 0) {
            issues.add(issue(RuleFields.TRIGGER_AFTER_SECONDS, UI + "issue.trigger_negative"));
        }
        // 固定成本按引用分别校验；数量必须为正整数，引用必须来自已选物品。
        RuleValue sourceCost = draft.root().child(RuleFields.SOURCE_COST);
        RuleValue sourceItems = listOf(draft.valueAt(RuleFields.SOURCE + "." + RuleFields.SOURCE_ITEMS));
        if (sourceCost != null && invalidItemCounts(sourceCost, sourceItems, Integer.MAX_VALUE)) {
            issues.add(issue(RuleFields.SOURCE_COST, UI + "issue.source_cost_not_positive"));
        }
        // catalyst_cost 契约（与服务端 RuleValidation / RuleCodecs 对齐）：必须是对象 {items, counts?, count?, radius?}，
        // 整数写法已废弃；items 不能为空、count / radius 只在显式写出时做区间拦截
        RuleValue catalystCost = draft.root().child(RuleFields.CATALYST_COST);
        boolean hasCatalystCost = catalystCost != null && !catalystCost.isNull();
        if (hasCatalystCost) {
            if (!catalystCost.isMapping()) {
                issues.add(issue(RuleFields.CATALYST_COST, UI + "issue.catalyst_cost_not_object"));
            } else {
                RuleValue counts = catalystCost.child(RuleFields.ITEM_COUNTS);
                if (counts != null && invalidItemCounts(counts, listOf(catalystCost.child(RuleFields.CATALYST_ITEMS)),
                        CatalystCost.MAX_COUNT)) {
                    issues.add(issue(RuleFields.CATALYST_COST + "." + RuleFields.ITEM_COUNTS,
                            UI + "issue.catalyst_cost_count_out_of_range"));
                }
                if (listOf(catalystCost.child(RuleFields.CATALYST_ITEMS)).size() == 0) {
                    issues.add(issue(RuleFields.CATALYST_COST + "." + RuleFields.CATALYST_ITEMS,
                            UI + "issue.catalyst_cost_items_empty"));
                }
                RuleValue countElement = catalystCost.child(RuleFields.CATALYST_COUNT);
                if (isInteger(countElement) && (countElement.asInteger(0) < CatalystCost.MIN_COUNT
                        || countElement.asInteger(0) > CatalystCost.MAX_COUNT)) {
                    issues.add(issue(RuleFields.CATALYST_COST + "." + RuleFields.CATALYST_COUNT,
                            UI + "issue.catalyst_cost_count_out_of_range"));
                }
                RuleValue radiusElement = catalystCost.child(RuleFields.CATALYST_RADIUS);
                if (isInteger(radiusElement) && (radiusElement.asInteger(0) < CatalystCost.MIN_RADIUS
                        || radiusElement.asInteger(0) > CatalystCost.MAX_RADIUS)) {
                    issues.add(issue(RuleFields.CATALYST_COST + "." + RuleFields.CATALYST_RADIUS,
                            UI + "issue.catalyst_cost_radius_out_of_range"));
                }
            }
        }
        // 结构版本：当前只支持 RuleCodecs.DEFAULT_SCHEMA_VERSION
        RuleValue schemaVersion = draft.root().child(RuleFields.SCHEMA_VERSION);
        if (isInteger(schemaVersion) && schemaVersion.asInteger(0) != RuleCodecs.DEFAULT_SCHEMA_VERSION) {
            issues.add(issue(RuleFields.SCHEMA_VERSION, UI + "issue.schema_version_unsupported"));
        }
        // 消耗效果统计覆盖顶层 effects 与候选结果内 effects
        Map<String, Integer> consumption = new LinkedHashMap<>();
        countConsumption(effects, consumption);
        for (RuleValue element : outcomes.items()) {
            if (element.isMapping()) {
                countConsumption(listOf(element.child(RuleFields.CANDIDATE_EFFECTS)), consumption);
            }
        }
        for (Integer count : consumption.values()) {
            if (count != null && count > 1) {
                issues.add(issue(RuleFields.EFFECTS, UI + "issue.duplicate_consumption"));
            }
        }
        // 固定成本与同名消耗效果互斥（重复记账）
        if (sourceCost != null && consumption.containsKey(CONSUME_SOURCE)) {
            issues.add(issue(RuleFields.SOURCE_COST, UI + "issue.double_bookkeeping"));
        }
        if (hasCatalystCost && consumption.containsKey(CONSUME_CATALYST)) {
            issues.add(issue(RuleFields.CATALYST_COST, UI + "issue.double_bookkeeping"));
        }
        return issues;
    }

    private static boolean invalidItemCounts(RuleValue raw, RuleValue items, int maximum) {
        if (!raw.isMapping()) return true;
        for (var entry : raw.entries().entrySet()) {
            RuleValue value = entry.getValue();
            if (!containsText(items, entry.getKey()) || !isInteger(value)) return true;
            double count = value.asDecimal(Double.NaN);
            if (!Double.isFinite(count) || count != Math.floor(count) || count < 1 || count > maximum) return true;
        }
        return false;
    }

    // 列表内是否存在指定文本（成本引用必须来自已选物品）
    private static boolean containsText(RuleValue items, String value) {
        for (RuleValue item : items.items()) {
            if (item.isText() && value.equals(item.asText())) return true;
        }
        return false;
    }

    // 统计一组效果里的消耗类型（同一消耗效果在规则内只允许出现一次）
    private static void countConsumption(RuleValue effects, Map<String, Integer> consumption) {
        for (RuleValue element : effects.items()) {
            if (!element.isMapping()) {
                continue;
            }
            RuleValue typeElement = element.child(RuleFields.TYPE);
            if (typeElement == null || !typeElement.isText()) {
                continue;
            }
            String type = typeElement.asText();
            if (type.startsWith(CONSUME_PREFIX)) {
                consumption.merge(type, 1, Integer::sum);
            }
        }
    }

    // 读取草稿中的数组字段：非数组统一按空数组处理
    private static RuleValue listOf(@Nullable RuleValue raw) {
        return raw != null && raw.isList() ? raw : RuleValue.list().build();
    }

    // 是否为数值：成本与结构版本的本地拦截只处理显式写出的数值
    private static boolean isInteger(@Nullable RuleValue raw) {
        return raw != null && raw.isNumber();
    }

    // 校验某条路径下的条件树（规则级传 ""，效果级传 "effects[i]"），问题路径指向 conditions 字段
    private void validateConditionTree(RuleDraft draft, String draftPath, String issuePath, List<FormIssue> issues) {
        RuleValue raw = draft.conditionsValueAt(draftPath);
        if (raw == null) {
            return;
        }
        ConditionExpression expression = RuleDraft.decodeConditions(raw, ClientTypeRegistries.conditions());
        if (expression == null) {
            // 只有本地可证结构损坏（不是对象 / 缺 op / op 不是字符串）才阻塞；
            // 未注册或第三方类型只提醒：原数据在草稿里原样保留，服务端才是权威校验方
            if (isStructurallyDamaged(raw)) {
                issues.add(issue(issuePath, UI + "issue.conditions_decode"));
            } else {
                issues.add(warning(issuePath, UI + "issue.conditions_unregistered"));
            }
            return;
        }
        if (!expression.isStructurallyValid()) {
            issues.add(issue(issuePath, ISSUE + "incomplete_group"));
        }
        if (expression.leafCount() > ConditionLimits.MAX_LEAVES) {
            issues.add(issue(issuePath, ISSUE + "too_many_leaves"));
        }
        if (expression.nodeCount() > ConditionLimits.MAX_NODES) {
            issues.add(issue(issuePath, ISSUE + "too_many_nodes"));
        }
        if (expression.depth() > ConditionLimits.MAX_DEPTH) {
            issues.add(issue(issuePath, ISSUE + "too_deep", expression.depth(), ConditionLimits.MAX_DEPTH));
        }
    }

    private FormIssue issue(String path, String messageKey) {
        return issue(path, messageKey, new Object[0]);
    }

    // 带占位符参数的校验项（阻塞保存）
    private FormIssue issue(String path, String messageKey, Object... args) {
        Component message = Component.translatable(messageKey, args);
        return FormIssue.error(path, message, message);
    }

    // 带占位符参数的提醒项（不阻塞保存）
    private FormIssue warning(String path, String messageKey, Object... args) {
        Component message = Component.translatable(messageKey, args);
        return FormIssue.warning(path, message, message);
    }

    // 条件树值是否本地可证损坏：不是对象、缺 op 或 op 不是标量
    private static boolean isStructurallyDamaged(@Nullable RuleValue raw) {
        if (raw == null || raw.isNull() || !raw.isMapping()) {
            return true;
        }
        RuleValue op = raw.child(RuleFields.OP);
        return op == null || !op.isScalar();
    }
}
