package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceStyle;

/** 宿主不可变视觉值；通用表面复用 kit，业务颜色按共同变化的职责组合。 */
public record UiHostPalette(UiSurfaceStyle surfaces, Controls controls, Status status,
                            Code code, Canvas canvas, Description description) {
    /** 像素主题的语义色。 */
    public static UiHostPalette pixelDefaults() { return defaults(); }
    /** 扁平外壳与像素正文共用既有业务颜色。 */
    public static UiHostPalette flatDefaults() { return defaults(); }

    private static UiHostPalette defaults() {
        return new UiHostPalette(
                new UiSurfaceStyle(UiPalette.TEXT_PRIMARY, UiPalette.TEXT_SECONDARY, UiPalette.TEXT_DISABLED,
                        UiPalette.TEXT_ON_DARK, UiPalette.SLOT_INNER, UiPalette.SLOT_FILL, UiPalette.PANEL_FILL,
                        UiFlatPalette.CHROME_BORDER, UiPalette.CONTROL_FILL, UiPalette.HEADER_TEXT,
                        UiPalette.CONTROL_SELECTED, UiPalette.MODAL_DIM, UiPalette.PANEL_FILL,
                        UiFlatPalette.CHROME_BORDER, UiFlatPalette.CHROME_DIVIDER, UiPalette.TEXT_PRIMARY,
                        UiPalette.ACCENT),
                new Controls(UiPalette.TEXT_ON_DARK, UiPalette.CONTROL_FILL, UiPalette.CONTROL_HOVER,
                        UiPalette.CONTROL_SELECTED, UiPalette.CONTROL_DISABLED, UiPalette.CONTROL_BEVEL_LIGHT,
                        UiPalette.CONTROL_BEVEL_DARK, UiPalette.ACCENT, UiPalette.ACCENT_HOVER,
                        UiPalette.ACCENT_PRESSED, UiPalette.DANGER, UiPalette.LIST_ROW_ODD, UiPalette.LINK,
                        UiPalette.SCROLL_TRACK, UiPalette.SCROLL_THUMB, UiFlatPalette.CHROME_DIVIDER,
                        UiFlatPalette.PANEL_CONTROL_FILL),
                new Status(UiPalette.WARNING, UiPalette.SUCCESS, UiFlatPalette.STATUS_TEXT,
                        UiFlatPalette.STATUS_TEXT_MUTED, UiFlatPalette.STATUS_TEXT_DANGER,
                        UiFlatPalette.STATUS_TEXT_WARNING, UiFlatPalette.HELP_TEXT, UiFlatPalette.HELP_TEXT_MUTED,
                        UiFlatPalette.PANEL_TEXT, UiPalette.TEXT_PRIMARY),
                new Code(UiPalette.CODE_KEY, UiPalette.CODE_STRING, UiPalette.CODE_NUMBER, UiPalette.CODE_LITERAL,
                        UiPalette.CODE_PUNCTUATION, UiPalette.CODE_GUTTER_TEXT, UiPalette.CODE_GUTTER_LINE,
                        UiPalette.CODE_SECTION_FILL, UiPalette.CODE_SECTION_TEXT, UiPalette.CODE_PATH_TEXT),
                new Canvas(UiFlatPalette.CANVAS_FILL, UiFlatPalette.CANVAS_BORDER, UiFlatPalette.CANVAS_GRID_DOT,
                        UiFlatPalette.CANVAS_HINT_TEXT, UiFlatPalette.CANVAS_LINK,
                        UiFlatPalette.CANVAS_LINK_SELECTED, UiFlatPalette.CANVAS_CARD_FILL,
                        UiFlatPalette.CANVAS_CARD_BAND, UiFlatPalette.CANVAS_ENDPOINT_FILL,
                        UiFlatPalette.CANVAS_ENDPOINT_BAND, UiFlatPalette.CANVAS_ALL_FILL,
                        UiFlatPalette.CANVAS_ALL_BAND, UiFlatPalette.CANVAS_ALL_TEXT, UiFlatPalette.CANVAS_ANY_FILL,
                        UiFlatPalette.CANVAS_ANY_BAND, UiFlatPalette.CANVAS_ANY_TEXT, UiFlatPalette.CANVAS_NOT_FILL,
                        UiFlatPalette.CANVAS_NOT_BAND, UiFlatPalette.CANVAS_NOT_TEXT,
                        UiFlatPalette.CANVAS_FLOW_LINK, UiFlatPalette.CANVAS_CARD_HOVER_FILL,
                        UiFlatPalette.CANVAS_CARD_SELECTED_FILL, UiFlatPalette.CANVAS_CARD_BORDER,
                        UiFlatPalette.CANVAS_CARD_BORDER_SELECTED, UiFlatPalette.CANVAS_TEXT,
                        UiFlatPalette.CANVAS_TEXT_MUTED, UiFlatPalette.CANVAS_TEXT_DANGER,
                        UiFlatPalette.CANVAS_TEXT_ON_ACCENT, UiFlatPalette.CANVAS_ACCENT,
                        UiFlatPalette.CANVAS_ACCENT_HOVER, UiFlatPalette.CANVAS_HOTSPOT_FILL,
                        UiFlatPalette.CANVAS_HOTSPOT_BORDER, UiFlatPalette.CANVAS_DRAG_GHOST_FILL,
                        UiFlatPalette.CANVAS_RUBBER_FILL, UiFlatPalette.CANVAS_PICKER_FILL,
                        UiFlatPalette.CANVAS_PICKER_BORDER, UiFlatPalette.CANVAS_PICKER_HEADER_FILL,
                        UiFlatPalette.CANVAS_PICKER_HEADER_TEXT, UiFlatPalette.CANVAS_PICKER_INPUT_FILL,
                        UiFlatPalette.CANVAS_PICKER_INPUT_BORDER, UiFlatPalette.CANVAS_PICKER_ROW_SELECTED,
                        UiFlatPalette.CANVAS_PICKER_TEXT, UiFlatPalette.CANVAS_PICKER_TEXT_MUTED,
                        UiFlatPalette.CANVAS_PICKER_TEXT_DISABLED),
                new Description(UiPalette.TOOLTIP_TITLE, UiPalette.TOOLTIP_METRIC, UiPalette.TOOLTIP_REFERENCE,
                        UiFlatPalette.CANVAS_ALL_TEXT, UiFlatPalette.CANVAS_ANY_TEXT,
                        UiFlatPalette.CANVAS_NOT_TEXT));
    }

    /** 控件状态与外壳结构的配套颜色。 */
    public record Controls(int textOnDark, int controlFill, int controlHover, int controlSelected,
            int controlDisabled, int controlBevelLight, int controlBevelDark, int accent, int accentHover,
            int accentPressed, int danger, int listRowOdd, int link, int scrollTrack, int scrollThumb,
            int chromeDivider, int panelControlFill) { }

    /** 业务状态与说明文字的配套颜色。 */
    public record Status(int warning, int success, int statusText, int statusTextMuted, int statusTextDanger,
            int statusTextWarning, int helpText, int helpTextMuted, int panelText, int filledText) { }

    /** 只读代码内容的配套颜色。 */
    public record Code(int codeKey, int codeString, int codeNumber, int codeLiteral, int codePunctuation,
            int codeGutterText, int codeGutterLine, int codeSectionFill, int codeSectionText, int codePathText) { }

    /** 画布与选择器的配套颜色。 */
    public record Canvas(int canvasFill, int canvasBorder, int canvasGridDot, int canvasHintText, int canvasLink,
            int canvasLinkSelected, int canvasCardFill, int canvasCardBand, int canvasEndpointFill,
            int canvasEndpointBand, int canvasAllFill, int canvasAllBand, int canvasAllText, int canvasAnyFill,
            int canvasAnyBand, int canvasAnyText, int canvasNotFill, int canvasNotBand, int canvasNotText,
            int canvasFlowLink, int canvasCardHoverFill, int canvasCardSelectedFill, int canvasCardBorder,
            int canvasCardBorderSelected, int canvasText, int canvasTextMuted, int canvasTextDanger,
            int canvasTextOnAccent, int canvasAccent, int canvasAccentHover, int canvasHotspotFill,
            int canvasHotspotBorder, int canvasDragGhostFill, int canvasRubberFill, int canvasPickerFill,
            int canvasPickerBorder, int canvasPickerHeaderFill, int canvasPickerHeaderText,
            int canvasPickerInputFill, int canvasPickerInputBorder, int canvasPickerRowSelected,
            int canvasPickerText, int canvasPickerTextMuted, int canvasPickerTextDisabled) { }

    /** 结构化提示语义的配套颜色。 */
    public record Description(int tooltipTitle, int tooltipMetric, int tooltipReference, int tooltipAll, int tooltipAny, int tooltipNot) { }
}
