package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.client.ui.screen.form.FormIssue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * 规则编辑会话：字段和树操作经由 apply 写入完整规则快照历史。
 * 未完成文本独立保留在会话缓冲，历史恢复同时恢复其节点路径。
 */
public final class EditSession {

    // 撤销/重做历史容量上限
    public static final int HISTORY_LIMIT = 100;

    // 操作标签（i18n key）
    public static final String OP_SET_FIELD = "gui.itemdespawntowhat.edit.undo.set_field";
    public static final String OP_SET_SOURCE = "gui.itemdespawntowhat.edit.undo.set_source";
    public static final String OP_ADD_CONDITION = "gui.itemdespawntowhat.edit.undo.add_condition";
    public static final String OP_ADD_GROUP = "gui.itemdespawntowhat.edit.undo.add_group";
    public static final String OP_WRAP_NOT = "gui.itemdespawntowhat.edit.undo.wrap_not";
    public static final String OP_DELETE_NODE = "gui.itemdespawntowhat.edit.undo.delete_node";
    public static final String OP_MOVE_NODE = "gui.itemdespawntowhat.edit.undo.move_node";
    public static final String OP_TOGGLE_KIND = "gui.itemdespawntowhat.edit.undo.toggle_kind";
    public static final String OP_ADD_EFFECT = "gui.itemdespawntowhat.edit.undo.add_effect";
    public static final String OP_REMOVE_EFFECT = "gui.itemdespawntowhat.edit.undo.remove_effect";
    public static final String OP_MOVE_EFFECT = "gui.itemdespawntowhat.edit.undo.move_effect";
    public static final String OP_RESTORE_ORIGINAL = "gui.itemdespawntowhat.edit.undo.restore_original";
    public static final String OP_CREATE_RULE = "gui.itemdespawntowhat.edit.undo.create_rule";
    public static final String OP_EDIT_CONDITIONS = "gui.itemdespawntowhat.edit.undo.edit_conditions";
    public static final String OP_DELETE_RULE = "gui.itemdespawntowhat.edit.undo.delete_rule";
    // 候选结果与结构互斥转换的操作标签
    public static final String OP_ADD_CANDIDATE = "gui.itemdespawntowhat.edit.undo.add_candidate";
    public static final String OP_REMOVE_CANDIDATE = "gui.itemdespawntowhat.edit.undo.remove_candidate";
    public static final String OP_MOVE_CANDIDATE = "gui.itemdespawntowhat.edit.undo.move_candidate";
    public static final String OP_EDIT_CANDIDATE = "gui.itemdespawntowhat.edit.undo.edit_candidate";
    public static final String OP_CONVERT_STRUCTURE = "gui.itemdespawntowhat.edit.undo.convert_structure";

    // 编辑目标 id（规则 id 或新建规则的目标 id）
    private final String targetId;
    // 操作前快照栈（栈顶为最近一次操作）
    private final Deque<Entry> undoStack = new ArrayDeque<>();
    private final Deque<Entry> redoStack = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private RuleDraft draft;
    private final Map<String, PendingInput> pendingInputs = new LinkedHashMap<>();
    private long nextInputIdentity;
    private long revision;
    private @Nullable Map<String, String> transactionInputPaths;

    public long revision() { return revision; }

    /** 会话内输入文本独立于已接受的规则 JSON，不写入磁盘或网络。 */
    public record PendingInput(long identity, List<String> text, List<FormIssue> issues) {
        public PendingInput {
            text = List.copyOf(text);
            issues = List.copyOf(issues);
        }
    }

    public void retainPendingInput(String path, List<String> text, List<FormIssue> issues) {
        PendingInput previous = pendingInputs.get(path);
        long identity = previous == null ? ++nextInputIdentity : previous.identity();
        pendingInputs.put(path, new PendingInput(identity, text, issues));
    }

    public @Nullable PendingInput pendingInput(String path) {
        return pendingInputs.get(path);
    }

    public void clearPendingInput(String path) {
        pendingInputs.remove(path);
    }

    public List<FormIssue> pendingInputIssues() {
        return pendingInputs.values().stream().flatMap(input -> input.issues().stream()).distinct().toList();
    }

    public boolean hasPendingInput(String scope) {
        return pendingInputs.keySet().stream().anyMatch(path -> within(path, scope));
    }

    /** 删除节点时移除其输入；移动节点时由宿主同步迁移路径。 */
    public void clearPendingInputs(String scope) {
        pendingInputs.keySet().removeIf(path -> within(path, scope));
    }

    public void remapPendingInputs(String from, String to) {
        remapPendingInputs(Map.of(from, to));
    }

