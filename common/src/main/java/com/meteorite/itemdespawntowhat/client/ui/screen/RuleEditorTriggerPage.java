package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionTreeOverlay;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.LeafPanel;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiConditionTreeSummary;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.type.condition.BiomeCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;

/**
 * 触发与条件页：触发来源、触发时间、条件树摘要与画布页入口，以及条件叶参数弹窗。
 */
final class RuleEditorTriggerPage {

    // 所属编辑页容器：共享状态与跨页能力都经它访问
    private final RuleEditorEditPages pages;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditPages.THEME;
    private static final String BIOME_MODE_FIELD = "mode";
    private static final String BIOME_IDS_FIELD = "biomes";
    private static final String CLIMATE_MODE = "climate";

    RuleEditorTriggerPage(RuleEditorEditPages pages) {
        this.pages = pages;
    }

    // 清理本页持有的控件引用（重建/切页时由容器调用）
    void reset() {
        triggerBoxes.clear();
        triggerNoteRect = null;
        delayForm = null;
        delayHintRect = null;
        conditionsForm = null;
    }

    final List<UiCheckBox> triggerBoxes = new ArrayList<>();

    @Nullable UiRect triggerNoteRect;

    @Nullable FormView delayForm;

    @Nullable UiRect delayHintRect;

    @Nullable FormView conditionsForm;

