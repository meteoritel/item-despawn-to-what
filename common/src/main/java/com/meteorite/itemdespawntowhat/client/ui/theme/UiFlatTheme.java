package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.PixelSliderPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 扁平宿主主题：画布页的扁平外观（1 像素同色描边 + 灰阶层次，无立体凸凹与阴影），
 * 调色板与绘制原语取自 {@link UiFlatPalette} 与 {@link UiFlatSkin}。
 *
 * <p>本主题只经 {@link UiHostThemes#flatTheme()} 供调用方显式取用，不是默认主题：
 * {@link UiHostThemes#defaultTheme()} 仍是像素外观，其它页面在未显式注入时画面不变。</p>
 *
 * <p>控件可见的调色板与像素主题相同：两种皮肤的差别在控件颜色之外的外壳描边与层次上，
 * 画布色 {@code canvas*} 本就是扁平取值，因此 {@link UiHostPalette} 可直接使用。度量沿用宿主级常量，
 * 行高与内边距与像素皮肤一致；滑块的数值与输入契约不变，仅颜色换成扁平档。</p>
 */
public final class UiFlatTheme implements UiHostTheme {

    // 无状态主题，全局共用一个实例
    static final UiFlatTheme INSTANCE = new UiFlatTheme();

    // 次要控件：扁平面板控件的常态 / 悬停 / 按下 / 选中 / 禁用
    private static final UiControlStyle CONTROL_STYLE = new UiControlStyle(
            UiFlatPalette.PANEL_CONTROL_FILL, UiFlatPalette.PANEL_CONTROL_HOVER, UiFlatPalette.PANEL_CONTROL_PRESSED,
            UiFlatPalette.PANEL_CONTROL_SELECTED, UiFlatPalette.PANEL_CONTROL_DISABLED, UiFlatPalette.FOCUS_OUTLINE,
            UiFlatPalette.PANEL_INSET_FILL, UiFlatPalette.PANEL_BORDER);

    // 主要操作：扁平强调色，禁用沿用面板禁用色
    private static final UiControlStyle PRIMARY_STYLE = new UiControlStyle(
            UiFlatPalette.CANVAS_ACCENT, UiFlatPalette.CANVAS_ACCENT_HOVER, UiPalette.ACCENT_PRESSED,
            UiFlatPalette.CANVAS_ACCENT_HOVER, UiFlatPalette.PANEL_CONTROL_DISABLED, UiFlatPalette.FOCUS_OUTLINE,
            UiFlatPalette.PANEL_INSET_FILL, UiFlatPalette.PANEL_BORDER);

    // 危险操作：扁平危险色
    private static final UiControlStyle DANGER_STYLE = new UiControlStyle(
            UiFlatPalette.CANVAS_TEXT_DANGER, UiPalette.DANGER_HOVER, UiPalette.DANGER_PRESSED,
            UiPalette.DANGER_HOVER, UiFlatPalette.PANEL_CONTROL_DISABLED, UiFlatPalette.FOCUS_OUTLINE,
            UiFlatPalette.PANEL_INSET_FILL, UiFlatPalette.PANEL_BORDER);

    // 禁用：全部取面板禁用色，焦点轮廓改用禁用文本色
    private static final UiControlStyle DISABLED_STYLE = new UiControlStyle(
            UiFlatPalette.PANEL_CONTROL_DISABLED, UiFlatPalette.PANEL_CONTROL_DISABLED,
            UiFlatPalette.PANEL_CONTROL_DISABLED, UiFlatPalette.PANEL_CONTROL_DISABLED,
            UiFlatPalette.PANEL_CONTROL_DISABLED, UiPalette.TEXT_DISABLED, UiFlatPalette.PANEL_INSET_FILL,
            UiFlatPalette.PANEL_BORDER);

    // 纯标签：透明底，只保留焦点轮廓与滚动槽
    private static final UiControlStyle LABEL_STYLE = new UiControlStyle(
            UiPalette.TRANSPARENT, UiPalette.TRANSPARENT, UiPalette.TRANSPARENT, UiPalette.TRANSPARENT,
            UiPalette.TRANSPARENT, UiFlatPalette.FOCUS_OUTLINE, UiFlatPalette.PANEL_INSET_FILL,
            UiFlatPalette.PANEL_BORDER);

    // 紧凑档滑块：扁平轨道与强调色填充
    private static final UiSliderStyle COMPACT_SLIDER = new UiSliderStyle(
            UiFlatPalette.PANEL_INSET_FILL, UiFlatPalette.CANVAS_ACCENT, UiFlatPalette.PANEL_CONTROL_FILL,
            UiFlatPalette.PANEL_CONTROL_HOVER, UiFlatPalette.PANEL_CONTROL_DISABLED, UiFlatPalette.FOCUS_OUTLINE,
            UiFlatPalette.CANVAS_TEXT_DANGER, UiFlatPalette.STATUS_TEXT_WARNING, UiPalette.TRANSPARENT,
            UiFlatPalette.PANEL_TEXT, UiFlatPalette.PANEL_TEXT, UiPalette.TEXT_DISABLED,
            UiFlatPalette.CANVAS_TEXT_DANGER, 4, 3, 2);

    // 宽松档滑块：几何加粗加宽，用色与紧凑档一致
    private static final UiSliderStyle ROOMY_SLIDER = new UiSliderStyle(
            UiFlatPalette.PANEL_INSET_FILL, UiFlatPalette.CANVAS_ACCENT, UiFlatPalette.PANEL_CONTROL_FILL,
            UiFlatPalette.PANEL_CONTROL_HOVER, UiFlatPalette.PANEL_CONTROL_DISABLED, UiFlatPalette.FOCUS_OUTLINE,
            UiFlatPalette.CANVAS_TEXT_DANGER, UiFlatPalette.STATUS_TEXT_WARNING, UiPalette.TRANSPARENT,
            UiFlatPalette.PANEL_TEXT, UiFlatPalette.PANEL_TEXT, UiPalette.TEXT_DISABLED,
            UiFlatPalette.CANVAS_TEXT_DANGER, 6, 5, 4);

    private UiFlatTheme() {
    }

    @Override
    public UiHostPalette palette() {
        return UiHostPalette.hostPalette();
    }

    @Override
    public int padding() {
        return UiTheme.PADDING;
    }

    @Override
    public int rowHeight() {
        return UiTheme.ROW_HEIGHT;
    }

    @Override
    public int textOffset() {
        return UiTheme.TEXT_OFFSET;
    }

    @Override
    public int headerHeight() {
        return UiTheme.HEADER_HEIGHT;
    }

    @Override
    public int border() {
        return UiTheme.BORDER;
    }

    @Override
    public int selectMarkerWidth() {
        return UiTheme.SELECT_MARKER_WIDTH;
    }

    @Override
    public int textColor(boolean enabled) {
        return enabled ? UiFlatPalette.PANEL_TEXT : UiPalette.TEXT_DISABLED;
    }

    @Override
    public int secondaryTextColor() {
        return UiFlatPalette.STATUS_TEXT_MUTED;
    }

    @Override
    public int controlTextColor(UiControlRole role) {
        // 与像素主题同一分工：主要/危险底色深用反白文字，禁用用禁用文本色，其余用正文色
        return switch (role) {
            case PRIMARY, DANGER -> UiFlatPalette.CANVAS_TEXT_ON_ACCENT;
            case DISABLED -> UiPalette.TEXT_DISABLED;
            case SECONDARY, LABEL -> UiFlatPalette.PANEL_TEXT;
        };
    }

    @Override
    public UiControlStyle controlStyle(UiControlRole role) {
        return switch (role) {
            case PRIMARY -> PRIMARY_STYLE;
            case DANGER -> DANGER_STYLE;
            case DISABLED -> DISABLED_STYLE;
            case LABEL -> LABEL_STYLE;
            case SECONDARY -> CONTROL_STYLE;
        };
    }

    @Override
    public UiSliderSkin sliderSkin(UiSliderRole role) {
        return switch (role) {
            case COMPACT -> new UiSliderSkin(COMPACT_SLIDER, PixelSliderPainter.INSTANCE);
            case ROOMY -> new UiSliderSkin(ROOMY_SLIDER, PixelSliderPainter.INSTANCE);
        };
    }

    // 浮动调板外壳：画布页的浮动调板与面板使用扁平描边样式
    @Override
    public UiControlStyle panelStyle() {
        return UiFlatSkin.PANEL_STYLE;
    }

    // 功能栏与工具列格子：扁平反馈样式
    @Override
    public UiControlStyle stripStyle() {
        return UiFlatSkin.STRIP_STYLE;
    }

    @Override
    public void drawWindow(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawSurface(graphics, rect, UiFlatPalette.PANEL_FILL, UiFlatPalette.PANEL_BORDER);
    }

    @Override
    public void drawInset(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawSurface(graphics, rect, UiFlatPalette.PANEL_INSET_FILL, UiFlatPalette.PANEL_BORDER);
    }

    @Override
    public void drawHeader(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawSurface(graphics, rect, UiFlatPalette.PANEL_HEADER_FILL, UiFlatPalette.PANEL_BORDER);
    }

    @Override
    public void drawDivider(GuiGraphics graphics, int x, int y, int width) {
        if (width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, UiFlatPalette.PANEL_BORDER);
    }

    // 扁平皮肤没有立体边：只在给定边上描 1 像素外框，不改动内部底色
    @Override
    public void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark) {
        UiFlatSkin.drawOutline(graphics, rect, dark);
    }

    @Override
    public void drawFocusOutline(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawFocusOutline(graphics, rect);
    }

    @Override
    public void drawSelection(GuiGraphics graphics, UiRect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiFlatPalette.PANEL_CONTROL_SELECTED);
    }

    // 选中标记：与像素主题同一几何，颜色换成扁平正文色
    @Override
    public void drawSelectMarker(GuiGraphics graphics, UiRect rect) {
        int x = rect.x() + 2;
        int midY = rect.y() + rect.height() / 2;
        int color = UiFlatPalette.PANEL_TEXT;
        graphics.fill(x, midY, x + 1, midY + 1, color);
        graphics.fill(x + 1, midY - 1, x + 2, midY + 2, color);
        graphics.fill(x + 2, midY - 2, x + 3, midY + 3, color);
    }

    @Override
    public void drawScrollTrack(GuiGraphics graphics, UiRect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiFlatPalette.PANEL_INSET_FILL);
    }

    @Override
    public void drawSegment(GuiGraphics graphics, UiRect rect, boolean enabled, boolean selected, boolean hovered) {
        int fill = !enabled ? UiFlatPalette.PANEL_CONTROL_DISABLED
                : selected ? UiFlatPalette.PANEL_CONTROL_SELECTED
                : hovered ? UiFlatPalette.PANEL_CONTROL_HOVER
                : UiFlatPalette.PANEL_CONTROL_FILL;
        UiFlatSkin.drawSurface(graphics, rect, fill, UiFlatPalette.PANEL_BORDER);
    }

    // 扁平卡片：画布页的卡片用同色描边面，不使用九宫格贴图
    @Override
    public void drawCard(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawSurface(graphics, rect, UiFlatPalette.CANVAS_CARD_FILL, UiFlatPalette.CANVAS_CARD_BORDER);
    }

    // 扁平分区面板：底色与 1 像素描边
    @Override
    public void drawPanel(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawSurface(graphics, rect, UiFlatPalette.PANEL_FILL, UiFlatPalette.PANEL_BORDER);
    }

    // 扁平可选中小底
    @Override
    public void drawChip(GuiGraphics graphics, UiRect rect, boolean selected, boolean hovered) {
        int fill = selected ? UiFlatPalette.PANEL_CONTROL_SELECTED
                : hovered ? UiFlatPalette.PANEL_CONTROL_HOVER
                : UiFlatPalette.PANEL_CONTROL_FILL;
        UiFlatSkin.drawSurface(graphics, rect, fill, UiFlatPalette.PANEL_BORDER);
    }

    @Override
    public UiIcon icon(int row, boolean disabled) {
        return UiFlatSkin.icon(row, disabled);
    }

    @Override
    public void drawChrome(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawChrome(graphics, rect);
    }

    @Override
    public void drawStatusBar(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawStatusBar(graphics, rect);
    }

    @Override
    public void drawHelp(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawHelp(graphics, rect);
    }

    @Override
    public void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) {
        UiFlatSkin.drawSurface(graphics, rect, fill, border);
    }
}
