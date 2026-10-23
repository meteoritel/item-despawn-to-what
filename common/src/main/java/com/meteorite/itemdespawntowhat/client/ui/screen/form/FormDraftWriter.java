package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.DraftPaths;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategoryAssignment;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CatalystCost;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

/**
 * 表单草稿写入：装载、会话输入保留与逐字段回写草稿。
 *
 * <p>所有写入仍经 {@link EditSession}（统一撤销门面）；本类只搬运数据，不持有界面几何。
 * 字段级撤销语义（未编辑不回写、无变化不记历史、关联字段同一条记录）在此保持原样。</p>
 */
final class FormDraftWriter {

    private final FormView view;

    FormDraftWriter(FormView view) {
        this.view = view;
    }

    // 从草稿重新装载全部控件
    void reload() {
        retainPendingInputs();
        view.loadedRevision = view.session.revision();
        RuleDraft draft = view.session.draft();
        for (FormRow row : view.rows) {
            RuleValue original = draft.valueAt(row.path);
            RuleValue container = view.basePath.isEmpty() ? draft.root() : draft.valueAt(view.basePath);
            if (row.control instanceof ItemCountsControl counts) {
                RuleValue owner = container != null && container.isMapping() ? container : RuleValue.mapping().build();
                boolean source = RuleFields.SOURCE_COST.equals(row.field.name());
                RuleValue references = source
                        ? draft.valueAt(DraftPaths.child(RuleFields.SOURCE, RuleFields.SOURCE_ITEMS))
                        : owner.child(RuleFields.CATALYST_ITEMS);
                List<String> ids = stringReferences(references);
                int fallback = row.field.intMin(CatalystCost.DEFAULT_COUNT);
                counts.setDefaultCount(fallback);
                if (source || owner.has(RuleFields.CATALYST_ITEMS)) counts.setReferences(ids);
            }
            RuleValue effective = original == null && row.defaultValue != null ? row.defaultValue.get()
                    : original == null && container != null && container.isMapping()
                    ? effectiveValue(container, row.field.name()) : null;
            row.defaultDisplayed = original == null && effective != null;
            RuleValue displayed = row.defaultDisplayed ? effective : original;
            if (row.control instanceof ItemCountsControl && row.defaultValue != null && original != null && original.isMapping()) {
                RuleValue.Mapping merged = RuleValue.mapping(row.defaultValue.get());
                for (Map.Entry<String, RuleValue> entry : original.entries().entrySet()) {
                    merged.put(entry.getKey(), entry.getValue().deepCopy());
                }
                displayed = merged.build();
            }
            row.control.load(displayed);
            row.control.rememberLoaded(original);
            row.loadedInputs = row.control.inputText();
            row.loaded = true;
            EditSession.PendingInput pending = view.session.pendingInput(row.path);
            if (pending != null) row.control.restoreInputText(pending.text());
        }
    }

    private static @Nullable RuleValue effectiveValue(RuleValue owner, String field) {
        if (RuleFields.FLUIDS.equals(field) && owner.has(RuleFields.FLUID)) {
            RuleValue fluid = owner.child(RuleFields.FLUID);
            if (fluid != null) return RuleValue.list().add(fluid.deepCopy()).build();
        }
        return BuiltinEditorDefaults.effectiveValue(owner, field);
    }

    private static List<String> stringReferences(@Nullable RuleValue raw) {
        List<String> result = new ArrayList<>();
        if (raw != null && raw.isList()) for (RuleValue value : raw.items()) {
            if (value.isText()) result.add(value.asText());
        }
        return result;
    }

    // 用给定对象装载控件（子列表新建子项时使用，不读草稿）
    void reloadWith(RuleValue object) {
        retainPendingInputs();
        view.loadedRevision = view.session.revision();
        for (FormRow row : view.rows) {
            RuleValue value = object.has(row.field.name()) ? object.child(row.field.name()) : null;
            row.control.load(value);
            row.loadedInputs = row.control.inputText();
            row.loaded = true;
            EditSession.PendingInput pending = view.session.pendingInput(row.path);
            if (pending != null) row.control.restoreInputText(pending.text());
        }
    }

