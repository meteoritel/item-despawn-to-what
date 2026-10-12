package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 正交折线连接：父节点底部到子节点顶部，按「竖 → 横 → 竖」三段绘制，屏幕像素取整。
 *
 * <p>横向段取两端点的中点高度，因此折线始终水平居中；{@code y1 > y2} 时两端自动对调，
 * 调用方不需要先排序。线宽至少 1 像素，端点按 {@code 中心 - 线宽/2} 取整对齐。</p>
 */
public final class UiOrthoLink {
    private UiOrthoLink() {
    }

    public static void drawDown(GuiGraphics graphics, int x1, int y1, int x2, int y2, int thickness, int color) {
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        int startX = y1 <= y2 ? x1 : x2;
        int endX = y1 <= y2 ? x2 : x1;
        int lineWidth = Math.max(1, thickness);
        int middle = (top + bottom) / 2;
        column(graphics, startX, top, middle + lineWidth, lineWidth, color);
        int half = lineWidth / 2;
        fill(graphics, Math.min(startX, endX) - half, middle,
                Math.max(startX, endX) - half + lineWidth, middle + lineWidth, color);
        column(graphics, endX, middle, bottom, lineWidth, color);
    }

    private static void column(GuiGraphics graphics, int centerX, int top, int bottom, int lineWidth, int color) {
        if (bottom <= top) return;
        int left = centerX - lineWidth / 2;
        fill(graphics, left, top, left + lineWidth, bottom, color);
    }

    private static void fill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int left = Math.min(x0, x1);
        int top = Math.min(y0, y1);
        int right = Math.max(x0, x1);
        int bottom = Math.max(y0, y1);
        if (right <= left || bottom <= top) return;
        graphics.fill(left, top, right, bottom, color);
    }
}
