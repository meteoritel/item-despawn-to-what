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

    /**
     * 在给定矩形内绘制点阵网格；矩形无效、缩放非法或点数超过上限时放弃绘制。
     *
     * @param scale 缩放档位
     * @param x0 矩形一端 X 坐标
     * @param y0 矩形一端 Y 坐标
     * @param x1 矩形另一端 X 坐标
     * @param y1 矩形另一端 Y 坐标
     * @param baseStep 未缩放的基准步长（像素）
     * @param color 方格颜色
     */
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
