package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.network.chat.Component;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * 可聚焦能力与激活动作独立；当前焦点由所属作用域唯一决定。
 */
public interface UiFocusTarget extends UiInputTarget {
    /**
     * 控件声明的焦点能力与当前可用性共同决定资格。
     */
    boolean canFocus();

    /**
     * 持久焦点视图；宿主包装与核心可共享同一视图。
     */
    UiFocusState focusState();

    /**
     * 复合面板可展开实际 Tab 目标；分面等组内导航控件保持一个停靠点。
     */
    default List<UiFocusTarget> focusTargets() {
        return List.of(this);
    }

    /**
     * 当前是否持有逻辑焦点。
     */
    default boolean isFocused() {
        return focusState().isFocused();
    }

    /**
     * 当前是否显示额外键盘焦点提示。
     */
    default boolean isFocusVisible() {
        return focusState().isVisible();
    }

    /**
     * 同步原生编辑状态等必要适配，不在此决定焦点所有权。
     */
    default void focusChanged(boolean focused) {
    }

    /**
     * 空格是否应留给文本编辑。
     */
    default boolean acceptsTextInput() {
        return false;
    }

    /**
     * 激活动作独立于焦点资格，允许不可聚焦按钮响应点击。
     */
    boolean activate();

    /**
     * 用于焦点自动滚动与可视化的 GUI 逻辑边界。
     */
    UiRect bounds();

    /**
     * 旁白与调试使用的可读名称。
     */
    @Nullable Component accessibleName();
}
