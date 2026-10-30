package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormField;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** 编辑器表单的共享装配：目录、条件上下文、数量默认值和几何失效。 */
final class EditorFormFactory {
    private final EditorShell shell;
    private final Font font;
    private final RuleEditClientWorkspace workspace;
    private final UiModalStack modals;
    private final EditorConditionForms conditions;
    private final Runnable geometryChanged;

    EditorFormFactory(EditorShell shell, Font font, EditorConditionForms conditions, Runnable geometryChanged) {
        this.shell = shell;
        this.font = font;
        this.workspace = shell.workspace();
        this.modals = shell.modals();
        this.conditions = conditions;
        this.geometryChanged = geometryChanged;
    }

    FormView create(EditSession session, String basePath, TypeEditorDescriptor descriptor) {
        FormView form = new FormView(shell.theme(), font, session, basePath);
        // 表单行几何变化时让页面布局缓存失效，页面滚动帧因此不必重排
        form.setOnGeometryChanged(geometryChanged);
        form.useCatalogSelections();
        form.setConditionEditingSupport(conditions.sharedConditionSupport());
        form.setConditionContextFactory(conditions.conditionContextFactory(form));
        form.setOnChanged(shell::draftChanged);
        // 输入超过长度上限时立即提示，格式为「字段名：原因」
        form.setOnRejectedNotice(message -> shell.setNotice(message,EditorShell.NoticeLevel.DANGER));
        form.setSelectionCatalogOpener((field, path, tags, selected, onPicked) -> {
            if (RuleFields.SOURCE.equals(basePath) && RuleFields.SOURCE_EXCLUDE.equals(field.name())) {
                if (shell.editable()) modals.push(SourceBlacklistPicker.modal(font, shell, session.root(),
                        shell.screenWidth(), shell.screenHeight(), onPicked));
                return;
            }
            RuleCatalogType type = tags ? RuleCatalogType.TAG : FieldCatalogTypes.catalogTypeOf(field);
            if (type == null || !shell.editable()) return;
            boolean multi = field.isMultiCatalog();
            // 目录过滤条件按行配置；复合控件转发的子字段没有独立行，因此不加过滤
            FormField row = form.findField(field.name());
            modals.push(RuleEditorPanelFactory.fieldCatalogModal(font, shell, workspace, type, multi,
                    Component.translatable(field.labelKey()), shell.screenWidth(), shell.screenHeight(), onPicked,
                    field, session.root(), path, selected, row == null ? null : row.catalogFilter()));
        });
        var handles = form.setDescriptor(descriptor);
        form.reload();
        form.useCardSpacing();
        EditorQuantityBindings.configureQuantityFields(session, basePath, handles);
        return form;
    }
}
