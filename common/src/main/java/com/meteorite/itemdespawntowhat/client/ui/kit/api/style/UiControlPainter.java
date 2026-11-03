package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import net.minecraft.client.gui.GuiGraphics;

/** 普通控件的结构画法；只读取几何与状态，文字与交互由控件持有。 */
@FunctionalInterface
public interface UiControlPainter {
    /** 一帧只读绘制数据，焦点表示当前是否应显示键盘焦点。 */
    record Frame(UiRect bounds, UiControlStyle style, UiControlStyle.State state, boolean focused) { }

    /** 使用当前几何绘制背景和结构。 */
    void paint(GuiGraphics graphics, Frame frame);

    /** 显式纯色画法，供宿主的像素与扁平主题组合。 */
    UiControlPainter FILL = (graphics, frame) -> {
        UiRect rect = frame.bounds();
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), frame.style().background(frame.state()));
    };
}
