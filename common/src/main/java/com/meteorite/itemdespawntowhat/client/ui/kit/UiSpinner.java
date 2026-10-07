package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/*** 由宿主时间驱动的追逐圆点加载动画，无贴图或逐帧资源创建。 */
public final class UiSpinner {
    private UiSpinner() { }

    public static void render(GuiGraphics graphics, int x, int y, long milliseconds, int color) {
        double phase = (milliseconds % 1800) / 1800.0 * Math.PI * 2;
        for (int i = 0; i < 5; i++) {
            double angle = phase - i * 0.34 - Math.PI / 2;
            int dx = x + 5 + (int) Math.round(Math.cos(angle) * 4);
            int dy = y + 5 + (int) Math.round(Math.sin(angle) * 4);
            int alpha = 255 - i * 35;
            graphics.fill(dx, dy, dx + 2, dy + 2, (color & 0xFFFFFF) | alpha << 24);
        }
    }
}
