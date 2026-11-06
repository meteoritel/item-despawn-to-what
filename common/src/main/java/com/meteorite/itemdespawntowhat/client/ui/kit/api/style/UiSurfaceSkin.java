package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** 不可变表面皮肤；状态底色、装饰色和 painter 作为整体解析与替换。 */
public record UiSurfaceSkin(UiControlStyle style, Colors colors, Painter painter) {
    /** 渐变端点、描边和内侧装饰；painter 从本次皮肤读取，局部改色无需复制画法。 */
    public record Colors(int top, int bottom, int border, int highlight, int shadow) { }

    /** 只读表面绘制入口；实现负责恢复自己改变的渲染状态。 */
    @FunctionalInterface
    public interface Painter {
        /** 绘制已确定几何与状态的背景，内容由控件继续绘制。 */
        void paint(GuiGraphics graphics, UiRect bounds, UiControlStyle.State state, UiSurfaceSkin skin);
    }

    /** 创建颜色与画法配套的皮肤。 */
    public UiSurfaceSkin {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(painter, "painter");
    }

    /** 零尺寸表面没有像素；保持极小窗口可安全参与布局。 */
    public void paint(GuiGraphics graphics, UiRect bounds, UiControlStyle.State state) {
        if (bounds.width() > 0 && bounds.height() > 0) painter.paint(graphics, bounds, state, this);
    }

    /** 替换状态样式，保留装饰与画法。 */
    public UiSurfaceSkin withStyle(UiControlStyle next) { return new UiSurfaceSkin(next, colors, painter); }

    /** 替换装饰色，保留状态底色与画法。 */
    public UiSurfaceSkin withColors(Colors next) { return new UiSurfaceSkin(style, next, painter); }

    /** 改常态底和渐变端点；其余交互状态保留原定义。 */
    public UiSurfaceSkin withFill(int color) {
        return new UiSurfaceSkin(new UiControlStyle(color, style.hoverBackground(), style.pressedBackground(),
                style.selectedBackground(), style.disabledBackground(), style.focusOutline(),
                style.scrollbarTrack(), style.scrollbarThumb()),
                new Colors(color, color, colors.border(), colors.highlight(), colors.shadow()), painter);
    }

    /** 纯色填充，没有描边。 */
    public static UiSurfaceSkin fill(int color) {
        return flat(color, 0).withPainter((g, r, state, skin) ->
                g.fill(r.x(), r.y(), r.right(), r.bottom(), skin.style.background(state)));
    }

    /** 状态底色与一像素描边。 */
    public static UiSurfaceSkin flat(int fill, int border) {
        return new UiSurfaceSkin(uniform(fill), new Colors(fill, fill, border, 0, 0), UiSurfaceSkin::drawFlat);
    }

    /** 窗口纵向渐变与一像素描边。 */
    public static UiSurfaceSkin gradient(int top, int bottom, int border) {
        return new UiSurfaceSkin(uniform(bottom), new Colors(top, bottom, border, 0, 0), (g, r, state, skin) -> {
            g.fillGradient(r.x(), r.y(), r.right(), r.bottom(), skin.colors.top(), skin.colors.bottom());
            g.renderOutline(r.x(), r.y(), r.width(), r.height(), skin.colors.border());
        });
    }

    /** 标题带渐变，仅在底边绘制分隔线。 */
    public static UiSurfaceSkin header(int top, int bottom, int border) {
        return gradient(top, bottom, border).withPainter((g, r, state, skin) -> {
            g.fillGradient(r.x(), r.y(), r.right(), r.bottom(), skin.colors.top(), skin.colors.bottom());
            g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), skin.colors.border());
        });
    }

    /** 内凹底板：顶部阴影、底部高光。 */
    public static UiSurfaceSkin inset(int fill, int border, int shadow, int highlight) {
        return new UiSurfaceSkin(uniform(fill), new Colors(fill, fill, border, highlight, shadow), (g, r, state, skin) -> {
            drawFlat(g, r, state, skin);
            if (r.width() > 2 && r.height() > 2) {
                g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, skin.colors.shadow());
                g.fill(r.x() + 1, r.bottom() - 2, r.right() - 1, r.bottom() - 1, skin.colors.highlight());
            }
        });
    }

    /** 抬升面板：顶部内侧高光。 */
    public static UiSurfaceSkin panel(int fill, int border, int highlight) {
        return new UiSurfaceSkin(uniform(fill), new Colors(fill, fill, border, highlight, 0), (g, r, state, skin) -> {
            drawFlat(g, r, state, skin);
            if (r.width() > 2 && r.height() > 2)
                g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, skin.colors.highlight());
        });
    }

    /** 复用控件背景 painter；文字仍由表面内容拥有。 */
    public static UiSurfaceSkin control(UiControlSkin control) {
        return flat(control.style().background(), 0).withStyle(control.style()).withPainter((g, r, state, skin) ->
                control.painter().paint(g, new UiControlPainter.Frame(r, skin.style, state, false)));
    }

    /** 保留全部色值，仅替换画法。 */
    public UiSurfaceSkin withPainter(Painter next) { return new UiSurfaceSkin(style, colors, next); }

    private static UiControlStyle uniform(int color) {
        return new UiControlStyle(color, color, color, color, color, 0, 0, 0);
    }

    private static void drawFlat(GuiGraphics graphics, UiRect rect, UiControlStyle.State state, UiSurfaceSkin skin) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), skin.style.background(state));
        graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), skin.colors.border());
    }
}
