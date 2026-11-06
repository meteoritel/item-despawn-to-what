package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;

/** 原版外观的宿主业务色；通用表面和提示直接消费 kit 默认定义。 */
public final class UiMinecraftPalette {

    // 工具类，禁止实例化
    private UiMinecraftPalette() {
    }

    // ---- 控件状态 ----

    // 自绘控件（复选框、时间条、目录网格等）的常态底色
    public static final int CONTROL_FILL = 0xFF566070;
    // 悬停底色：半透明白，叠在任意暗底上都是一档轻微提亮
    public static final int CONTROL_HOVER = 0x33FFFFFF;
    // 选中底色：不透明暗蓝，保证白字与勾选标记都清晰
    public static final int CONTROL_SELECTED = 0xFF3C4C63;
    // 禁用底色
    public static final int CONTROL_DISABLED = 0xFF3A414B;
    // 控件外凸高光边
    public static final int CONTROL_BEVEL_LIGHT = 0x66FFFFFF;
    // 控件外凸阴影边
    public static final int CONTROL_BEVEL_DARK = 0x80000000;
    // 主要操作与勾选标记
    public static final int ACCENT = 0xFF6B84A8;
    // 主要操作悬停
    public static final int ACCENT_HOVER = 0xFF8098BC;
    // 主要操作按下色；暗底上也当勾选标记用，因此取亮档
    public static final int ACCENT_PRESSED = 0xFFA8C0E0;
    // 危险色：暗底上当文字、亮色块当填充都成立，与 FILLED_TEXT 成对
    public static final int DANGER = 0xFFFF7A7A;
    // 目录网格奇数行底色：透明，让内凹底色透出来形成隔行条纹
    public static final int LIST_ROW_ODD = 0x00000000;
    // 行内链接文字
    public static final int LINK = 0xFFA8C4FF;

    // ---- 状态 ----

    // 警告：作填充时配 FILLED_TEXT 深色文字
    public static final int WARNING = 0xFFFFCC66;
    // 成功：作填充时配 FILLED_TEXT 深色文字
    public static final int SUCCESS = 0xFF8FD49A;
    // 状态栏错误文字
    public static final int STATUS_DANGER = 0xFFFF8A8A;
    // 状态栏警告文字
    public static final int STATUS_WARNING = 0xFFFFD27A;
    // 实心状态色块上的文字：三类色块都是亮色，文字因此取深色
    public static final int FILLED_TEXT = 0xFF1A1C20;

    // ---- 外壳 ----

    // 功能栏 / 工具列的分组线
    public static final int CHROME_DIVIDER = 0x66FFFFFF;
    // 外壳上的自绘控件底色
    public static final int PANEL_CONTROL_FILL = 0xFF4A5361;

    /**
     * 组装本方案的宿主调色板。
     *
     * <p>画布与只读代码沿用扁平方案的取值：原版皮肤只调整窗口、语义表面与业务状态色，
     * 不重画条件树画布和 JSON 着色。状态填充色与 {@link #FILLED_TEXT} 成对，
     * 危险色同时承担暗底上的错误文字，因此取亮红。</p>
     *
     */
    static UiHostPalette hostPalette() {
        UiHostPalette base = UiHostPalette.businessDefaults();
        UiSurfaceStyle colors = UiMinecraftTheme.INSTANCE.surfaces();
        return new UiHostPalette(colors,
                new UiHostPalette.Controls(colors.tooltipText(), CONTROL_FILL, CONTROL_HOVER, CONTROL_SELECTED,
                        CONTROL_DISABLED, CONTROL_BEVEL_LIGHT, CONTROL_BEVEL_DARK, ACCENT, ACCENT_HOVER,
                        ACCENT_PRESSED, DANGER, LIST_ROW_ODD, LINK,
                        UiMinecraftTheme.INSTANCE.surfaceSkin(UiSurfaceRole.SCROLL_TRACK).style().background(),
                        UiMinecraftTheme.INSTANCE.surfaceSkin(UiSurfaceRole.SCROLL_THUMB).style().background(),
                        CHROME_DIVIDER, PANEL_CONTROL_FILL, 0x1CFFFFFF, 0x1CFFFFFF),
                new UiHostPalette.Status(WARNING, SUCCESS, colors.text(), colors.secondaryText(), STATUS_DANGER,
                        STATUS_WARNING, colors.text(), colors.secondaryText(), colors.text(), FILLED_TEXT),
                base.code(), base.canvas(),
                new UiHostPalette.Description(0xFFFFE08A, 0xFF9FD1FF, 0xFF8FE0D6,
                        0xFF9FD1FF, 0xFF9FE0B4, 0xFFC8A8F0));
    }
}
