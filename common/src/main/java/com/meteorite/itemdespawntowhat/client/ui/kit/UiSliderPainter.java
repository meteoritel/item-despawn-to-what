package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 滑块绘制策略：把几何与状态交给实现方，实现方决定像素画法。
 *
 * <p>kit 只提供一帧几何（轨道、已填充段、手柄、文字槽）与状态标志，不描述美术细节，因此宿主
 * 可以替换成贴图绘制而不改变控件逻辑。实现方不得修改控件状态。</p>
 */
public interface UiSliderPainter {

    /**
     * 一帧只读绘制数据。
     *
     * @param bounds        控件整体矩形
     * @param track         轨道矩形
     * @param filled        已填充段矩形，宽度可为 0
     * @param thumb         手柄矩形
     * @param textSlot      数值文字槽矩形，宽度可为 0
     * @param style         注入样式
     * @param enabled       是否可用
     * @param hovered       指针是否悬停
     * @param pressed       是否按下
     * @param focused       是否聚焦
     * @param dragging      是否正在拖动
     * @param error         是否处于错误状态
     * @param overflowBelow 当前值低于显示窗口下沿
     * @param overflowAbove 当前值高于显示窗口上沿
     */
    record Frame(UiRect bounds, UiRect track, UiRect filled, UiRect thumb, UiRect textSlot,
                 UiSliderStyle style, boolean enabled, boolean hovered, boolean pressed,
                 boolean focused, boolean dragging, boolean error,
                 boolean overflowBelow, boolean overflowAbove) {
    }

    // 绘制一帧；文字由控件在 paint 之后自行绘制
    void paint(GuiGraphics graphics, Frame frame);
}
