package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;

/***
 * 按钮变体。
 * <p>只表达语义（主要 / 次要 / 危险 / 禁用）与是否可交互；具体底色、文本色与立体边由注入的宿主主题
 * 按 {@link UiControlRole} 给出，变体本身不再持有任何颜色，因此换主题不需要改变体。
 */
public enum UiButtonVariant {

    // 主要操作（保存、应用）
    PRIMARY(UiControlRole.PRIMARY, true),
    // 次要操作（取消、返回）
    SECONDARY(UiControlRole.SECONDARY, true),
    // 危险操作（删除）
    DANGER(UiControlRole.DANGER, true),
    // 禁用（占位展示，不可点击）
    DISABLED(UiControlRole.DISABLED, false);

    // 对应的控件语义角色
    private final UiControlRole role;
    // 是否可交互
    private final boolean interactive;

    UiButtonVariant(UiControlRole role, boolean interactive) {
        this.role = role;
        this.interactive = interactive;
    }

    // 控件语义角色
    public UiControlRole role() {
        return role;
    }

    // 是否可交互
    public boolean interactive() {
        return interactive;
    }
}
