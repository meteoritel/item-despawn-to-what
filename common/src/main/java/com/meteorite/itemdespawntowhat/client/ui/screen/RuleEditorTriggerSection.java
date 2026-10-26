package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorRegistries;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditContext;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditContextFactory;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditingSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionHostCoordination;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionTreeOverlay;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.LeafPanel;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDescriptors;
import com.meteorite.itemdespawntowhat.core.service.RuleEditSessionLimits;

/**
 * 触发与条件页：触发来源、触发时间、条件树摘要与画布页入口，以及条件叶参数弹窗。
 */
final class RuleEditorTriggerSection {

    // 所属编辑页容器：共享状态与跨页能力都经它访问
    private final RuleEditorEditSections pages;

    // 主题：与容器共用宿主默认主题
    private static final UiHostTheme THEME = RuleEditorEditSections.THEME;

    RuleEditorTriggerSection(RuleEditorEditSections pages) {
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
            UiCheckBox box = new UiCheckBox(RuleEditorEditSections.THEME, pages.font, Component.translatable(RuleEditorPageSupport.UI + "trigger." + value), effective.contains(value));
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
        EditSession session = pages.shell.editingSession();
        if (delayForm == null || session == null) return;
        int ticks = pages.workspace.referenceLifespanTicks();
        // 整数秒向上取整保证不足一秒的寿命也可拖动；常用窗口不钳制既有触发值。
        int seconds = ticks <= 0 ? BuiltinEditorDescriptors.DEFAULT_TRIGGER_WINDOW_SECONDS
                : (int) Math.ceil((double) ticks / RuleEditSessionLimits.TICKS_PER_SECOND);
        delayForm.setRowSliderWindow(RuleFields.TRIGGER_AFTER_SECONDS, 0, Math.max(1, seconds));
    }

