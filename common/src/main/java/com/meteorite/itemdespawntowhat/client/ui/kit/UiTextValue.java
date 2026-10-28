package com.meteorite.itemdespawntowhat.client.ui.kit;

import org.jetbrains.annotations.Nullable;

/**
 * 文本能力：回填保留完整原文且静默，用户编辑才通知；原版编辑与历史由适配器负责。
 * <p>每个控件持有自己的文本与历史，宿主在客户端线程调用；卸载时结束选择捕获。</p>
 * <pre>{@code
 * input.setMaxLength(128).setOnValueChanged(this::refresh);
 * input.setValue(savedText).moveCursorToStart();
 * // 卸载控件时
 * input.endInteraction(UiInteractionSession.EndReason.UNMOUNTED);
 * }</pre>
 */
public interface UiTextValue extends UiFocusTarget {
    /**
     * 当前完整文本。
     */
    String value();

    /**
     * 程序回填，null 按空串处理，不触发用户变化回调。
     */
    UiTextValue setValue(@Nullable String value);

    /**
     * 静默清空文本。
     */
    default UiTextValue clear() {
        return setValue("");
    }

    /**
     * 全选当前文本。
     */
    UiTextValue selectAll();

    /**
     * 回填后将光标和视口复位到开头。
     */
    UiTextValue moveCursorToStart();

    /**
     * 用户接受的变化与历史恢复通知；null 移除回调。
     */
    UiTextValue setOnValueChanged(@Nullable Runnable listener);

    /**
     * 文本码点上限；已有回填文本原样保留。
     */
    UiTextValue setMaxLength(int maximum);

    /**
     * 输入整体拒绝时通知；null 移除回调。
     */
    UiTextValue setOnOverflow(@Nullable Runnable listener);

    /**
     * 设置可编辑性；只读选择由具体适配器决定。
     */
    UiTextValue setEditable(boolean editable);

    /**
     * 结束文本选择捕获，卸载或作用域切换时由宿主调用。
     */
    void endInteraction(UiInteractionSession.EndReason reason);

    /**
     * 文本目标将空格交给原版模型处理。
     */
    @Override
    default boolean acceptsTextInput() {
        return true;
    }
}
