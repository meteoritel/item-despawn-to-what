package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 催化剂数量联动：消耗开关判定、默认数量表，以及催化剂引用列表变化后的数量表对账与字段改名。
 *
 * <p>数量表固定跟在规则级 {@code catalyst_cost} 下；引用列表以规则级存在叶为唯一来源，
 * 因此本类只读写草稿字段，不判断界面可见性。</p>
 */
final class CatalystQuantities {

    // 工具类，禁止实例化
    private CatalystQuantities() {
    }

    /**
     * 规则是否按「消耗催化剂」呈现：{@code consume_counts} 尚未填满时视为已声明，至少一项为正才算消耗。
     * <p>每轮实际支付的最小单位是 1，表内全为 0 只表示检测、不支付，因此按未勾选呈现；
     * 空表或形态异常的表保持可见，交由用户补齐或由校验提示，而不是静默隐藏。
     *
     * @param cost 规则级 catalyst_cost 对象，可为 null
     * @return 是否按消耗呈现
     */
    static boolean consumesCatalysts(@Nullable RuleValue cost) {
        RuleValue counts = cost == null ? null : cost.child(RuleFields.CONSUME_COUNTS);
        if (counts == null) return false;
        if (!counts.isMapping() || counts.entries().isEmpty()) return true;
        for (RuleValue value : counts.entries().values()) {
            if (value.isNumber() && value.asInteger(0) > 0) return true;
        }
        return false;
    }

    static RuleValue defaultQuantities(@Nullable RuleValue owner, List<String> references) {
        RuleValue.Mapping values = RuleValue.mapping();
        RuleValue count = owner == null ? null : owner.child(RuleFields.CATALYST_COUNT);
        int fallback = count != null && count.isNumber() ? count.asInteger(CatalystCost.DEFAULT_COUNT)
                : CatalystCost.DEFAULT_COUNT;
        references.forEach(reference -> values.put(reference, RuleValue.integer(fallback)));
        return values.build();
    }
    static void retargetCatalystItems(RuleDraft draft) {
        RuleValue cost = draft.valueAt(RuleFields.CATALYST_COST);
        if (cost == null) cost = RuleValue.mapping().build();
        if (cost.isMapping()) {
            List<String> references = PresenceLeaves.ruleLevelCatalystItems(draft.root());
            draft.setAt(RuleFields.CATALYST_COST, RuleValue.mapping(cost)
                    .put(RuleFields.CATALYST_ITEMS, RuleValue.textList(references).build()).build());
            reconcileQuantities(draft, references);
            RuleValue updated = draft.valueAt(RuleFields.CATALYST_COST);
            if (updated != null && updated.has(RuleFields.CONSUME_COUNTS)) {
                draft.setAt(RuleFields.CATALYST_COST, withQuantities(updated, RuleFields.CONSUME_COUNTS,
                        references));
            }
        }
    }

    // 按 ownerPath 写回重建后的计数表；数量字段固定为 item_counts，仅此字段需要与引用列表对账
    static void reconcileQuantities(RuleDraft draft, List<String> references) {
        RuleValue owner = draft.valueAt(RuleFields.CATALYST_COST);
        if (owner == null || !owner.isMapping()) {
            return;
        }
        draft.setAt(RuleFields.CATALYST_COST, withQuantities(owner, RuleFields.ITEM_COUNTS, references));
    }

    // 按 references 顺序重建 field 计数表：已有计数值原样保留，新键取 DEFAULT_COUNT
    static RuleValue withQuantities(RuleValue owner, String field, List<String> references) {
        RuleValue previous = owner.childOr(field, RuleValue.mapping().build());
        RuleValue.Mapping next = RuleValue.mapping();
        for (String reference : references) {
            RuleValue existing = previous.isMapping() ? previous.child(reference) : null;
            next.put(reference, existing != null ? existing.deepCopy()
                    : RuleValue.integer(CatalystCost.DEFAULT_COUNT));
        }
        return RuleValue.mapping(owner).put(field, next.build()).build();
    }
    static List<EditorField> relabelCatalystThreshold(List<EditorField> fields) {
        List<EditorField> relabeled = new ArrayList<>();
        for (EditorField field : fields) {
            relabeled.add(RuleFields.ITEM_COUNTS.equals(field.name()) ? relabelCatalystCountField(field) : field);
        }
        return relabeled;
    }

    static EditorField relabelCatalystCountField(EditorField field) {
        return field.withLabel(RuleEditorPageSupport.UI + "rule.catalyst_min_count");
    }
}