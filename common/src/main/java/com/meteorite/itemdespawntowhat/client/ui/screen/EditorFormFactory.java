package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiModalStack;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.client.gui.Font;

/** 编辑器表单的共享装配：目录、条件上下文、数量默认值和几何失效。 */
final class EditorFormFactory {
    private final EditorShell shell;
    private final Font font;
    private final UiModalStack modals;
    private final EditorConditionForms conditions;
    private final Runnable geometryChanged;

    EditorFormFactory(EditorShell shell, Font font, EditorConditionForms conditions, Runnable geometryChanged) {
        this.shell = shell;
        this.font = font;
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
        CatalogSelectionOpener catalog = new CatalogSelectionOpener(shell, font, session);
        form.setSelectionCatalogOpener((field, path, tags, filter, binding) -> {
            if (RuleFields.SOURCE.equals(basePath) && RuleFields.SOURCE_EXCLUDE.equals(field.name())) {
                if (shell.editable() && shell.editingSession() == session) {
                    modals.push(SourceBlacklistPicker.modal(font, shell, session, session.root(),
                            shell.screenWidth(), shell.screenHeight(), binding));
                }
                return;
            }
            catalog.open(field, path, tags, filter, binding);
        });
        var handles = form.setDescriptor(descriptor);
        form.reload();
        form.useCardSpacing();
        EditorQuantityBindings.configureQuantityFields(session, basePath, handles);
        return form;
    }
}
