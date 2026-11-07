package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.ResultStructure;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategoryAssignment;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;

/** 编辑器字段的有效默认值、兼容写入与分类跟随装配。 */
final class EditorFormBindings {
    private EditorFormBindings() {}

    static void configure(FormView form, FieldPath basePath) {
        form.setDefaultValues((path, owner, field) -> {
            RuleValue legacyFluid = owner.child(RuleFields.FLUID);
            if (RuleFields.FLUIDS.equals(field.name()) && legacyFluid != null) {
                return RuleValue.list().add(legacyFluid.deepCopy()).build();
            }
            return RuleFields.CONDITION.equals(path.lastKey())
                    ? BuiltinEditorDefaults.effectiveConditionValue(owner, field.name())
                    : BuiltinEditorDefaults.effectiveValue(owner, field.name());
        });
        if (FieldPath.key(RuleFields.SOURCE).equals(basePath)) form.setUndoOperation(EditSession.OP_SET_SOURCE);
        if (ResultStructure.isResultPath(basePath)) form.setRelatedEdit(root -> {
            String category = RuleCategoryAssignment.derive(root);
            return edits -> RuleCategoryAssignment.follow(edits, category);
        });
        form.addFieldWritten((edits, path, before) -> {
            if (!RuleFields.FLUIDS.equals(path.lastKey())) return;
            edits.removeAt(path.parent().child(RuleFields.FLUID));
        });
    }
}