    void buildTrigger(EditSession session) {
        RuleValue rule = session.draft().root();
        List<String> effective = RuleTriggers.effective(rule);
        for (String value : RuleTriggers.known()) {
            UiCheckBox box = new UiCheckBox(RuleEditorEditPages.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "trigger." + value), effective.contains(value));
            box.setOnChanged(checked -> onTriggerToggled());
            triggerBoxes.add(box);
            pages.liveWidgets.add(box);
        }
        if (RuleTriggers.allowsDelay(rule)) {
            delayForm = pages.newForm(session, "", TypeEditorDescriptor.of(RuleEditorPageSupport.DELAY_PAGE, Component.translatable(RuleEditorPageSupport.UI + "rule.trigger_after_seconds"), List.of(
                    EditorField.integerSlider(RuleFields.TRIGGER_AFTER_SECONDS, RuleEditorPageSupport.UI + "rule.trigger_after_seconds", 0, Integer.MAX_VALUE, 0, BuiltinEditorDescriptors.DEFAULT_TRIGGER_WINDOW_SECONDS).optional())));
            pages.liveForms.add(delayForm);
            pages.liveWidgets.add(delayForm);
        }
        TypeEditorDescriptor conditions = TypeEditorDescriptor.of(RuleEditorPageSupport.CONDITIONS_PAGE, Component.translatable(RuleEditorPageSupport.UI + "tab.trigger"), List.of(
                EditorField.conditionTree(RuleFields.CONDITIONS, RuleEditorPageSupport.UI + "rule.conditions")));
        conditionsForm = pages.newForm(session, "", conditions);
        conditionsForm.setLabelWidth(90);
        pages.liveForms.add(conditionsForm);
        pages.liveWidgets.add(conditionsForm);
    }

    void updateLifespanWindow() {
        EditSession session = pages.host.session();
        if (delayForm == null || session == null) return;
        int ticks = pages.host.workspace().referenceLifespanTicks();
        // 整数秒向上取整保证不足一秒的寿命也可拖动；常用窗口不钳制既有触发值。
        int seconds = ticks <= 0 ? BuiltinEditorDescriptors.DEFAULT_TRIGGER_WINDOW_SECONDS
                : (int) Math.ceil((double) ticks / RuleEditSessionLimits.TICKS_PER_SECOND);
        delayForm.setRowSliderWindow(RuleFields.TRIGGER_AFTER_SECONDS, 0, Math.max(1, seconds));
    }

    void onTriggerToggled() {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        RuleDraft draft = session.draft();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < triggerBoxes.size(); i++) {
            if (triggerBoxes.get(i).isChecked()) {
                values.add(RuleTriggers.known().get(i));
            }
        }
        for (String value : RuleTriggers.effective(draft.root())) {
            if (!RuleTriggers.known().contains(value) && !values.contains(value)) {
                values.add(value);
            }
        }
        pages.apply(EditSession.OP_SET_FIELD, () -> draft.setAt(RuleFields.TRIGGERS, RuleTriggers.toArray(values)));
    }

    ConditionSupport pageConditionSupport(FormView owner) {
        ConditionSupport base = pages.host.conditionSupport();
        return new ConditionSupport() {
            @Override
            public List<ConditionTreeContract.TypeOption> typeOptions() {
                return base.typeOptions();
            }

            @Override
            public ConditionTreeContract.LeafFactory leafFactory() {
                // 新建催化剂存在条件不预写门槛：门槛留空才能显示「默认：N」
                return type -> RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type) ? RuleEditorPageSupport.presenceLeafNode(type) : base.leafFactory().create(type);
            }

            @Override
            public Consumer<ConditionNode.Leaf> onEditLeaf() {
                return leaf -> openLeafEditor(owner, leaf);
            }

            @Override
            public void revealLeaf(ConditionNode.Leaf leaf, String fieldPath) {
                openLeafEditor(owner, leaf, fieldPath);
            }

            @Override
            public boolean openTree(UiConditionTreeSummary tree, @Nullable String fieldPath) {
                // 摘要给相对节点路径，问题定位给绝对草稿路径；局部动作条件需要补齐所属作用域。
                String path = fieldPath != null && (fieldPath.equals(RuleFields.CONDITIONS)
                        || fieldPath.startsWith(RuleFields.CONDITIONS + ".")) ? owner.draftPath(fieldPath) : fieldPath;
                return openConditionTree(owner, path);
            }

            @Override
            public TypeRegistry<ConditionType<?>> registry() {
                return base.registry();
            }
        };
    }

    void editConditionLeaf(ConditionNode.Leaf leaf) {
        for (FormView form : pages.liveForms) {
            if (RuleEditorPageSupport.findTree(form, leaf) != null) {
                openLeafEditor(form, leaf);
                return;
            }
        }
    }

    void openLeafEditor(FormView owner, ConditionNode.Leaf leaf) {
        openLeafEditor(owner, leaf, null);
    }

    void openLeafEditor(FormView owner, ConditionNode.Leaf leaf, @Nullable String fieldPath) {
        EditSession session = pages.host.session();
        if (session == null) {
            return;
        }
        UiConditionTreeSummary tree = RuleEditorPageSupport.findTree(owner, leaf);
        String base = tree != null && tree.selectedPath() != null ? tree.selectedPath() : RuleFields.CONDITIONS;
        String path = owner.draftPath(base.endsWith("." + RuleFields.CONDITION) ? base : base + "." + RuleFields.CONDITION);
        // 先写入新建的树节点，叶参数表单才有可读取的草稿路径。
        owner.applyToDraft();
        ConditionTreeOverlay.Parameters parameters = createLeafParameters(session, path, leaf);
        FormView form = parameters.form();
        LeafPanel panel = parameters.panel();
        UiModal modal = UiModal.create(RuleEditorEditPages.THEME, pages.font).title(parameters.title());
        int contentHeight = Math.clamp(panel.contentHeight() + 8, 36, 220);
        modal.contentWidget(panel, contentHeight);
        modal.onClosed(() -> {
            RuleValue current = session.draft().valueAt(path);
            if (current != null && current.isMapping()
                    && leaf.condition().type().toString().equals(RuleEditorPageSupport.stringField(current, RuleFields.TYPE))) {
                form.commitPendingInputs();
                pages.host.onDraftChanged();
            }
            panel.unmount();
        });
        modal.onHistoryChanged(shortcut -> {
            String operation = shortcut == com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut.UNDO
                    ? session.redoOpKey() : session.undoOpKey();
            if (!EditSession.OP_SET_FIELD.equals(operation) && !EditSession.OP_SET_SOURCE.equals(operation)) {
                // 结构恢复可能把同类型邻居放回原路径，关闭独立参数弹窗避免错误重绑。
                modal.onClosed(panel::unmount);
                pages.host.modals().close(modal);
                return;
            }
            RuleValue current = session.draft().valueAt(path);
            if (current != null && current.isMapping()
                    && leaf.condition().type().toString().equals(RuleEditorPageSupport.stringField(current, RuleFields.TYPE))) {
                panel.reload();
            } else {
                pages.host.modals().close(modal);
            }
        });
        pages.leafPanels.add(new RuleEditorEditPages.LeafEntry(modal, panel));
        modal.retainOnConfirm(true);
        modal.confirm(Component.translatable(RuleEditorPageSupport.UI + "button.confirm"), () -> {
            form.pendingInputIssue();
            FormIssue issue = form.issues().stream().filter(FormIssue::blocking).findFirst().orElse(null);
            if (issue != null) {
                Component message = Component.translatable(RuleEditorPageSupport.UI + "notice.issue", issue.label(), issue.message());
                modal.message(message);
                modal.layoutCentered(pages.host.screenWidth(), pages.host.screenHeight());
                panel.focusField(issue.path());
                return;
            }
            form.commitPendingInputs();
            owner.reload();
            pages.host.onDraftChanged();
            pages.host.modals().closeTop();
        });
        modal.cancel(Component.translatable(RuleEditorPageSupport.UI + "button.cancel"));
        modal.layoutCentered(pages.host.screenWidth(), pages.host.screenHeight());
        pages.host.modals().push(modal);
        if (fieldPath != null) panel.focusField(fieldPath);
    }

    ConditionTreeOverlay.Parameters createLeafParameters(EditSession session, String path, ConditionNode.Leaf leaf) {
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.descriptorFor(leaf.condition().type());
        boolean catalystLeaf = RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(leaf.condition().type());
        // 催化剂门槛字段对外统一称「最低触发数量」，留空时才给出「默认：N」
        if (catalystLeaf) {
            descriptor = new TypeEditorDescriptor(descriptor.id(), descriptor.label(),
                    RuleEditorPageSupport.relabelCatalystThreshold(descriptor.fields()), descriptor.readOnly());
        }
        RuleEditorPageSupport.PresenceLeaf presence = catalystLeaf ? pages.ruleEditorInputPage.findPresenceLeaf(path) : null;
        FormView form = new FormView(pages.font, session, path);
        form.useCatalogSelections();
        form.setConditionSupport(pageConditionSupport(form));
        form.setSuggestionProvider(pages.host.suggestions());
        form.setOnChanged(() -> {
            form.applyToDraft();
            pages.host.onDraftChanged();
        });
        // 输入被长度上限拒绝时立刻显示一条可读提示（字段名：问题）
        form.setOnRejectedNotice(message -> pages.host.notice(message, THEME.palette().danger()));
        form.setSelectionCatalogOpener((field, fieldPath, tags, selected, onPicked) -> {
            RuleCatalogType type = tags ? RuleCatalogType.TAG : FieldCatalogTypes.catalogTypeOf(field);
            if (type == null || pages.host.rejectWhenFrozen()) return;
            boolean multi = field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.TAG_LIST
                    || field.type() == com.meteorite.itemdespawntowhat.client.edit.EditorFieldType.RL_LIST;
            pages.host.modals().push(RuleEditorPanelFactory.fieldCatalogModal(pages.font, pages.host.workspace(), type, multi,
                    Component.translatable(field.labelKey()), pages.host.screenWidth(), pages.host.screenHeight(), onPicked,
                    field, session.draft(), fieldPath, selected));
        });
        form.setDescriptor(descriptor);
        form.reload();
        if (BiomeCondition.ID.equals(leaf.condition().type())) configureBiomeFields(form, descriptor);
        pages.ruleEditorInputPage.configureQuantityFields(form, session, path, descriptor);
        RuleEditorPageSupport.PresenceLeaf linked = pages.ruleEditorInputPage.findPresenceLeaf(path);
        if (linked != null && linked.ruleLevel()) pages.ruleEditorInputPage.configurePresenceWrites(form, session, linked);
        // 已知条件类型附带区间条（昼夜/高度光照）与注册表字段的目录按钮；
        // 面板自身负责子控件的事件转发与 Tab 顺序，普通数值字段仍由表单承载
        LeafPanel content = RuleEditorPanelFactory.leafPanel(pages.font, form, session, descriptor.fields(),
                path, change -> {
                    form.commitPendingInputs();
                    session.apply(EditSession.OP_SET_FIELD, change);
                    pages.host.onDraftChanged();
                }, () -> {
                    form.reload();
                    pages.host.onDraftChanged();
                }, pages.host.modals()::push, pages.host.workspace(), pages.host.screenWidth(), pages.host.screenHeight());
        String noteKey = catalystLeaf ? RuleEditorPageSupport.UI + "rule.catalyst_all_required"
                : RuleEditorPageSupport.FLUID_PRESENT_ID.equals(leaf.condition().type()) ? RuleEditorPageSupport.UI + "field.fluid_present.fluid.hint" : null;
        LeafPanel panel = noteKey == null ? content : content.addNote(() -> Component.translatable(noteKey));
        Component title = presence == null ? descriptor.label() : Component.translatable(RuleEditorPageSupport.UI + "rule.presence.title",
                descriptor.label(), presence.ordinal(), presence.scopeLabel());
        return new ConditionTreeOverlay.Parameters(form, panel, title);
    }

    private static void configureBiomeFields(FormView form, TypeEditorDescriptor descriptor) {
        BooleanSupplier climate = () -> {
            RuleValue mode = form.pendingValue(form.draftPath(BIOME_MODE_FIELD));
            return mode != null && mode.isText() && CLIMATE_MODE.equals(mode.asText());
        };
        form.setRowVisibility(form.draftPath(BIOME_IDS_FIELD), () -> !climate.getAsBoolean());
        for (EditorField field : descriptor.fields()) if (field.type() == EditorFieldType.CLIMATE_RANGE) {
            form.setRowVisibility(form.draftPath(field.name()), climate);
        }
    }

    boolean openConditionTree(FormView owner, @Nullable String fieldPath) {
        EditSession session = pages.host.session();
        if (session == null || pages.host.rejectWhenFrozen()) return false;
        owner.commitPendingInputs();
        String scope = owner.draftPath(RuleFields.CONDITIONS);
        RuleValue owners = RuleEditorPageSupport.conditionOwnerShape(session.draft().root());
        ConditionTreeOverlay overlay = new ConditionTreeOverlay(pages.font, session, scope, pageConditionSupport(owner),
                (path, leaf) -> createLeafParameters(session, path, leaf), pages.host::onDraftChanged);
        int chromeReserve = THEME.headerHeight() + THEME.padding() * 3 + UiModal.BUTTON_HEIGHT;
        UiModal modal = UiModal.create(RuleEditorEditPages.THEME, pages.font).title(Component.translatable(RuleEditorPageSupport.UI + "tree.title"))
                .preferredWidth(Math.max(UiModal.MIN_WIDTH, pages.host.screenWidth() - RuleEditorPageSupport.TREE_OVERLAY_MARGIN))
                .contentWidget(overlay, Math.max(0, pages.host.screenHeight() - RuleEditorPageSupport.TREE_OVERLAY_MARGIN - chromeReserve));
        // 功能栏的撤销/重做按钮与 Ctrl+Z 走同一条历史链路
        overlay.setHistoryActions(
                () -> pages.host.modals().requestHistory(com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut.UNDO),
                () -> pages.host.modals().requestHistory(com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut.REDO));
        modal.onClosed(() -> {
            overlay.unmount();
            owner.reload();
            pages.host.onDraftChanged();
        });
        modal.onHistoryChanged(shortcut -> {
            String operation = shortcut == com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut.UNDO
                    ? session.redoOpKey() : session.undoOpKey();
            String ownerPath = scope.substring(0, Math.max(0, scope.length() - RuleFields.CONDITIONS.length() - 1));
            boolean outerStructure = operation != null && switch (operation) {
                case EditSession.OP_ADD_EFFECT, EditSession.OP_REMOVE_EFFECT, EditSession.OP_MOVE_EFFECT,
                        EditSession.OP_ADD_CANDIDATE, EditSession.OP_REMOVE_CANDIDATE, EditSession.OP_MOVE_CANDIDATE,
                        EditSession.OP_EDIT_CANDIDATE, EditSession.OP_CONVERT_STRUCTURE,
                        EditSession.OP_RESTORE_ORIGINAL, EditSession.OP_CREATE_RULE, EditSession.OP_DELETE_RULE -> true;
                default -> false;
            };
            if (!ownerPath.isEmpty() && (outerStructure || session.draft().valueAt(ownerPath) == null
                    || !owners.equals(RuleEditorPageSupport.conditionOwnerShape(session.draft().root())))) {
                modal.onClosed(overlay::discardView);
                pages.host.modals().close(modal);
            } else overlay.historyChanged();
        });
        modal.cancel(Component.translatable(RuleEditorPageSupport.UI + "button.back"));
        modal.layoutCentered(pages.host.screenWidth(), pages.host.screenHeight());
        pages.host.modals().push(modal);
        if (fieldPath != null) overlay.reveal(fieldPath);
        return true;
    }

    int layoutTrigger(UiRect area) {
        int x = area.x() + RuleEditorPageSupport.CARD_PAD;
        int width = Math.max(0, area.width() - RuleEditorPageSupport.CARD_PAD * 2);
        int top = area.y();
        int y = top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H;
        int cursorX = x;
        for (UiCheckBox box : triggerBoxes) {
            int boxWidth = Math.min(width, box.preferredWidth(4));
            if (cursorX > x && cursorX + boxWidth > x + width) {
                cursorX = x;
                y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
            }
            box.setBounds(cursorX, y, boxWidth, RuleEditorPageSupport.ROW_H);
            cursorX += boxWidth + RuleEditorPageSupport.CARD_GAP;
        }
        y += RuleEditorPageSupport.ROW_H + RuleEditorPageSupport.PAD;
        triggerNoteRect = null;
        delayHintRect = null;
        if (RuleTriggers.isExplicitlyEmpty(pages.currentView())) {
            triggerNoteRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H);
            y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
        }
        y = pages.layoutForm(delayForm, x, y, width);
        if (delayForm != null) {
            delayHintRect = new UiRect(x, y, width, RuleEditorPageSupport.LINE_H);
            y += RuleEditorPageSupport.LINE_H + RuleEditorPageSupport.PAD;
        }
        top = pages.finishCard(area.x(), top, area.width(), y, "card.trigger");
        y = pages.layoutForm(conditionsForm, x, top + RuleEditorPageSupport.CARD_PAD + RuleEditorPageSupport.CARD_HEADER_H, width);
        return pages.finishCard(area.x(), top, area.width(), y, "card.conditions");
    }

    void renderTrigger(GuiGraphics graphics) {
        RuleValue view = pages.currentView();
        // 提示只在「显式选择空触发」这一确实缺失的状态出现，有效触发不再恒显
        if (RuleTriggers.isExplicitlyEmpty(view)) {
            pages.drawText(graphics, triggerNoteRect, Component.translatable(RuleEditorPageSupport.UI + "trigger.note"), THEME.palette().textDisabled());
        }
        // 等待时长只对自然触发生效，其他触发组合下不显示该说明
        if (RuleTriggers.allowsDelay(view)) {
            pages.drawText(graphics, delayHintRect, Component.translatable(RuleEditorPageSupport.UI + "trigger.delay_hint"), THEME.palette().textDisabled());
        }
    }
}
