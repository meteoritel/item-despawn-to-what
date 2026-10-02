package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;

/***
 * 按钮变体。
 * <p>只表达语义（主要 / 次要 / 危险 / 禁用），具体颜色由主题决定；
 * 变体同时提供外凸立体边的明暗色，保证四种变体外观一致。
 */
public enum UiButtonVariant {

    // 主要操作（保存、应用）
    PRIMARY(UiTheme.primaryStyle(), UiPalette.TEXT_ON_DARK, UiPalette.CONTROL_BEVEL_LIGHT, UiPalette.CONTROL_BEVEL_DARK, true),
    // 次要操作（取消、返回）
    SECONDARY(UiTheme.secondaryStyle(), UiPalette.TEXT_PRIMARY, UiPalette.CONTROL_BEVEL_LIGHT, UiPalette.CONTROL_BEVEL_DARK, true),
    // 危险操作（删除）
    DANGER(UiTheme.dangerStyle(), UiPalette.TEXT_ON_DARK, UiPalette.CONTROL_BEVEL_LIGHT, UiPalette.CONTROL_BEVEL_DARK, true),
    // 禁用（占位展示，不可点击）
    DISABLED(UiTheme.disabledStyle(), UiPalette.TEXT_DISABLED, UiPalette.CONTROL_BEVEL_LIGHT, UiPalette.CONTROL_BEVEL_DARK, false);

    // 底色样式
    private final UiControlStyle style;
    // 文本颜色
    private final int textColor;
    // 立体边高光色
    private final int bevelLight;
    // 立体边阴影色
    private final int bevelDark;
    // 是否可交互
    private final boolean interactive;

    UiButtonVariant(UiControlStyle style, int textColor, int bevelLight, int bevelDark, boolean interactive) {
        this.style = style;
        this.textColor = textColor;
        this.bevelLight = bevelLight;
        this.bevelDark = bevelDark;
        this.interactive = interactive;
    }

    // 底色样式
    public UiControlStyle style() {
        return style;
    }

    // 文本颜色
    public int textColor() {
        return textColor;
    }

    // 立体边高光色
    public int bevelLight() {
        return bevelLight;
    }

    // 立体边阴影色
    public int bevelDark() {
        return bevelDark;
    }

    // 是否可交互
    public boolean interactive() {
        return interactive;
    }
}
