package com.meteorite.itemdespawntowhat.client.ui.support;

import net.minecraft.client.gui.components.AbstractWidget;
import org.jetbrains.annotations.Nullable;

// 焦点委托：将焦点设置操作从组件中解耦，避免组件直接依赖具体的 Screen 类。
@FunctionalInterface
public interface FocusDelegate {
    void setFocusedWidget(@Nullable AbstractWidget widget);
}
