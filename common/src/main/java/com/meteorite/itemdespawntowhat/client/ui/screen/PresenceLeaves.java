package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 条件树中的「存在」条件叶：催化剂与流体叶的构造、草稿路径推导、扫描与增删。
 *
 * <p>叶子身份由条件类型决定（催化剂叶的类型字段是物品引用列表，流体叶是单个流体引用）；
 * 扫描结果带规则级 / 动作级作用域与同类叶序号，供页面定位与标题使用。</p>
 */
final class PresenceLeaves {

    // 工具类，禁止实例化
    private PresenceLeaves() {
    }

    record PresenceLeaf(String path, String conditionPath, ResourceLocation type, int ordinal,
                                Component scopeLabel, @Nullable String effectPath, int candidateIndex, int effectIndex) {

        // 催化剂叶的类型字段是物品引用列表，流体叶是单个流体引用
        boolean catalyst() {
            return RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type);
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
    static @Nullable PresenceLeaf selectedCatalystLeaf(RuleDraft draft) {
        for (PresenceLeaf leaf : ruleLevelPresence(draft.root(), RuleEditorPageSupport.CATALYST_PRESENT_ID)) {
            if (!RuleValueReads.stringList(draft.valueAt(leaf.fieldPath())).isEmpty()) return leaf;
        }
        return null;
    }

    static void addRuleLevelPresence(RuleDraft draft, ResourceLocation type) {
        RuleValue leaf = presenceLeafValue(type);
        if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type)) {
            RuleValue cost = draft.valueAt(RuleFields.CATALYST_COST);
            RuleValue condition = RuleValue.mapping(leaf.child(RuleFields.CONDITION))
                    .put(RuleFields.CATALYST_ITEMS, cost == null ? RuleValue.list().build()
                            : cost.childOr(RuleFields.CATALYST_ITEMS, RuleValue.list().build()).deepCopy()).build();
            leaf = RuleValue.mapping(leaf).put(RuleFields.CONDITION, condition).build();
        }
        RuleValue current = draft.valueAt(RuleFields.CONDITIONS);
        if (current == null || current.isNull()) {
            draft.setAt(RuleFields.CONDITIONS, leaf);
            return;
        }
        if (current.isMapping() && RuleFields.OP_ALL_OF.equals(RuleValueReads.stringField(current, RuleFields.OP))) {
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
        String op = RuleValueReads.stringField(node, RuleFields.OP);
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
        if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type)) {
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
                Component.translatable(RuleEditorPageSupport.UI + "rule.presence.scope_rule"), null, -1, -1, leaves, counters);
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
                        Component.translatable(RuleEditorPageSupport.UI + "rule.presence.scope_action", index + 1), effectPath, -1, index,
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
                            Component.translatable(RuleEditorPageSupport.UI + "rule.presence.scope_effect", candidateIndex + 1, effectIndex + 1),
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
        if (RuleFields.OP_LEAF.equals(RuleValueReads.stringField(node, RuleFields.OP))) {
            RuleValue condition = node.child(RuleFields.CONDITION);
            if (condition == null || !condition.isMapping()) {
                return;
            }
            ResourceLocation type = ResourceLocation.tryParse(RuleValueReads.stringField(condition, RuleFields.TYPE));
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
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (PresenceLeaf leaf : ruleLevelPresence(draft.root(), RuleEditorPageSupport.CATALYST_PRESENT_ID)) {
            result.addAll(RuleValueReads.stringList(draft.valueAt(leaf.fieldPath())));
        }
        return List.copyOf(result);
    }

    static boolean isPresenceType(ResourceLocation type) {
        return RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type) || RuleEditorPageSupport.FLUID_PRESENT_ID.equals(type);
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
                || !leaf.type().toString().equals(RuleValueReads.stringField(leafCondition, RuleFields.TYPE))) {
            return false;
        }
        return form.applyToDraft();
    }
    static Component presenceHeader(PresenceLeaf leaf) {
        return Component.translatable(RuleEditorPageSupport.UI + "rule.presence.title", TypeLabels.conditionLabel(leaf.type()),
                leaf.ordinal(), leaf.scopeLabel());
    }
}
