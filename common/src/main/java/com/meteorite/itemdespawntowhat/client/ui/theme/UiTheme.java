package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.PixelSliderPainter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNineSlice;

/***
 * 灰色像素主题。
 * <p>集中提供三样东西：控件样式（{@link UiControlStyle}）、像素边框绘制、文本颜色。
 * 编辑卡片与分段按钮采用抗锯齿圆角底图，其余传统控件保留像素边框。
 */
public final class UiTheme {

    // 工具类，禁止实例化
    private UiTheme() {
    }

    // ---- 尺寸常量 ----

    // 标准行高（原版字体 9 像素 + 留白）
    public static final int ROW_HEIGHT = 12;
    // 边框宽度（像素）
    public static final int BORDER = 1;
    // 面板内边距
    public static final int PADDING = 4;
    // 分区标题栏高度
    public static final int HEADER_HEIGHT = 14;
    // 文本相对行顶部的偏移（(行高 - 字体高) / 2 向下取整）
    public static final int TEXT_OFFSET = 2;
    // 列表选中行的标记宽度
    public static final int SELECT_MARKER_WIDTH = 7;

    // ---- 控件样式 ----

    // 次要操作（默认）
    private static final UiControlStyle SECONDARY_STYLE = new UiControlStyle(
            UiPalette.CONTROL_FILL, UiPalette.CONTROL_HOVER, UiPalette.CONTROL_PRESSED,
            UiPalette.CONTROL_SELECTED, UiPalette.CONTROL_DISABLED, UiPalette.FOCUS_OUTLINE,
            UiPalette.SCROLL_TRACK, UiPalette.SCROLL_THUMB);

    // 主要操作
    private static final UiControlStyle PRIMARY_STYLE = new UiControlStyle(
            UiPalette.ACCENT, UiPalette.ACCENT_HOVER, UiPalette.ACCENT_PRESSED,
            UiPalette.ACCENT_HOVER, UiPalette.CONTROL_DISABLED, UiPalette.FOCUS_OUTLINE,
            UiPalette.SCROLL_TRACK, UiPalette.SCROLL_THUMB);

    // 危险操作
    private static final UiControlStyle DANGER_STYLE = new UiControlStyle(
            UiPalette.DANGER, UiPalette.DANGER_HOVER, UiPalette.DANGER_PRESSED,
            UiPalette.DANGER_HOVER, UiPalette.CONTROL_DISABLED, UiPalette.FOCUS_OUTLINE,
            UiPalette.SCROLL_TRACK, UiPalette.SCROLL_THUMB);

    // 禁用操作
    private static final UiControlStyle DISABLED_STYLE = new UiControlStyle(
            UiPalette.CONTROL_DISABLED, UiPalette.CONTROL_DISABLED, UiPalette.CONTROL_DISABLED,
            UiPalette.CONTROL_DISABLED, UiPalette.CONTROL_DISABLED, UiPalette.TEXT_DISABLED,
            UiPalette.SCROLL_TRACK, UiPalette.SCROLL_THUMB);

    // 纯标签（无交互）
    private static final UiControlStyle LABEL_STYLE = new UiControlStyle(
            UiPalette.TRANSPARENT, UiPalette.TRANSPARENT, UiPalette.TRANSPARENT,
            UiPalette.TRANSPARENT, UiPalette.TRANSPARENT, UiPalette.FOCUS_OUTLINE,
            UiPalette.SCROLL_TRACK, UiPalette.SCROLL_THUMB);

    // 次要操作样式
    public static UiControlStyle secondaryStyle() {
        return SECONDARY_STYLE;
    }

    // 主要操作样式
    public static UiControlStyle primaryStyle() {
        return PRIMARY_STYLE;
    }

    // 危险操作样式
    public static UiControlStyle dangerStyle() {
        return DANGER_STYLE;
    }

    // 禁用样式
    public static UiControlStyle disabledStyle() {
        return DISABLED_STYLE;
    }

    // 滑块样式：把主题色调色板映射成 kit 的不可变样式（kit 不反向依赖主题，只能由宿主注入）
    // 尺寸沿用 widget/UiSlider 的 TRACK_HEIGHT=4、THUMB_WIDTH=3、手柄上下各溢出 2 像素
    private static final UiSliderStyle SLIDER_STYLE = new UiSliderStyle(
            UiPalette.SCROLL_TRACK, UiPalette.ACCENT, UiPalette.CONTROL_FILL, UiPalette.CONTROL_HOVER,
            UiPalette.CONTROL_DISABLED, UiPalette.FOCUS_OUTLINE, UiPalette.DANGER, UiPalette.WARNING,
            UiPalette.TRANSPARENT, UiPalette.TEXT_PRIMARY, UiPalette.TEXT_PRIMARY, UiPalette.TEXT_DISABLED,
            UiPalette.DANGER, 4, 3, 2);

