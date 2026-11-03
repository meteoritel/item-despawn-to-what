package com.meteorite.itemdespawntowhat.client.ui.kit.api.text;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** 注入自然文本宽高，供文档和控件布局在测量阶段读取。 */
public interface TextMeasurer {
    /** 测量组件的自然像素宽度。 */
    int width(Component text);
    /** 当前字体的自然行高。 */
    int lineHeight();

    /** 创建原版 Font 的度量适配器。 */
    static TextMeasurer of(Font font) { return new FontMeasurer(font); }

    /** 原版字体适配器，仅在排版阶段测量。 */
    record FontMeasurer(Font font) implements TextMeasurer {
        @Override public int width(Component text) { return font.width(text); }
        @Override public int lineHeight() { return font.lineHeight; }
    }
}