    /** 同时迁移所有路径，交换兄弟节点时不会互相覆盖。 */
    public void remapPendingInputs(Map<String, String> paths) {
        if (transactionInputPaths != null) transactionInputPaths.putAll(paths);
        Map<String, PendingInput> moved = new LinkedHashMap<>();
        for (var entry : pendingInputs.entrySet()) {
            String from = paths.keySet().stream().filter(scope -> within(entry.getKey(), scope))
                    .max(java.util.Comparator.comparingInt(String::length)).orElse(null);
            String path = from == null ? entry.getKey() : paths.get(from) + entry.getKey().substring(from.length());
            moved.put(path, atPath(entry.getValue(), entry.getKey(), path));
        }
        pendingInputs.clear();
        pendingInputs.putAll(moved);
    }

    private static PendingInput atPath(PendingInput input, String from, String to) {
        List<FormIssue> issues = input.issues().stream().map(issue ->
                new FormIssue(within(issue.path(), from) ? to + issue.path().substring(from.length()) : issue.path(),
                        issue.label(), issue.message(), issue.blocking())).toList();
        return new PendingInput(input.identity(), input.text(), issues);
    }

    private static boolean within(String path, String scope) {
        return scope.isEmpty() || path.equals(scope) || path.startsWith(scope + ".") || path.startsWith(scope + "[");
    }

    // 目标级布尔标记的读写桥（「恢复原始版本」这类不改变 JSON 的编辑用它进历史）
    private @Nullable BooleanSupplier flagReader;
    private @Nullable Consumer<Boolean> flagWriter;

    // 历史条目：操作标签 + 操作前的完整快照 + 删除标记 + 目标级布尔标记
    private record Entry(String opKey, JsonObject snapshot, boolean deleted, boolean flag, Map<String, PendingInput> inputs, Map<String, String> paths) {
    }

    public EditSession(String targetId, RuleDraft draft) {
        this.targetId = targetId == null ? "" : targetId;
        this.draft = Objects.requireNonNull(draft, "draft");
    }

    // 编辑目标 id
    public String targetId() {
        return targetId;
    }

    // 当前草稿
    public RuleDraft draft() {
        return draft;
    }

    // 载入新草稿（切换目标或服务器下发新快照），清空历史
    public void load(RuleDraft next) {
        this.draft = Objects.requireNonNull(next, "next");
        undoStack.clear();
        redoStack.clear();
        pendingInputs.clear();
        revision++;
        notifyListeners();
    }

    // 是否有未应用改动（含删除标记）
    public boolean isDirty() {
        return draft.isDirty() || draft.isDeleted() || !pendingInputs.isEmpty();
    }

    // ---- 统一编辑入口 ----

    // 所有草稿修改都必须走这里：opKey 为操作标签（i18n key），change 内只做字段写入
    public void apply(String opKey, Runnable change) {
        Objects.requireNonNull(change, "change");
        JsonObject before = draft.view().deepCopy();
        Map<String, PendingInput> inputsBefore = Map.copyOf(pendingInputs);
        boolean deletedBefore = draft.isDeleted();
        boolean flagBefore = currentFlag();
        Map<String, String> changedPaths = new LinkedHashMap<>();
        transactionInputPaths = changedPaths;
        try { change.run(); }
        finally { transactionInputPaths = null; }
        // 删除标记与目标级标记也算改动：只改标记不改 JSON 的操作同样要能撤销
        if (!before.equals(draft.view()) || deletedBefore != draft.isDeleted() || flagBefore != currentFlag()) {
            undoStack.push(new Entry(normalize(opKey), before, deletedBefore, flagBefore, inputsBefore, inversePaths(changedPaths)));
            while (undoStack.size() > HISTORY_LIMIT) {
                undoStack.removeLast();
            }
            redoStack.clear();
            revision++;
        }
        notifyListeners();
    }

    // 安装目标级布尔标记的读写桥（由模型提供，读的是模型侧的标记集合）
    public void setFlagBridge(@Nullable BooleanSupplier reader, @Nullable Consumer<Boolean> writer) {
        this.flagReader = reader;
        this.flagWriter = writer;
    }

    // 当前目标级标记（未安装桥时为 false）
    private boolean currentFlag() {
        return this.flagReader != null && this.flagReader.getAsBoolean();
    }

    // 写回目标级标记
    private void applyFlag(boolean value) {
        if (this.flagWriter != null) {
            this.flagWriter.accept(value);
        }
    }

