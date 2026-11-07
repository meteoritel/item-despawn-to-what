package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.FieldPath;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** 表单装载与提交协议；候选收集、同次历史写入、接受基线及视图同步按顺序执行。 */
final class FormCommit {
    private enum InputMode { IDLE, FINISH }

    private record FieldChange(FormView form, FormRow row, FieldPath path,
                               @Nullable RuleValue before, @Nullable RuleValue next) {
        boolean changed() { return !Objects.equals(before, next); }
        boolean attached() { return row.attached && row.path.equals(path); }
    }

    private final FormView view;

    FormCommit(FormView view) { this.view = view; }

    void reload() {
        retainPendingInputs();
        view.loadedRevision = view.session.revision();
        RuleValue owner = view.basePath.isRoot() ? view.session.root() : view.session.valueAt(view.basePath);
        view.acceptedObject = view.parent == null ? null : copy(owner);
        for (FormRow row : List.copyOf(view.rows)) {
            if (row.attached) loadRow(row, owner);
        }
    }

    void reloadWith(RuleValue object) {
        retainPendingInputs();
        view.loadedRevision = view.session.revision();
        view.acceptedObject = view.parent == null ? null : object.deepCopy();
        for (FormRow row : List.copyOf(view.rows)) {
            if (row.attached) loadRow(row, object);
        }
    }

    private void loadRow(FormRow row, @Nullable RuleValue owner) {
        if (row.beforeLoad != null) row.beforeLoad.run();
        RuleValue original = owner == null ? null : owner.child(row.field.name());
        RuleValue effective = view.defaultValue(row, owner == null ? RuleValue.mapping().build() : owner);
        row.defaultDisplayed = original == null && effective != null;
        RuleValue displayed = row.displayValue.apply(row.defaultDisplayed ? effective : original);
        row.control.load(displayed);
        row.control.rememberLoaded(original);
        row.loadedInputs = List.copyOf(row.control.inputText());
        row.loaded = true;
        restorePendingInput(row);
    }

    void reloadField(FormRow row) {
        if (!row.attached) return;
        view.session.clearPendingInput(row.path);
        view.loadedRevision = view.session.revision();
        RuleValue owner = view.basePath.isRoot() ? view.session.root() : view.session.valueAt(view.basePath);
        loadRow(row, owner);
    }

    private void restorePendingInput(FormRow row) {
        EditSession.PendingInput pending = view.session.pendingInput(row.path);
        if (pending == null) return;
        row.control.restoreInputText(pending.text());
        retainRowInput(row);
    }

    void retainPendingInputs() {
        if (view.loadedRevision != view.session.revision()) return;
        for (FormRow row : view.rows) {
            row.control.retainChildInputs();
            retainRowInput(row);
        }
    }

    void retainRowInput(FormRow row) {
        if (view.loadedRevision != view.session.revision() || !row.attached || !row.loaded) return;
        List<String> text = row.control.inputText();
        List<FormIssue> issues = row.visible ? row.control.issues(row.path) : List.of();
        if (!text.equals(row.loadedInputs) || !text.isEmpty() && issues.stream().anyMatch(FormIssue::blocking)) {
            view.session.retainPendingInput(row.path, text, issues);
        } else {
            view.session.clearPendingInput(row.path);
        }
    }

    boolean commitPendingInputs() { return submit(InputMode.FINISH); }
    boolean applyToDraft() { return submit(InputMode.IDLE); }

    private boolean submit(InputMode mode) {
        if (view.loadedRevision != view.session.revision()) {
            reload();
            return false;
        }
        retainPendingInputs();
        boolean written = false;
        for (FormRow row : List.copyOf(view.rows)) {
            if (!row.attached || !row.visible || row.field.isDisplayOnly() || row.path.isRoot()) continue;
            if (mode == InputMode.FINISH) row.control.finishInput();
            if (!row.attached) continue;
            List<FieldChange> fields = new ArrayList<>();
            collect(view, row, mode, fields);
            written |= write(fields, view.undoOperation(row));
        }
        retainPendingInputs();
        return written;
    }

    private static void collect(FormView form, FormRow row, InputMode mode, List<FieldChange> fields) {
        if (!row.attached || !row.visible || row.field.isDisplayOnly()) return;
        List<FormView> children = row.control.childForms();
        if (!children.isEmpty()) {
            for (FormView child : children) {
                if (child.loadedRevision != child.session.revision()) child.reload();
                for (FormRow nested : child.rows) collect(child, nested, mode, fields);
            }
            return;
        }
        if (mode == InputMode.IDLE && row.control.isEditing()
                || row.control.issues(row.path).stream().anyMatch(FormIssue::blocking)) return;
        fields.add(new FieldChange(form, row, row.path, copy(form.session.valueAt(row.path)), row.control.draftValue()));
    }

    void writeField(FormRow row, @Nullable RuleValue next) {
        if (!row.attached || view.loadedRevision != view.session.revision()) return;
        List<FieldChange> fields = new ArrayList<>();
        if (row.control.childForms().isEmpty()) {
            fields.add(new FieldChange(view, row, row.path, copy(view.session.valueAt(row.path)), copy(next)));
        } else {
            collect(view, row, InputMode.FINISH, fields);
        }
        write(fields, view.undoOperation(row));
    }

