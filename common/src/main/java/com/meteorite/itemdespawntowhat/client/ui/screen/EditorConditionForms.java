package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeViewModel;
import net.minecraft.client.gui.Font;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditContextFactory;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorRegistries;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.ResultStructure;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiHistoryShortcut;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditContext;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionEditingSupport;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionHostCoordination;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.ConditionTreeOverlay;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.LeafPanel;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.ConditionTreeContract;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModal;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.type.condition.BiomeCondition;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 条件画布、局部参数表与原宿主的装配协调。 */
final class EditorConditionForms {
    private final UiHostTheme THEME;
    private final EditorShell shell;
    private final Font font;
    private final RuleEditClientWorkspace workspace;
    private final UiModalStack modals;
    private final ConditionEditingSupport baseSupport;

    EditorConditionForms(EditorShell shell, Font font, ConditionEditingSupport baseSupport) {
        this.THEME = shell.theme();
        this.shell = shell;
        this.font = font;
        this.workspace = shell.workspace();
        this.modals = shell.modals();
        this.baseSupport = baseSupport;
    }

    // 共享的条件定义与按场景创建：不捕获表单、树或导航状态，可单独装配
    ConditionEditingSupport sharedConditionSupport() {
        ConditionEditingSupport base = baseSupport;
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
                shell.draftChanged();
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
            public boolean open(@Nullable String treePath, ConditionTreeViewModel viewModel) {
                // null 是显式「只打开作用域画布」；非空坐标转换失败则不开画布，也不提交原宿主输入
                if (treePath == null) return openConditionTree(coordination, scope, ownerPath, null, viewModel);
                String absolute = absolutePath(treePath);
                if (absolute == null) return false;
                return openConditionTree(coordination, scope, ownerPath, absolute, viewModel);
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
        PresenceLeaves.PresenceLeaf presence = catalystLeaf ? EditorQuantityBindings.findPresenceLeaf(session, path) : null;
        FormView form = new FormView(shell.theme(), font, session, path);
        form.useCatalogSelections();
        form.setConditionEditingSupport(sharedConditionSupport());
        form.setConditionContextFactory(conditionContextFactory(form));
        form.setOnChanged(shell::draftChanged);
        // 输入被长度上限拒绝时立刻显示一条可读提示（字段名：问题）
        form.setOnRejectedNotice(message -> shell.setNotice(message,EditorShell.NoticeLevel.DANGER));
        form.setSelectionCatalogOpener((field, fieldPath, tags, multiple, selected, onPicked) -> {
            RuleCatalogType type = tags ? RuleCatalogType.TAG : FieldCatalogTypes.catalogTypeOf(field);
            if (type == null || !shell.editable()) return;
            modals.push(RuleEditorPanelFactory.fieldCatalogModal(font, shell, workspace, type, multiple,
                    Component.translatable(field.labelKey()), shell.screenWidth(), shell.screenHeight(), onPicked,
                    field, session.root(), fieldPath, selected));
        });
        form.setDescriptor(descriptor);
        form.reload();
        if (BiomeCondition.ID.equals(leaf.condition().type())) configureBiomeFields(form, descriptor);
        EditorQuantityBindings.configureQuantityFields(session, path, form.fields());
        PresenceLeaves.PresenceLeaf linked = EditorQuantityBindings.findPresenceLeaf(session, path);
        if (linked != null && linked.ruleLevel()) EditorQuantityBindings.configurePresenceWrites(form, session, linked);
        // 已知条件类型附带区间条（昼夜/高度光照）与注册表字段的目录按钮；
        // 面板自身负责子控件的事件转发与 Tab 顺序，普通数值字段仍由表单承载
        LeafPanel content = RuleEditorPanelFactory.leafPanel(font, form, session, descriptor.fields(),
                path, change -> {
                    form.commitPendingInputs();
                    session.apply(EditSession.OP_SET_FIELD, change);
                    shell.draftChanged();
                }, () -> {
                    form.reload();
                    shell.draftChanged();
                }, modals::push, shell, workspace, shell.screenWidth(), shell.screenHeight());
        String noteKey = catalystLeaf ? RuleEditorPageSupport.UI + "rule.catalyst_all_required"
                : RuleEditorPageSupport.FLUID_PRESENT_ID.equals(leaf.condition().type()) ? RuleEditorPageSupport.UI + "field.fluid_present.fluid.hint" : null;
        LeafPanel panel = noteKey == null ? content : content.addNote(() -> Component.translatable(noteKey));
        Component title = presence == null ? descriptor.label() : Component.translatable(RuleEditorPageSupport.UI + "rule.presence.title",
                descriptor.label(), presence.ordinal(), presence.scopeLabel());
        return new ConditionTreeOverlay.Parameters(form, panel, title);
    }

    private static void configureBiomeFields(FormView form, TypeEditorDescriptor descriptor) {
        var modeField = form.field(RuleFields.CONDITION_MODE);
        BooleanSupplier climate = () -> {
            RuleValue mode = modeField.pendingValue();
            return mode != null && mode.isText() && RuleFields.CONDITION_MODE_CLIMATE.equals(mode.asText());
        };
        form.field(RuleFields.CONDITION_BIOMES).visibleWhen(() -> !climate.getAsBoolean());
        for (EditorField field : descriptor.fields()) if (field.isClimateRange()) {
            form.field(field.name()).visibleWhen(climate);
        }
    }

    boolean openConditionTree(ConditionHostCoordination coordination, String scope, String ownerPath,
                              @Nullable String fieldPath, ConditionTreeViewModel viewModel) {
        EditSession session = shell.editingSession();
        if (session == null || !shell.editable()) return false;
        coordination.prepareHost();
        RuleValue owners = ResultStructure.conditionOwnerShape(session.root());
        ConditionTreeOverlay overlay = new ConditionTreeOverlay(THEME, shell.chromeTheme(), font, session, scope, sharedConditionSupport(), viewModel,
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
                                field -> EditorQuantityBindings.quantityReferences(session, conditionPath, field));
                    }
                }, shell::draftChanged);
        int chromeReserve = THEME.headerHeight() + THEME.padding() * 3 + UiModal.BUTTON_HEIGHT;
        UiModal modal = UiModal.create(THEME, font).title(Component.translatable(RuleEditorPageSupport.UI + "tree.title"))
                .preferredWidth(Math.max(UiModal.MIN_WIDTH, shell.screenWidth() - RuleEditorPageSupport.TREE_OVERLAY_MARGIN))
                .contentWidget(overlay, Math.max(0, shell.screenHeight() - RuleEditorPageSupport.TREE_OVERLAY_MARGIN - chromeReserve));
        // 功能栏的撤销/重做按钮与 Ctrl+Z 走同一条历史链路
        overlay.setHistoryActions(
                () -> modals.requestHistory(UiHistoryShortcut.UNDO),
                () -> modals.requestHistory(UiHistoryShortcut.REDO));
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
            if (!ownerPath.isEmpty() && (outerStructure || session.valueAt(ownerPath) == null
                    || !owners.equals(ResultStructure.conditionOwnerShape(session.root())))) {
                modal.onClosed(() -> coordination.discardView(overlay));
                modals.close(modal);
            } else overlay.historyChanged();
        });
        modal.cancel(Component.translatable(RuleEditorPageSupport.UI + "button.back"));
        modal.layoutCentered(shell.screenWidth(), shell.screenHeight());
        modals.push(modal);
        if (fieldPath != null) overlay.reveal(fieldPath);
        return true;
    }

}
