package com.meteorite.itemdespawntowhat.client.ui.kit;

import org.jetbrains.annotations.Nullable;

/**
 * 单值控件的一次操作生命周期：begin → preview* → commit 或 cancel。
 *
 * <p>数据回填（{@code setValue} 类操作）只改值，不经过本类，因此不会产生回调与编辑历史；
 * 用户操作先 {@link #begin}，拖动或按键重复期间调 {@link #preview}，释放时 {@link #commit}，
 * Esc 或失权时 {@link #cancel}。{@link #commit()} 在值没有变化时返回 {@code false}，
 * 宿主据此避免写入一条空历史。</p>
 *
 * <p>同一次操作绝不会同时 commit 与 cancel：两个方法都先重置内部状态，后一次调用自然变成空操作。</p>
 */
public final class UiValueInteraction {

    /** 生命周期回调；接口方法都有默认空实现，宿主只覆写关心的部分。 */
    public interface Listener {
        // 操作开始，startValue 为交互起点值
        default void began(double startValue) {
        }

        // 预览值变化，回调可能多次
        default void previewed(double startValue, double currentValue) {
        }

        // 正常提交；只有值确实变化时才会调用
        default void committed(double startValue, double currentValue) {
        }

        // 取消并回退到起点值；恢复值等于起点值时也会调用
        default void cancelled(double startValue, double restoredValue) {
        }
    }

    /** 无回调的监听器，用于纯展示或在桥接层改接旧回调。 */
    public static final Listener NONE = new Listener() {
    };

    private boolean active;
    private double startValue;
    private double currentValue;
    @Nullable private Listener listener;

    // 开始一次操作；已有操作在进行中时返回 false，调用方应先结束旧操作
    public boolean begin(double startValue, @Nullable Listener listener) {
        if (active || !Double.isFinite(startValue)) return false;
        this.active = true;
        this.startValue = startValue;
        this.currentValue = startValue;
        this.listener = listener;
        if (listener != null) listener.began(startValue);
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public double startValue() {
        return startValue;
    }

    public double currentValue() {
        return currentValue;
    }

    // 值相对起点是否已变化；未在操作中时恒为 false
    public boolean changed() {
        return active && currentValue != startValue;
    }

    // 预览一个新值；未在操作中、值非法或与当前相同都不回调
    public boolean preview(double value) {
        if (!active || !Double.isFinite(value) || value == currentValue) return false;
        currentValue = value;
        if (listener != null) listener.previewed(startValue, currentValue);
        return true;
    }

    // 正常提交；返回值表示是否产生了变化（false = 不写历史）
    public boolean commit() {
        if (!active) return false;
        double committed = currentValue;
        boolean changed = committed != startValue;
        Listener current = listener;
        reset();
        if (changed && current != null) current.committed(startValue, committed);
        return changed;
    }

    // 取消并回退；返回值表示是否结束了一次进行中的操作
    public boolean cancel() {
        if (!active) return false;
        double restored = startValue;
        Listener current = listener;
        reset();
        if (current != null) current.cancelled(startValue, restored);
        return true;
    }

    // 清空操作状态；不触发任何回调
    public void reset() {
        active = false;
        listener = null;
        currentValue = startValue;
    }
}
