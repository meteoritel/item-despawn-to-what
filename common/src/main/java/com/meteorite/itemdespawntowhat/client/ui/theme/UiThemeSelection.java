package com.meteorite.itemdespawntowhat.client.ui.theme;

import java.util.Objects;

/** 客户端运行期间的主题选择；只持方案标识，不持屏幕或活动 Context。 */
public final class UiThemeSelection {
    private static UiThemeScheme current = UiThemeScheme.CURRENT;

    private UiThemeSelection() { }

    /** 当前选择，供新屏幕初始化。 */
    public static UiThemeScheme current() { return current; }

    /** 在 GUI 线程记录用户选择；活动屏幕负责安全发布。 */
    public static void select(UiThemeScheme scheme) { current = Objects.requireNonNull(scheme, "scheme"); }
}
