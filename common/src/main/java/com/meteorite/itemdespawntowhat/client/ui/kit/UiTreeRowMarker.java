package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 通用树行的缩进、展开命中与像素三角；颜色与尺寸由宿主提供。
 * <p>宿主在 GUI 绘制线程调用；命中坐标先换算到内容坐标，与绘制使用相同缩进原点。</p>
 * <pre>{@code
 * int left = UiTreeRowMarker.left(depth, indent);
 * boolean hit = UiTreeRowMarker.hit(contentX, depth, indent, markerWidth);
 * UiTreeRowMarker.draw(graphics, contentOriginX + left, centerY, expanded, color);
 * }</pre>
 */
public final class UiTreeRowMarker {
    private UiTreeRowMarker() {}
    /** 当前深度对应的标记起点。 */
    public static int left(int depth, int indent) { return Math.max(0, depth) * Math.max(0, indent); }
    /** 标记带的水平命中，与文本缩进使用同一原点。 */
    public static boolean hit(double contentX, int depth, int indent, int width) {
        int x = left(depth, indent);
        return contentX >= x && contentX <= x + width;
    }
    /** 绘制与字体无关的七像素展开三角。 */
    public static void draw(GuiGraphics graphics, int x, int centerY, boolean expanded, int color) {
        for (int step = 0; step < 4; step++) {
            if (expanded) graphics.fill(x + step, centerY - 2 + step, x + 7 - step, centerY - 1 + step, color);
            else graphics.fill(x + 1 + step, centerY - 3 + step, x + 2 + step, centerY + 4 - step, color);
        }
    }
}
