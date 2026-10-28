package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

/**
 * 一个输入作用域内持续交互的唯一登记；值快照与具体恢复方式由目标拥有。
 */
public final class UiInteractionSession {
    /**
     * 结束原因同时决定本次交互是正常完成还是取消。
     */
    public enum EndReason {
        RELEASE(false),
        FOCUS_TRANSFER(false),
        CANCEL(true),
        HIDDEN(true),
        DISABLED(true),
        UNMOUNTED(true),
        SCOPE_CHANGED(true),
        HOST_CLOSED(true);

        private final boolean cancels;

        EndReason(boolean cancels) {
            this.cancels = cancels;
        }

        /**
         * 目标是否应回退尚未提交的值预览。
         */
        public boolean cancels() {
            return cancels;
        }
    }

    /**
     * 输入来源用来匹配释放，避免键盘重复和指针手势互相结束。
     */
    public enum Source {POINTER, KEY, TEXT}

    /**
     * 活动交互目标负责处理输入，并在结束通知中提交或恢复自己的模型。
     */
    public interface Target extends UiInputTarget {
        /**
         * 会话已从作用域摘除；回调可以触发内容重建。
         */
        void interactionEnded(EndReason reason);
    }

    private @Nullable Target target;
    private @Nullable Source source;
    private int input = -1;

    /**
     * 开始一个交互；同源重复输入复用会话，切换操作先正常完成旧会话。
     */
    public void begin(Target next, Source nextSource, int nextInput) {
        Objects.requireNonNull(next);
        Objects.requireNonNull(nextSource);
        if (target == next && source == nextSource && input == nextInput) return;
        end(EndReason.FOCUS_TRANSFER);
        target = next;
        source = nextSource;
        input = nextInput;
    }

    /**
     * 当前活动目标；无活动交互时为 null。
     */
    public @Nullable Target target() {
        return target;
    }

    /**
     * 获取匹配来源与按键的目标，迟到释放没有目标。
     */
    public @Nullable Target target(Source source, int input) {
        return matches(source, input) ? target : null;
    }

    /**
     * 当前会话是否属于给定目标。
     */
    public boolean owns(Target candidate) {
        return target == candidate;
    }

    /**
     * 当前会话是否匹配给定来源与按键。
     */
    public boolean matches(Source candidateSource, int candidateInput) {
        return target != null && source == candidateSource && input == candidateInput;
    }

    /**
     * 先清空所有权再通知目标，提交回调中的卸载不会重复结束同一次操作。
     */
    public boolean end(EndReason reason) {
        Target previous = target;
        if (previous == null) return false;
        target = null;
        source = null;
        input = -1;
        previous.interactionEnded(reason);
        return true;
    }

    /**
     * 仅结束仍由指定目标持有的会话，控件失效不会取消其它控件的交互。
     */
    public boolean end(Target candidate, EndReason reason) {
        return target == candidate && end(reason);
    }
}