    private static final UiSliderStyle CARD_SLIDER_STYLE = new UiSliderStyle(
            UiPalette.SLOT_INNER, UiPalette.ACCENT, UiPalette.CONTROL_FILL, UiPalette.CONTROL_HOVER,
            UiPalette.CONTROL_DISABLED, UiPalette.FOCUS_OUTLINE, UiPalette.DANGER, UiPalette.WARNING,
            UiPalette.TRANSPARENT, UiPalette.TEXT_PRIMARY, UiPalette.TEXT_PRIMARY, UiPalette.TEXT_DISABLED,
            UiPalette.DANGER, 6, 5, 4);

    // 手柄补上明暗边和中央刻痕，灰底上也能看清当前位置。
    private static final UiSliderPainter CARD_SLIDER_PAINTER = (graphics, frame) -> {
        PixelSliderPainter.INSTANCE.paint(graphics, frame);
        UiRect thumb = frame.thumb();
        drawBevel(graphics, thumb, UiPalette.WINDOW_BORDER_LIGHT, UiPalette.PANEL_BORDER_DARK);
        if (thumb.width() >= 5 && thumb.height() >= 6) {
            int center = thumb.x() + thumb.width() / 2;
            graphics.fill(center, thumb.y() + 3, center + 1, thumb.bottom() - 3, UiPalette.TEXT_SECONDARY);
        }
    };

    // 纯标签样式（透明底）
    public static UiControlStyle labelStyle() {
        return LABEL_STYLE;
    }

    // 滑块与区间滑块的主题样式（宿主注入给 kit 控件用）
    public static UiSliderStyle sliderStyle() {
        return SLIDER_STYLE;
    }

    // 加高表单使用更宽的手柄与轨道，沿用相同数值和输入契约。
    public static UiSliderStyle cardSliderStyle() {
        return CARD_SLIDER_STYLE;
    }

    public static UiSliderPainter cardSliderPainter() { return CARD_SLIDER_PAINTER; }

    // ---- 绘制：窗口与面板 ----

