package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.ClientTypeRegistries;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 输入页：来源与消耗、催化剂与流体的存在条件、消耗参数与门槛提示。
 */
final class RuleEditorInputSection {

    // 所属编辑页容器：共享状态与跨页能力都经它访问
    private final RuleEditorEditSections pages;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditSections.THEME;

    RuleEditorInputSection(RuleEditorEditSections pages) {
        this.pages = pages;
    }

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
        catalystPresenceToggle = null;
        fluidPresenceToggle = null;
        fluidConsumeToggle = null;
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

    @Nullable UiCheckBox fluidConsumeToggle;

    final List<PresenceRow> presenceRows = new ArrayList<>();

    static final class PresenceRow {
        final PresenceLeaves.PresenceLeaf leaf;
        final FormView form;
        @Nullable UiRect headerRect;

        PresenceRow(PresenceLeaves.PresenceLeaf leaf, FormView form) {
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
        for (EditorField field : descriptor.fields()) {
            if (!isQuantityField(field)) continue;
            String path = DraftPaths.child(basePath, field.name());
            form.setItemCountReferences(path, quantityReferences(session, basePath, field));
            if (!RuleFields.CONSUME_COUNTS.equals(field.name()))
                form.setRowDefaultValue(path, () -> quantityDefaults(session, basePath, quantityFromSource(field)));
        }
    }

    // 只有规则级催化剂/来源成本字段与引用列表联动；通用 MapSpec 由控件沿用草稿自身的键
    private static boolean isQuantityField(EditorField field) {
        return field.isItemCounts()
                && (RuleFields.SOURCE_COST.equals(field.name())
                || RuleFields.ITEM_COUNTS.equals(field.name())
                || RuleFields.CONSUME_COUNTS.equals(field.name()));
    }

    // 物品数量字段的取值来源：来源成本对象或催化剂成本对象。
    boolean quantityFromSource(EditorField field) {
        return RuleFields.SOURCE_COST.equals(field.name());
    }

