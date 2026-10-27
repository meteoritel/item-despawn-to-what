package com.meteorite.itemdespawntowhat.client.ui.screen;

import net.minecraft.client.gui.Font;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;
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

    final EditorSectionContent content;
    private final EditorShell shell;
    private final Font font;
    private final EditorFormFactory forms;
    private final EditorSectionActions commands;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditSections.THEME;

    RuleEditorInputSection(EditorShell shell, Font font, EditorFormFactory forms, EditorSectionActions actions) {
        this.shell = shell;
        this.font = font;
        this.forms = forms;
        this.commands = actions;
        this.content = new EditorSectionContent(font);
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

    void refreshQuantityReferences() {
        EditSession session = shell.editingSession();
        if (session == null) return;
        if (sourceCostForm != null) {
            sourceCostForm.field(RuleFields.SOURCE_COST).itemCountReferences(RuleValue.textItems(session.valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))));
            sourceCostForm.reload();
        }
        for (PresenceRow row : presenceRows) if (row.leaf.catalyst()) {
            row.form.reload();
        }
        if (catalystForm != null) {
            String base = RuleFields.CATALYST_COST;
            catalystForm.field(RuleFields.CONSUME_COUNTS).itemCountReferences(RuleValue.textItems(session.valueAt(DraftPaths.child(base, RuleFields.CATALYST_ITEMS))));
            catalystForm.field(RuleFields.ITEM_COUNTS).itemCountReferences(RuleValue.textItems(session.valueAt(
                            DraftPaths.child(base, RuleFields.CATALYST_ITEMS))));
            catalystForm.reload();
        }
        shell.rebuildFocus();
    }

    void updateBlacklistVisibility() {
        if (sourceForm != null) sourceForm.field(RuleFields.SOURCE_EXCLUDE).setVisible(!SourceBlacklistPicker.sourceTags(shell.editingValue()).isEmpty());
    }

    private FormView newForm(EditSession session, String basePath, TypeEditorDescriptor descriptor) {
        FormView form = forms.create(session, basePath, descriptor);
        form.setOnChanged(() -> {
            if (form == sourceForm) {
                updateBlacklistVisibility();
                refreshQuantityReferences();
            }
            if (form == catalystForm) {
                for (RuleEditorInputSection.PresenceRow row : presenceRows) {
                    if (row.leaf.catalyst()) row.form.reload();
                }
            }
            shell.draftChanged();
        });
        return form;
    }

    void buildInput(EditSession session) {
        RuleValue rule = session.root();
        sourceForm = newForm(session, RuleFields.SOURCE, BuiltinEditorDescriptors.sourceDescriptor());
        sourceForm.field(RuleFields.SOURCE_ITEMS).hideLabel();
        // 卡片标题与列表空态已说明该行用途，悬停不再弹出字段提示遮挡列表
        sourceForm.field(RuleFields.SOURCE_ITEMS).suppressHintTooltip();
        sourceForm.setOnFieldWritten((draft, path, before) -> {
            if (!path.equals(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))) return;
            List<String> references = RuleValue.textItems(draft.valueAt(path));
            if (draft.root().has(RuleFields.SOURCE_COST)) {
                draft.replaceWith(CatalystQuantities.withQuantities(draft.root(),
                        RuleFields.SOURCE_COST, references));
            }
        });
        updateBlacklistVisibility();
        content.forms.add(sourceForm);
        content.widgets.add(sourceForm);

        sourceCostButton = new UiButton(RuleEditorEditSections.THEME, font, Component.translatable(RuleEditorPageSupport.UI + "cost.mode.custom"),
                UiButtonVariant.SECONDARY, this::specifySourceCost).setChipStyle(true);
        {
            boolean custom = rule.has(RuleFields.SOURCE_COST);
            sourceCostButton.setVisible(!custom);
            sourceCostForm = custom ? newForm(session, "", BuiltinEditorDescriptors.sourceCostDescriptor()) : null;
            if (sourceCostForm != null) {
                // 卡片上方已用「源物品消耗」说明字段含义，行内不再重复标签
                sourceCostForm.field(RuleFields.SOURCE_COST).hideLabel();
            }
            if (!custom) {
                costNote = Component.translatable(RuleEditorPageSupport.UI + "cost.auto.one");
            }
        }
        content.widgets.add(sourceCostButton);
        if (sourceCostForm != null) {
            content.forms.add(sourceCostForm);
            content.widgets.add(sourceCostForm);
        }

        buildPresenceSection(session, RuleEditorPageSupport.CATALYST_PRESENT_ID, RuleEditorPageSupport.UI + "rule.presence.add_catalyst");
        RuleValue cost = rule.child(RuleFields.CATALYST_COST);
        boolean catalystOn = CatalystQuantities.consumesCatalysts(cost);
        boolean catalystEnabled = PresenceLeaves.hasRuleLevelPresence(rule, RuleEditorPageSupport.CATALYST_PRESENT_ID);
        catalystToggle = new UiCheckBox(THEME, font,
                Component.translatable(RuleEditorPageSupport.UI + "rule.catalyst_consume"), catalystOn);
        catalystToggle.setVisible(catalystEnabled || cost != null);
        catalystToggle.setOnChanged(this::onCatalystToggle);
        content.widgets.add(catalystToggle);
        catalystForm = catalystEnabled || cost != null
                ? newForm(session, RuleFields.CATALYST_COST, BuiltinEditorDescriptors.catalystCostDescriptor()) : null;
        if (catalystForm != null) {
            catalystForm.field(RuleFields.CATALYST_ITEMS).setVisible(!catalystEnabled);
            String consumePath = DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CONSUME_COUNTS);
            catalystForm.setOnFieldWritten((draft, path, before) -> {
                EditorQuantityBindings.materializeCatalystInput(session, draft);

                // 每轮消耗至少 1 件：写回后没有任何正数等同于不消耗，移除该表并关闭开关
                if (consumePath.equals(path)
                        && !CatalystQuantities.consumesCatalysts(draft.valueAt(RuleFields.CATALYST_COST))) {
                    draft.removeAt(consumePath);
                    if (catalystToggle != null) catalystToggle.setChecked(false);
                    return;
                }
                if (!path.equals(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CATALYST_ITEMS))) return;
                List<String> references = RuleValue.textItems(draft.valueAt(path));
                CatalystQuantities.reconcileQuantities(draft, references);
                RuleValue current = draft.valueAt(RuleFields.CATALYST_COST);
                if (current != null && current.has(RuleFields.CONSUME_COUNTS)) {
                    draft.setAt(RuleFields.CATALYST_COST, CatalystQuantities.withQuantities(current,
                            RuleFields.CONSUME_COUNTS, references));
                }
            });
            bindConsumeVisibility(catalystForm, session);
            catalystForm.field(RuleFields.ITEM_COUNTS).pairBelow(catalystForm.field(RuleFields.CONSUME_COUNTS));
            content.forms.add(catalystForm);
            content.widgets.add(catalystForm);
        }
        buildPresenceSection(session, RuleEditorPageSupport.FLUID_PRESENT_ID, RuleEditorPageSupport.UI + "rule.presence.add_fluid");
        boolean fluidConsume = rule.flag(RuleFields.CONSUME_FLUID, false);
        fluidConsumeToggle = new UiCheckBox(THEME, font,
                Component.translatable(RuleEditorPageSupport.UI + "rule.consume_fluid"), fluidConsume);
        // 没有流体存在条件就没有可消耗的接触流体：开关只在启用流体条件后出现；
        // 规则里已开启时仍保留开关，否则这个值在界面上既看不到也关不掉
        fluidConsumeToggle.setVisible(fluidConsume
                || PresenceLeaves.hasRuleLevelPresence(rule, RuleEditorPageSupport.FLUID_PRESENT_ID));
        fluidConsumeToggle.setOnChanged(checked -> commands.apply(EditSession.OP_SET_FIELD, edits -> edits.setAt(RuleFields.CONSUME_FLUID, RuleValue.flag(checked))));
        content.widgets.add(fluidConsumeToggle);
    }

    private boolean consumesCatalysts(EditSession session) {
        return CatalystQuantities.consumesCatalysts(session.valueAt(RuleFields.CATALYST_COST));
    }

    /** 催化剂卡片中与消耗绑定的行只在勾选「消耗催化剂」后显示。 */
    private void bindConsumeVisibility(FormView form, EditSession session) {
        form.field(RuleFields.CONSUME_COUNTS).visibleWhen(() -> consumesCatalysts(session));
        form.field(RuleFields.CATALYST_RADIUS).visibleWhen(() -> consumesCatalysts(session));
    }

    void specifySourceCost() {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        commands.apply(EditSession.OP_SET_FIELD, edits -> edits.setAt(RuleFields.SOURCE_COST, CatalystQuantities.defaultQuantities(null,
                RuleValue.textItems(edits.valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))))));
    }

    void onCatalystToggle(boolean checked) {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        commands.apply(EditSession.OP_SET_FIELD, edits -> {
            EditorQuantityBindings.materializeCatalystInput(session, edits);
            RuleValue cost = edits.valueAt(RuleFields.CATALYST_COST);
            if (cost == null || !cost.isMapping()) cost = RuleValue.mapping().build();
            if (!cost.has(RuleFields.CATALYST_ITEMS)) {
                cost = RuleValue.mapping(cost).put(RuleFields.CATALYST_ITEMS,
                        RuleValue.textList(PresenceLeaves.ruleLevelCatalystItems(edits.root())).build()).build();
                edits.setAt(RuleFields.CATALYST_COST, cost);
            }
            if (checked) {
                RuleValue values = CatalystQuantities.withQuantities(cost, RuleFields.ITEM_COUNTS,
                        RuleValue.textItems(cost.child(RuleFields.CATALYST_ITEMS)))
                        .childOr(RuleFields.ITEM_COUNTS, RuleValue.mapping().build());
                edits.setAt(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CONSUME_COUNTS), values.deepCopy());
            } else {
                edits.removeAt(DraftPaths.child(RuleFields.CATALYST_COST, RuleFields.CONSUME_COUNTS));
            }
        });
    }

    void buildPresenceSection(EditSession session, ResourceLocation type, String toggleKey) {
        List<PresenceLeaves.PresenceLeaf> ruleLevel = PresenceLeaves.ruleLevelPresence(session.root(), type);
        UiCheckBox toggle = new UiCheckBox(RuleEditorEditSections.THEME, font, Component.translatable(toggleKey), !ruleLevel.isEmpty());
        toggle.setOnChanged(checked -> onPresenceToggle(type, checked));
        content.widgets.add(toggle);
        if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type)) {
            catalystPresenceToggle = toggle;
        } else {
            fluidPresenceToggle = toggle;
        }
        for (PresenceLeaves.PresenceLeaf leaf : ruleLevel) {
            FormView form = presenceForm(session, leaf);
            presenceRows.add(new PresenceRow(leaf, form));
            content.forms.add(form);
            content.widgets.add(form);
        }
    }

    FormView presenceForm(EditSession session, PresenceLeaves.PresenceLeaf leaf) {
        FormView form = newForm(session, leaf.conditionPath(), BuiltinEditorDescriptors.presenceDescriptor(leaf.type()));
        form.field(leaf.fieldName()).hideLabel();
        EditorQuantityBindings.configurePresenceWrites(form, session, leaf);
        // 类型改写立即落盘（类型 + 关联消耗配置重指向 = 同一次可撤销操作）；此处不重建页面，
        // 目录选择可连续多次回调，重建会让后续选择写进已卸载的控件
        form.setOnChanged(() -> {
            EditSession current = shell.editingSession();
            if (current == null || !shell.editable()) {
                return;
            }
            refreshQuantityReferences();
            applyEnabled(shell.editable());
            // 目录选择已在同一次字段写入中提交，仍需通知宿主刷新规则摘要。
            shell.draftChanged();
        });
        // 选择卡片自带逐项移除；多个同类型叶保留移除整条条件的入口。
        if (PresenceLeaves.ruleLevelPresence(session.root(), leaf.type()).size() > 1) {
            form.field(leaf.fieldName()).setAction(new UiButton(RuleEditorEditSections.THEME, font, Component.translatable(RuleEditorPageSupport.UI + "rule.presence.remove"),
                    UiButtonVariant.SECONDARY, () -> removePresenceLeaf(leaf)).setChipStyle(true));
        }
        return form;
    }

    void onPresenceToggle(ResourceLocation type, boolean checked) {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        commands.apply(EditSession.OP_SET_FIELD, edits -> {
            if (checked) {
                PresenceLeaves.addRuleLevelPresence(edits, type);
                if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type) && !edits.root().has(RuleFields.CATALYST_COST)) {
                    edits.setAt(RuleFields.CATALYST_COST, RuleValue.mapping()
                            .put(RuleFields.CATALYST_ITEMS, RuleValue.list().build())
                            .put(RuleFields.CATALYST_RADIUS, RuleValue.integer(CatalystCost.DEFAULT_RADIUS)).build());
                }
            } else {
                if (RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type)) edits.removeAt(RuleFields.CATALYST_COST);
                else edits.removeAt(RuleFields.CONSUME_FLUID);
                PresenceLeaves.removeRuleLevelPresence(edits, type);
            }
        });
    }

    void removePresenceLeaf(PresenceLeaves.PresenceLeaf leaf) {
        EditSession session = shell.editingSession();
        if (session == null) {
            return;
        }
        commands.apply(EditSession.OP_SET_FIELD, edits -> {
            PresenceLeaves.removeLeafAt(edits, leaf.path());
            if (leaf.catalyst()) CatalystQuantities.retargetCatalystItems(edits);
        });
    }

    boolean flushPresenceTypes() {
        EditSession session = shell.editingSession();
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
            boolean showHeader = PresenceLeaves.ruleLevelPresence(shell.editingValue(), type).size() > 1;
            row.headerRect = showHeader ? new UiRect(x, cursor, width, RuleEditorPageSupport.LINE_H) : null;
            if (showHeader) cursor += RuleEditorPageSupport.LINE_H;
            cursor = EditorSectionContent.layoutForm(row.form, x, cursor, width);
        }
        return cursor;
    }

    int layoutInput(UiRect area) {
        int x = area.x() + RuleEditorPageSupport.CARD_PAD;
        int width = Math.max(0, area.width() - RuleEditorPageSupport.CARD_PAD * 2);
        int top = area.y();
        int y = EditorSectionContent.layoutForm(sourceForm, x, top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H, width);
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
        y = EditorSectionContent.layoutForm(sourceCostForm, x, y, width);
        top = content.finishCard(area.x(), top, area.width(), y, "card.source");
        y = layoutPresenceSection(RuleEditorPageSupport.CATALYST_PRESENT_ID, x, top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H, width);
        if (catalystToggle != null && catalystToggle.isVisible()) {
            catalystToggle.setBounds(x, y, width, RuleEditorPageSupport.ROW_H);
            y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        }
        if (catalystNote != null) {
            catalystNoteRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H);
            y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
        }
        y = EditorSectionContent.layoutForm(catalystForm, x, y, width);
        top = content.finishCard(area.x(), top, area.width(), y, "card.catalyst");
        y = layoutPresenceSection(RuleEditorPageSupport.FLUID_PRESENT_ID, x, top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H, width);
        if (fluidConsumeToggle != null && fluidConsumeToggle.isVisible()) {
            fluidConsumeToggle.setBounds(x, y, width, RuleEditorPageSupport.ROW_H);
            y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        }
        return content.finishCard(area.x(), top, area.width(), y, "card.fluid");
    }

    void renderInput(GuiGraphics graphics) {
        content.drawText(graphics, costModeRect, Component.translatable(RuleEditorPageSupport.UI + "cost.source_title"), THEME.palette().textSecondary());
        if (costNote != null) {
            content.drawText(graphics, costNoteRect, costNote, THEME.palette().textDisabled());
        }
        if (catalystNote != null) {
            content.drawText(graphics, catalystNoteRect, catalystNote, THEME.palette().textDisabled());
        }
        // 存在条件标题行：类型 + 同类型编号 + 作用域，多个同类叶也能逐项辨识
        for (PresenceRow row : presenceRows) {
            content.drawText(graphics, row.headerRect, PresenceLeaves.presenceHeader(row.leaf), THEME.palette().textSecondary());
        }
    }
    void applyEnabled(boolean editable) {
        RuleValue view = shell.editingValue();
        if (sourceCostButton != null) {
            sourceCostButton.setEnabled(editable);
        }
        if (catalystToggle != null) {
            EditSession session = shell.editingSession();
            boolean hasCost = view != null && view.has(RuleFields.CATALYST_COST);
            catalystToggle.setEnabled(editable && (hasCost || session != null
                    && PresenceLeaves.selectedCatalystLeaf(session.root()) != null));
        }
        if (catalystPresenceToggle != null) {
            catalystPresenceToggle.setEnabled(editable);
        }
        if (fluidPresenceToggle != null) {
            fluidPresenceToggle.setEnabled(editable);
        }
        if (fluidConsumeToggle != null) {
            fluidConsumeToggle.setEnabled(editable);
        }
    }

    void restoreSwitches() {
        RuleValue view = shell.editingValue();
        if (view == null) return;
        if (catalystToggle != null) {
            RuleValue cost = view.child(RuleFields.CATALYST_COST);
            catalystToggle.setChecked(CatalystQuantities.consumesCatalysts(cost));
        }
        if (catalystPresenceToggle != null) {
            catalystPresenceToggle.setChecked(
                    PresenceLeaves.hasRuleLevelPresence(view, RuleEditorPageSupport.CATALYST_PRESENT_ID));
        }
        if (fluidPresenceToggle != null) {
            fluidPresenceToggle.setChecked(
                    PresenceLeaves.hasRuleLevelPresence(view, RuleEditorPageSupport.FLUID_PRESENT_ID));
        }
        if (fluidConsumeToggle != null) {
            fluidConsumeToggle.setChecked(view.flag(RuleFields.CONSUME_FLUID, false));
        }
    }
}