    void onTriggerToggled() {
        EditSession session = pages.shell.editingSession();
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

    // 共享的条件定义与按场景创建：不捕获表单、树或导航状态，可单独装配
    ConditionEditingSupport sharedConditionSupport() {
        ConditionEditingSupport base = pages.conditionEditingSupport;
        return new ConditionEditingSupport() {
            @Override
            public List<ConditionTreeContract.TypeOption> typeOptions() {
                return base.typeOptions();
            }

            @Override
            public ConditionTreeContract.LeafFactory leafFactory() {
                // 新建催化剂存在条件不预写门槛：门槛留空才能显示「默认：N」
                return type -> RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(type) ? PresenceLeaves.presenceLeafNode(type) : base.leafFactory().create(type);
            }

            @Override
            public TypeRegistry<ConditionType<?>> registry() {
                return base.registry();
            }
        };
    }

    // 宿主协调：打开前提交原宿主输入，正常关闭刷新原宿主并通知，作用域失效只丢弃旧视图
    ConditionHostCoordination conditionHostCoordination(FormView host) {
        return new ConditionHostCoordination() {
            @Override
            public void prepareHost() {
                host.commitPendingInputs();
            }

            @Override
            public void closeView(ConditionTreeOverlay view) {
                view.unmount();
                host.reload();
                pages.shell.draftChanged();
            }

            @Override
            public void discardView(ConditionTreeOverlay view) {
                view.discardView();
            }
        };
    }

    /**
     * 条件编辑上下文工厂：以实际条件字段的绝对路径与所属对象路径创建局部目标，
     * 宿主生命周期仍由原宿主表单调配。
     *
     * @param host 发起编辑的原宿主表单
     * @return 该表单的条件编辑上下文工厂
     */
    ConditionEditContextFactory conditionContextFactory(FormView host) {
        ConditionHostCoordination coordination = conditionHostCoordination(host);
        return (scope, ownerPath) -> new ConditionEditContext() {
            @Override
            public String scope() {
                return scope;
            }

            @Override
            public String ownerPath() {
                return ownerPath;
            }

            @Override
            public @Nullable String absolutePath(String treePath) {
                String root = ConditionTreeContract.ROOT_PATH;
                // 只接受完整段边界：根本身，或根后接点分隔的后继；拒绝 conditionsExtra 这类相似前缀
                if (!treePath.equals(root) && !treePath.startsWith(root + ".")) return null;
                return scope + treePath.substring(root.length());
            }

            @Override
            public boolean open(@Nullable String treePath) {
                // null 是显式「只打开作用域画布」；非空坐标转换失败则不开画布，也不提交原宿主输入
                if (treePath == null) return openConditionTree(coordination, scope, ownerPath, null);
                String absolute = absolutePath(treePath);
                if (absolute == null) return false;
                return openConditionTree(coordination, scope, ownerPath, absolute);
            }
        };
    }

    ConditionTreeOverlay.Parameters createLeafParameters(EditSession session, String path, ConditionNode.Leaf leaf) {
        TypeEditorDescriptor descriptor = EditorRegistries.CONDITIONS.descriptorFor(leaf.condition().type());
        boolean catalystLeaf = RuleEditorPageSupport.CATALYST_PRESENT_ID.equals(leaf.condition().type());
        // 催化剂门槛字段对外统一称「最低触发数量」，留空时才给出「默认：N」
        if (catalystLeaf) {
            descriptor = new TypeEditorDescriptor(descriptor.id(), descriptor.label(),
                    CatalystQuantities.relabelCatalystThreshold(descriptor.fields()), descriptor.readOnly());
        }
        PresenceLeaves.PresenceLeaf presence = catalystLeaf ? pages.inputSection.findPresenceLeaf(path) : null;
        FormView form = new FormView(pages.font, session, path);
        form.useCatalogSelections();
        form.setConditionEditingSupport(sharedConditionSupport());
        form.setConditionContextFactory(conditionContextFactory(form));
        form.setOnChanged(() -> {
            form.applyToDraft();
            pages.shell.draftChanged();
        });
        // 输入被长度上限拒绝时立刻显示一条可读提示（字段名：问题）
        form.setOnRejectedNotice(message -> pages.shell.setNotice(message,EditorShell.NoticeLevel.DANGER));
        form.setSelectionCatalogOpener((field, fieldPath, tags, selected, onPicked) -> {
            RuleCatalogType type = tags ? RuleCatalogType.TAG : FieldCatalogTypes.catalogTypeOf(field);
            if (type == null || !pages.shell.editable()) return;
            boolean multi = field.isMultiCatalog();
            pages.modals.push(RuleEditorPanelFactory.fieldCatalogModal(pages.font, pages.shell, pages.workspace, type, multi,
                    Component.translatable(field.labelKey()), pages.shell.screenWidth(), pages.shell.screenHeight(), onPicked,
                    field, session.draft(), fieldPath, selected));
        });
        form.setDescriptor(descriptor);
        form.reload();
        if (BiomeCondition.ID.equals(leaf.condition().type())) configureBiomeFields(form, descriptor);
        pages.inputSection.configureQuantityFields(form, session, path, descriptor);
        PresenceLeaves.PresenceLeaf linked = pages.inputSection.findPresenceLeaf(path);
        if (linked != null && linked.ruleLevel()) pages.inputSection.configurePresenceWrites(form, session, linked);
        // 已知条件类型附带区间条（昼夜/高度光照）与注册表字段的目录按钮；
        // 面板自身负责子控件的事件转发与 Tab 顺序，普通数值字段仍由表单承载
        LeafPanel content = RuleEditorPanelFactory.leafPanel(pages.font, form, session, descriptor.fields(),
                path, change -> {
                    form.commitPendingInputs();
                    session.apply(EditSession.OP_SET_FIELD, change);
                    pages.shell.draftChanged();
                }, () -> {
                    form.reload();
                    pages.shell.draftChanged();
                }, pages.modals::push, pages.shell, pages.workspace, pages.shell.screenWidth(), pages.shell.screenHeight());
        String noteKey = catalystLeaf ? RuleEditorPageSupport.UI + "rule.catalyst_all_required"
                : RuleEditorPageSupport.FLUID_PRESENT_ID.equals(leaf.condition().type()) ? RuleEditorPageSupport.UI + "field.fluid_present.fluid.hint" : null;
        LeafPanel panel = noteKey == null ? content : content.addNote(() -> Component.translatable(noteKey));
        Component title = presence == null ? descriptor.label() : Component.translatable(RuleEditorPageSupport.UI + "rule.presence.title",
                descriptor.label(), presence.ordinal(), presence.scopeLabel());
        return new ConditionTreeOverlay.Parameters(form, panel, title);
    }

    private static void configureBiomeFields(FormView form, TypeEditorDescriptor descriptor) {
        BooleanSupplier climate = () -> {
            RuleValue mode = form.pendingValue(form.draftPath(RuleFields.CONDITION_MODE));
            return mode != null && mode.isText() && RuleFields.CONDITION_MODE_CLIMATE.equals(mode.asText());
        };
        form.setRowVisibility(form.draftPath(RuleFields.CONDITION_BIOMES), () -> !climate.getAsBoolean());
        for (EditorField field : descriptor.fields()) if (field.isClimateRange()) {
            form.setRowVisibility(form.draftPath(field.name()), climate);
        }
    }

    boolean openConditionTree(ConditionHostCoordination coordination, String scope, String ownerPath,
                              @Nullable String fieldPath) {
        EditSession session = pages.shell.editingSession();
        if (session == null || !pages.shell.editable()) return false;
        coordination.prepareHost();
        RuleValue owners = RuleValueReads.conditionOwnerShape(session.draft().root());
        ConditionTreeOverlay overlay = new ConditionTreeOverlay(pages.font, session, scope, sharedConditionSupport(),
                new ConditionTreeOverlay.ParameterFactory() {
                    @Override
                    public ConditionTreeOverlay.Parameters create(String path, ConditionNode.Leaf leaf) {
                        return createLeafParameters(session, path, leaf);
                    }

                    // 参数承载能力按现役描述符与绑定引用判定：不构建面板，也不写草稿
                    @Override
                    public boolean carriesField(String conditionPath, ConditionNode.Leaf leaf, String fieldPath) {
                        if (leaf.condition() == null) return false;
                        TypeEditorDescriptor descriptor = EditorRegistries.CONDITIONS.descriptorFor(leaf.condition().type());
                        return RuleEditorPanelFactory.carriesField(descriptor.fields(), conditionPath, fieldPath,
                                field -> pages.inputSection.quantityReferences(session, conditionPath, field));
                    }
                }, pages.shell::draftChanged);
        int chromeReserve = THEME.headerHeight() + THEME.padding() * 3 + UiModal.BUTTON_HEIGHT;
        UiModal modal = UiModal.create(RuleEditorEditSections.THEME, pages.font).title(Component.translatable(RuleEditorPageSupport.UI + "tree.title"))
                .preferredWidth(Math.max(UiModal.MIN_WIDTH, pages.shell.screenWidth() - RuleEditorPageSupport.TREE_OVERLAY_MARGIN))
                .contentWidget(overlay, Math.max(0, pages.shell.screenHeight() - RuleEditorPageSupport.TREE_OVERLAY_MARGIN - chromeReserve));
        // 功能栏的撤销/重做按钮与 Ctrl+Z 走同一条历史链路
        overlay.setHistoryActions(
                () -> pages.modals.requestHistory(UiHistoryShortcut.UNDO),
                () -> pages.modals.requestHistory(UiHistoryShortcut.REDO));
        modal.onClosed(() -> coordination.closeView(overlay));
        modal.onHistoryChanged(shortcut -> {
            String operation = shortcut == UiHistoryShortcut.UNDO
                    ? session.redoOpKey() : session.undoOpKey();
            boolean outerStructure = operation != null && switch (operation) {
                case EditSession.OP_ADD_EFFECT, EditSession.OP_REMOVE_EFFECT, EditSession.OP_MOVE_EFFECT,
                        EditSession.OP_ADD_CANDIDATE, EditSession.OP_REMOVE_CANDIDATE, EditSession.OP_MOVE_CANDIDATE,
                        EditSession.OP_EDIT_CANDIDATE, EditSession.OP_CONVERT_STRUCTURE,
                        EditSession.OP_CREATE_RULE, EditSession.OP_DELETE_RULE -> true;
                default -> false;
            };
            if (!ownerPath.isEmpty() && (outerStructure || session.draft().valueAt(ownerPath) == null
                    || !owners.equals(RuleValueReads.conditionOwnerShape(session.draft().root())))) {
                modal.onClosed(() -> coordination.discardView(overlay));
                pages.modals.close(modal);
            } else overlay.historyChanged();
        });
        modal.cancel(Component.translatable(RuleEditorPageSupport.UI + "button.back"));
        modal.layoutCentered(pages.shell.screenWidth(), pages.shell.screenHeight());
        pages.modals.push(modal);
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
            pages.drawText(graphics, triggerNoteRect, Component.translatable(RuleEditorPageSupport.UI + "trigger.note"), THEME.palette().warning());
        }
        // 等待时长只对自然触发生效，其他触发组合下不显示该说明
        if (RuleTriggers.allowsDelay(view)) {
            pages.drawText(graphics, delayHintRect, Component.translatable(RuleEditorPageSupport.UI + "trigger.delay_hint"), THEME.palette().textDisabled());
        }
    }
}