    // 物品数量字段当前绑定的引用条目：与参数表单装配使用同一份读取，避免出现第二份推导
    List<String> quantityReferences(EditSession session, String basePath, EditorField field) {
        if (quantityFromSource(field)) {
            return RuleValueReads.stringList(session.draft()
                    .valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS)));
        }
        if (RuleFields.CATALYST_COST.equals(basePath) && !ownerAt(session, basePath).has(RuleFields.CATALYST_ITEMS))
            return PresenceLeaves.ruleLevelCatalystItems(session.draft());
        return RuleValueReads.stringList(ownerAt(session, basePath).child(RuleFields.CATALYST_ITEMS));
    }

    // 字段所属对象；缺失或非映射时按空对象处理，读取引用与默认值都只看存在的条目
    private RuleValue ownerAt(EditSession session, String basePath) {
        RuleValue owner = basePath.isEmpty() ? session.draft().root() : session.draft().valueAt(basePath);
        return owner == null || !owner.isMapping() ? RuleValue.mapping().build() : owner;
    }

    RuleValue quantityDefaults(EditSession session, String basePath, boolean source) {
        if (RuleFields.CATALYST_COST.equals(basePath) && !source) return inputThresholdDefaults(session);
        RuleValue owner = basePath.isEmpty() ? session.draft().root() : session.draft().valueAt(basePath);
        if (owner == null || !owner.isMapping()) {
            owner = RuleValue.mapping().build();
        }
        List<String> references = source
                ? RuleValueReads.stringList(session.draft().valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS)))
                : RuleValueReads.stringList(owner.child(RuleFields.CATALYST_ITEMS));
        RuleValue values = CatalystQuantities.defaultQuantities(owner, references);
        if (!RuleEditorPageSupport.CATALYST_PRESENT_ID.toString().equals(RuleValueReads.stringField(owner, RuleFields.TYPE))
                || owner.has(RuleFields.CATALYST_COUNT)) {
            return values;
        }
        Rule rule = session.draft().root()
                .decode(RuleCodecs.codec(ClientTypeRegistries.effects(), ClientTypeRegistries.conditions()));
        if (rule != null) {
            List<TaggedId> items = taggedItems(basePath);
            for (TaggedId item : items) {
                int threshold = CatalystThresholdProjection.resolveThreshold(rule, item);
                values = RuleValue.mapping(values).put(item.serialized(), RuleValue.integer(threshold)).build();
            }
        }
        return values;
    }

    // 尚未声明规则成本的条件规则按原门槛显示；首次编辑输入时将其统一落入规则配置。
    private RuleValue inputThresholdDefaults(EditSession session) {
        RuleValue cost = ownerAt(session, RuleFields.CATALYST_COST);
        List<String> references = cost.has(RuleFields.CATALYST_ITEMS)
                ? RuleValueReads.stringList(cost.child(RuleFields.CATALYST_ITEMS))
                : PresenceLeaves.ruleLevelCatalystItems(session.draft());
        RuleValue.Mapping defaults = RuleValue.mapping(CatalystQuantities.defaultQuantities(cost, references));
        if (!cost.has(RuleFields.CATALYST_ITEMS)) {
            for (PresenceLeaves.PresenceLeaf leaf : PresenceLeaves.ruleLevelPresence(
                    session.draft().root(), RuleEditorPageSupport.CATALYST_PRESENT_ID)) {
                RuleValue condition = session.draft().valueAt(leaf.conditionPath());
                if (condition == null) continue;
                RuleValue counts = condition.childOr(RuleFields.ITEM_COUNTS, RuleValue.mapping().build());
                for (String reference : RuleValueReads.stringList(condition.child(RuleFields.CATALYST_ITEMS))) {
                    RuleValue value = counts.childOr(reference, condition.childOr(RuleFields.CATALYST_COUNT,
                            RuleValue.integer(CatalystCost.DEFAULT_COUNT)));
                    defaults.put(reference, value.deepCopy());
                }
            }
        }
        return defaults.build();
    }

    private void materializeCatalystInput(EditSession session) {
        RuleValue cost = ownerAt(session, RuleFields.CATALYST_COST);
        if (cost.has(RuleFields.CATALYST_ITEMS)) return;
        RuleValue defaults = inputThresholdDefaults(session);
        session.draft().setAt(RuleFields.CATALYST_COST, RuleValue.mapping(cost)
                .put(RuleFields.CATALYST_ITEMS, RuleValue.textList(
                        PresenceLeaves.ruleLevelCatalystItems(session.draft())).build())
                .put(RuleFields.ITEM_COUNTS, cost.childOr(RuleFields.ITEM_COUNTS, defaults)).build());
        for (PresenceLeaves.PresenceLeaf leaf : PresenceLeaves.ruleLevelPresence(
                session.draft().root(), RuleEditorPageSupport.CATALYST_PRESENT_ID)) {
            RuleValue condition = session.draft().valueAt(leaf.conditionPath());
            if (condition != null) session.draft().setAt(leaf.conditionPath(), RuleValue.mapping(condition)
                    .remove(RuleFields.ITEM_COUNTS).remove(RuleFields.CATALYST_COUNT).build());
        }
    }

    void refreshQuantityReferences() {
        EditSession session = pages.shell.editingSession();
        if (session == null) return;
        if (sourceCostForm != null) {
            String path = RuleFields.SOURCE_COST;
            sourceCostForm.setItemCountReferences(path,
                    RuleValueReads.stringList(session.draft().valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))));
            sourceCostForm.reload();
        }
        for (PresenceRow row : presenceRows) if (row.leaf.catalyst()) {
            row.form.setItemCountReferences(DraftPaths.child(row.leaf.conditionPath(), RuleFields.ITEM_COUNTS),
                    RuleValueReads.stringList(session.draft().valueAt(row.leaf.fieldPath())));
            row.form.reload();
        }
        if (catalystForm != null) {
            String base = RuleFields.CATALYST_COST;
            catalystForm.setItemCountReferences(DraftPaths.child(base, RuleFields.CONSUME_COUNTS),
                    RuleValueReads.stringList(session.draft().valueAt(DraftPaths.child(base, RuleFields.CATALYST_ITEMS))));
            catalystForm.setItemCountReferences(DraftPaths.child(base, RuleFields.ITEM_COUNTS),
                    RuleValueReads.stringList(session.draft().valueAt(
                            DraftPaths.child(base, RuleFields.CATALYST_ITEMS))));
            catalystForm.reload();
        }
        pages.shell.rebuildFocus();
    }

    void updateBlacklistVisibility() {
        if (sourceForm != null) sourceForm.setRowVisible(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_EXCLUDE),
                !SourceBlacklistPicker.sourceTags(pages.currentView()).isEmpty());
    }

    void buildInput(EditSession session) {
        RuleValue rule = session.draft().root();
        sourceForm = pages.newForm(session, RuleFields.SOURCE, BuiltinEditorDescriptors.sourceDescriptor());
        sourceForm.hideRowLabel(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS));
        // 卡片标题与列表空态已说明该行用途，悬停不再弹出字段提示遮挡列表
        sourceForm.suppressRowHintTooltip(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS));
        sourceForm.setOnFieldWritten((path, before) -> {
            if (!path.equals(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))) return;
            List<String> references = RuleValueReads.stringList(session.draft().valueAt(path));
            if (session.draft().root().has(RuleFields.SOURCE_COST)) {
                session.draft().replaceWith(CatalystQuantities.withQuantities(session.draft().root(),
                        RuleFields.SOURCE_COST, references, CatalystCost.DEFAULT_COUNT));
            }
        });
        updateBlacklistVisibility();
        pages.liveForms.add(sourceForm);
        pages.liveWidgets.add(sourceForm);

        sourceCostButton = new UiButton(RuleEditorEditSections.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "cost.mode.custom"),
                UiButtonVariant.SECONDARY, this::specifySourceCost).setChipStyle(true);
        {
            boolean custom = rule.has(RuleFields.SOURCE_COST);
            sourceCostButton.setVisible(!custom);
            sourceCostForm = custom ? pages.newForm(session, "", BuiltinEditorDescriptors.sourceCostDescriptor()) : null;
            if (sourceCostForm != null) {
                // 卡片上方已用「源物品消耗」说明字段含义，行内不再重复标签
                sourceCostForm.hideRowLabel(RuleFields.SOURCE_COST);
            }
            if (!custom) {
                costNote = Component.translatable(RuleEditorPageSupport.UI + "cost.auto.one");
            }
        }
        pages.liveWidgets.add(sourceCostButton);
        if (sourceCostForm != null) {
            pages.liveForms.add(sourceCostForm);
            pages.liveWidgets.add(sourceCostForm);
        }

        buildPresenceSection(session, RuleEditorPageSupport.CATALYST_PRESENT_ID, RuleEditorPageSupport.UI + "rule.presence.add_catalyst");
        RuleValue cost = rule.child(RuleFields.CATALYST_COST);
        boolean catalystOn = CatalystQuantities.consumesCatalysts(cost);
        boolean catalystEnabled = PresenceLeaves.hasRuleLevelPresence(rule, RuleEditorPageSupport.CATALYST_PRESENT_ID);
        catalystToggle = new UiCheckBox(THEME, pages.font,
                Component.translatable(RuleEditorPageSupport.UI + "rule.catalyst_consume"), catalystOn);
        catalystToggle.setVisible(catalystEnabled || cost != null);
        catalystToggle.setOnChanged(this::onCatalystToggle);
        pages.liveWidgets.add(catalystToggle);
        catalystForm = catalystEnabled || cost != null
                ? pages.newForm(session, RuleFields.CATALYST_COST, BuiltinEditorDescriptors.catalystCostDescriptor()) : null;
        if (catalystForm != null) {
            catalystForm.setRowVisible(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CATALYST_ITEMS), !catalystEnabled);
            String consumePath = DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CONSUME_COUNTS);
            catalystForm.setOnFieldWritten((path, before) -> {
                materializeCatalystInput(session);
                RuleDraft draft = session.draft();
                // 每轮消耗至少 1 件：写回后没有任何正数等同于不消耗，移除该表并关闭开关
                if (consumePath.equals(path)
                        && !CatalystQuantities.consumesCatalysts(draft.valueAt(RuleFields.CATALYST_COST))) {
                    draft.removeAt(consumePath);
                    if (catalystToggle != null) catalystToggle.setChecked(false);
                    return;
                }
                if (!path.equals(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CATALYST_ITEMS))) return;
                List<String> references = RuleValueReads.stringList(draft.valueAt(path));
                CatalystQuantities.reconcileQuantities(draft, references);
                RuleValue current = draft.valueAt(RuleFields.CATALYST_COST);
                if (current != null && current.has(RuleFields.CONSUME_COUNTS)) {
                    draft.setAt(RuleFields.CATALYST_COST, CatalystQuantities.withQuantities(current,
                            RuleFields.CONSUME_COUNTS, references, CatalystCost.DEFAULT_COUNT));
                }
            });
            bindConsumeVisibility(catalystForm, session);
            catalystForm.pairItemCountRows(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.ITEM_COUNTS),
                    DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CONSUME_COUNTS));
            pages.liveForms.add(catalystForm);
            pages.liveWidgets.add(catalystForm);
        }
        buildPresenceSection(session, RuleEditorPageSupport.FLUID_PRESENT_ID, RuleEditorPageSupport.UI + "rule.presence.add_fluid");
        boolean fluidConsume = rule.flag(RuleFields.CONSUME_FLUID, false);
        fluidConsumeToggle = new UiCheckBox(THEME, pages.font,
                Component.translatable(RuleEditorPageSupport.UI + "rule.consume_fluid"), fluidConsume);
        // 没有流体存在条件就没有可消耗的接触流体：开关只在启用流体条件后出现；
        // 规则里已开启时仍保留开关，否则这个值在界面上既看不到也关不掉
        fluidConsumeToggle.setVisible(fluidConsume
                || PresenceLeaves.hasRuleLevelPresence(rule, RuleEditorPageSupport.FLUID_PRESENT_ID));
        fluidConsumeToggle.setOnChanged(checked -> pages.apply(EditSession.OP_SET_FIELD,
                () -> session.draft().setAt(RuleFields.CONSUME_FLUID, RuleValue.flag(checked))));
        pages.liveWidgets.add(fluidConsumeToggle);
    }

    private boolean consumesCatalysts(EditSession session) {
        return CatalystQuantities.consumesCatalysts(session.draft().valueAt(RuleFields.CATALYST_COST));
    }

    /** 催化剂卡片中与消耗绑定的行只在勾选「消耗催化剂」后显示。 */
    private void bindConsumeVisibility(FormView form, EditSession session) {
        form.setRowVisibility(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CONSUME_COUNTS),
                () -> consumesCatalysts(session));
        form.setRowVisibility(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CATALYST_RADIUS),
                () -> consumesCatalysts(session));
    }

    void specifySourceCost() {
        EditSession session = pages.shell.editingSession();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> draft.setAt(RuleFields.SOURCE_COST, CatalystQuantities.defaultQuantities(null,
                RuleValueReads.stringList(draft.valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))))));
    }

    void onCatalystToggle(boolean checked) {
        EditSession session = pages.shell.editingSession();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> {
            materializeCatalystInput(session);
            RuleValue cost = draft.valueAt(RuleFields.CATALYST_COST);
            if (cost == null || !cost.isMapping()) cost = RuleValue.mapping().build();
            if (!cost.has(RuleFields.CATALYST_ITEMS)) {
                cost = RuleValue.mapping(cost).put(RuleFields.CATALYST_ITEMS,
                        RuleValue.textList(PresenceLeaves.ruleLevelCatalystItems(draft)).build()).build();
                draft.setAt(RuleFields.CATALYST_COST, cost);
            }
            if (checked) {
                RuleValue values = CatalystQuantities.withQuantities(cost, RuleFields.ITEM_COUNTS,
                        RuleValueReads.stringList(cost.child(RuleFields.CATALYST_ITEMS)), CatalystCost.DEFAULT_COUNT)
                        .childOr(RuleFields.ITEM_COUNTS, RuleValue.mapping().build());
                draft.setAt(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CONSUME_COUNTS), values.deepCopy());
            } else {
                draft.removeAt(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CONSUME_COUNTS));
            }
        });
    }

    void buildPresenceSection(EditSession session, ResourceLocation type, String toggleKey) {
        List<PresenceLeaves.PresenceLeaf> ruleLevel = PresenceLeaves.ruleLevelPresence(session.draft().root(), type);
        UiCheckBox toggle = new UiCheckBox(RuleEditorEditSections.THEME, pages.font, Component.translatable(toggleKey), !ruleLevel.isEmpty());
        toggle.setOnChanged(checked -> onPresenceToggle(type, checked));
        pages.liveWidgets.add(toggle);
        if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type)) {
            catalystPresenceToggle = toggle;
        } else {
            fluidPresenceToggle = toggle;
        }
        for (PresenceLeaves.PresenceLeaf leaf : ruleLevel) {
            FormView form = presenceForm(session, leaf);
            presenceRows.add(new PresenceRow(leaf, form));
            pages.liveForms.add(form);
            pages.liveWidgets.add(form);
        }
    }

    FormView presenceForm(EditSession session, PresenceLeaves.PresenceLeaf leaf) {
        FormView form = pages.newForm(session, leaf.conditionPath(), presenceDescriptor(leaf));
        form.hideRowLabel(leaf.fieldPath());
        configurePresenceWrites(form, session, leaf);
        // 类型改写立即落盘（类型 + 关联消耗配置重指向 = 同一次可撤销操作）；此处不重建页面，
        // 目录选择可连续多次回调，重建会让后续选择写进已卸载的控件
        form.setOnChanged(() -> {
            EditSession current = pages.shell.editingSession();
            if (current == null || !pages.shell.editable()) {
                return;
            }
            form.applyToDraft();
            refreshQuantityReferences();
            pages.applyEnabled();
            // 目录选择已在同一次字段写入中提交，仍需通知宿主刷新规则摘要。
            pages.shell.draftChanged();
        });
        // 选择卡片自带逐项移除；多个同类型叶保留移除整条条件的入口。
        if (PresenceLeaves.ruleLevelPresence(session.draft().root(), leaf.type()).size() > 1) {
            form.setRowAction(leaf.fieldPath(), new UiButton(RuleEditorEditSections.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "rule.presence.remove"),
                    UiButtonVariant.SECONDARY, () -> removePresenceLeaf(leaf)).setChipStyle(true));
        }
        return form;
    }

    void configurePresenceWrites(FormView form, EditSession session, PresenceLeaves.PresenceLeaf leaf) {
        form.setOnFieldWritten((path, before) -> {
            if (!leaf.fieldPath().equals(path)) return;
            if (leaf.catalyst()) {
                materializeCatalystInput(session);
                RuleValue condition = session.draft().valueAt(leaf.conditionPath());
                if (condition != null) session.draft().setAt(leaf.conditionPath(), RuleValue.mapping(condition)
                        .remove(RuleFields.ITEM_COUNTS).remove(RuleFields.CATALYST_COUNT).build());
                CatalystQuantities.retargetCatalystItems(session.draft());
            }
        });
    }

    TypeEditorDescriptor presenceDescriptor(PresenceLeaves.PresenceLeaf leaf) {
        List<EditorField> fields = leaf.catalyst()
                ? List.of(EditorField.tagList(RuleFields.CATALYST_ITEMS, RuleEditorPageSupport.UI + "field.catalyst_present.items", "minecraft:item")
                        .asRequired(),
                        EditorField.note(RuleEditorPageSupport.UI + "rule.catalyst_all_required"))
                : List.of(EditorField.tagList(RuleFields.FLUIDS, RuleEditorPageSupport.UI + "field.fluid_present.fluid", "minecraft:fluid").optional(),
                        EditorField.note(RuleEditorPageSupport.UI + "field.fluid_present.fluid.hint"));
        return TypeEditorDescriptor.of(leaf.type(), TypeLabels.conditionLabel(leaf.type()), fields);
    }

    void onPresenceToggle(ResourceLocation type, boolean checked) {
        EditSession session = pages.shell.editingSession();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> {
            if (checked) {
                PresenceLeaves.addRuleLevelPresence(draft, type);
                if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type) && !draft.root().has(RuleFields.CATALYST_COST)) {
                    draft.setAt(RuleFields.CATALYST_COST, RuleValue.mapping()
                            .put(RuleFields.CATALYST_ITEMS, RuleValue.list().build())
                            .put(RuleFields.CATALYST_RADIUS, RuleValue.integer(CatalystCost.DEFAULT_RADIUS)).build());
                }
            } else {
                if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type)) draft.removeAt(RuleFields.CATALYST_COST);
                else draft.removeAt(RuleFields.CONSUME_FLUID);
                PresenceLeaves.removeRuleLevelPresence(draft, type);
            }
        });
    }

    void removePresenceLeaf(PresenceLeaves.PresenceLeaf leaf) {
        EditSession session = pages.shell.editingSession();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        pages.apply(EditSession.OP_SET_FIELD, () -> {
            PresenceLeaves.removeLeafAt(draft, leaf.path());
            if (leaf.catalyst()) CatalystQuantities.retargetCatalystItems(draft);
        });
    }

    @Nullable PresenceLeaves.PresenceLeaf findPresenceLeaf(String conditionPath) {
        for (PresenceLeaves.PresenceLeaf leaf : PresenceLeaves.scanPresenceLeaves(pages.currentView())) {
            if (leaf.conditionPath().equals(conditionPath)) {
                return leaf;
            }
        }
        return null;
    }

    boolean flushPresenceTypes() {
        EditSession session = pages.shell.editingSession();
        if (session == null) {
            return false;
        }
        boolean changed = false;
        for (PresenceRow row : presenceRows) {
            if (row.form.isVisible() && PresenceLeaves.flushPresenceType(session, row.leaf, row.form)) {
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
            boolean showHeader = PresenceLeaves.ruleLevelPresence(pages.currentView(), type).size() > 1;
            row.headerRect = showHeader ? new UiRect(x, cursor, width, RuleEditorPageSupport.LINE_H) : null;
            if (showHeader) cursor += RuleEditorPageSupport.LINE_H;
            cursor = pages.layoutForm(row.form, x, cursor, width);
        }
        return cursor;
    }

    List<TaggedId> taggedItems(String conditionPath) {
        EditSession session = pages.shell.editingSession();
        List<TaggedId> items = new ArrayList<>();
        if (session == null) {
            return items;
        }
        for (String reference : RuleValueReads.stringList(session.draft().valueAt(
                DraftPaths.child(conditionPath, RuleFields.CATALYST_ITEMS)))) {
            TaggedId.parse(reference).result().ifPresent(items::add);
        }
        return items;
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
        if (fluidConsumeToggle != null && fluidConsumeToggle.isVisible()) {
            fluidConsumeToggle.setBounds(x, y, width, RuleEditorPageSupport.ROW_H);
            y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        }
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
            pages.drawText(graphics, row.headerRect, PresenceLeaves.presenceHeader(row.leaf), THEME.palette().textSecondary());
        }
    }
}
