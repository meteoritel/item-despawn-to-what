package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 根准备点的最后请求优先队列；完整手势正常释放后才发布主题或宿主方案。 */
public final class UiThemePublication<T> {
    private final Supplier<T> current;
    private final Consumer<T> publish;
    private @Nullable T pending;

    /** 当前值与发布动作由唯一所有者提供；可一次更新宿主多个 Context。 */
    public UiThemePublication(Supplier<T> current, Consumer<T> publish) {
        this.current = Objects.requireNonNull(current, "current");
        this.publish = Objects.requireNonNull(publish, "publish");
    }

    /** 替换待发布值；请求当前实例会撤销旧请求。 */
    public void request(T next) { pending = Objects.requireNonNull(next, "next") == current.get() ? null : next; }

    /** 布局和命中前调用；捕获仍存在时继续等待，成功发布返回 true。 */
    public boolean prepare(@Nullable UiInputScope active) {
        if (pending == null || active != null && active.interaction().pointerTarget() != null) return false;
        T next = pending;
        pending = null;
        publish.accept(next);
        return true;
    }

    /** 关闭根时丢弃未发布请求，选择的持久范围由宿主决定。 */
    public void discard() { pending = null; }
}