    // 绘制带高光/阴影的窗口面板（外描边 + 底色 + 四条立体边）
    public static void drawWindow(GuiGraphics graphics, UiRect rect) {
        if (rect.width() < 2 || rect.height() < 2) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.SHADOW);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, UiPalette.WINDOW_FILL);
        drawBevel(graphics, inset(rect), UiPalette.WINDOW_BORDER_LIGHT, UiPalette.WINDOW_BORDER_DARK);
    }

    // 绘制分区面板（比窗口更深的底色）
    public static void drawPanel(GuiGraphics graphics, UiRect rect) {
        if (rect.width() < 2 || rect.height() < 2) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.PANEL_FILL);
        drawBevel(graphics, rect, UiPalette.PANEL_BORDER_LIGHT, UiPalette.PANEL_BORDER_DARK);
    }

    private static final UiNineSlice CARD = surface("card");
    private static final UiNineSlice CHIP = surface("chip");
    private static final UiNineSlice CONTROL = surface("control");
    private static final UiNineSlice HOVER = surface("hover");
    private static final UiNineSlice SELECTED = surface("selected");
    private static final UiNineSlice DISABLED = surface("disabled");

    // 小尺寸底图只拉伸边与中心，圆角保持尺寸，不形成整条白色高光。
    private static UiNineSlice surface(String name) {
        return new UiNineSlice(ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "textures/gui/editor/" + name + ".png"), 32, 8);
    }

    private static void drawSurface(GuiGraphics graphics, UiRect rect, UiNineSlice surface) {
        if (rect.width() < 4 || rect.height() < 4) return;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try { surface.render(graphics, rect); }
        finally { graphics.flush(); RenderSystem.disableBlend(); }
    }

    // 浅灰底与柔和单层描边，圆角透明边缘与页面背景自然混合。
    public static void drawCard(GuiGraphics graphics, UiRect rect) {
        drawSurface(graphics, rect, CARD);
    }

    public static void drawChip(GuiGraphics graphics, UiRect rect, boolean selected, boolean hovered) {
        drawSurface(graphics, rect, selected ? SELECTED : hovered ? HOVER : CHIP);
    }

    public static void drawSegment(GuiGraphics graphics, UiRect rect, boolean enabled, boolean selected, boolean hovered) {
        drawSurface(graphics, rect, !enabled ? DISABLED : selected ? SELECTED : hovered ? HOVER : CONTROL);
    }

    // 绘制内凹区域（输入框、列表底、槽位）
    public static void drawInset(GuiGraphics graphics, UiRect rect) {
        if (rect.width() < 2 || rect.height() < 2) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.SLOT_FILL);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, UiPalette.SLOT_FILL);
        drawBevel(graphics, rect, UiPalette.SLOT_BORDER_DARK, UiPalette.SLOT_BORDER_LIGHT);
    }

    // 绘制分区标题栏（上方标题、下方内容）
    public static void drawHeader(GuiGraphics graphics, UiRect rect) {
        if (rect.width() < 2 || rect.height() < 2) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.HEADER_FILL);
        drawBevel(graphics, rect, UiPalette.PANEL_BORDER_LIGHT, UiPalette.PANEL_BORDER_DARK);
    }

    // 绘制一条水平分隔线
    public static void drawDivider(GuiGraphics graphics, int x, int y, int width) {
        if (width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, UiPalette.PANEL_BORDER_DARK);
        graphics.fill(x, y + 1, x + width, y + 2, UiPalette.PANEL_BORDER_LIGHT);
    }

    // 绘制外凸立体边：上/左为高光，下/右为阴影
    // TODO 美术资源：立体边目前用纯色填充，后续可替换为灰色像素贴图九宫格
    public static void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark) {
        if (rect.width() < 2 || rect.height() < 2) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right() - 1, rect.y() + 1, light);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom() - 1, light);
        graphics.fill(rect.x() + 1, rect.bottom() - 1, rect.right(), rect.bottom(), dark);
        graphics.fill(rect.right() - 1, rect.y() + 1, rect.right(), rect.bottom() - 1, dark);
    }

    // 绘制键盘焦点轮廓（虚线感：只画四角与中线，避免遮住文字）
    public static void drawFocusOutline(GuiGraphics graphics, UiRect rect) {
        if (rect.width() < 4 || rect.height() < 4) {
            return;
        }
        int color = UiPalette.FOCUS_OUTLINE;
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), color);
        graphics.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), color);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
    }

    // 绘制选中背景（配合文字标记使用）
    public static void drawSelection(GuiGraphics graphics, UiRect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.CONTROL_SELECTED);
    }

    // 绘制选中标记（列表/树选中行左侧的三角尖）
    public static void drawSelectMarker(GuiGraphics graphics, UiRect rect) {
        int x = rect.x() + 2;
        int midY = rect.y() + rect.height() / 2;
        graphics.fill(x, midY, x + 1, midY + 1, UiPalette.TEXT_PRIMARY);
        graphics.fill(x + 1, midY - 1, x + 2, midY + 2, UiPalette.TEXT_PRIMARY);
        graphics.fill(x + 2, midY - 2, x + 3, midY + 3, UiPalette.TEXT_PRIMARY);
    }

    // 绘制水平进度/滚动轨道
    // TODO 美术资源：滚动条轨道与滑块可替换为像素贴图
    public static void drawScrollTrack(GuiGraphics graphics, UiRect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiPalette.SCROLL_TRACK);
    }

    // ---- 文本颜色 ----

    // 根据启用状态返回标准文本颜色
    public static int textColor(boolean enabled) {
        return enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED;
    }

    // 次要说明文本颜色
    public static int secondaryTextColor() {
        return UiPalette.TEXT_SECONDARY;
    }

    // 内缩矩形（去掉 1 像素外框）
    public static UiRect inset(UiRect rect) {
        if (rect.width() < 2 || rect.height() < 2) {
            return rect;
        }
        return new UiRect(rect.x() + 1, rect.y() + 1, rect.width() - 2, rect.height() - 2);
    }

    // 裁剪矩形到父矩形内部
    public static UiRect clampTo(UiRect rect, UiRect container) {
        int x1 = Math.max(rect.x(), container.x());
        int y1 = Math.max(rect.y(), container.y());
        int x2 = Math.min(rect.right(), container.right());
        int y2 = Math.min(rect.bottom(), container.bottom());
        if (x2 <= x1 || y2 <= y1) {
            return new UiRect(container.x(), container.y(), 0, 0);
        }
        return new UiRect(x1, y1, x2 - x1, y2 - y1);
    }
}
