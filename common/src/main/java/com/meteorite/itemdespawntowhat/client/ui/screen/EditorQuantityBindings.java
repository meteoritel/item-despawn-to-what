package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormField;
import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.runtime.CatalystThresholdProjection;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** 数量默认值与存在条件写回规则，供输入卡片和条件参数表共用。 */
final class EditorQuantityBindings {
    private EditorQuantityBindings() {}

    static void configureQuantityFields(EditSession session, FieldPath basePath,
                                         List<FormField> handles) {
        for (var handle : handles) {
            EditorField field = handle.descriptor();
            if (!isQuantityField(field)) continue;
            handle.bindItemCounts(() -> quantityReferences(session, basePath, field),
                    RuleFields.CONSUME_COUNTS.equals(field.name()) ? null
                            : () -> quantityDefaults(session, basePath, quantityFromSource(field)));
        }
    }

    static void configureReferenceWrites(FormView form, FieldPath basePath) {
        FieldPath referencePath = basePath.child(RuleFields.CATALYST_ITEMS);
        form.addFieldWritten((edits, path, before) -> {
            if (!referencePath.equals(path)) return;
            FieldPath countsPath = basePath.child(RuleFields.ITEM_COUNTS);
            RuleValue amounts = edits.valueAt(countsPath);
            if (amounts == null || !amounts.isMapping()) return;
            RuleValue.Mapping selected = RuleValue.mapping();
            for (String id : RuleValue.textItems(edits.valueAt(path))) {
                RuleValue amount = amounts.child(id);
                if (amount != null) selected.put(id, amount.deepCopy());
            }
            edits.setAt(countsPath, selected.build());
        });
        form.addFieldCommitted(path -> {
            if (referencePath.equals(path)) reloadQuantityFields(form);
        });
    }

    static void reloadQuantityFields(FormView form) {
        for (FormField field : form.fields()) if (isQuantityField(field.descriptor())) field.reload();
    }

    private static boolean isQuantityField(EditorField field) {
        return field.isItemCounts()
                && (RuleFields.SOURCE_COST.equals(field.name())
                || RuleFields.ITEM_COUNTS.equals(field.name())
                || RuleFields.CONSUME_COUNTS.equals(field.name()));
    }

    static boolean quantityFromSource(EditorField field) {
        return RuleFields.SOURCE_COST.equals(field.name());
    }

    static List<String> quantityReferences(EditSession session, FieldPath basePath, EditorField field) {
        if (quantityFromSource(field)) {
            return RuleValue.textItems(session.root()
                    .valueAt(FieldPath.key(RuleFields.SOURCE).child(RuleFields.SOURCE_ITEMS)));
        }
        if (FieldPath.key(RuleFields.CATALYST_COST).equals(basePath) && !ownerAt(session, basePath).has(RuleFields.CATALYST_ITEMS))
            return PresenceLeaves.ruleLevelCatalystItems(session.root());
        return RuleValue.textItems(ownerAt(session, basePath).child(RuleFields.CATALYST_ITEMS));
    }

    private static RuleValue ownerAt(EditSession session, FieldPath basePath) {
        RuleValue owner = basePath.isRoot() ? session.root() : session.valueAt(basePath);
        return owner == null || !owner.isMapping() ? RuleValue.mapping().build() : owner;
    }

    static RuleValue quantityDefaults(EditSession session, FieldPath basePath, boolean source) {
        if (FieldPath.key(RuleFields.CATALYST_COST).equals(basePath) && !source) return inputThresholdDefaults(session);
        RuleValue owner = basePath.isRoot() ? session.root() : session.valueAt(basePath);
        if (owner == null || !owner.isMapping()) {
            owner = RuleValue.mapping().build();
        }
        List<String> references = source
                ? RuleValue.textItems(session.valueAt(FieldPath.key(RuleFields.SOURCE).child(RuleFields.SOURCE_ITEMS)))
                : RuleValue.textItems(owner.child(RuleFields.CATALYST_ITEMS));
        RuleValue values = CatalystQuantities.defaultQuantities(owner, references);
        if (!RuleEditorPageSupport.CATALYST_PRESENT_ID.toString().equals(RuleValue.text(owner, RuleFields.TYPE))
                || owner.has(RuleFields.CATALYST_COUNT)) {
            return values;
        }
        Rule rule = session.root()
                .decode(RuleCodecs.codec(ClientTypeRegistries.effects(), ClientTypeRegistries.conditions()));
        if (rule != null) {
            List<TaggedId> items = taggedItems(session, basePath);
            for (TaggedId item : items) {
                int threshold = CatalystThresholdProjection.resolveThreshold(rule, item);
                values = RuleValue.mapping(values).put(item.serialized(), RuleValue.integer(threshold)).build();
            }
        }
        return values;
    }

