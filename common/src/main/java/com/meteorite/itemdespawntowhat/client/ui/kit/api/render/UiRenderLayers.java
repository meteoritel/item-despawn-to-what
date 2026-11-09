package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;

import net.minecraft.client.gui.GuiGraphics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.internal.render.UiModelSurface;
import com.meteorite.itemdespawntowhat.client.ui.kit.internal.render.UiScissor;
import java.util.function.Consumer;

/** 同一作用域内统一背景、模型和前景；每层弹窗完整覆盖上一层及其 tooltip。 */
public final class UiRenderLayers {
    private static final int LAYER_STEP = 20;
    /** 文字、Sprite 与物品共享的当前局部内容层。 */
    public static final int CONTENT = 0;
    /** 位于内容图标之上的控件前景。 */
    public static final int FOREGROUND = LAYER_STEP * 2;
    /** 侧板位于普通控件前景之上。 */
    public static final int SIDE_PANEL = FOREGROUND + LAYER_STEP;
    /** 同一作用域内共用的提示层。 */
    public static final int TOOLTIP = FOREGROUND * 2 + LAYER_STEP;
    /** 相邻模态作用域的间距，容纳各自的局部提示层。 */
    public static final int MODAL_STEP = 600;
    /** 页面与首层模态之间的遮罩。 */
    public static final int MODAL_MASK = MODAL_STEP;
    private static final int VANILLA_ITEM_OFFSET = 150;

    private UiRenderLayers() { }

    /**
     * 在内容变换入栈前设置视口裁剪；按调用方 pose 换算为 GUI 像素，向内取整并先提交绘制批次。
     * 1.21.1 的原版裁剪入口不读取 pose，本入口保持视口独立于内容平移与缩放。
     */
    public static void enableScissor(GuiGraphics graphics, UiRect viewport) {
        // 向内取整保证边界外不泄漏；切换前提交绘制批次，因为 blit 这类绘制在非 managed
        // 上下文里不会自动 endBatch（GuiGraphics.flushIfUnmanaged 只覆盖 fill/drawString）。
        UiScissor.enable(graphics, viewport.x(), viewport.y(), viewport.right(), viewport.bottom(), false, true);
    }

    /** 提交当前裁剪内批次，再恢复上一层裁剪；与 enableScissor 在 finally 中配对。 */
    public static void disableScissor(GuiGraphics graphics) {
        graphics.flush();
        graphics.disableScissor();
    }


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

    /** 以覆盖根给出的深度绘制遮罩；透明度和淡入由该根计算。 */
    public static void mask(GuiGraphics graphics, UiRect bounds, int depth, int color) {
        if ((color >>> 24) == 0) return;
        draw(graphics, (depth + 1) * MODAL_STEP,
                () -> graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), color));
    }

    /** 从零开始的模态深度对应内容层；首层位于遮罩上方。 */
    public static int modalContent(int depth) {
        return (depth + 1) * MODAL_STEP + 1;
    }

    /** 模型先在独立深度工作区完成绘制，再以当前内容平面合成；回调使用传入的局部上下文。 */
    public static void model(GuiGraphics graphics, UiRect bounds, Consumer<GuiGraphics> painter) {
        UiModelSurface.draw(graphics, bounds, painter);
    }

    /** 在局部模型工作区补偿原版物品偏移；回调不得捕获外部绘制上下文。 */
    public static void item(GuiGraphics graphics, UiRect bounds, Consumer<GuiGraphics> painter) {
        model(graphics, bounds, local -> draw(local, CONTENT - VANILLA_ITEM_OFFSET, () -> painter.accept(local)));
    }

    /** 释放共享模型工作区；UiScreen 自动调用，直接接入原版 Screen 的宿主在卸载时调用。 */
    public static void releaseModels() {
        UiModelSurface.release();
    }
}
