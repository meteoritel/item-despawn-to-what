package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderSkin;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 通用主题接口：默认接入使用 {@link UiMinecraftTheme}，宿主可注入其它实现。
 * 同一主题返回稳定的不可变样式；运行时替换由根持有的 {@link UiThemeContext} 发布。
 */
public interface UiStyleSource {
    /** 当前来源的版本；不可变主题为零，主题上下文在替换时递增。 */
    default long revision() { return 0; }
    /** 普通控件的结构色。 */
    UiControlStyle controlStyle(UiControlRole role);
    /** 滑条结构与画法。 */
    UiSliderSkin sliderSkin(UiSliderRole role);
    /** 通用表面完整皮肤；遗漏角色使用 kit 原版默认。 */
    default UiSurfaceSkin surfaceSkin(UiSurfaceRole role) { return UiMinecraftTheme.INSTANCE.surfaceSkin(role); }
    /** 面板和内容语义色。 */
    default UiSurfaceStyle surfaces() { return UiSurfaceStyle.MINECRAFT; }
    /** 提示的完整外框皮肤。 */
    default UiTooltipSkin tooltipSkin() { return UiTooltipSkin.MINECRAFT; }
    /** 共享布局度量。 */
    default UiThemeMetrics metrics() { return UiThemeMetrics.MINECRAFT; }
    /** 普通控件完整皮肤；纯色主题可沿用此实现。 */
    default UiControlSkin controlSkin(UiControlRole role) {
        return new UiControlSkin(controlStyle(role), UiControlPainter.FILL, controlTextColor(role), surfaces().disabledText());
    }
    /** 启用或禁用文本色。 */
    default int textColor(boolean enabled) { return enabled ? surfaces().text() : surfaces().disabledText(); }
    /** 次要文字色。 */
    default int secondaryTextColor() { return surfaces().secondaryText(); }
    /** 控件语义角色的文字色。 */
    default int controlTextColor(UiControlRole role) { return textColor(role != UiControlRole.DISABLED); }
    /** 面板内边距。 */
    default int padding() { return metrics().padding(); }
    /** 标准行高。 */
    default int rowHeight() { return metrics().rowHeight(); }
    /** 行文字顶部偏移。 */
    default int textOffset() { return metrics().textOffset(); }
    /** 标题栏高度。 */
    default int headerHeight() { return metrics().headerHeight(); }
    /** 面板边框宽度。 */
    default int border() { return metrics().border(); }
    /** 列表选择标记宽度。 */
    default int selectMarkerWidth() { return metrics().selectMarkerWidth(); }
    /** 绘制常态语义表面。 */
    default void drawSurface(GuiGraphics graphics, UiRect rect, UiSurfaceRole role) {
        drawSurface(graphics, rect, role, UiControlStyle.State.NORMAL);
    }
    /** 绘制指定状态的语义表面。 */
    default void drawSurface(GuiGraphics graphics, UiRect rect, UiSurfaceRole role, UiControlStyle.State state) {
        surfaceSkin(role).paint(graphics, rect, state);
    }
    /** 面板底与边框。 */
    default void drawWindow(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiSurfaceRole.WINDOW); }
    /** 输入和列表底板。 */
    default void drawInset(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiSurfaceRole.INSET); }
    /** 标题栏底色。 */
    default void drawHeader(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiSurfaceRole.HEADER); }
    /** 分隔线。 */
    default void drawDivider(GuiGraphics graphics, int x, int y, int width) {
        drawSurface(graphics, new UiRect(x, y, Math.max(0, width), 1), UiSurfaceRole.DIVIDER);
    }
    /** 矩形内部的键盘焦点框。 */
    default void drawFocusOutline(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiSurfaceRole.FOCUS); }
    /** 列表与文本选择底色。 */
    default void drawSelection(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiSurfaceRole.SELECTION); }
    /** 行左侧的选择标记。 */
    default void drawSelectMarker(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiSurfaceRole.SELECT_MARKER); }
    /** 滚动轨道。 */
    default void drawScrollTrack(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiSurfaceRole.SCROLL_TRACK); }
    /** 滚动滑块。 */
    default void drawScrollThumb(GuiGraphics graphics, UiRect rect) { drawSurface(graphics, rect, UiSurfaceRole.SCROLL_THUMB); }
    /** 可选中行或标签底。 */
    default void drawChip(GuiGraphics graphics, UiRect rect, boolean selected, boolean hovered) {
        drawSurface(graphics, rect, UiSurfaceRole.CHIP, selected ? UiControlStyle.State.SELECTED
                : hovered ? UiControlStyle.State.HOVER : UiControlStyle.State.NORMAL);
    }
}