    private static RuleValue inputThresholdDefaults(EditSession session) {
        RuleValue cost = ownerAt(session, FieldPath.key(RuleFields.CATALYST_COST));
        List<String> references = cost.has(RuleFields.CATALYST_ITEMS)
                ? RuleValue.textItems(cost.child(RuleFields.CATALYST_ITEMS))
                : PresenceLeaves.ruleLevelCatalystItems(session.root());
        RuleValue.Mapping defaults = RuleValue.mapping(CatalystQuantities.defaultQuantities(cost, references));
        if (!cost.has(RuleFields.CATALYST_ITEMS)) {
            for (PresenceLeaves.PresenceLeaf leaf : PresenceLeaves.ruleLevelPresence(
                    session.root(), RuleEditorPageSupport.CATALYST_PRESENT_ID)) {
                RuleValue condition = session.valueAt(leaf.conditionPath());
                if (condition == null) continue;
                RuleValue counts = condition.childOr(RuleFields.ITEM_COUNTS, RuleValue.mapping().build());
                for (String reference : RuleValue.textItems(condition.child(RuleFields.CATALYST_ITEMS))) {
                    RuleValue value = counts.childOr(reference, condition.childOr(RuleFields.CATALYST_COUNT,
                            RuleValue.integer(CatalystCost.DEFAULT_COUNT)));
                    defaults.put(reference, value.deepCopy());
                }
            }
        }
        return defaults.build();
    }

    static void materializeCatalystInput(EditSession session, RuleDraft edits) {
        RuleValue cost = ownerAt(session, FieldPath.key(RuleFields.CATALYST_COST));
        if (cost.has(RuleFields.CATALYST_ITEMS)) return;
        RuleValue defaults = inputThresholdDefaults(session);
        edits.setAt(FieldPath.key(RuleFields.CATALYST_COST), RuleValue.mapping(cost)
                .put(RuleFields.CATALYST_ITEMS, RuleValue.textList(
                        PresenceLeaves.ruleLevelCatalystItems(session.root())).build())
                .put(RuleFields.ITEM_COUNTS, cost.childOr(RuleFields.ITEM_COUNTS, defaults)).build());
        for (PresenceLeaves.PresenceLeaf leaf : PresenceLeaves.ruleLevelPresence(
                session.root(), RuleEditorPageSupport.CATALYST_PRESENT_ID)) {
            RuleValue condition = session.valueAt(leaf.conditionPath());
            if (condition != null) edits.setAt(leaf.conditionPath(), RuleValue.mapping(condition)
                    .remove(RuleFields.ITEM_COUNTS).remove(RuleFields.CATALYST_COUNT).build());
        }
    }

    static void configurePresenceWrites(FormView form, EditSession session, PresenceLeaves.PresenceLeaf leaf) {
        form.addFieldWritten((draft, path, before) -> {
            if (!leaf.fieldPath().equals(path)) return;
            if (leaf.catalyst()) {
                materializeCatalystInput(session, draft);
                RuleValue condition = draft.valueAt(leaf.conditionPath());
                if (condition != null) draft.setAt(leaf.conditionPath(), RuleValue.mapping(condition)
                        .remove(RuleFields.ITEM_COUNTS).remove(RuleFields.CATALYST_COUNT).build());
                CatalystQuantities.retargetCatalystItems(draft);
            }
        });
    }

    static @Nullable PresenceLeaves.PresenceLeaf findPresenceLeaf(EditSession session, FieldPath conditionPath) {
        for (PresenceLeaves.PresenceLeaf leaf : PresenceLeaves.scanPresenceLeaves(session.root())) {
            if (leaf.conditionPath().equals(conditionPath)) {
                return leaf;
            }
        }
        return null;
    }

    static List<TaggedId> taggedItems(EditSession session, FieldPath conditionPath) {
        List<TaggedId> items = new ArrayList<>();
        for (String reference : RuleValue.textItems(session.valueAt(
                conditionPath.child(RuleFields.CATALYST_ITEMS)))) {
            TaggedId item = TaggedId.tryParse(reference.trim());
            if (item != null) items.add(item);
        }
        return items;
    }
}
