package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.ResultStructure;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategoryAssignment;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormView;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;

/** 编辑器字段的有效默认值、兼容写入与分类跟随装配。 */
final class EditorFormBindings {
    private EditorFormBindings() {}

    static void configure(FormView form, String basePath) {
        form.setDefaultValues((path, owner, field) -> {
            if (RuleFields.FLUIDS.equals(field.name()) && owner.has(RuleFields.FLUID)) {
                return RuleValue.list().add(owner.child(RuleFields.FLUID).deepCopy()).build();
            }
            return path.equals(RuleFields.CONDITION) || path.endsWith("." + RuleFields.CONDITION)
                    ? BuiltinEditorDefaults.effectiveConditionValue(owner, field.name())
                    : BuiltinEditorDefaults.effectiveValue(owner, field.name());
        });
        if (RuleFields.SOURCE.equals(basePath)) form.setUndoOperation(EditSession.OP_SET_SOURCE);
        if (ResultStructure.isResultPath(basePath)) form.setRelatedEdit(root -> {
            String category = RuleCategoryAssignment.derive(root);
            return edits -> RuleCategoryAssignment.follow(edits, category);
        });
        form.addFieldWritten((edits, path, before) -> {
            var segments = DraftPaths.parse(path);
            if (!RuleFields.FLUIDS.equals(segments.getLast().name())) return;
            String owner = DraftPaths.render(segments.subList(0, segments.size() - 1));
            edits.removeAt(DraftPaths.child(owner, RuleFields.FLUID));
        });
    }
}
