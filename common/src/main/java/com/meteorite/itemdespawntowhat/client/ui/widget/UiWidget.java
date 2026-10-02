package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/***
 * 通用控件的渲染与输入契约。
 * <p>宿主（Screen 或弹窗）统一转发鼠标、键盘与滚动事件；
 * 控件返回 {@code true} 表示事件已消费，宿主不再继续分发。
 * <p>所有坐标均为 GUI 逻辑坐标，不使用缩放矩阵。
 */
public interface UiWidget {

    // 控件矩形
    UiRect bounds();

    // 设置控件矩形（宽高为负时按 0 处理）
    void setBounds(int x, int y, int width, int height);

    // 渲染控件
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    // 是否可见（不可见时不渲染、不接收输入）
    default boolean isVisible() {
        return true;
    }

    // 鼠标按下
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    // 鼠标松开
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    // 鼠标拖拽
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    // 鼠标滚轮
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    // 键盘按下
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    // 字符输入
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
}
