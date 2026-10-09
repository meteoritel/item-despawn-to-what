package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** 撤销快捷键只派发到当前作用域；作用域内的文本控件优先，历史由宿主提供。 */
public final class UiHistoryDispatcher {
    @FunctionalInterface
    public interface KeyHandler {
        boolean keyPressed(int keyCode, int scanCode, int modifiers);
    }

    private UiHistoryDispatcher() {}

    /** 非历史快捷键返回 false；历史快捷键始终消费，空历史也不向其他作用域穿透。 */
    public static boolean dispatch(int keyCode, int scanCode, int modifiers,
            KeyHandler activeScope, @Nullable Consumer<UiHistoryShortcut> history) {
        UiHistoryShortcut shortcut = UiHistoryShortcut.fromKey(keyCode, modifiers);
        if (shortcut == null) return false;
        if (!activeScope.keyPressed(keyCode, scanCode, modifiers) && history != null) {
            history.accept(shortcut);
        }
        return true;
    }
}
