package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * 输入捕获登记表：一个输入作用域同一时刻只允许一个目标持有捕获，并在结束时分发原因。
 *
 * <p>捕获解决两件事：指针拖出控件矩形后仍能收到释放；以及控件被隐藏、禁用、卸载、焦点作用域
 * 切换或宿主关闭时，预览能被统一收回，不需要每个控件各写一套结束逻辑。宿主在每个作用域
 * （例如一层模态）持有一个实例，控件在开始拖动时调用 {@link #begin(Target)}。</p>
 *
 * <p>只有 {@link EndReason#RELEASE} 表示正常收尾，其余原因都要求回退预览；是否失去服务器权限、
 * 是否保留草稿由宿主判断，kit 只区分「正常结束」与「取消」。</p>
 */
public final class UiInputCapture {

    /** 捕获结束原因；除 {@link #RELEASE} 外都表示这次交互被取消。 */
    public enum EndReason {
        /** 指针/按键正常释放，交互应当提交。 */
        RELEASE,
        /** 用户显式取消（例如 Esc），交互应当回退。 */
        CANCEL,
        /** 控件被隐藏。 */
        HIDDEN,
        /** 控件被禁用。 */
        DISABLED,
        /** 控件被卸载或内容树重建。 */
        UNMOUNTED,
        /** 焦点作用域切换（例如打开模态、切页）。 */
        FOCUS_SCOPE_CHANGED,
        /** 宿主关闭（屏幕关闭、断线）。 */
        HOST_CLOSED;

        /** 该原因是否要求回退预览而不是提交。 */
        public boolean cancels() {
            return this != RELEASE;
        }
    }

    /** 捕获目标：实现方必须能在回调里安全地清理自身状态。 */
    public interface Target {
        // 捕获结束通知；实现方不得在回调内再次 begin 同一作用域的捕获
        void captureEnded(EndReason reason);
    }

    @Nullable private Target captured;

    // 开始捕获；若已有其它目标持有捕获，先用 CANCEL 结束它，保证同一时刻只有一个目标
    public void begin(Target target) {
        Objects.requireNonNull(target, "Capture target");
        if (captured == target) return;
        if (captured != null) end(EndReason.CANCEL);
        captured = target;
    }

    public boolean isCaptured() {
        return captured != null;
    }

    public boolean isCapturedBy(Target target) {
        return captured != null && captured == target;
    }

    @Nullable
    public Target captured() {
        return captured;
    }

    /**
     * 统一结束入口：清空捕获并通知目标；没有捕获时返回 {@code false} 且不产生任何回调。
     * 先清引用再回调，回调内重入不会重复结束。
     */
    public boolean end(EndReason reason) {
        Objects.requireNonNull(reason, "End reason");
        Target target = captured;
        if (target == null) return false;
        captured = null;
        target.captureEnded(reason);
        return true;
    }

    // ---- 具名包装：宿主在隐藏/禁用/卸载/失焦/关闭时直接调用，不必自造原因 ----

    public boolean release() { return end(EndReason.RELEASE); }

    public boolean cancel() { return end(EndReason.CANCEL); }

    public boolean endForHidden() { return end(EndReason.HIDDEN); }

    public boolean endForDisabled() { return end(EndReason.DISABLED); }

    public boolean endForUnmount() { return end(EndReason.UNMOUNTED); }

    public boolean endForFocusScopeChange() { return end(EndReason.FOCUS_SCOPE_CHANGED); }

    public boolean endForHostClose() { return end(EndReason.HOST_CLOSED); }
}
