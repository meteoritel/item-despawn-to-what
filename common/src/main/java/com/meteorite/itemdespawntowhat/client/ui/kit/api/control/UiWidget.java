package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 通用控件的渲染与输入契约。
 * <p>宿主（Screen 或弹窗）统一转发鼠标、键盘与滚动事件；
 * 控件返回 {@code true} 表示事件已消费，宿主不再继续分发。
 * <p>所有坐标均为 GUI 逻辑坐标，不使用缩放矩阵。
 */
public interface UiWidget extends UiInputTarget {

    /** 当前控件的 GUI 逻辑矩形。 */
    UiRect bounds();

    /** 设置 GUI 逻辑矩形，入口将负宽高按零处理。 */
    void setBounds(int x, int y, int width, int height);

    /** 使用宿主字体与指针快照绘制控件。 */
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    /** 是否参与绘制与输入；默认可见，宿主按实际状态过滤。 */
    default boolean isVisible() {
        return true;
    }

}