    /**
     * 保存会话内输入，不改变规则 JSON 或配置历史。
     */
    void retainPendingInputs() {
        // 历史或结构操作已恢复路径时，旧控件不得再按旧路径覆盖会话输入。
        if (view.loadedRevision != view.session.revision()) return;
        for (FormRow row : view.rows) {
            row.control.retainChildInputs();
            if (!row.loaded) continue;
            List<String> text = row.control.inputText();
            if (!text.equals(row.loadedInputs)) {
                view.session.retainPendingInput(row.path, text, row.visible ? row.control.issues(row.path) : List.of());
            } else view.session.clearPendingInput(row.path);
        }
    }

    boolean applyToDraft(boolean finish) {
        if (view.loadedRevision != view.session.revision()) reload();
        retainPendingInputs();
        RuleDraft draft = view.session.draft();
        boolean written = false;
        for (FormRow row : view.rows) {
            if (!row.visible || row.field.type() == EditorFieldType.NOTE || row.path.isEmpty()) {
                continue;
            }
            if (finish) row.control.finishInput();
            if ((!finish && row.control.isEditing()) || (!row.control.inputText().isEmpty()
                    && row.control.issues(row.path).stream().anyMatch(FormIssue::blocking))) continue;
            RuleValue next = row.control.store();
            RuleValue current = draft.valueAt(row.path);
            if (Objects.equals(current, next)) {
                if (finish || !row.control.isEditing()) acceptInput(row);
                continue;
            }
            writeField(row, next);
            acceptInput(row);
            written = true;
        }
        retainPendingInputs();
        return written;
    }

    void acceptInput(FormRow row) {
        row.loadedInputs = row.control.inputText();
        row.control.rememberLoaded(view.session.draft().valueAt(row.path));
        row.control.inputAccepted();
        view.session.clearPendingInput(row.path);
    }

    // 文本提交、卡片移除和目录添加共用写入入口。
    void writeField(FormRow row, @Nullable RuleValue next) {
        RuleDraft draft = view.session.draft();
        RuleValue current = draft.valueAt(row.path);
        if (Objects.equals(current, next)) return;
        RuleValue before = current == null ? null : current.deepCopy();
        boolean resultField = row.path.startsWith(RuleFields.EFFECTS + "[")
                || row.path.startsWith(RuleFields.OUTCOMES + "[");
        String categoryBefore = resultField ? RuleCategoryAssignment.derive(draft.root()) : null;
        view.session.clearPendingInput(row.path);
        view.session.apply(opKeyFor(row), () -> {
            if (next == null) draft.removeAt(row.path);
            else draft.setAt(row.path, next.deepCopy());
            if (RuleFields.FLUIDS.equals(row.field.name())) draft.removeAt(view.childPath(RuleFields.FLUID));
            if (RuleFields.CATALYST_ITEMS.equals(row.field.name())) {
                RuleValue amounts = draft.valueAt(view.childPath(RuleFields.ITEM_COUNTS));
                if (amounts != null && amounts.isMapping()) {
                    RuleValue.Mapping selected = RuleValue.mapping();
                    for (String id : stringReferences(next)) {
                        RuleValue amount = amounts.has(id) ? amounts.child(id) : null;
                        if (amount != null) selected.put(id, amount.deepCopy());
                    }
                    draft.setAt(view.childPath(RuleFields.ITEM_COUNTS), selected.build());
                }
            }
            view.notifyFieldWritten(row.path, before);
            // 产出参数补齐后才可能解码成功；分类与字段及其关联更新共用一条撤销记录。
            if (resultField) RuleCategoryAssignment.follow(draft, categoryBefore);
        });
        view.loadedRevision = view.session.revision();
        if (RuleFields.CATALYST_ITEMS.equals(row.field.name())) {
            // 只刷新数量行，保留其它字段尚未提交的输入。
            for (FormRow quantity : view.rows)
                if (quantity.control instanceof ItemCountsControl counts) {
                    counts.setReferences(stringReferences(next));
                    int fallback = quantity.field.intMin(CatalystCost.DEFAULT_COUNT);
                    counts.setDefaultCount(fallback);
                    counts.load(draft.valueAt(quantity.path));
                }
        }
    }

    // 撤销操作名：来源列表单独成项，其余交给控件自报
    private static String opKeyFor(FormRow row) {
        if (row.path.equals(RuleFields.SOURCE) || row.path.startsWith(RuleFields.SOURCE + ".")) {
            return EditSession.OP_SET_SOURCE;
        }
        return row.control.undoOpKey();
    }
}
