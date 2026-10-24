package com.meteorite.itemdespawntowhat.client.ui.theme;

/**
 * 宿主控件调色板视图：把像素主题与画布页的用色收敛成控件可见的一组只读语义色。
 *
 * <p>控件不再自行读取全局调色板常量，而是经注入的 {@link UiHostTheme#palette()} 取色；本类只暴露控件
 * 真正用到的颜色，不把整套调色板摊到控件层，换主题只需替换主题实现，控件代码不变。</p>
 *
 * <p>像素皮肤与画布页共享本调色板：两者的差别在绘制原语（立体边、同色描边与层次）上，而不在控件用色上，
 * 因此画布页的 {@code canvas*} 色同样由本表提供，注入主题不会改变画布页既有配色。</p>
 */
// 宿主调色板 API：由页面层经主题调用；本包内暂无调用点的项暂不触发未使用提示
public final class UiHostPalette {

    // 宿主共用实例：调色板按定义不可变，无需按皮肤复制
    private static final UiHostPalette INSTANCE = new UiHostPalette();

    // 只允许经主题入口获取
    private UiHostPalette() {
    }

    // 宿主调色板
    public static UiHostPalette hostPalette() {
        return INSTANCE;
    }

    // ---- 文本 ----

    public int textPrimary() {
        return UiPalette.TEXT_PRIMARY;
    }

    public int textSecondary() {
        return UiPalette.TEXT_SECONDARY;
    }

    public int textDisabled() {
        return UiPalette.TEXT_DISABLED;
    }

    public int textOnDark() {
        return UiPalette.TEXT_ON_DARK;
    }

    // ---- 控件结构色 ----

    public int controlFill() {
        return UiPalette.CONTROL_FILL;
    }

    public int controlHover() {
        return UiPalette.CONTROL_HOVER;
    }

    public int controlSelected() {
        return UiPalette.CONTROL_SELECTED;
    }

    public int controlDisabled() {
        return UiPalette.CONTROL_DISABLED;
    }

    public int controlBevelLight() {
        return UiPalette.CONTROL_BEVEL_LIGHT;
    }

    public int controlBevelDark() {
        return UiPalette.CONTROL_BEVEL_DARK;
    }

    // ---- 状态色 ----

    public int accent() {
        return UiPalette.ACCENT;
    }

    public int accentHover() {
        return UiPalette.ACCENT_HOVER;
    }

    public int accentPressed() {
        return UiPalette.ACCENT_PRESSED;
    }

    public int danger() {
        return UiPalette.DANGER;
    }

    // ---- 区域与滚动 ----

    public int slotFill() {
        return UiPalette.SLOT_FILL;
    }

    public int slotInner() {
        return UiPalette.SLOT_INNER;
    }

    public int headerText() {
        return UiPalette.HEADER_TEXT;
    }

    public int listRowOdd() {
        return UiPalette.LIST_ROW_ODD;
    }

    public int link() {
        return UiPalette.LINK;
    }

    public int modalDim() {
        return UiPalette.MODAL_DIM;
    }

    public int scrollTrack() {
        return UiPalette.SCROLL_TRACK;
    }

    public int scrollThumb() {
        return UiPalette.SCROLL_THUMB;
    }

    // ---- 状态色（补充） ----

    // 警告状态
    public int warning() {
        return UiPalette.WARNING;
    }

    // 成功状态
    public int success() {
        return UiPalette.SUCCESS;
    }

    // 分区面板底色
    public int panelFill() {
        return UiPalette.PANEL_FILL;
    }

    // ---- 只读代码视图 ----

    // 对象键
    public int codeKey() {
        return UiPalette.CODE_KEY;
    }

    // 字符串值
    public int codeString() {
        return UiPalette.CODE_STRING;
    }

    // 数字值
    public int codeNumber() {
        return UiPalette.CODE_NUMBER;
    }

