package com.meteorite.itemdespawntowhat.client.ui.panel.configlist;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class ScrollableTextRenderer {

    // 文本滚动速度：像素/ms
    private static final float SCROLL_SPEED_PX_MS = 0.025f;
    // 滚动前静止时长（ms）
    private static final long SCROLL_PAUSE_MS = 1500L;
    // 文字与裁剪区右边缘的最小间距
    private static final int SCROLL_PADDING = 6;

    private ScrollableTextRenderer() {
    }

    public static void drawScrollableText(GuiGraphics guiGraphics, Font font,
                                          Component text, int x, int y,
                                          int maxWidth, int color,
                                          long createdAt) {
        int textWidth = font.width(text);
        int offset = calcScrollOffset(textWidth, maxWidth, createdAt);

        guiGraphics.enableScissor(x, y - 1, x + maxWidth, y + font.lineHeight + 1);
        guiGraphics.drawString(font, text, x + offset, y, color, false);
        guiGraphics.disableScissor();
    }

    private static int calcScrollOffset(int textWidth, int maxWidth, long createdAt) {
        if (textWidth <= maxWidth) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - createdAt;
        int overflow = textWidth - maxWidth + SCROLL_PADDING;
        long scrollDuration = (long) (overflow / SCROLL_SPEED_PX_MS);
        long cycleDuration = SCROLL_PAUSE_MS * 2 + scrollDuration * 2;
        long t = elapsed % cycleDuration;

        if (t < SCROLL_PAUSE_MS) {
            return 0;
        }
        if (t < SCROLL_PAUSE_MS + scrollDuration) {
            return -(int) ((t - SCROLL_PAUSE_MS) * SCROLL_SPEED_PX_MS);
        }
        if (t < SCROLL_PAUSE_MS * 2 + scrollDuration) {
            return -overflow;
        }
        long phase = t - SCROLL_PAUSE_MS * 2 - scrollDuration;
        return -(overflow - (int) (phase * SCROLL_SPEED_PX_MS));
    }
}
