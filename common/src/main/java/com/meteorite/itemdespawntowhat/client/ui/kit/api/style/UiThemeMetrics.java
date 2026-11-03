package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

/** 通用控件和面板实际共用的逻辑像素度量；由根在主题变化时统一重排。 */
public record UiThemeMetrics(int padding, int rowHeight, int textOffset, int headerHeight,
                             int border, int selectMarkerWidth) {
    /** 原版风格的紧凑布局。 */
    public static final UiThemeMetrics MINECRAFT = new UiThemeMetrics(4, 16, 4, 16, 1, 5);

    public UiThemeMetrics {
        if (padding < 0 || rowHeight < 1 || textOffset < 0 || headerHeight < 1
                || border < 0 || selectMarkerWidth < 0) throw new IllegalArgumentException("Invalid UI metrics");
    }
}
