package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;

/**
 * 通过组合复用本地状态的可聚焦控件；内容、激活及复合几何由实际控件实现。
 */
public interface UiShellWidget extends UiWidget, UiFocusTarget {
    /**
     * 当前控件的状态壳，包装与核心可返回同一实例。
     */
    UiWidgetShell shell();

    /**
     * {@inheritDoc}
     */
    @Override
    default UiRect bounds() {
        return shell().bounds();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default void setBounds(int x, int y, int width, int height) {
        shell().setBounds(x, y, width, height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean isEnabled() {
        return shell().isEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean isVisible() {
        return shell().isVisible();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean canFocus() {
        return shell().canFocus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default UiFocusState focusState() {
        return shell().focusState();
    }
}
