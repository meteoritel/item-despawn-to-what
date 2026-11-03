package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import java.util.Objects;

/** 普通控件的不可变结构、画法与文字色，一次解析为同一主题的完整皮肤。 */
public record UiControlSkin(UiControlStyle style, UiControlPainter painter, int textColor, int disabledTextColor) {
    public UiControlSkin {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(painter, "painter");
    }
}
