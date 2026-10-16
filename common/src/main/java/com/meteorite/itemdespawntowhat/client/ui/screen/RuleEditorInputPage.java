package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EffectEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.runtime.CatalystThresholdProjection;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 输入页：来源与消耗、催化剂与流体的存在条件、消耗参数与门槛提示。
 */
final class RuleEditorInputPage {

    // 所属编辑页容器：共享状态与跨页能力都经它访问
    private final RuleEditorEditPages pages;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditPages.THEME;

    RuleEditorInputPage(RuleEditorEditPages pages) {
        this.pages = pages;
    }

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
        catalystPresenceToggle = null;
        fluidPresenceToggle = null;
        presenceRows.clear();
        sourceForm = null;
        sourceCostButton = null;
        sourceCostForm = null;
        catalystForm = null;
        costModeRect = null;
        costNoteRect = null;
        costNote = null;
        catalystToggle = null;
        catalystNoteRect = null;
        catalystNote = null;
    }

    @Nullable UiCheckBox catalystPresenceToggle;

    @Nullable UiCheckBox fluidPresenceToggle;

    final List<PresenceRow> presenceRows = new ArrayList<>();

    static final class PresenceRow {
        final RuleEditorPageSupport.PresenceLeaf leaf;
        final FormView form;
        @Nullable UiRect headerRect;

        PresenceRow(RuleEditorPageSupport.PresenceLeaf leaf, FormView form) {
            this.leaf = leaf;
            this.form = form;
        }
    }

    @Nullable FormView sourceForm;

    @Nullable UiButton sourceCostButton;

    @Nullable FormView sourceCostForm;

    @Nullable FormView catalystForm;

    @Nullable UiRect costModeRect;

    @Nullable UiRect costNoteRect;

    @Nullable Component costNote;

    @Nullable UiCheckBox catalystToggle;

    @Nullable UiRect catalystNoteRect;

    @Nullable Component catalystNote;

    void configureQuantityFields(FormView form, EditSession session, String basePath,
                                         TypeEditorDescriptor descriptor) {
        RuleValue owner = basePath.isEmpty() ? session.draft().root() : session.draft().valueAt(basePath);
        if (owner == null || !owner.isMapping()) {
            owner = RuleValue.mapping().build();
        }
        for (EditorField field : descriptor.fields()) {
            if (field.type() != com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.ITEM_COUNTS) continue;
            boolean source = RuleFields.SOURCE_COST.equals(field.name())
                    || RuleEditorPageSupport.CONSUME_SOURCE_ID.toString().equals(RuleEditorPageSupport.stringField(owner, RuleFields.TYPE));
            String path = DraftPaths.child(basePath, field.name());
            List<String> references = source
                    ? RuleEditorPageSupport.stringList(session.draft().valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS)))
                    : RuleEditorPageSupport.stringList(owner.child(RuleFields.CATALYST_ITEMS));
            form.setItemCountReferences(path, references);
            form.setRowDefaultValue(path, () -> quantityDefaults(session, basePath, source));
        }
    }

    RuleValue quantityDefaults(EditSession session, String basePath, boolean source) {
        RuleValue owner = basePath.isEmpty() ? session.draft().root() : session.draft().valueAt(basePath);
        if (owner == null || !owner.isMapping()) {
            owner = RuleValue.mapping().build();
        }
        List<String> references = source
                ? RuleEditorPageSupport.stringList(session.draft().valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS)))
                : RuleEditorPageSupport.stringList(owner.child(RuleFields.CATALYST_ITEMS));
        RuleValue values = RuleEditorPageSupport.defaultQuantities(owner, references);
        if (!RuleEditorPageSupport.CATALYST_PRESENT_ID.toString().equals(RuleEditorPageSupport.stringField(owner, RuleFields.TYPE))
                || owner.has(RuleFields.CATALYST_COUNT)) {
            return values;
        }
        Rule rule = session.draft().root()
                .decode(RuleCodecs.codec(ClientTypeRegistries.effects(), ClientTypeRegistries.conditions()));
        if (rule != null) {
            List<TaggedId> items = taggedItems(basePath);
            for (TaggedId item : items) {
                int threshold = CatalystThresholdProjection.resolveThreshold(rule, items,
                        RuleEditorPageSupport.scopeEffectOf(rule, basePath), item);
                values = RuleValue.mapping(values).put(item.serialized(), RuleValue.integer(threshold)).build();
            }
        }
        return values;
    }

    void refreshQuantityReferences() {
        EditSession session = pages.host.session();
        if (session == null) return;
        RuleValue rule = session.draft().root();
        if (sourceCostForm != null) {
            RuleCostBinding.Ref ref = RuleCostBinding.consumeSource(rule);
            String path = ref == null ? RuleFields.SOURCE_COST
                    : DraftPaths.child(ref.path(), RuleFields.ITEM_COUNTS);
            sourceCostForm.setItemCountReferences(path,
                    RuleEditorPageSupport.stringList(session.draft().valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))));
            sourceCostForm.reload();
        }
        for (PresenceRow row : presenceRows) if (row.leaf.catalyst()) {
            row.form.setItemCountReferences(DraftPaths.child(row.leaf.conditionPath(), RuleFields.ITEM_COUNTS),
                    RuleEditorPageSupport.stringList(session.draft().valueAt(row.leaf.fieldPath())));
            row.form.reload();
        }
        if (catalystForm != null) {
            RuleCostBinding.Ref ref = RuleCostBinding.consumeCatalyst(rule);
            String base = ref == null ? RuleFields.CATALYST_COST : ref.path();
            catalystForm.setItemCountReferences(DraftPaths.child(base, RuleFields.ITEM_COUNTS),
                    RuleEditorPageSupport.stringList(session.draft().valueAt(
                            DraftPaths.child(base, RuleFields.CATALYST_ITEMS))));
            catalystForm.reload();
        }
        pages.host.onControlsChanged();
    }

    void updateBlacklistVisibility() {
        if (sourceForm != null) sourceForm.setRowVisible(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_EXCLUDE),
                !SourceBlacklistPicker.sourceTags(pages.currentView()).isEmpty());
    }

    void buildInput(EditSession session) {
        RuleValue rule = session.draft().root();
        sourceForm = pages.newForm(session, RuleFields.SOURCE, BuiltinEditorDescriptors.sourceDescriptor());
        sourceForm.hideRowLabel(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS));
        sourceForm.setOnFieldWritten((path, before) -> {
            if (!path.equals(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))) return;
            List<String> references = RuleEditorPageSupport.stringList(session.draft().valueAt(path));
            if (session.draft().root().has(RuleFields.SOURCE_COST)) {
                session.draft().replaceWith(RuleEditorPageSupport.withQuantities(session.draft().root(),
                        RuleFields.SOURCE_COST, references, CatalystCost.DEFAULT_COUNT));
            }
            RuleCostBinding.Ref ref = RuleCostBinding.consumeSource(session.draft().root());
            RuleValue effect = ref == null ? null : session.draft().valueAt(ref.path());
            if (effect != null && effect.isMapping()) {
                RuleEditorPageSupport.reconcileQuantities(session.draft(), ref.path(), RuleFields.ITEM_COUNTS, references,
                        RuleEditorPageSupport.countField(effect));
            }
        });
        updateBlacklistVisibility();
        pages.liveForms.add(sourceForm);
        pages.liveWidgets.add(sourceForm);

        RuleCostBinding.Ref sourceRef = RuleCostBinding.consumeSource(rule);
        sourceCostButton = new UiButton(RuleEditorEditPages.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "cost.mode.custom"),
                UiButtonVariant.SECONDARY, this::specifySourceCost);
        if (sourceRef != null) {
            sourceCostButton.setEnabled(false);
            costNote = Component.translatable(RuleEditorPageSupport.UI + "cost.mode.from_effect");
            sourceCostForm = pages.newForm(session, sourceRef.path(), RuleEditorPageSupport.ownFieldsOnly(EffectEditorRegistry.descriptorFor(RuleEditorPageSupport.CONSUME_SOURCE_ID)));
        } else {
            boolean custom = rule.has(RuleFields.SOURCE_COST);
            sourceCostButton.setVisible(!custom);
            sourceCostForm = custom ? pages.newForm(session, "", BuiltinEditorDescriptors.sourceCostDescriptor()) : null;
            if (!custom) {
                boolean consumesOther = RuleCostBinding.consumeCatalyst(rule) != null
                        || RuleCostBinding.find(rule, TypeLabels.OWN_NAMESPACE + ":consume_fluid") != null;
                costNote = Component.translatable(RuleEditorPageSupport.UI + (consumesOther ? "cost.auto.zero" : "cost.auto.one"));
            }
        }
        pages.liveWidgets.add(sourceCostButton);
        if (sourceCostForm != null) {
            pages.liveForms.add(sourceCostForm);
            pages.liveWidgets.add(sourceCostForm);
        }

        buildPresenceSection(session, RuleEditorPageSupport.CATALYST_PRESENT_ID, RuleEditorPageSupport.UI + "rule.presence.add_catalyst");
        RuleCostBinding.Ref catalystRef = RuleCostBinding.consumeCatalyst(rule);
        boolean catalystOn = rule.has(RuleFields.CATALYST_COST);
        boolean catalystEnabled = RuleEditorPageSupport.hasRuleLevelPresence(rule, RuleEditorPageSupport.CATALYST_PRESENT_ID);
        TypeEditorDescriptor catalystDescriptor = BuiltinEditorDescriptors.catalystCostDescriptor();
        // 已有独立消耗规则没有存在叶时，仍保留其物品编辑入口。
        if (!catalystEnabled) {
            List<EditorField> fields = new ArrayList<>();
            fields.add(EditorField.tagList(RuleFields.CATALYST_ITEMS, RuleEditorPageSupport.UI + "rule.catalyst_cost.items",
                    "minecraft:item").asRequired());
            fields.addAll(catalystDescriptor.fields());
            catalystDescriptor = TypeEditorDescriptor.of(catalystDescriptor.id(), catalystDescriptor.label(), fields);
        }
        catalystToggle = new UiCheckBox(RuleEditorEditPages.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "rule.catalyst_consume"), catalystRef != null || catalystOn);
        catalystToggle.setVisible(catalystEnabled || catalystRef != null || catalystOn);
        catalystToggle.setOnChanged(this::onCatalystToggle);
        if (catalystRef != null) {
            catalystNote = Component.translatable(RuleEditorPageSupport.UI + "cost.from_effect_path", Component.literal(catalystRef.path()));
            catalystForm = pages.newForm(session, catalystRef.path(), catalystDescriptor);
        } else {
            catalystForm = catalystOn ? pages.newForm(session, RuleFields.CATALYST_COST, catalystDescriptor) : null;
        }
        pages.liveWidgets.add(catalystToggle);
        if (catalystForm != null) {
            pages.liveForms.add(catalystForm);
            pages.liveWidgets.add(catalystForm);
        }
        buildPresenceSection(session, RuleEditorPageSupport.FLUID_PRESENT_ID, RuleEditorPageSupport.UI + "rule.presence.add_fluid");
    }

    void specifySourceCost() {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> draft.setAt(RuleFields.SOURCE_COST, RuleEditorPageSupport.defaultQuantities(null,
                RuleEditorPageSupport.stringList(draft.valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))))));
    }

    void onCatalystToggle(boolean checked) {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> {
            if (checked) {
                RuleEditorPageSupport.PresenceLeaf leaf = RuleEditorPageSupport.selectedCatalystLeaf(draft);
                if (leaf == null) return;
                List<String> items = RuleEditorPageSupport.ruleLevelCatalystItems(draft);
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (RuleEditorPageSupport.PresenceLeaf entry : RuleEditorPageSupport.ruleLevelPresence(draft.root(), RuleEditorPageSupport.CATALYST_PRESENT_ID)) {
                    RuleValue condition = draft.valueAt(entry.conditionPath());
                    if (condition == null || !condition.isMapping()) {
                        continue;
                    }
                    RuleValue updated = RuleEditorPageSupport.withQuantities(condition, RuleFields.ITEM_COUNTS,
                            RuleEditorPageSupport.stringList(condition.child(RuleFields.CATALYST_ITEMS)), catalystThreshold(entry));
                    draft.setAt(entry.conditionPath(), updated);
                    RuleValue itemCounts = updated.childOr(RuleFields.ITEM_COUNTS, RuleValue.mapping().build());
                    for (var amount : itemCounts.entries().entrySet()) {
                        counts.merge(amount.getKey(), amount.getValue().asInteger(0), Math::max);
                    }
                }
                RuleValue.Mapping countValues = RuleValue.mapping();
                counts.forEach((key, value) -> countValues.put(key, RuleValue.integer(value)));
                RuleValue.Mapping cost = RuleValue.mapping();
                cost.put(RuleFields.CATALYST_ITEMS, RuleValue.textList(items).build());
                cost.put(RuleFields.ITEM_COUNTS, countValues.build());
                draft.setAt(RuleFields.CATALYST_COST, cost.build());
            } else {
                RuleEditorPageSupport.removeCatalystConsumption(draft);
            }
        });
    }

    int catalystThreshold(RuleEditorPageSupport.PresenceLeaf leaf) {
        EditSession session = pages.host.session();
        RuleValue count = session == null ? null
                : session.draft().valueAt(DraftPaths.child(leaf.conditionPath(), RuleFields.CATALYST_COUNT));
        return count != null && count.isNumber() ? count.asInteger(effectiveThreshold(leaf.conditionPath()))
                : effectiveThreshold(leaf.conditionPath());
    }

    void buildPresenceSection(EditSession session, ResourceLocation type, String toggleKey) {
        List<RuleEditorPageSupport.PresenceLeaf> ruleLevel = RuleEditorPageSupport.ruleLevelPresence(session.draft().root(), type);
        UiCheckBox toggle = new UiCheckBox(RuleEditorEditPages.THEME, pages.font, Component.translatable(toggleKey), !ruleLevel.isEmpty());
        toggle.setOnChanged(checked -> onPresenceToggle(type, checked));
        pages.liveWidgets.add(toggle);
        if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type)) {
            catalystPresenceToggle = toggle;
        } else {
            fluidPresenceToggle = toggle;
        }
        for (RuleEditorPageSupport.PresenceLeaf leaf : ruleLevel) {
            FormView form = presenceForm(session, leaf);
            presenceRows.add(new PresenceRow(leaf, form));
            pages.liveForms.add(form);
            pages.liveWidgets.add(form);
        }
    }

    FormView presenceForm(EditSession session, RuleEditorPageSupport.PresenceLeaf leaf) {
        FormView form = pages.newForm(session, leaf.conditionPath(), presenceDescriptor(leaf));
        form.hideRowLabel(leaf.fieldPath());
        configurePresenceWrites(form, session, leaf);
        // 类型改写立即落盘（类型 + 关联消耗配置重指向 = 同一次可撤销操作）；此处不重建页面，
        // 目录选择可连续多次回调，重建会让后续选择写进已卸载的控件
        form.setOnChanged(() -> {
            EditSession current = pages.host.session();
            if (current == null || pages.host.rejectWhenFrozen()) {
                return;
            }
            form.applyToDraft();
            refreshQuantityReferences();
            pages.applyEnabled();
            // 目录选择已在同一次字段写入中提交，仍需通知宿主刷新规则摘要。
            pages.host.onDraftChanged();
        });
        // 选择卡片自带逐项移除；多个同类型叶保留移除整条条件的入口。
        if (RuleEditorPageSupport.ruleLevelPresence(session.draft().root(), leaf.type()).size() > 1) {
            form.setRowAction(leaf.fieldPath(), new UiButton(RuleEditorEditPages.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "rule.presence.remove"),
                    UiButtonVariant.SECONDARY, () -> removePresenceLeaf(leaf)));
        }
        return form;
    }

    void configurePresenceWrites(FormView form, EditSession session, RuleEditorPageSupport.PresenceLeaf leaf) {
        form.setOnFieldWritten((path, before) -> {
            if (!leaf.fieldPath().equals(path)) return;
            if (leaf.catalyst()) {
                RuleEditorPageSupport.reconcileQuantities(session.draft(), leaf.conditionPath(), RuleFields.ITEM_COUNTS,
                        RuleEditorPageSupport.stringList(session.draft().valueAt(
                                DraftPaths.child(leaf.conditionPath(), RuleFields.CATALYST_ITEMS))),
                        catalystThreshold(leaf));
                RuleEditorPageSupport.retargetCatalystItems(session.draft(), RuleEditorPageSupport.stringList(before),
                        RuleEditorPageSupport.stringList(session.draft().valueAt(path)));
            } else {
                var selected = RuleEditorPageSupport.stringList(session.draft().valueAt(path));
                List<String> previous = RuleEditorPageSupport.stringList(before);
                if (selected.size() == 1) RuleEditorPageSupport.retargetFluidActions(session.draft(),
                        previous.size() == 1 ? previous.getFirst() : "", selected.getFirst());
            }
        });
    }

    TypeEditorDescriptor presenceDescriptor(RuleEditorPageSupport.PresenceLeaf leaf) {
        List<EditorField> fields = leaf.catalyst()
                ? List.of(EditorField.tagList(RuleFields.CATALYST_ITEMS, RuleEditorPageSupport.UI + "field.catalyst_present.items", "minecraft:item")
                        .asRequired(), EditorField.itemCounts(RuleFields.ITEM_COUNTS,
                        RuleEditorPageSupport.UI + "rule.catalyst_min_count", CatalystCost.MIN_COUNT, CatalystCost.MAX_COUNT),
                        EditorField.note(RuleEditorPageSupport.UI + "rule.catalyst_all_required"))
                : List.of(EditorField.tagList(RuleFields.FLUIDS, RuleEditorPageSupport.UI + "field.fluid_present.fluid", "minecraft:fluid").optional(),
                        EditorField.note(RuleEditorPageSupport.UI + "field.fluid_present.fluid.hint"));
        return TypeEditorDescriptor.of(leaf.type(), TypeLabels.conditionLabel(leaf.type()), fields);
    }

    void onPresenceToggle(ResourceLocation type, boolean checked) {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> {
            if (checked) {
                RuleEditorPageSupport.addRuleLevelPresence(draft, type);
            } else {
                if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type)) RuleEditorPageSupport.removeCatalystConsumption(draft);
                RuleEditorPageSupport.removeRuleLevelPresence(draft, type);
            }
        });
    }

    void removePresenceLeaf(RuleEditorPageSupport.PresenceLeaf leaf) {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> RuleEditorPageSupport.removeLeafAt(draft, leaf.path()));
    }

    @Nullable RuleEditorPageSupport.PresenceLeaf findPresenceLeaf(String conditionPath) {
        for (RuleEditorPageSupport.PresenceLeaf leaf : RuleEditorPageSupport.scanPresenceLeaves(pages.currentView())) {
            if (leaf.conditionPath().equals(conditionPath)) {
                return leaf;
            }
        }
        return null;
    }

    boolean flushPresenceTypes() {
        EditSession session = pages.host.session();
        if (session == null) {
            return false;
        }
        boolean changed = false;
        for (PresenceRow row : presenceRows) {
            if (row.form.isVisible() && RuleEditorPageSupport.flushPresenceType(session, row.leaf, row.form)) {
                changed = true;
            }
        }
        return changed;
    }

    int layoutPresenceSection(ResourceLocation type, int x, int y, int width) {
        int cursor = y;
        UiCheckBox toggle = RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type) ? catalystPresenceToggle : fluidPresenceToggle;
        if (toggle != null) {
            toggle.setBounds(x, cursor, width, RuleEditorPageSupport.ROW_H);
            cursor += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        }
        for (PresenceRow row : presenceRows) {
            if (!row.leaf.ruleLevel() || !row.leaf.type().equals(type)) {
                continue;
            }
            boolean showHeader = RuleEditorPageSupport.ruleLevelPresence(pages.currentView(), type).size() > 1;
            row.headerRect = showHeader ? new UiRect(x, cursor, width, RuleEditorPageSupport.LINE_H) : null;
            if (showHeader) cursor += RuleEditorPageSupport.LINE_H;
            cursor = pages.layoutForm(row.form, x, cursor, width);
        }
        return cursor;
    }

    int effectiveThreshold(String conditionPath) {
        RuleValue view = pages.currentView();
        if (view == null) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        Rule rule = view.decode(RuleCodecs.codec(ClientTypeRegistries.effects(), ClientTypeRegistries.conditions()));
        if (rule == null) {
            return fallbackThreshold(conditionPath);
        }
        return CatalystThresholdProjection.resolveThreshold(rule, taggedItems(conditionPath),
                RuleEditorPageSupport.scopeEffectOf(rule, conditionPath));
    }

    List<TaggedId> taggedItems(String conditionPath) {
        EditSession session = pages.host.session();
        List<TaggedId> items = new ArrayList<>();
        if (session == null) {
            return items;
        }
        for (String reference : RuleEditorPageSupport.stringList(session.draft().valueAt(
                DraftPaths.child(conditionPath, RuleFields.CATALYST_ITEMS)))) {
            TaggedId.parse(reference).result().ifPresent(items::add);
        }
        return items;
    }

    int fallbackThreshold(String conditionPath) {
        EditSession session = pages.host.session();
        if (session == null) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        RuleDraft draft = session.draft();
        List<String> items = RuleEditorPageSupport.stringList(draft.valueAt(
                DraftPaths.child(conditionPath, RuleFields.CATALYST_ITEMS)));
        String scopePath = RuleEditorPageSupport.scopePathOf(conditionPath);
        RuleValue scope = draft.valueAt(scopePath);
        if (scope == null || !scope.isMapping()) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        boolean consumption = RuleFields.CATALYST_COST.equals(scopePath)
                || RuleCostBinding.CONSUME_CATALYST.equals(RuleEditorPageSupport.stringField(scope, RuleFields.TYPE));
        if (!consumption) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        List<String> scopeItems = RuleEditorPageSupport.stringList(scope.child(RuleFields.CATALYST_ITEMS));
        if (!scopeItems.isEmpty() && !items.isEmpty() && RuleEditorPageSupport.referencesDiffer(scopeItems, items)) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        return Math.max(CatalystPresentCondition.DEFAULT_COUNT,
                intAt(DraftPaths.child(scopePath, RuleFields.CATALYST_COUNT)));
    }

    int intAt(String path) {
        EditSession session = pages.host.session();
        if (session == null) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        RuleValue value = session.draft().valueAt(path);
        if (value == null || !value.isNumber()) {
            return CatalystPresentCondition.DEFAULT_COUNT;
        }
        return value.asInteger(CatalystPresentCondition.DEFAULT_COUNT);
    }

    int layoutInput(UiRect area) {
        int x = area.x() + RuleEditorPageSupport.CARD_PAD;
        int width = Math.max(0, area.width() - RuleEditorPageSupport.CARD_PAD * 2);
        int top = area.y();
        int y = pages.layoutForm(sourceForm, x, top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H, width);
        costModeRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H);
        y += RuleEditorPageSupport.LINE_H + 2;
        if (sourceCostButton != null && sourceCostButton.isVisible()) {
            sourceCostButton.setBounds(x, y, Math.min(width, sourceCostButton.preferredWidth(RuleEditorPageSupport.PAD)), RuleEditorPageSupport.ROW_H);
            y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        }
        if (costNote != null) {
            costNoteRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H);
            y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
        }
        y = pages.layoutForm(sourceCostForm, x, y, width);
        top = pages.finishCard(area.x(), top, area.width(), y, "card.source");
        y = layoutPresenceSection(RuleEditorPageSupport.CATALYST_PRESENT_ID, x, top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H, width);
        if (catalystToggle != null && catalystToggle.isVisible()) {
            catalystToggle.setBounds(x, y, width, RuleEditorPageSupport.ROW_H);
            y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        }
        if (catalystNote != null) {
            catalystNoteRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H);
            y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
        }
        y = pages.layoutForm(catalystForm, x, y, width);
        top = pages.finishCard(area.x(), top, area.width(), y, "card.catalyst");
        y = layoutPresenceSection(RuleEditorPageSupport.FLUID_PRESENT_ID, x, top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H, width);
        return pages.finishCard(area.x(), top, area.width(), y, "card.fluid");
    }

    void renderInput(GuiGraphics graphics) {
        pages.drawText(graphics, costModeRect, Component.translatable(RuleEditorPageSupport.UI + "cost.source_title"), THEME.palette().textSecondary());
        if (costNote != null) {
            pages.drawText(graphics, costNoteRect, costNote, THEME.palette().textDisabled());
        }
        if (catalystNote != null) {
            pages.drawText(graphics, catalystNoteRect, catalystNote, THEME.palette().textDisabled());
        }
        // 存在条件标题行：类型 + 同类型编号 + 作用域，多个同类叶也能逐项辨识
        for (PresenceRow row : presenceRows) {
            pages.drawText(graphics, row.headerRect, RuleEditorPageSupport.presenceHeader(row.leaf), THEME.palette().textSecondary());
        }
    }
}
