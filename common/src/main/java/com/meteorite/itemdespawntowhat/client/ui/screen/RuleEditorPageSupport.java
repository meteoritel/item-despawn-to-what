package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeSummary;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiListView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiWidget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.Effect;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.FluidPresentCondition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑页共享支持：四个页面共用的布局常量、描述符 id、存在条件数据操作与纯函数工具。
 *
 * <p>本类不持有界面状态：需要草稿、会话或规则视图的参数一律由调用方传入，
 * 因此四个页面类与 {@link RuleEditorEditPages} 都可直接复用。</p>
 */
final class RuleEditorPageSupport {

    // 工具类，禁止实例化
    private RuleEditorPageSupport() {
    }


    static final int PAD = 4;
    static final int TREE_OVERLAY_MARGIN = 8;
    static final int ROW_H = 20;
    static final int BUTTON_H = 20;
    static final int BUTTON_GAP = 4;
    static final int CARD_PAD = 8;
    static final int CARD_GAP = 8;
    static final int CARD_HEADER_H = 20;
    static final int LINE_H = 10;

    static final String UI = TypeLabels.UI_PREFIX;

    static final ResourceLocation INFO_PAGE = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "rule_info_page");
    static final ResourceLocation DELAY_PAGE = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "rule_delay_page");
    static final ResourceLocation CONDITIONS_PAGE = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "conditions_page");
    static final ResourceLocation CONSUME_SOURCE_ID = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "consume_source");
    static final ResourceLocation CONSUME_CATALYST_ID = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "consume_catalyst");
    static final ResourceLocation CONSUME_FLUID_ID = ResourceLocation.fromNamespaceAndPath(TypeLabels.OWN_NAMESPACE, "consume_fluid");

    static final ResourceLocation CATALYST_PRESENT_ID = CatalystPresentCondition.ID;
    static final ResourceLocation FLUID_PRESENT_ID = FluidPresentCondition.ID;

    record PresenceLeaf(String path, String conditionPath, ResourceLocation type, int ordinal,
                                Component scopeLabel, @Nullable String effectPath, int candidateIndex, int effectIndex) {

        // 催化剂叶的类型字段是物品引用列表，流体叶是单个流体引用
        boolean catalyst() {
            return CATALYST_PRESENT_ID.equals(type);
        }

        // 类型字段名
        String fieldName() {
            return catalyst() ? RuleFields.CATALYST_ITEMS : RuleFields.FLUIDS;
        }

        // 类型字段的草稿路径
        String fieldPath() {
            return DraftPaths.child(conditionPath, fieldName());
        }

        // 规则级条件叶（非动作局部）
        boolean ruleLevel() {
            return effectPath == null;
        }
    }

    static RuleValue defaultQuantities(@Nullable RuleValue owner, List<String> references) {
        RuleValue.Mapping values = RuleValue.mapping();
        RuleValue count = owner == null ? null : owner.child(RuleFields.CATALYST_COUNT);
        int fallback = count != null && count.isNumber() ? count.asInteger(CatalystCost.DEFAULT_COUNT)
                : CatalystCost.DEFAULT_COUNT;
        references.forEach(reference -> values.put(reference, RuleValue.integer(fallback)));
        return values.build();
    }

    static TypeEditorDescriptor ownFieldsOnly(TypeEditorDescriptor descriptor) {
        List<EditorField> fields = new ArrayList<>();
        for (EditorField field : descriptor.fields()) {
            if (RuleFields.DELAY_TICKS.equals(field.name()) || RuleFields.CHANCE.equals(field.name()) || RuleFields.CONDITIONS.equals(field.name())) {
                continue;
            }
            fields.add(field);
        }
        return TypeEditorDescriptor.of(descriptor.id(), descriptor.label(), fields);
    }

    static @Nullable PresenceLeaf selectedCatalystLeaf(RuleDraft draft) {
        for (PresenceLeaf leaf : ruleLevelPresence(draft.root(), CATALYST_PRESENT_ID)) {
            if (!stringList(draft.valueAt(leaf.fieldPath())).isEmpty()) return leaf;
        }
        return null;
    }

    static void removeCatalystConsumption(RuleDraft draft) {
        draft.remove(RuleFields.CATALYST_COST);
        RuleCostBinding.Ref ref;
        while ((ref = RuleCostBinding.consumeCatalyst(draft.root())) != null) {
            draft.removeAt(ref.path());
        }
    }

    static void addRuleLevelPresence(RuleDraft draft, ResourceLocation type) {
        RuleValue leaf = presenceLeafValue(type);
        if (CATALYST_PRESENT_ID.equals(type)) {
            RuleValue condition = RuleValue.mapping(leaf.child(RuleFields.CONDITION))
                    .put(RuleFields.CATALYST_COUNT, RuleValue.integer(CatalystPresentCondition.DEFAULT_COUNT))
                    .build();
            leaf = RuleValue.mapping(leaf).put(RuleFields.CONDITION, condition).build();
        }
        RuleValue current = draft.valueAt(RuleFields.CONDITIONS);
        if (current == null || current.isNull()) {
            draft.setAt(RuleFields.CONDITIONS, leaf);
            return;
        }
        if (current.isMapping() && RuleFields.OP_ALL_OF.equals(stringField(current, RuleFields.OP))) {
            RuleValue terms = current.child(RuleFields.TERMS);
            RuleValue.Sequence array = terms != null && terms.isList() ? RuleValue.list(terms) : RuleValue.list();
            array.add(leaf);
            draft.setAt(RuleFields.CONDITIONS,
                    RuleValue.mapping(current).put(RuleFields.TERMS, array.build()).build());
            return;
        }
        // 保留旧根整体语义：包一层 all_of，而不是把叶插进当前选中的节点
        RuleValue group = RuleValue.mapping()
                .put(RuleFields.OP, RuleValue.text(RuleFields.OP_ALL_OF))
                .put(RuleFields.TERMS, RuleValue.list().add(current.deepCopy()).add(leaf).build())
                .build();
        draft.setAt(RuleFields.CONDITIONS, group);
    }

    static void removeRuleLevelPresence(RuleDraft draft, ResourceLocation type) {
        while (true) {
            PresenceLeaf target = null;
            for (PresenceLeaf leaf : ruleLevelPresence(draft.root(), type)) {
                target = leaf;
                break;
            }
            if (target == null) {
                return;
            }
            int remaining = ruleLevelPresence(draft.root(), type).size();
            removeLeafAt(draft, target.path());
            if (ruleLevelPresence(draft.root(), type).size() >= remaining) {
                // 删除没有生效时停手，避免死循环
                return;
            }
        }
    }

    static void removeLeafAt(RuleDraft draft, String path) {
        String current = path;
        while (current != null) {
            draft.removeAt(current);
            String parent = parentPath(current);
            if (parent == null) {
                return;
            }
            RuleValue parentNode = draft.valueAt(parent);
            if (parentNode == null || !parentNode.isMapping() || !isEmptyGroup(parentNode)) {
                return;
            }
            current = parent;
        }
    }

    static @Nullable String parentPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? null : path.substring(0, dot);
    }

    static boolean isEmptyGroup(RuleValue node) {
        String op = stringField(node, RuleFields.OP);
        if (RuleFields.OP_INVERTED.equals(op)) {
            RuleValue term = node.child(RuleFields.TERM);
            return term == null || term.isNull();
        }
        if (RuleFields.OP_ALL_OF.equals(op) || RuleFields.OP_ANY_OF.equals(op)) {
            RuleValue terms = node.child(RuleFields.TERMS);
            return terms == null || !terms.isList() || terms.items().isEmpty();
        }
        return false;
    }

    static RuleValue presenceLeafValue(ResourceLocation type) {
        RuleValue.Mapping condition = RuleValue.mapping().put(RuleFields.TYPE, RuleValue.text(type.toString()));
        if (CATALYST_PRESENT_ID.equals(type)) {
            condition.put(RuleFields.CATALYST_ITEMS, RuleValue.list().build());
        }
        return RuleValue.mapping()
                .put(RuleFields.OP, RuleValue.text(RuleFields.OP_LEAF))
                .put(RuleFields.CONDITION, condition.build())
                .build();
    }

    static @Nullable ConditionNode.Leaf presenceLeafNode(ResourceLocation type) {
        ConditionExpression expression = RuleDraft.decodeConditions(presenceLeafValue(type),
                ClientTypeRegistries.conditions());
        return expression != null && expression.root() instanceof ConditionNode.Leaf leaf ? leaf : null;
    }

    static List<PresenceLeaf> scanPresenceLeaves(@Nullable RuleValue view) {
        List<PresenceLeaf> leaves = new ArrayList<>();
        if (view == null) {
            return leaves;
        }
        Map<String, Integer> counters = new HashMap<>();
        collectPresence(view.child(RuleFields.CONDITIONS), RuleFields.CONDITIONS,
                Component.translatable(UI + "rule.presence.scope_rule"), null, -1, -1, leaves, counters);
        RuleValue effects = view.child(RuleFields.EFFECTS);
        if (effects != null && effects.isList()) {
            List<RuleValue> array = effects.items();
            for (int index = 0; index < array.size(); index++) {
                if (!array.get(index).isMapping()) {
                    continue;
                }
                String effectPath = DraftPaths.indexPath(RuleFields.EFFECTS, index);
                collectPresence(array.get(index).child(RuleFields.CONDITIONS),
                        DraftPaths.child(effectPath, RuleFields.CONDITIONS),
                        Component.translatable(UI + "rule.presence.scope_action", index + 1), effectPath, -1, index,
                        leaves, counters);
            }
        }
        RuleValue outcomes = view.child(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isList()) {
            List<RuleValue> candidates = outcomes.items();
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                if (!candidates.get(candidateIndex).isMapping()) {
                    continue;
                }
                RuleValue candidateEffects = candidates.get(candidateIndex).child(RuleFields.EFFECTS);
                if (candidateEffects == null || !candidateEffects.isList()) {
                    continue;
                }
                List<RuleValue> inner = candidateEffects.items();
                for (int effectIndex = 0; effectIndex < inner.size(); effectIndex++) {
                    if (!inner.get(effectIndex).isMapping()) {
                        continue;
                    }
                    String effectPath = DraftPaths.indexPath(DraftPaths.child(
                            DraftPaths.indexPath(RuleFields.OUTCOMES, candidateIndex), RuleFields.EFFECTS), effectIndex);
                    collectPresence(inner.get(effectIndex).child(RuleFields.CONDITIONS),
                            DraftPaths.child(effectPath, RuleFields.CONDITIONS),
                            Component.translatable(UI + "rule.presence.scope_effect", candidateIndex + 1, effectIndex + 1),
                            effectPath, candidateIndex, effectIndex, leaves, counters);
                }
            }
        }
        return leaves;
    }

    static void collectPresence(@Nullable RuleValue node, String path, Component scope,
                                        @Nullable String effectPath, int candidateIndex, int effectIndex,
                                        List<PresenceLeaf> out, Map<String, Integer> counters) {
        if (node == null || !node.isMapping()) {
            return;
        }
        if (RuleFields.OP_LEAF.equals(stringField(node, RuleFields.OP))) {
            RuleValue condition = node.child(RuleFields.CONDITION);
            if (condition == null || !condition.isMapping()) {
                return;
            }
            ResourceLocation type = ResourceLocation.tryParse(stringField(condition, RuleFields.TYPE));
            if (type == null || !isPresenceType(type)) {
                return;
            }
            int ordinal = counters.merge(type.toString(), 1, Integer::sum);
            out.add(new PresenceLeaf(path, DraftPaths.child(path, RuleFields.CONDITION), type, ordinal, scope,
                    effectPath, candidateIndex, effectIndex));
            return;
        }
        RuleValue terms = node.child(RuleFields.TERMS);
        if (terms != null && terms.isList()) {
            List<RuleValue> array = terms.items();
            for (int index = 0; index < array.size(); index++) {
                collectPresence(array.get(index), DraftPaths.indexPath(DraftPaths.child(path, RuleFields.TERMS), index),
                        scope, effectPath, candidateIndex, effectIndex, out, counters);
            }
        }
        RuleValue term = node.child(RuleFields.TERM);
        if (term != null && !term.isNull()) {
            collectPresence(term, DraftPaths.child(path, RuleFields.TERM), scope, effectPath, candidateIndex,
                    effectIndex, out, counters);
        }
    }

    static List<PresenceLeaf> ruleLevelPresence(@Nullable RuleValue view, ResourceLocation type) {
        List<PresenceLeaf> result = new ArrayList<>();
        for (PresenceLeaf leaf : scanPresenceLeaves(view)) {
            if (leaf.ruleLevel() && leaf.type().equals(type)) {
                result.add(leaf);
            }
        }
        return result;
    }

    static boolean hasRuleLevelPresence(@Nullable RuleValue view, ResourceLocation type) {
        return !ruleLevelPresence(view, type).isEmpty();
    }

    static List<String> ruleLevelCatalystItems(RuleDraft draft) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (PresenceLeaf leaf : ruleLevelPresence(draft.root(), CATALYST_PRESENT_ID)) {
            result.addAll(stringList(draft.valueAt(leaf.fieldPath())));
        }
        return List.copyOf(result);
    }

    static boolean isPresenceType(ResourceLocation type) {
        return CATALYST_PRESENT_ID.equals(type) || FLUID_PRESENT_ID.equals(type);
    }

    static boolean flushPresenceType(EditSession session, PresenceLeaf leaf, FormView form) {
        RuleDraft draft = session.draft();
        // 叶可能已被条件树删除或改类型：路径不再指向同类叶时不写回，避免复活已删除的条件
        RuleValue leafNode = draft.valueAt(leaf.path());
        if (leafNode == null || !leafNode.isMapping()) {
            return false;
        }
        RuleValue leafCondition = leafNode.child(RuleFields.CONDITION);
        if (leafCondition == null || !leafCondition.isMapping()
                || !leaf.type().toString().equals(stringField(leafCondition, RuleFields.TYPE))) {
            return false;
        }
        return form.applyToDraft();
    }

    static void retargetFluidActions(RuleDraft draft, String before, String after) {
        if (after.isEmpty()) {
            // 类型被清空属于「任意流体」的中间态，不连带清空移除动作
            return;
        }
        retargetFluidAction(draft, RuleFields.EFFECTS, before, after);
        RuleValue outcomes = draft.valueAt(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isList()) {
            for (int index = 0; index < outcomes.size(); index++) {
                RuleValue candidate = outcomes.item(index);
                if (candidate != null && candidate.isMapping()) {
                    retargetFluidAction(draft, DraftPaths.child(DraftPaths.indexPath(RuleFields.OUTCOMES, index),
                            RuleFields.EFFECTS), before, after);
                }
            }
        }
    }

    static void retargetFluidAction(RuleDraft draft, String listPath, String before, String after) {
        RuleValue list = draft.valueAt(listPath);
        if (list == null || !list.isList()) {
            return;
        }
        RuleValue.Sequence next = RuleValue.list();
        boolean changed = false;
        for (RuleValue entry : list.items()) {
            if (!entry.isMapping() || !CONSUME_FLUID_ID.toString().equals(stringField(entry, RuleFields.TYPE))) {
                next.add(entry);
                continue;
            }
            String existing = primitiveString(entry.child(RuleFields.FLUID));
            if (!existing.isEmpty() && !existing.equals(before)) {
                next.add(entry);
                continue;
            }
            next.add(RuleValue.mapping(entry).put(RuleFields.FLUID, RuleValue.text(after)).build());
            changed = true;
        }
        if (changed) {
            draft.setAt(listPath, next.build());
        }
    }

    static String primitiveString(@Nullable RuleValue element) {
        return element != null && element.isScalar() ? element.asText() : "";
    }

    static void retargetCatalystItems(RuleDraft draft, List<String> before, List<String> after) {
        RuleValue cost = draft.valueAt(RuleFields.CATALYST_COST);
        if (cost != null && cost.isMapping()) {
            List<String> references = ruleLevelCatalystItems(draft);
            draft.setAt(RuleFields.CATALYST_COST, RuleValue.mapping(cost)
                    .put(RuleFields.CATALYST_ITEMS, RuleValue.textList(references).build()).build());
            reconcileQuantities(draft, RuleFields.CATALYST_COST, references, CatalystCost.DEFAULT_COUNT);
        }
        retargetConsumptionList(draft, RuleFields.EFFECTS, before, after);
        RuleValue outcomes = draft.valueAt(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isList()) {
            for (int index = 0; index < outcomes.size(); index++) {
                RuleValue candidate = outcomes.item(index);
                if (candidate == null || !candidate.isMapping()) {
                    continue;
                }
                retargetConsumptionList(draft, DraftPaths.child(DraftPaths.indexPath(RuleFields.OUTCOMES, index),
                        RuleFields.EFFECTS), before, after);
            }
        }
    }

    static void retargetConsumptionList(RuleDraft draft, String listPath, List<String> before, List<String> after) {
        RuleValue list = draft.valueAt(listPath);
        if (list == null || !list.isList()) {
            return;
        }
        RuleValue.Sequence next = RuleValue.list();
        boolean changed = false;
        for (RuleValue entry : list.items()) {
            RuleValue updated = retargetConsumptionEntry(entry, before, after);
            changed |= updated != entry;
            next.add(updated);
        }
        if (changed) {
            draft.setAt(listPath, next.build());
        }
    }

    // 重定向单个消耗动作的催化剂引用；无需改动时原样返回
    private static RuleValue retargetConsumptionEntry(RuleValue element, List<String> before, List<String> after) {
        if (element == null || !element.isMapping()
                || !RuleCostBinding.CONSUME_CATALYST.equals(stringField(element, RuleFields.TYPE))) {
            return element;
        }
        List<String> existing = stringList(element.child(RuleFields.CATALYST_ITEMS));
        if (!existing.isEmpty() && referencesDiffer(existing, before)) {
            return element;
        }
        RuleValue updated = RuleValue.mapping(element)
                .put(RuleFields.CATALYST_ITEMS, RuleValue.textList(after).build())
                .build();
        return withQuantities(updated, RuleFields.ITEM_COUNTS, after, countField(element));
    }

    // 按 ownerPath 写回重建后的计数表；数量字段固定为 item_counts，仅此字段需要与引用列表对账
    static void reconcileQuantities(RuleDraft draft, String ownerPath, List<String> references, int fallback) {
        RuleValue owner = draft.valueAt(ownerPath);
        if (owner == null || !owner.isMapping()) {
            return;
        }
        draft.setAt(ownerPath, withQuantities(owner, RuleFields.ITEM_COUNTS, references, fallback));
    }

    // 按 references 顺序重建 field 计数表：已有计数值原样保留，新键取 max(DEFAULT_COUNT, fallback)
    static RuleValue withQuantities(RuleValue owner, String field, List<String> references, int fallback) {
        RuleValue previous = owner.childOr(field, RuleValue.mapping().build());
        RuleValue.Mapping next = RuleValue.mapping();
        for (String reference : references) {
            RuleValue existing = previous.isMapping() ? previous.child(reference) : null;
            next.put(reference, existing != null ? existing.deepCopy()
                    : RuleValue.integer(Math.max(CatalystCost.DEFAULT_COUNT, fallback)));
        }
        return RuleValue.mapping(owner).put(field, next.build()).build();
    }

    static int countField(@Nullable RuleValue owner) {
        RuleValue value = owner == null ? null : owner.child(RuleFields.CATALYST_COUNT);
        return value != null && value.isNumber() ? value.asInteger(CatalystCost.DEFAULT_COUNT)
                : CatalystCost.DEFAULT_COUNT;
    }

    static boolean referencesDiffer(List<String> left, List<String> right) {
        return left.isEmpty() || right.isEmpty() || !new HashSet<>(left).equals(new HashSet<>(right));
    }

    static Component presenceHeader(PresenceLeaf leaf) {
        return Component.translatable(UI + "rule.presence.title", TypeLabels.conditionLabel(leaf.type()),
                leaf.ordinal(), leaf.scopeLabel());
    }

    static List<EditorField> relabelCatalystThreshold(List<EditorField> fields) {
        List<EditorField> relabeled = new ArrayList<>();
        for (EditorField field : fields) {
            relabeled.add(RuleFields.ITEM_COUNTS.equals(field.name()) ? relabelCatalystCountField(field) : field);
        }
        return relabeled;
    }

    static EditorField relabelCatalystCountField(EditorField field) {
        return new EditorField(field.name(), UI + "rule.catalyst_min_count", field.type(), field.domain(),
                field.nullable(), field.required(), field.registry(), field.enumGroup(), field.hintKey(),
                field.subFields(), field.presets(), field.numbers(), field.displayPrecision());
    }

    static String scopePathOf(String conditionPath) {
        int marker = conditionPath.indexOf("." + RuleFields.CONDITIONS);
        return marker < 0 ? RuleFields.CATALYST_COST : conditionPath.substring(0, marker);
    }

    static @Nullable Effect scopeEffectOf(Rule rule, String conditionPath) {
        String scope = scopePathOf(conditionPath);
        if (RuleFields.CATALYST_COST.equals(scope)) {
            return null;
        }
        int effect = indexIn(scope, RuleFields.EFFECTS);
        if (effect < 0) {
            return null;
        }
        int candidate = indexIn(scope, RuleFields.OUTCOMES);
        if (candidate < 0) {
            return effect < rule.effects().size() ? rule.effects().get(effect) : null;
        }
        if (candidate >= rule.outcomes().size()) {
            return null;
        }
        List<Effect> effects = rule.outcomes().get(candidate).effects();
        return effect < effects.size() ? effects.get(effect) : null;
    }

    static List<String> stringList(@Nullable RuleValue element) {
        List<String> values = new ArrayList<>();
        if (element == null || !element.isList()) {
            return values;
        }
        for (RuleValue entry : element.items()) {
            if (entry.isScalar()) {
                values.add(entry.asText());
            }
        }
        return values;
    }

    static String stringField(@Nullable RuleValue object, String name) {
        RuleValue value = object == null ? null : object.child(name);
        return value != null && value.isScalar() ? value.asText() : "";
    }

    static RuleValue conditionOwnerShape(RuleValue rule) {
        RuleValue.Mapping shape = RuleValue.mapping()
                .put(RuleFields.EFFECTS, effectTypeSequence(rule == null ? null : rule.child(RuleFields.EFFECTS)));
        RuleValue.Sequence candidates = RuleValue.list();
        RuleValue outcomes = rule == null ? null : rule.child(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isList()) {
            for (RuleValue raw : outcomes.items()) {
                RuleValue.Mapping candidate = RuleValue.mapping();
                if (raw != null && raw.isMapping()) {
                    candidate.put(RuleFields.CANDIDATE_ID, raw.childOr(RuleFields.CANDIDATE_ID, RuleValue.nil()));
                    candidate.put(RuleFields.EFFECTS, effectTypeSequence(raw.child(RuleFields.EFFECTS)));
                }
                candidates.add(candidate.build());
            }
        }
        return shape.put(RuleFields.OUTCOMES, candidates.build()).build();
    }

    static RuleValue effectTypeSequence(@Nullable RuleValue raw) {
        RuleValue.Sequence types = RuleValue.list();
        if (raw != null && raw.isList()) {
            for (RuleValue effect : raw.items()) {
                types.add(effect != null && effect.isMapping()
                        ? effect.childOr(RuleFields.TYPE, RuleValue.nil()) : RuleValue.nil());
            }
        }
        return types.build();
    }

    static @Nullable UiConditionTreeSummary findTree(FormView owner, ConditionNode.Leaf leaf) {
        for (UiFocusTarget target : owner.focusTargets()) {
            if (target instanceof UiConditionTreeSummary tree && tree.selectedNode() == leaf) {
                return tree;
            }
        }
        for (UiFocusTarget target : owner.focusTargets()) {
            if (target instanceof UiConditionTreeSummary tree) {
                return tree;
            }
        }
        return null;
    }

    static String combinationOf(@Nullable RuleValue rule) {
        if (rule == null) {
            return CombinationMode.ROUND_ROBIN.key();
        }
        RuleValue element = rule.child(RuleFields.COMBINATION);
        return element != null && element.isText() ? element.asText() : CombinationMode.ROUND_ROBIN.key();
    }

    static boolean containsAction(@Nullable RuleValue actions, String type, @Nullable String variant) {
        if (actions == null || !actions.isList()) return false;
        for (RuleValue entry : actions.items()) {
            if (!entry.isMapping()) continue;
            if (!(TypeLabels.OWN_NAMESPACE + ":" + type).equals(RuleCostBinding.typeOf(entry))) continue;
            RuleValue actual = entry.child("variant");
            if (variant == null || actual != null && variant.equals(actual.asText())) return true;
        }
        return false;
    }

    static boolean isCommonActionField(EditorField field) {
        return List.of("chance", "delay_ticks", "conditions").contains(field.name());
    }

    static boolean isConsumptionAction(ResourceLocation type) {
        return CONSUME_SOURCE_ID.equals(type) || CONSUME_CATALYST_ID.equals(type) || CONSUME_FLUID_ID.equals(type);
    }

    static boolean isInputOwnedConsumptionField(ResourceLocation type, EditorField field) {
        String name = field.name();
        if (CONSUME_SOURCE_ID.equals(type)) {
            return RuleFields.CATALYST_COUNT.equals(name) || RuleFields.ITEM_COUNTS.equals(name);
        }
        if (CONSUME_CATALYST_ID.equals(type)) {
            return RuleFields.CATALYST_ITEMS.equals(name) || RuleFields.CATALYST_COUNT.equals(name)
                    || RuleFields.ITEM_COUNTS.equals(name) || RuleFields.CATALYST_RADIUS.equals(name);
        }
        if (CONSUME_FLUID_ID.equals(type)) {
            // 流体类型由输入页的存在叶声明；require_source 是移除动作自身的匹配行为，仍可在此编辑
            return RuleFields.FLUID.equals(name);
        }
        return false;
    }

    static void show(@Nullable UiWidget widget, boolean visible) {
        if (widget instanceof UiButton button) button.setVisible(visible);
        else if (widget instanceof UiListView<?> list) list.setVisible(visible);
        else if (widget instanceof FormView form) form.setVisible(visible);
        else if (widget instanceof UiSegmentedControl segments) segments.setVisible(visible);
        else if (widget instanceof UiCheckBox box) box.setVisible(visible);
    }

    static int intOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    static void addPageIssues(@Nullable RuleValue rule, List<FormIssue> issues) {
        int candidates = ResultStructure.candidateCount(rule);
        int effects = ResultStructure.totalEffectCount(rule);
        Component limit = Component.translatable(UI + "issue.candidate_limit", ConditionLimits.MAX_EFFECTS);
        if (candidates > ConditionLimits.MAX_EFFECTS) {
            issues.add(FormIssue.error(RuleFields.OUTCOMES, Component.translatable(UI + "candidate.section"), limit));
        } else if (effects > ConditionLimits.MAX_EFFECTS) {
            issues.add(FormIssue.error(RuleFields.EFFECTS, Component.translatable(UI + "candidate.section"), limit));
        }
        if (RuleEditorPanelFactory.climateAllUnbounded(rule)) {
            Component message = Component.translatable(UI + "issue.climate_empty");
            issues.add(FormIssue.error(RuleFields.CONDITIONS, Component.translatable(UI + "rule.conditions"), message));
        }
        RuleValue combination = rule == null ? null : rule.child(RuleFields.COMBINATION);
        if (combination != null && combination.isText() && !isKnownCombination(combination.asText())) {
            Component message = Component.translatable(UI + "issue.combination_invalid");
            issues.add(FormIssue.error(RuleFields.COMBINATION, Component.translatable(UI + "rule.combination"), message));
        }
    }

    static boolean isKnownCombination(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim();
        return CombinationMode.ROUND_ROBIN.key().equals(normalized) || CombinationMode.PRIORITY.key().equals(normalized);
    }

    static int indexIn(String path, String name) {
        int at = path.indexOf(name + "[");
        if (at < 0) {
            return -1;
        }
        int start = at + name.length() + 1;
        int end = path.indexOf(']', start);
        if (end < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(start, end).trim());
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    static boolean boolOf(@Nullable RuleValue owner, String name, boolean fallback) {
        RuleValue element = owner == null ? null : owner.child(name);
        return element != null && element.isFlag() ? element.asFlag(fallback) : fallback;
    }

    static String stringOf(@Nullable RuleValue owner) {
        RuleValue element = owner == null ? null : owner.child(RuleFields.ID);
        return element != null && element.isText() ? element.asText() : "";
    }
}
