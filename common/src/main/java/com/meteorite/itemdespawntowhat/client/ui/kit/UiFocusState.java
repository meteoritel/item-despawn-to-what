package com.meteorite.itemdespawntowhat.client.ui.kit;

import org.jetbrains.annotations.Nullable;

/**
 * 控件的焦点视图；实际持焦目标与输入方式只保存在所属作用域中。
 */
public final class UiFocusState {
    private @Nullable UiFocusManager owner;
    private @Nullable UiFocusTarget target;

    void attach(UiFocusManager manager, UiFocusTarget registeredTarget) {
        owner = manager;
        target = registeredTarget;
    }

    /**
     * 当前是否持有逻辑焦点。
     */
    public boolean isFocused() {
        return owner != null && owner.current() == target;
    }

    /**
     * 当前是否应绘制键盘焦点提示。
     */
    public boolean isVisible() {
        return owner != null && owner.current() == target && owner.keyboardMode();
    }

    /**
     * 复合控件的内部编辑目标是否持焦；所有权仍只有管理器中的一个目标。
     */
    public boolean containsFocus() {
        return owner != null && owner.containsFocus(target);
    }

    /**
     * 请求组内编辑目标，共用组在外部 Tab 序列中的位置。
     */
    public boolean focusWithin(UiFocusTarget child) {
        return owner != null && owner.focusWithin(target, child);
    }

    /**
     * 显式请求焦点，实际资格与作用域状态由管理器判断。
     */
    public boolean request() {
        return owner != null && owner.focusOn(target);
    }

    /**
     * 仅清除本目标的焦点。
     */
    public void clear() {
        if (owner != null && owner.current() == target) owner.clearFocus();
    }

    /**
     * 所属作用域；未挂载控件没有输入作用域。
     */
    public @Nullable UiInputScope scope() {
        return owner == null ? null : owner.scope();
    }
}
