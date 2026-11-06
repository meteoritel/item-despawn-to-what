package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import java.util.Objects;

/** 普通控件的不可变结构、画法与文字色，一次解析为同一主题的完整皮肤。 */
public record UiControlSkin(UiControlStyle style, UiControlPainter painter, int textColor, int disabledTextColor) {
    /** 结构与画法共同构成可绘制的控件皮肤。 */
    public UiControlSkin {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(painter, "painter");
    }

    /** 保留画法与文字色，替换状态结构。 */
    public UiControlSkin withStyle(UiControlStyle next) { return new UiControlSkin(next, painter, textColor, disabledTextColor); }

    /** 保留结构与文字色，替换只读背景画法。 */
    public UiControlSkin withPainter(UiControlPainter next) { return new UiControlSkin(style, next, textColor, disabledTextColor); }

    /** 同时替换正常与禁用文字色。 */
    public UiControlSkin withTextColors(int normal, int disabled) { return new UiControlSkin(style, painter, normal, disabled); }
}
