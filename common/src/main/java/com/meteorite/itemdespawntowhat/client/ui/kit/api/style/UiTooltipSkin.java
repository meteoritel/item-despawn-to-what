package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import java.util.Objects;

/** 提示外框的样式、留白与画法；普通文字和宿主富内容共用。 */
public record UiTooltipSkin(UiSurfaceStyle style, int padding, Painter painter) {
    /** 通用默认原版提示。 */
    public static final UiTooltipSkin MINECRAFT = minecraft(UiSurfaceStyle.MINECRAFT);

    /** 保持可绘制的内容留白与完整画法。 */
    public UiTooltipSkin {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(painter, "painter");
        if (padding < 0) throw new IllegalArgumentException("padding");
    }

    /** 外框画法，正文排版不进入主题。 */
    @FunctionalInterface
    public interface Painter {
        /** 按已完成布局的外框绘制。 */
        void paint(GuiGraphics graphics, UiRect bounds, UiSurfaceStyle style);
    }

    /** 使用原版深色底与渐变边框的画法。 */
    public static UiTooltipSkin minecraft(UiSurfaceStyle style) {
        return new UiTooltipSkin(style, 4, UiTooltipSkin::minecraftBackground);
    }

    /** 单色底与一像素描边，用于宿主浅色提示。 */
    public static UiTooltipSkin flat(UiSurfaceStyle style) {
        return new UiTooltipSkin(style, 4, (graphics, rect, colors) -> {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), colors.tooltipFill());
            graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), colors.tooltipBorderTop());
        });
    }

    /** 绘制当前 skin 的外框。 */
    public void paint(GuiGraphics graphics, UiRect bounds) { painter.paint(graphics, bounds, style); }

    private static void minecraftBackground(GuiGraphics graphics, UiRect bounds, UiSurfaceStyle style) {
        int left = bounds.x() + 1;
        int top = bounds.y() + 1;
        int outerWidth = bounds.width() - 2;
        int outerHeight = bounds.height() - 2;
        graphics.fill(left, top - 1, left + outerWidth, top, style.tooltipFill());
        graphics.fill(left, top + outerHeight, left + outerWidth, top + outerHeight + 1, style.tooltipFill());
        graphics.fill(left, top, left + outerWidth, top + outerHeight, style.tooltipFill());
        graphics.fill(left - 1, top, left, top + outerHeight, style.tooltipFill());
        graphics.fill(left + outerWidth, top, left + outerWidth + 1, top + outerHeight, style.tooltipFill());
        graphics.fillGradient(left, top + 1, left + 1, top + outerHeight - 1, style.tooltipBorderTop(), style.tooltipBorderBottom());
        graphics.fillGradient(left + outerWidth - 1, top + 1, left + outerWidth, top + outerHeight - 1,
                style.tooltipBorderTop(), style.tooltipBorderBottom());
        graphics.fill(left, top, left + outerWidth, top + 1, style.tooltipBorderTop());
        graphics.fill(left, top + outerHeight - 1, left + outerWidth, top + outerHeight, style.tooltipBorderBottom());
    }
}