    // true / false / null
    public int codeLiteral() {
        return UiPalette.CODE_LITERAL;
    }

    // 标点与结构字符
    public int codePunctuation() {
        return UiPalette.CODE_PUNCTUATION;
    }

    // 行号
    public int codeGutterText() {
        return UiPalette.CODE_GUTTER_TEXT;
    }

    // 行号栏分隔线
    public int codeGutterLine() {
        return UiPalette.CODE_GUTTER_LINE;
    }

    // 代码块标题条底色
    public int codeSectionFill() {
        return UiPalette.CODE_SECTION_FILL;
    }

    // 代码块标题
    public int codeSectionText() {
        return UiPalette.CODE_SECTION_TEXT;
    }

    // 代码块副标题（文件路径）
    public int codePathText() {
        return UiPalette.CODE_PATH_TEXT;
    }

    // ---- 画布扁平外壳 ----

    // 功能栏与工具列描边
    public int chromeBorder() {
        return UiFlatPalette.CHROME_BORDER;
    }

    // 功能栏 / 工具列的图标分组线
    public int chromeDivider() {
        return UiFlatPalette.CHROME_DIVIDER;
    }

    // 底部状态栏正文
    public int statusText() {
        return UiFlatPalette.STATUS_TEXT;
    }

    // 底部状态栏次要文字
    public int statusTextMuted() {
        return UiFlatPalette.STATUS_TEXT_MUTED;
    }

    // 底部状态栏错误文字
    public int statusTextDanger() {
        return UiFlatPalette.STATUS_TEXT_DANGER;
    }

    // 底部状态栏警告文字
    public int statusTextWarning() {
        return UiFlatPalette.STATUS_TEXT_WARNING;
    }

    // 帮助浮层标题
    public int helpText() {
        return UiFlatPalette.HELP_TEXT;
    }

    // 帮助浮层正文
    public int helpTextMuted() {
        return UiFlatPalette.HELP_TEXT_MUTED;
    }

    // 浮动调板正文
    public int panelText() {
        return UiFlatPalette.PANEL_TEXT;
    }

    // 浮动调板控件常态底色
    public int panelControlFill() {
        return UiFlatPalette.PANEL_CONTROL_FILL;
    }

    // ---- 条件树画布 ----

    public int canvasFill() {
        return UiFlatPalette.CANVAS_FILL;
    }

    public int canvasBorder() {
        return UiFlatPalette.CANVAS_BORDER;
    }

    public int canvasGridDot() {
        return UiFlatPalette.CANVAS_GRID_DOT;
    }

    public int canvasHintText() {
        return UiFlatPalette.CANVAS_HINT_TEXT;
    }

    public int canvasLink() {
        return UiFlatPalette.CANVAS_LINK;
    }

    public int canvasLinkSelected() {
        return UiFlatPalette.CANVAS_LINK_SELECTED;
    }

    public int canvasCardFill() {
        return UiFlatPalette.CANVAS_CARD_FILL;
    }

    // 卡片标题带：用色阶区分层级，替代原来的横贯分隔线
    public int canvasCardBand() {
        return UiFlatPalette.CANVAS_CARD_BAND;
    }

    // 端点槽位（来源 / 结果）主体与标题带
    public int canvasEndpointFill() {
        return UiFlatPalette.CANVAS_ENDPOINT_FILL;
    }

    public int canvasEndpointBand() {
        return UiFlatPalette.CANVAS_ENDPOINT_BAND;
    }

