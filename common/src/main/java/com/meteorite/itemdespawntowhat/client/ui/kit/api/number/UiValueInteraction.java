package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * 不可变值快照的一次操作：begin → preview → commit 或 cancel。
 * 实际值由控件持有，取消时控件先恢复值再通知；结束先摘除状态，回调可重入开始下一次操作。
 *
 * @param <V> 模型自己的不可变快照类型
 */
public final class UiValueInteraction<V> {
    /** 一次操作的通知；finished 在无净变化时也调用，用于收束关联文本快照。 */
    public interface Listener<V> {
        /** 开始本次操作。 */
        default void began(V startValue) {
        }

        /** 实际值已变化，尚未提交。 */
        default void previewed(V startValue, V currentValue) {
        }

        /** 正常完成且最终值相对起点有净变化。 */
        default void committed(V startValue, V currentValue) {
        }

        /** 本次操作结清，包括无净变化和取消。 */
        default void finished() {
        }

        /** 控件已恢复本次起点。 */
        default void cancelled(V startValue, V restoredValue) {
        }
    }

    private final Predicate<V> valid;
    private final BiPredicate<V, V> equivalent;
    private boolean active;
    private V startValue;
    private V currentValue;
    private @Nullable Listener<V> listener;

    /** 由真实模型注入有效性与等价规则，保留端点缺席、方向和数值相等语义。 */
    public UiValueInteraction(Predicate<V> valid, BiPredicate<V, V> equivalent) {
        this.valid = Objects.requireNonNull(valid, "valid");
        this.equivalent = Objects.requireNonNull(equivalent, "equivalent");
    }

    /** 开始一次操作；进行中或起点无效时返回 false。 */
    public boolean begin(V value, @Nullable Listener<V> nextListener) {
        if (active || !valid.test(value)) return false;
        active = true;
        startValue = value;
        currentValue = value;
        listener = nextListener;
        if (nextListener != null) nextListener.began(value);
        return true;
    }

    /** 是否存在未结清操作。 */
    public boolean isActive() {
        return active;
    }

    /** 当前操作的起点，仅在操作开始后读取。 */
    public V startValue() {
        return startValue;
    }

    /** 最近预览值，仅在操作开始后读取。 */
    public V currentValue() {
        return currentValue;
    }

    /** 当前操作是否相对起点有净变化。 */
    public boolean changed() {
        return active && !equivalent.test(currentValue, startValue);
    }

    /** 接纳已写入实际模型的预览；无操作、无效或同值时返回 false。 */
    public boolean preview(V value) {
        if (!active || !valid.test(value) || equivalent.test(value, currentValue)) return false;
        currentValue = value;
        if (listener != null) listener.previewed(startValue, value);
        return true;
    }

    /** 正常结清；只有净变化触发 committed，返回是否有净变化。 */
    public boolean commit() {
        if (!active) return false;
        V start = startValue;
        V committed = currentValue;
        boolean changed = !equivalent.test(committed, start);
        Listener<V> current = listener;
        reset();
        if (current != null) {
            if (changed) current.committed(start, committed);
            current.finished();
        }
        return changed;
    }

    /** 控件恢复实际值后取消；每次进行中的操作只通知一次。 */
    public boolean cancel() {
        if (!active) return false;
        V restored = startValue;
        Listener<V> current = listener;
        reset();
        if (current != null) {
            current.cancelled(restored, restored);
            current.finished();
        }
        return true;
    }

    /** 静默清空操作状态。 */
    public void reset() {
        active = false;
        listener = null;
        currentValue = startValue;
    }
}
