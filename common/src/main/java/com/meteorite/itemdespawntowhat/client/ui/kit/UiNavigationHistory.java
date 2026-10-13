package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 有界导航历史；宿主提供快照、有效性检查与恢复，输入和模态优先级由宿主决定。 */
public final class UiNavigationHistory<State> {
    private final int capacity;
    private final Deque<State> entries = new ArrayDeque<>();
    private boolean restoring;

    public UiNavigationHistory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Navigation capacity must be positive");
        this.capacity = capacity;
    }

    /**
     * 记录一个快照：超出容量时丢弃最旧项；与栈顶相同的快照、以及恢复过程中的记录都被忽略。
     */
    public void push(State state) {
        Objects.requireNonNull(state);
        if (restoring || state.equals(entries.peekLast())) return;
        entries.addLast(state);
        while (entries.size() > capacity) entries.removeFirst();
    }

    /**
     * 先剔除已失效的快照，再报告是否还有可回退项。
     */
    public boolean canGoBack(Predicate<State> valid) {
        entries.removeIf(state -> !valid.test(state));
        return !entries.isEmpty();
    }

    /**
     * 从最新往回找第一个有效快照后恢复；恢复期间触发的 {@link #push(State)} 不写入历史。
     * 没有有效项时返回 {@code false}，历史保持原样。
     */
    public boolean back(Predicate<State> valid, Consumer<State> restore) {
        while (!entries.isEmpty()) {
            State state = entries.removeLast();
            if (!valid.test(state)) continue;
            restoring = true;
            try { restore.accept(state); } finally { restoring = false; }
            return true;
        }
        return false;
    }

    /** 清空全部快照。 */
    public void clear() { entries.clear(); }
}
