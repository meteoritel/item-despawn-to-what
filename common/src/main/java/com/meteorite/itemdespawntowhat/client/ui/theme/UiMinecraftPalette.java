package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceStyle;

/**
 * 原版方案（{@link UiThemeScheme#MINECRAFT}）的宿主侧配色。
 *
 * <p>kit 的 {@link UiSurfaceStyle#MINECRAFT} 是只读基准，本类不改它，只在宿主侧给同一方案
 * 一套半透明表面。屏幕先由原版 renderBackground 画模糊世界与暗色渐变，再画本方案的窗口，
 * 所以窗口 alpha 取到能透出背景的一档，层次改由明度承担：窗口是暗底，内凹再沉一档，
 * 面板、卡片、标题带逐级提亮，避免整窗不透明色块和多层叠加后糊成一片。</p>
 *
 * <p>取值集中在本类供 {@link UiMinecraftHostTheme} 与 {@link UiHostSurfaceSkins} 共用，
 * 调色只改一处；画布与只读代码的配色仍沿用扁平方案，原版皮肤不重画这两块内容。</p>
 */
public final class UiMinecraftPalette {

    // 工具类，禁止实例化
    private UiMinecraftPalette() {
    }

    // ---- 表面 ----

    // 窗口渐变上端：alpha 刻意压在中下段——原版 renderBackground 已叠过一层暗化渐变，
    // 窗口再取高 alpha 会重新变得不透明；下方各层靠明度而不是靠把窗口压黑来分区
    public static final int WINDOW_TOP = 0x9C1A1D23;
    // 窗口渐变下端：更深一档，给整屏一个落地感
    public static final int WINDOW_BOTTOM = 0xA8121418;
    // 窗口 1 像素描边：半透明冷灰，暗底上仍有轮廓
    public static final int WINDOW_BORDER = 0x80505A6A;
    // 标题带上端：比窗口明显提亮的一档，靠明度差分出标题区
    public static final int HEADER_TOP = 0xC0444E5F;
    // 标题带下端：向正文过渡，保留纵向渐变
    public static final int HEADER_BOTTOM = 0xB4374150;
    // 标题带底边线：与窗口、页面内容之间保留 1 像素亮分隔
    public static final int HEADER_EDGE = 0x80505C6E;
    // 标题文字
    public static final int HEADER_TEXT = 0xFFF4F6FA;
    // 面板底色：比窗口提亮一档的抬升面，分组区域靠明度浮起
    public static final int PANEL_FILL = 0xC02E3642;
    // 面板 1 像素描边
    public static final int PANEL_BORDER = 0x80505A6A;
    // 面板顶部内侧 1 像素高光：轻微外凸，和纯色块拉开立体感
    public static final int PANEL_HIGHLIGHT = 0x14FFFFFF;
    // 卡片底色：再亮一档，用于面板上的分区卡片
    public static final int CARD_FILL = 0xB43A4554;
    // 卡片 1 像素描边
    public static final int CARD_BORDER = 0x8A5C6678;
    // 列表与输入内凹底色：透光的近黑，压在所有表面之下但不变成纯黑
    public static final int INSET_FILL = 0x960A0C10;
    // 内凹 1 像素描边
    public static final int INSET_BORDER = 0x8A5A6474;
    // 内凹顶部内侧阴影：原版输入框的沉入口径
    public static final int INSET_SHADOW = 0x60000000;
    // 内凹底部内侧高光
    public static final int INSET_HIGHLIGHT = 0x12FFFFFF;
    // 帮助浮层底色：比面板更实一档，保证浮层正文可读
    public static final int HELP_FILL = 0xD0343D4A;
    // 帮助浮层 1 像素描边
    public static final int HELP_BORDER = 0x8A5C6678;
    // 分隔线：半透明亮线，压在深色标题带下缘
    public static final int DIVIDER = 0x8A4C5666;
    // 列表选中底色：半透明蓝，叠在暗底上仍能和白字拉开对比
    public static final int SELECTION_FILL = 0x80506A8A;
    // 弹窗遮罩：沿用原版半透明暗化，不额外压黑内容
    public static final int MODAL_DIM = 0xA8000000;

    // ---- 文本 ----

    // 正文：接近白但不用纯白，避免长段落刺眼
    public static final int TEXT_PRIMARY = 0xFFF2F4F8;
    // 次要说明文字
    public static final int TEXT_SECONDARY = 0xFFB8C0CC;
    // 禁用文字
    public static final int TEXT_DISABLED = 0xFF7C8592;
    // 输入框正文
    public static final int INPUT_TEXT = 0xFFEFF2F7;
    // 强调底色上的文字
    public static final int TEXT_ON_DARK = 0xFFFFFFFF;

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
    // 滚动条轨道：半透明黑，不抢内容
    public static final int SCROLL_TRACK = 0x66000000;
    // 滚动条滑块：半透明亮灰
    public static final int SCROLL_THUMB = 0x80A6B0BE;

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

    // kit 通用消费方读取的表面语义色；inputBorder 在本方案只被目录网格当作隔行底使用，
    // 内凹描边由 UiHostSurfaceSkins 的 INSET 分支提供，因此取半透明白。
    private static final UiSurfaceStyle SURFACES = new UiSurfaceStyle(
            TEXT_PRIMARY, TEXT_SECONDARY, TEXT_DISABLED, INPUT_TEXT,
            INSET_FILL, 0x1CFFFFFF, PANEL_FILL, PANEL_BORDER,
            HEADER_BOTTOM, HEADER_TEXT, SELECTION_FILL, MODAL_DIM,
            0xF0101216, 0x505000FF, 0x5028007F, TEXT_ON_DARK, 0xFFFFFFFF);

    /** 供 kit 通用消费方读取的不可变语义表面。 */
    public static UiSurfaceStyle surfaces() {
        return SURFACES;
    }

    /**
     * 组装本方案的宿主调色板。
     *
     * <p>画布与只读代码沿用扁平方案的取值：原版皮肤只调整窗口、语义表面与业务状态色，
     * 不重画条件树画布和 JSON 着色。状态填充色与 {@link #FILLED_TEXT} 成对，
     * 危险色同时承担暗底上的错误文字，因此取亮红。</p>
     *
     * @param base 提供画布与代码语义的既有调色板
     */
    static UiHostPalette hostPalette(UiHostPalette base) {
        return new UiHostPalette(surfaces(),
                new UiHostPalette.Controls(TEXT_ON_DARK, CONTROL_FILL, CONTROL_HOVER, CONTROL_SELECTED,
                        CONTROL_DISABLED, CONTROL_BEVEL_LIGHT, CONTROL_BEVEL_DARK, ACCENT, ACCENT_HOVER,
                        ACCENT_PRESSED, DANGER, LIST_ROW_ODD, LINK, SCROLL_TRACK, SCROLL_THUMB,
                        CHROME_DIVIDER, PANEL_CONTROL_FILL),
                new UiHostPalette.Status(WARNING, SUCCESS, TEXT_PRIMARY, TEXT_SECONDARY, STATUS_DANGER,
                        STATUS_WARNING, TEXT_PRIMARY, TEXT_SECONDARY, TEXT_PRIMARY, FILLED_TEXT),
                base.code(), base.canvas(),
                new UiHostPalette.Description(0xFFFFE08A, 0xFF9FD1FF, 0xFF8FE0D6,
                        0xFF9FD1FF, 0xFF9FE0B4, 0xFFC8A8F0));
    }
}
