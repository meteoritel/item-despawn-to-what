package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 画布点阵网格：在给定屏幕矩形内按缩放档位换算步长，逐点绘制 1 像素方格。
 *
 * <p>网格锚定在矩形左上角，因此适合作为固定背景（不随内容平移蠕动）；
 * 步长等于 {@code baseStep * scale} 取整后至少 1 像素。</p>
 */
public final class UiDotGrid {
    /** 单次绘制的点数上限：异常大的矩形直接放弃绘制，避免逐点循环把一帧拖死。 */
    private static final long MAX_DOTS = 4_000_000L;

    private UiDotGrid() {
    }

    public static void draw(GuiGraphics graphics, double scale, int x0, int y0, int x1, int y1,
                            int baseStep, int color) {
        int left = Math.min(x0, x1);
        int top = Math.min(y0, y1);
        int right = Math.max(x0, x1);
        int bottom = Math.max(y0, y1);
        if (right <= left || bottom <= top || baseStep <= 0) return;
        if (!Double.isFinite(scale) || scale <= 0) return;
        int step = Math.max(1, (int) Math.round(baseStep * scale));
        long columns = (right - left + (long) step - 1L) / step;
        long rows = (bottom - top + (long) step - 1L) / step;
        if (columns * rows > MAX_DOTS) return;
        for (int x = left; x < right; x += step) {
            for (int y = top; y < bottom; y += step) {
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }
}