    // ---- 撤销/重做 ----

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    // 下一次撤销的操作标签（无则 null）
    public @Nullable String undoOpKey() {
        Entry entry = undoStack.peek();
        return entry == null ? null : entry.opKey();
    }

    // 下一次重做的操作标签（无则 null）
    public @Nullable String redoOpKey() {
        Entry entry = redoStack.peek();
        return entry == null ? null : entry.opKey();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        Entry entry = undoStack.pop();
        redoStack.push(new Entry(entry.opKey(), draft.view().deepCopy(), draft.isDeleted(), currentFlag(), Map.copyOf(pendingInputs), inversePaths(entry.paths())));
        restorePendingInputs(entry);
        draft.replaceWith(entry.snapshot());
        draft.setDeleted(entry.deleted());
        applyFlag(entry.flag());
        revision++;
        notifyListeners();
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        Entry entry = redoStack.pop();
        undoStack.push(new Entry(entry.opKey(), draft.view().deepCopy(), draft.isDeleted(), currentFlag(), Map.copyOf(pendingInputs), inversePaths(entry.paths())));
        restorePendingInputs(entry);
        draft.replaceWith(entry.snapshot());
        draft.setDeleted(entry.deleted());
        applyFlag(entry.flag());
        revision++;
        notifyListeners();
        return true;
    }

    private void restorePendingInputs(Entry entry) {
        Map<Long, Map.Entry<String, PendingInput>> latest = new LinkedHashMap<>();
        pendingInputs.entrySet().forEach(input -> latest.put(input.getValue().identity(), input));
        Map<String, PendingInput> restored = new LinkedHashMap<>();
        for (var target : entry.inputs().entrySet()) {
            var current = latest.remove(target.getValue().identity());
            restored.put(target.getKey(), current == null ? target.getValue()
                    : atPath(current.getValue(), current.getKey(), target.getKey()));
        }
        // 新输入未必存在于历史快照。字段撤销保留原位置；树操作按完整叶对象定位，
        // 无唯一匹配时不绑定到其他同类型节点，删除节点的输入随节点离开当前草稿。
        boolean fieldEdit = OP_SET_FIELD.equals(entry.opKey()) || OP_SET_SOURCE.equals(entry.opKey());
        for (var current : latest.values()) {
            String mapped = entry.paths().keySet().stream().filter(scope -> within(current.getKey(), scope))
                    .max(java.util.Comparator.comparingInt(String::length)).orElse(null);
            String target = mapped != null ? entry.paths().get(mapped) + current.getKey().substring(mapped.length())
                    : fieldEdit ? current.getKey() : restoredInputPath(current.getKey(), entry.snapshot());
            if (target != null) restored.put(target, atPath(current.getValue(), current.getKey(), target));
        }
        pendingInputs.clear();
        pendingInputs.putAll(restored);
    }

    private static Map<String, String> inversePaths(Map<String, String> paths) {
        Map<String, String> inverse = new LinkedHashMap<>();
        paths.forEach((from, to) -> inverse.put(to, from));
        return Map.copyOf(inverse);
    }

    private @Nullable String restoredInputPath(String path, JsonObject target) {
        int leafEnd = path.lastIndexOf("." + RuleFields.CONDITION + ".");
        if (leafEnd < 0) return path;
        String ownerPath = path.substring(0, leafEnd + RuleFields.CONDITION.length() + 1);
        JsonElement owner = draft.getAt(ownerPath);
        if (owner == null) return null;
        List<String> matches = new ArrayList<>();
        findObjectPaths(target, "", owner, matches);
        return matches.size() == 1 ? matches.getFirst() + path.substring(ownerPath.length()) : null;
    }

    private static void findObjectPaths(JsonElement value, String path, JsonElement target, List<String> matches) {
        if (value.equals(target)) matches.add(path);
        if (value instanceof JsonObject object) {
            object.entrySet().forEach(entry -> findObjectPaths(entry.getValue(),
                    path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), target, matches));
        } else if (value.isJsonArray()) {
            for (int index = 0; index < value.getAsJsonArray().size(); index++) {
                findObjectPaths(value.getAsJsonArray().get(index), path + "[" + index + "]", target, matches);
            }
        }
    }

    // 清空历史（应用保存成功后调用，保留当前值）
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        notifyListeners();
    }

    // 当前已接受的草稿快照（落盘与冲突比较使用）
    public JsonObject snapshot() {
        return draft.view().deepCopy();
    }

    // 状态变化监听（脏标记、撤销栈深度变化）
    public void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }

    private static String normalize(@Nullable String opKey) {
        return opKey == null || opKey.isBlank() ? OP_SET_FIELD : opKey;
    }
}