package com.meteorite.itemdespawntowhat.client.ui.kit;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** 撤销快捷键识别：只使用事件携带的修饰键，不读取全局输入状态。 */
public enum UiHistoryShortcut {
    UNDO,
    REDO;

    public static @Nullable UiHistoryShortcut fromKey(int keyCode, int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0
                || (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
            return null;
        }
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == GLFW.GLFW_KEY_Z) {
            return shift ? REDO : UNDO;
        }
        return keyCode == GLFW.GLFW_KEY_Y && !shift ? REDO : null;
    }
}
