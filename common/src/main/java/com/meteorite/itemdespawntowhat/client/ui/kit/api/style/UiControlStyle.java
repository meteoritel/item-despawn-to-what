package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

/**
 * 控件语义样式 token 组：把控件各交互状态的背景、焦点轮廓与滚动条结构色集中到一处，
 * 避免结构色散落到控件与各浮层的渲染点（对齐 {@code UiTextPalette} 的语义色分层约定）。
 *
 * <p>文本色仍由调用方从语义色表传入——本类只管**控件结构色**（背景、轮廓、滚动条），
 * 与宿主主题「文本色由调用方注入」的分工一致。</p>
 *
 * <p>{@link #PARCHMENT} 与 {@link #DARK} 是可显式选择的结构色预设；
 * 通用控件的默认外观由 {@link UiMinecraftTheme} 提供。</p>
 */
public record UiControlStyle(int background, int hoverBackground, int pressedBackground,
                             int selectedBackground, int disabledBackground,
                             int focusOutline, int scrollbarTrack, int scrollbarThumb) {

    /** 控件交互状态；绘制时由控件按自身标志按优先级解析，不由调用方直接指定。 */
    public enum State { NORMAL, HOVER, PRESSED, SELECTED, DISABLED }

    /** 羊皮纸主题：考古笔记书页类浅色底界面。 */
    public static final UiControlStyle PARCHMENT = new UiControlStyle(
            0xFFF2E5C6, 0xFFE0C798, 0xFFD2B584, 0xFFE3CE96, 0xFFEDE4CB,
            0xFF3A6EA5, 0xFFDCD0B4, 0xFF8A7350);

    /** 暗色结构色预设：用于深色背景的控件与浮层。 */
    public static final UiControlStyle DARK = new UiControlStyle(
            0xFF2A2A2A, 0xFF3C3C3C, 0xFF1E1E1E, 0xFF4A3E10, 0xFF232323,
            0xFF78B4FF, 0xFF1A1A1A, 0xFF6E6E6E);

    // 状态取背景色；优先级（禁用 > 按下 > 悬停 > 选中 > 普通）由控件在解析状态时保证。
    public int background(State state) {
        return switch (state) {
            case HOVER -> hoverBackground;
            case PRESSED -> pressedBackground;
            case SELECTED -> selectedBackground;
            case DISABLED -> disabledBackground;
            case NORMAL -> background;
        };
    }
}
