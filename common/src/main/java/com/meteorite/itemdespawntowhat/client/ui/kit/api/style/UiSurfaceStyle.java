package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

/** 输入、列表、面板、提示与加载标记使用的不可变语义色；业务画布配色由宿主保留。 */
public record UiSurfaceStyle(int text, int secondaryText, int disabledText, int inputText,
                             int inputFill, int inputBorder, int panelFill, int panelBorder,
                             int headerFill, int headerText, int selectionFill, int modalDim,
                             int tooltipFill, int tooltipBorderTop, int tooltipBorderBottom,
                             int tooltipText, int loading) {
    /** 原版输入框、灰色面板与紫色提示边框。 */
    public static final UiSurfaceStyle MINECRAFT = new UiSurfaceStyle(
            0xFFFFFFFF, 0xFFAAAAAA, 0xFFA0A0A0, 0xFFE0E0E0,
            0xFF000000, 0xFFA0A0A0, 0xFF313131, 0xFF101010,
            0xFF444444, 0xFFFFFFFF, 0xFF555577, 0xA0000000,
            0xF0100010, 0x505000FF, 0x5028007F, 0xFFFFFFFF, 0xFFFFFFFF);
}
