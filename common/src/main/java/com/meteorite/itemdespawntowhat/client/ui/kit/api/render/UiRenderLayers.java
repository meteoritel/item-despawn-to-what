package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;

import net.minecraft.client.gui.GuiGraphics;

/** 同一作用域内统一背景、模型和前景；每层弹窗完整覆盖上一层及其 tooltip。 */
public final class UiRenderLayers {
    /** 当前局部作用域的图标层。 */
    public static final int ICON = 20;
    /** 位于内容图标之上的控件前景。 */
    public static final int FOREGROUND = 40;
    /** 侧板位于普通控件前景之上。 */
    public static final int SIDE_PANEL = FOREGROUND + ICON;
    /** 同一作用域内共用的提示层。 */
    public static final int TOOLTIP = FOREGROUND * 2 + ICON;
    /** 相邻模态作用域的间距，容纳各自的局部提示层。 */
    public static final int MODAL_STEP = 600;
    /** 页面与首层模态之间的遮罩。 */
    public static final int MODAL_MASK = MODAL_STEP;
    private static final int VANILLA_ITEM_OFFSET = 150;

    private UiRenderLayers() { }

    /**
     * 相对当前作用域绘制，提交缓冲并在异常时恢复矩阵。
     */
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

    /** 从零开始的模态深度对应内容层；首层位于遮罩上方。 */
    public static int modalContent(int depth) {
        return (depth + 1) * MODAL_STEP + 1;
    }

    /** 补偿原版 renderItem 的偏移，将物品放入当前作用域的图标层。 */
    public static void item(GuiGraphics graphics, Runnable painter) {
        draw(graphics, ICON - VANILLA_ITEM_OFFSET, painter);
    }
}