    public int canvasAllFill() { return UiFlatPalette.CANVAS_ALL_FILL; }
    public int canvasAllBand() { return UiFlatPalette.CANVAS_ALL_BAND; }
    public int canvasAllText() { return UiFlatPalette.CANVAS_ALL_TEXT; }
    public int canvasAnyFill() { return UiFlatPalette.CANVAS_ANY_FILL; }
    public int canvasAnyBand() { return UiFlatPalette.CANVAS_ANY_BAND; }
    public int canvasAnyText() { return UiFlatPalette.CANVAS_ANY_TEXT; }
    public int canvasNotFill() { return UiFlatPalette.CANVAS_NOT_FILL; }
    public int canvasNotBand() { return UiFlatPalette.CANVAS_NOT_BAND; }
    public int canvasNotText() { return UiFlatPalette.CANVAS_NOT_TEXT; }
    public int canvasFlowLink() { return UiFlatPalette.CANVAS_FLOW_LINK; }

    public int canvasCardHoverFill() {
        return UiFlatPalette.CANVAS_CARD_HOVER_FILL;
    }

    public int canvasCardSelectedFill() {
        return UiFlatPalette.CANVAS_CARD_SELECTED_FILL;
    }

    public int canvasCardBorder() {
        return UiFlatPalette.CANVAS_CARD_BORDER;
    }

    public int canvasCardBorderSelected() {
        return UiFlatPalette.CANVAS_CARD_BORDER_SELECTED;
    }

    public int canvasText() {
        return UiFlatPalette.CANVAS_TEXT;
    }

    public int canvasTextMuted() {
        return UiFlatPalette.CANVAS_TEXT_MUTED;
    }

    public int canvasTextDanger() {
        return UiFlatPalette.CANVAS_TEXT_DANGER;
    }

    public int canvasTextOnAccent() {
        return UiFlatPalette.CANVAS_TEXT_ON_ACCENT;
    }

    public int canvasAccent() {
        return UiFlatPalette.CANVAS_ACCENT;
    }

    public int canvasAccentHover() {
        return UiFlatPalette.CANVAS_ACCENT_HOVER;
    }

    public int canvasHotspotFill() {
        return UiFlatPalette.CANVAS_HOTSPOT_FILL;
    }

    public int canvasHotspotBorder() {
        return UiFlatPalette.CANVAS_HOTSPOT_BORDER;
    }

    public int canvasDragGhostFill() {
        return UiFlatPalette.CANVAS_DRAG_GHOST_FILL;
    }

    public int canvasRubberFill() {
        return UiFlatPalette.CANVAS_RUBBER_FILL;
    }

    public int canvasPickerFill() {
        return UiFlatPalette.CANVAS_PICKER_FILL;
    }

    public int canvasPickerBorder() {
        return UiFlatPalette.CANVAS_PICKER_BORDER;
    }

    public int canvasPickerHeaderFill() {
        return UiFlatPalette.CANVAS_PICKER_HEADER_FILL;
    }

    public int canvasPickerHeaderText() {
        return UiFlatPalette.CANVAS_PICKER_HEADER_TEXT;
    }

    public int canvasPickerInputFill() {
        return UiFlatPalette.CANVAS_PICKER_INPUT_FILL;
    }

    public int canvasPickerInputBorder() {
        return UiFlatPalette.CANVAS_PICKER_INPUT_BORDER;
    }

    public int canvasPickerRowSelected() {
        return UiFlatPalette.CANVAS_PICKER_ROW_SELECTED;
    }

    public int canvasPickerText() {
        return UiFlatPalette.CANVAS_PICKER_TEXT;
    }

    public int canvasPickerTextMuted() {
        return UiFlatPalette.CANVAS_PICKER_TEXT_MUTED;
    }

    public int canvasPickerTextDisabled() {
        return UiFlatPalette.CANVAS_PICKER_TEXT_DISABLED;
    }

    // ---- 悬停说明面板 ----

    // 面板标题与分区标题
    public int tooltipTitle() {
        return UiPalette.TOOLTIP_TITLE;
    }

    // 可度量取值：数值、区间、时刻
    public int tooltipMetric() {
        return UiPalette.TOOLTIP_METRIC;
    }

    // 注册表引用：维度、群系、方块、物品、流体
    public int tooltipReference() {
        return UiPalette.TOOLTIP_REFERENCE;
    }
}
