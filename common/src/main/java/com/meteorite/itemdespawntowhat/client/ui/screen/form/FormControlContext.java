package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogSelectionBinding.Mode;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** 复合控件的装配能力；草稿事务、行守卫与子表单继承由表单负责。 */
final class FormControlContext {
    private final FormView form;

    FormControlContext(FormView form) { this.form = form; }

    FormView newChildForm(FieldPath relative, List<EditorField> fields) { return form.newChildForm(relative, fields); }
    FieldPath childPath(FieldPath relative) { return form.childPath(relative); }
    void clearChildInputs(FieldPath relative) { form.clearChildInputs(relative); }
    void changeChildStructure(String relative, Runnable change, Supplier<RuleValue> value) {
        form.changeChildStructure(relative, change, value);
    }
    void openReferenceChoice(FormControl control, EditorField field, Supplier<FieldPath> path, Mode mode,
                             Supplier<List<String>> selected, Consumer<List<String>> picked) {
        form.openReferenceChoice(control, field, path, mode, selected, picked);
    }
    @Nullable ConditionEditingSupport conditionSupport() { return form.conditionEditingSupport(); }
    @Nullable ConditionEditContext conditionContext(EditorField field) { return form.conditionContext(field); }
}
