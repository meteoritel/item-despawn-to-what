package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/***
 * 编辑会话门面：P5 的每一个编辑入口都必须经过 {@link #apply(String, Runnable)}，
 * 禁止绕过历史直接改 {@link RuleDraft}（契约 P6 前置要求）。
 * <p>当前维护「当前值 + 脏标记 + 有界撤销/重做快照」；草稿落盘、跨会话恢复、
 * 应用期间冻结等 P6 语义在本类内部继续补齐，界面调用点不需要再改动。
 * <p>历史条目保存操作前的完整规则快照：规则 JSON 很小，直接快照最简单可靠，
 * 且天然支持「一次操作影响多个字段」的复合编辑。
 */
public final class EditSession {

    // 撤销/重做历史容量上限（契约 P6：上限 100 条）
    public static final int HISTORY_LIMIT = 100;

    // 操作标签（i18n key），P5 能标多少标多少，缺的 P6 再补
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

    // 编辑目标 id（规则 id 或新建规则的目标 id）
    private final String targetId;
    // 操作前快照栈（栈顶为最近一次操作）
    private final Deque<Entry> undoStack = new ArrayDeque<>();
    private final Deque<Entry> redoStack = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private RuleDraft draft;

    // 目标级布尔标记的读写桥（「恢复原始版本」这类不改变 JSON 的编辑用它进历史）
    private @Nullable BooleanSupplier flagReader;
    private @Nullable Consumer<Boolean> flagWriter;

    // 历史条目：操作标签 + 操作前的完整快照 + 删除标记 + 目标级布尔标记
    private record Entry(String opKey, JsonObject snapshot, boolean deleted, boolean flag) {
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
        notifyListeners();
    }

    // 是否有未应用改动（含删除标记）
    public boolean isDirty() {
        return draft.isDirty() || draft.isDeleted();
    }

    // ---- 统一编辑入口 ----

    // 所有草稿修改都必须走这里：opKey 为操作标签（i18n key），change 内只做字段写入
    public void apply(String opKey, Runnable change) {
        Objects.requireNonNull(change, "change");
        JsonObject before = draft.view().deepCopy();
        boolean deletedBefore = draft.isDeleted();
        boolean flagBefore = currentFlag();
        change.run();
        // 删除标记与目标级标记也算改动：只改标记不改 JSON 的操作同样要能撤销
        if (!before.equals(draft.view()) || deletedBefore != draft.isDeleted() || flagBefore != currentFlag()) {
            undoStack.push(new Entry(normalize(opKey), before, deletedBefore, flagBefore));
            while (undoStack.size() > HISTORY_LIMIT) {
                undoStack.removeLast();
            }
            redoStack.clear();
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
        redoStack.push(new Entry(entry.opKey(), draft.view().deepCopy(), draft.isDeleted(), currentFlag()));
        draft.replaceWith(entry.snapshot());
        draft.setDeleted(entry.deleted());
        applyFlag(entry.flag());
        notifyListeners();
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        Entry entry = redoStack.pop();
        undoStack.push(new Entry(entry.opKey(), draft.view().deepCopy(), draft.isDeleted(), currentFlag()));
        draft.replaceWith(entry.snapshot());
        draft.setDeleted(entry.deleted());
        applyFlag(entry.flag());
        notifyListeners();
        return true;
    }

    // 清空历史（应用保存成功后调用，保留当前值）
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        notifyListeners();
    }

    // 当前草稿快照（P6 落盘与冲突比较使用）
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
