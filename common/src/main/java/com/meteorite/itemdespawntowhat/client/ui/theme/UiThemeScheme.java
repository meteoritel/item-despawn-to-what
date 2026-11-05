package com.meteorite.itemdespawntowhat.client.ui.theme;

/** 编辑器可选择的完整外观方案；标识独立于屏幕与编辑工作区。 */
public enum UiThemeScheme {
    /** 像素正文与扁平画布。 */
    CURRENT("current"),
    /** 原版控件与协调的业务视觉。 */
    MINECRAFT("minecraft");

    private final String id;

    UiThemeScheme(String id) { this.id = id; }

    /** 用于保存选择和生成本地化 key 的稳定标识。 */
    public String id() { return id; }

    /** 解析正文与画布的一次完整发布。 */
    public UiHostThemeSet themes() {
        return switch (this) {
            case CURRENT -> UiHostThemeSet.pixel();
            case MINECRAFT -> UiHostThemeSet.minecraft();
        };
    }

    /** 两态入口的下一种外观。 */
    public UiThemeScheme next() { return this == CURRENT ? MINECRAFT : CURRENT; }
}