    private boolean write(List<FieldChange> fields, String operation) {
        if (fields.isEmpty() || fields.stream().anyMatch(field -> !field.attached())) return false;
        view.rootForm().retainPendingInputs();
        List<FieldChange> changed = fields.stream().filter(FieldChange::changed).toList();
        long revision = view.session.revision();
        if (fields.stream().anyMatch(field -> field.form.loadedRevision != revision)) return false;
        Map<FieldPath, RuleValue> writes = prepareWrites(changed);
        if (writes == null) return false;
        Set<FormView> forms = new LinkedHashSet<>();
        for (FieldChange field : changed) {
            for (FormView form = field.form; form != null; form = form.parent) forms.add(form);
        }
        List<Consumer<RuleDraft>> related = new ArrayList<>();
        for (FormView form : forms) {
            Consumer<RuleDraft> edit = form.relatedEdit();
            if (edit != null) related.add(edit);
        }
        // 已提交字段的输入不进入撤销前快照，撤销后显示历史值；非法兄弟字段仍保留。
        for (FieldChange field : fields) view.session.clearPendingInput(field.path);
        if (!changed.isEmpty()) {
            view.session.apply(operation, edits -> {
                applyWrites(edits, writes);
                for (FieldChange field : changed) field.form.notifyFieldWritten(edits, field.path, field.before);
                for (Consumer<RuleDraft> edit : related) edit.accept(edits);
            });
        }
        if (!changed.isEmpty()) synchronize(view.rootForm());
        for (FieldChange field : fields) {
            if (field.attached()) field.form.acceptInput(field.row);
        }
        // 回调可能重载或卸载字段，接受基线必须在它们之前完成。
        for (FieldChange field : changed) {
            if (field.attached()) field.form.notifyFieldCommitted(field.path);
        }
        return !changed.isEmpty();
    }

    // 只根据本批合法叶字段创建所属对象，非法兄弟输入继续留在会话。
    private @Nullable Map<FieldPath, RuleValue> prepareWrites(List<FieldChange> fields) {
        Map<FieldPath, RuleValue> writes = new LinkedHashMap<>();
        if (fields.isEmpty()) return writes;
        RuleDraft candidate = RuleDraft.of(view.session.root());
        Set<FieldPath> created = new LinkedHashSet<>();
        for (FieldChange field : fields) {
            List<FormView> owners = new ArrayList<>();
            for (FormView form = field.form; form != null; form = form.parent) owners.add(form);
            for (FormView owner : owners.reversed()) {
                if (owner.basePath.isRoot()) continue;
                RuleValue value = candidate.valueAt(owner.basePath);
                if (value != null && value.isMapping()) continue;
                if (!candidate.canSetAt(owner.basePath)) return null;
                candidate.setAt(owner.basePath, RuleValue.mapping().build());
                created.add(owner.basePath);
            }
            if (!candidate.canSetAt(field.path)) return null;
            if (field.next == null) candidate.removeAt(field.path);
            else candidate.setAt(field.path, field.next);
        }
        for (FieldChange field : fields) {
            FieldPath target = field.path;
            for (FieldPath owner : created) {
                if (target.isWithin(owner)) target = owner;
            }
            writes.put(target, copy(candidate.valueAt(target)));
        }
        return writes;
    }

    private static void applyWrites(RuleDraft edits, Map<FieldPath, RuleValue> writes) {
        writes.forEach((path, value) -> {
            if (value == null) edits.removeAt(path);
            else edits.setAt(path, value);
        });
    }

    void acceptInput(FormRow row) {
        if (!row.attached) return;
        row.control.acceptValue(view.session.valueAt(row.path));
        row.loadedInputs = List.copyOf(row.control.inputText());
        List<FormIssue> issues = row.control.issues(row.path);
        if (!row.loadedInputs.isEmpty() && issues.stream().anyMatch(FormIssue::blocking)) {
            view.session.retainPendingInput(row.path, row.loadedInputs, issues);
        } else {
            view.session.clearPendingInput(row.path);
        }
    }

    private static void synchronize(FormView form) {
        form.loadedRevision = form.session.revision();
        RuleValue accepted = form.basePath.isRoot() ? form.session.root() : form.session.valueAt(form.basePath);
        form.acceptedObject = form.parent == null ? null : copy(accepted);
        for (FormRow row : form.rows) {
            if (!row.control.childForms().isEmpty()) row.control.rememberLoaded(form.session.valueAt(row.path));
            for (FormView child : row.control.childForms()) synchronize(child);
        }
    }

    void changeStructure(String relative, Runnable change, Supplier<RuleValue> candidate) {
        commitPendingInputs();
        FormRow row = view.rows.stream().filter(field -> field.field.name().equals(relative)).findFirst().orElseThrow();
        if (!row.attached) return;
        RuleValue before = copy(view.session.valueAt(row.path));
        Map<FieldPath, RuleValue> owners = prepareWrites(List.of(new FieldChange(view, row, row.path, before, before)));
        if (owners == null) return;
        List<Consumer<RuleDraft>> related = new ArrayList<>();
        for (FormView form = view; form != null; form = form.parent) {
            Consumer<RuleDraft> edit = form.relatedEdit();
            if (edit != null) related.add(edit);
        }
        // 子项重排的输入路径映射与容器变更必须记录在同一次历史中；候选读取本身是纯操作。
        view.session.apply(view.undoOperation(row), edits -> {
            applyWrites(edits, owners);
            if (!row.control.inputText().isEmpty()) {
                // 卡片文本按整行保存；条目增删后为新布局建立独立输入身份。
                view.session.clearPendingInput(row.path);
                view.session.recordInputStructure(Set.of(row.path), Set.of(row.path));
            }
            change.run();
            edits.setAt(row.path, candidate.get());
            view.notifyFieldWritten(edits, row.path, before);
            for (Consumer<RuleDraft> edit : related) edit.accept(edits);
        });
        synchronize(view.rootForm());
        acceptInput(row);
        view.notifyFieldCommitted(row.path);
        view.invalidateMeasure();
    }

    private static @Nullable RuleValue copy(@Nullable RuleValue value) {
        return value == null ? null : value.deepCopy();
    }
}
