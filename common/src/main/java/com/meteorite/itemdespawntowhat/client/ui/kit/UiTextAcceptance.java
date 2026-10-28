package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * 单份文本接纳状态：码点上限、业务过滤、最近合法值与程序回填抑制。
 * <p>宿主先用 accepts 预检，不能预检的原版模型在 changed 中整体回退。每个文本适配器拥有一份实例。</p>
 * <p>由文本控件所在的客户端线程调用；filter 应为无副作用的判断，静默写入后采样原版实际值。</p>
 * <pre>{@code
 * acceptance.silently(() -> model.setValue(savedText));
 * acceptance.reset(model.value());
 * // 在原版模型的用户变化通知中调用
 * acceptance.changed(model.value(), model::setValue);
 * }</pre>
 */
public final class UiTextAcceptance {
    private int maximum;
    private int silentDepth;
    private String accepted = "";
    private Predicate<String> filter = text -> true;
    private @Nullable Runnable overflow;
    private @Nullable Runnable changed;

    /** 初始化上限，0 表示只接纳空串。 */
    public UiTextAcceptance(int maximum) { setMaximum(maximum); }
    /** 调整上限不截断已载入文本。 */
    public void setMaximum(int maximum) { this.maximum = Math.max(0, maximum); }
    /** null 恢复默认的任意文本规则。 */
    public void setFilter(@Nullable Predicate<String> filter) { this.filter = filter == null ? text -> true : filter; }
    /** 超限回调，业务过滤拒绝不触发。 */
    public void setOnOverflow(@Nullable Runnable listener) { overflow = listener; }
    /** 用户变化回调。 */
    public void setOnChanged(@Nullable Runnable listener) { changed = listener; }
    /** 在写入原版模型前预检；程序回填通过。 */
    public boolean accepts(String candidate) {
        if (silentDepth > 0) return true;
        if (candidate.codePointCount(0, candidate.length()) > maximum) {
            if (overflow != null) overflow.run();
            return false;
        }
        return filter.test(candidate);
    }
    /** 模型通知入口；拒绝时通过原版写入口回退，接受时仅通知一次。 */
    public void changed(String candidate, Consumer<String> restore) {
        if (silentDepth > 0 || candidate.equals(accepted)) return;
        if (!accepts(candidate)) {
            silently(() -> restore.accept(accepted));
            return;
        }
        accepted = candidate;
        notifyChanged();
    }
    /** 嵌套安全的静默作用域，用于回填、历史恢复及原版模型重建。 */
    public void silently(Runnable writer) {
        silentDepth++;
        try { writer.run(); } finally { silentDepth--; }
    }
    /** 静默写入后采样实际模型值，允许保留已加载的超限原文。 */
    public void reset(String value) { accepted = value; }
    /** 历史恢复完成后由宿主显式通知。 */
    public void notifyChanged() { if (changed != null) changed.run(); }
}
