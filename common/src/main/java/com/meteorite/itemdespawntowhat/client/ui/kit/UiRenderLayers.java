package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/*** 同一作用域内统一背景、模型和前景；每层弹窗完整覆盖上一层及其 tooltip。 */
public final class UiRenderLayers {
    public static final int ICON = 20;
    public static final int FOREGROUND = 40;
    public static final int MODAL_STEP = 600;
    private static final int VANILLA_ITEM_OFFSET = 150;

    private UiRenderLayers() { }

    // 相对当前作用域绘制，提交缓冲并保证恢复矩阵；禁止用绘制先后替代层级。
    public static void draw(GuiGraphics graphics, int offset, Runnable painter) {
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, offset);
            painter.run();
        } finally {
            try { graphics.flush(); }
            finally { graphics.pose().popPose(); }
        }
    }

    // 原版 renderItem 自加 150，先补偿再放入本作用域的图标层。
    public static void item(GuiGraphics graphics, Runnable painter) {
        draw(graphics, ICON - VANILLA_ITEM_OFFSET, painter);
    }
}
