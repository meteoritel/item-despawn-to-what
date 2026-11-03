package com.meteorite.itemdespawntowhat.client.ui.theme;

import java.util.Objects;

/** 宿主正文与画布外壳的一次发布方案；两个业务槽位不进入 kit。 */
public record UiHostThemeSet(UiHostTheme content, UiHostTheme chrome) {
    public UiHostThemeSet {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(chrome, "chrome");
    }
    /** 现行像素正文与扁平画布。 */
    public static UiHostThemeSet pixel() { return new UiHostThemeSet(UiHostThemes.defaultTheme(), UiHostThemes.flatTheme()); }
    /** 整套扁平主题。 */
    public static UiHostThemeSet flat() { return new UiHostThemeSet(UiHostThemes.flatTheme(), UiHostThemes.flatTheme()); }
    /** 原版通用控件与宿主业务扩展。 */
    public static UiHostThemeSet minecraft() { return new UiHostThemeSet(UiHostThemes.minecraftTheme(), UiHostThemes.minecraftTheme()); }
}
