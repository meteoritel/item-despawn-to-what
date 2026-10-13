package com.meteorite.itemdespawntowhat.client.ui.theme;

import java.util.Objects;

/**
 * 宿主主题入口：屏幕与弹窗从这里取主题，再注入自己装配的控件。
 *
 * <p>默认主题是现行灰色像素外观（{@link UiPixelTheme}），因此未显式注入的既有屏幕画面不变。
 * {@link #flatTheme()} 提供画布页的扁平主题（{@link UiFlatTheme}），只有显式取用的调用方才换成扁平外观，
 * 不影响其它页面。控件经本入口注入的主题取样式，不再静态读取 {@link UiTheme}、
 * {@link UiPalette} 等主题类。</p>
 */
public final class UiHostThemes {

    // 默认宿主主题
    private static final UiHostTheme DEFAULT = UiPixelTheme.INSTANCE;
    // 画布页的扁平主题：只在调用方显式取用时生效
    private static final UiHostTheme FLAT = UiFlatTheme.INSTANCE;

    // 工具类，禁止实例化
    private UiHostThemes() {
    }

    // 默认主题：现行像素外观
    public static UiHostTheme defaultTheme() {
        return DEFAULT;
    }

    // 扁平主题：画布页专用外观，供画布页显式注入自己的控件
    public static UiHostTheme flatTheme() {
        return FLAT;
    }

    // 校验注入的主题非空，供控件构造期使用
    public static UiHostTheme require(UiHostTheme theme) {
        return Objects.requireNonNull(theme, "theme");
    }
}
