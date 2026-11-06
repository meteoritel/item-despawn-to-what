package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

/** 输入、列表、面板、提示与加载标记使用的不可变语义色；业务画布配色由宿主保留。 */
public record UiSurfaceStyle(int text, int secondaryText, int disabledText, int inputText,
                             int inputFill, int inputBorder, int panelFill, int panelBorder,
                             int headerFill, int headerText, int selectionFill, int modalDim,
                             int tooltipFill, int tooltipBorderTop, int tooltipBorderBottom,
                             int tooltipText, int loading) {
    /** 半透明 Minecraft 外壳的通用语义色，提示保持原版紫色渐变边框。 */
    public static final UiSurfaceStyle MINECRAFT = new UiSurfaceStyle(
            0xFFF2F4F8, 0xFFB8C0CC, 0xFF7C8592, 0xFFEFF2F7,
            0x960A0C10, 0x8A5A6474, 0xC02E3642, 0x80505A6A,
            0xB4374150, 0xFFF4F6FA, 0x80506A8A, 0xA8000000,
            0xF0100010, 0x505000FF, 0x5028007F, 0xFFFFFFFF, 0xFFFFFFFF);

    /** 替换内容文字组，其余语义色保持。 */
    public UiSurfaceStyle withText(int normal, int secondary, int disabled) {
        return new UiSurfaceStyle(normal, secondary, disabled, inputText, inputFill, inputBorder, panelFill, panelBorder,
                headerFill, headerText, selectionFill, modalDim, tooltipFill, tooltipBorderTop, tooltipBorderBottom, tooltipText, loading);
    }

    /** 替换输入与列表内凹组。 */
    public UiSurfaceStyle withInput(int fill, int border, int text) {
        return new UiSurfaceStyle(this.text, secondaryText, disabledText, text, fill, border, panelFill, panelBorder,
                headerFill, headerText, selectionFill, modalDim, tooltipFill, tooltipBorderTop, tooltipBorderBottom, tooltipText, loading);
    }

    /** 替换面板组。 */
    public UiSurfaceStyle withPanel(int fill, int border) {
        return new UiSurfaceStyle(text, secondaryText, disabledText, inputText, inputFill, inputBorder, fill, border,
                headerFill, headerText, selectionFill, modalDim, tooltipFill, tooltipBorderTop, tooltipBorderBottom, tooltipText, loading);
    }

    /** 替换标题组。 */
    public UiSurfaceStyle withHeader(int fill, int text) {
        return new UiSurfaceStyle(this.text, secondaryText, disabledText, inputText, inputFill, inputBorder, panelFill, panelBorder,
                fill, text, selectionFill, modalDim, tooltipFill, tooltipBorderTop, tooltipBorderBottom, tooltipText, loading);
    }

    /** 替换选择底色与模态遮罩组。 */
    public UiSurfaceStyle withSelection(int fill, int dim) {
        return new UiSurfaceStyle(text, secondaryText, disabledText, inputText, inputFill, inputBorder, panelFill, panelBorder,
                headerFill, headerText, fill, dim, tooltipFill, tooltipBorderTop, tooltipBorderBottom, tooltipText, loading);
    }

    /** 替换提示组，普通提示和富内容外框共用。 */
    public UiSurfaceStyle withTooltip(int fill, int top, int bottom, int text) {
        return new UiSurfaceStyle(this.text, secondaryText, disabledText, inputText, inputFill, inputBorder, panelFill, panelBorder,
                headerFill, headerText, selectionFill, modalDim, fill, top, bottom, text, loading);
    }
}
