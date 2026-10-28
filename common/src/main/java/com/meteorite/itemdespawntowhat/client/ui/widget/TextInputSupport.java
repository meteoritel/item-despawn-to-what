package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import net.minecraft.client.gui.screens.Screen;

/** 原版文本适配共用的宿主尺寸与事件边界采样；kit 接收显式上下文。 */
final class TextInputSupport {
    static final int PADDING_X = 3;
    static final int PADDING_Y = 2;
    static final int MIN_WIDTH = 24;
    private TextInputSupport() {}
    static UiInputContext pointer(double x, double y, int button) {
        return UiInputContext.of(x, y, button, Screen.hasShiftDown(), Screen.hasControlDown(), Screen.hasAltDown());
    }
}
