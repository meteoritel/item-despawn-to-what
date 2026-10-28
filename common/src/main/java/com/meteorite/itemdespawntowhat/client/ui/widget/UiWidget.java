package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 通用控件的渲染与输入契约。
 * <p>宿主（Screen 或弹窗）统一转发鼠标、键盘与滚动事件；
 * 控件返回 {@code true} 表示事件已消费，宿主不再继续分发。
 * <p>所有坐标均为 GUI 逻辑坐标，不使用缩放矩阵。
 */
public interface UiWidget extends UiInputTarget {

    // 控件矩形
    UiRect bounds();

    // 设置控件矩形（宽高为负时按 0 处理）
    void setBounds(int x, int y, int width, int height);

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    // 是否可见（不可见时不渲染、不接收输入）
    default boolean isVisible() {
        return true;
    }

}
