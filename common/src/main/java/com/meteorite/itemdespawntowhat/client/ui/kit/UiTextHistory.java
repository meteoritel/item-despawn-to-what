package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** 控件局部文本历史；宿主负责快照采集、回填和变化通知。 */
public final class UiTextHistory {
    public static final int DEFAULT_CAPACITY = 100;

    /** 光标与选择锚点按 UTF-16 位置保存，与原版文本控件的坐标一致。 */
    public record Snapshot(String text, int cursor, int anchor) {
        public Snapshot {
            Objects.requireNonNull(text);
            cursor = Math.clamp(cursor, 0, text.length());
            anchor = Math.clamp(anchor, 0, text.length());
        }
    }

    private record Edit(Snapshot before, Snapshot after) {}

    private final int capacity;
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();

    public UiTextHistory() {
        this(DEFAULT_CAPACITY);
    }

    public UiTextHistory(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("文本历史容量必须大于零");
        }
        this.capacity = capacity;
    }

    /** 一次被接受的输入记录一次；光标、选择和拒绝输入不产生记录。 */
    public boolean record(Snapshot before, Snapshot after) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        if (before.text().equals(after.text())) {
            return false;
        }
        undo.addLast(new Edit(before, after));
        if (undo.size() > capacity) {
            undo.removeFirst();
        }
        redo.clear();
        return true;
    }

    public @Nullable Snapshot undo() {
        Edit edit = undo.pollLast();
        if (edit == null) {
            return null;
        }
        redo.addLast(edit);
        return edit.before();
    }

    public @Nullable Snapshot redo() {
        Edit edit = redo.pollLast();
        if (edit == null) {
            return null;
        }
        undo.addLast(edit);
        return edit.after();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    /** 装载不同文本时清空，避免历史跨字段或跨装载基线串联。 */
    public void clear() {
        undo.clear();
        redo.clear();
    }
}
