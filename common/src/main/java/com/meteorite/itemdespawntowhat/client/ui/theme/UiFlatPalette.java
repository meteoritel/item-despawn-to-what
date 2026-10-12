package com.meteorite.itemdespawntowhat.client.ui.theme;

/**
 * 条件树画布页（画布本体、页内外壳与内联参数框）的扁平配色。
 *
 * <p>与 {@link UiPalette} 的立体像素主题并列：本类只提供二维扁平外观需要的颜色，
 * 用灰阶层次区分区域，逻辑运算符使用蓝、绿、紫语义色，不出现高光边与阴影边。
 * 语义相同的基础色直接引用 {@link UiPalette}，避免同一含义维护两份字面量。</p>
 *
 * <p>本类只被条件树画布页与它的适配层读取；其它编辑页继续使用 {@link UiPalette}，
 * 因此新增本类不改变任何既有画面。</p>
 */
public final class UiFlatPalette {

    // 工具类，禁止实例化
    private UiFlatPalette() {
    }

    // ---- 外壳：功能栏、工具列、状态栏 ----

    // 功能栏与工具列底色
    public static final int CHROME_FILL = 0xFFB4B4B4;
    // 外壳描边
    public static final int CHROME_BORDER = 0xFF3C3C3C;
    // 状态栏底色
    public static final int STATUS_FILL = 0xFF8E8E8E;
    // 状态栏文本
    public static final int STATUS_TEXT = UiPalette.TEXT_PRIMARY;
    // 状态栏次要文本
    public static final int STATUS_TEXT_MUTED = UiPalette.TEXT_SECONDARY;
    // 状态栏错误文本
    public static final int STATUS_TEXT_DANGER = UiPalette.DANGER;
    // 状态栏警告文本
    public static final int STATUS_TEXT_WARNING = UiPalette.WARNING;

    // ---- 浮动调板 ----

    // 调板主体底色
    public static final int PANEL_FILL = 0xFFCFCFCF;
    // 调板标题栏底色（含折叠按钮区）
    public static final int PANEL_HEADER_FILL = 0xFF8E8E8E;
    // 调板 1 像素描边
    public static final int PANEL_BORDER = 0xFF3C3C3C;
    // 调板正文
    public static final int PANEL_TEXT = UiPalette.TEXT_PRIMARY;
    // 调板内容区内凹底色
    public static final int PANEL_INSET_FILL = 0xFFC0C0C0;
    // 调板控件常态底色
    public static final int PANEL_CONTROL_FILL = 0xFFDCDCDC;
    // 调板控件悬停底色
    public static final int PANEL_CONTROL_HOVER = 0xFFEAEAEA;
    // 调板控件按下底色
    public static final int PANEL_CONTROL_PRESSED = 0xFFA8A8A8;
    // 调板控件选中底色
    public static final int PANEL_CONTROL_SELECTED = 0xFF9FB6CE;
    // 调板控件禁用底色
    public static final int PANEL_CONTROL_DISABLED = 0xFFBEBEBE;

    // ---- 条件树画布 ----

    // 画布底色
    public static final int CANVAS_FILL = UiPalette.SLOT_FILL;
    // 画布 1 像素描边
    public static final int CANVAS_BORDER = UiPalette.SLOT_BORDER_DARK;
    // 点阵网格
    public static final int CANVAS_GRID_DOT = UiPalette.SLOT_BORDER_DARK;
    // 空态提示文字
    public static final int CANVAS_HINT_TEXT = UiPalette.TEXT_SECONDARY;

    // 条件叶卡片底色
    public static final int CANVAS_CARD_FILL = UiPalette.CONTROL_FILL;
    // 容器卡片底色：比叶深一档
    public static final int CANVAS_GROUP_FILL = UiPalette.PANEL_FILL;
    // 逻辑运算符用固定语义色；选中态只改变描边，不覆盖运算符颜色。
    public static final int CANVAS_ALL_FILL = 0xFFD9E7FF;
    public static final int CANVAS_ALL_TEXT = 0xFF234EA0;
    public static final int CANVAS_ANY_FILL = 0xFFD9F6E4;
    public static final int CANVAS_ANY_TEXT = 0xFF22643D;
    public static final int CANVAS_NOT_FILL = 0xFFEADFF8;
    public static final int CANVAS_NOT_TEXT = 0xFF674193;
    public static final int CANVAS_FLOW_LINK = 0xFF315E69;
    // 悬停中的卡片底色
    public static final int CANVAS_CARD_HOVER_FILL = UiPalette.CONTROL_HOVER;
    // 选中卡片底色
    public static final int CANVAS_CARD_SELECTED_FILL = UiPalette.CONTROL_SELECTED;
    // 未选中卡片描边
    public static final int CANVAS_CARD_BORDER = UiPalette.CONTROL_BEVEL_DARK;
    // 选中卡片描边
    public static final int CANVAS_CARD_BORDER_SELECTED = UiPalette.ACCENT;
    // 父子连线（默认）
    public static final int CANVAS_LINK = UiPalette.PANEL_BORDER_DARK;
    // 父子连线（任一端被选中）
    public static final int CANVAS_LINK_SELECTED = UiPalette.ACCENT;

    // 卡片主行文字
    public static final int CANVAS_TEXT = UiPalette.TEXT_PRIMARY;
    // 卡片次行（参数摘要）文字
    public static final int CANVAS_TEXT_MUTED = UiPalette.TEXT_SECONDARY;
    // 问题卡片主行文字（超限 / 缺条件 / 子树有问题）
    public static final int CANVAS_TEXT_DANGER = UiPalette.DANGER;
    // 强调底色上的文字
    public static final int CANVAS_TEXT_ON_ACCENT = UiPalette.TEXT_ON_DARK;

    // 强调色：选中描边、热区、拖动落点
    public static final int CANVAS_ACCENT = UiPalette.ACCENT;
    // 强调色的悬停态
    public static final int CANVAS_ACCENT_HOVER = UiPalette.ACCENT_HOVER;
    // 热区描边
    public static final int CANVAS_HOTSPOT_BORDER = UiPalette.CONTROL_BEVEL_LIGHT;
    // 拖动浮影底色
    public static final int CANVAS_DRAG_GHOST_FILL = UiPalette.CONTROL_SELECTED;
    // 框选矩形填充（半透明）
    public static final int CANVAS_RUBBER_FILL = 0x305F7FB0;

    // 类型选择浮层与分组 / 包组小菜单
    public static final int CANVAS_PICKER_FILL = UiPalette.WINDOW_FILL;
    public static final int CANVAS_PICKER_BORDER = UiPalette.WINDOW_BORDER_DARK;
    public static final int CANVAS_PICKER_HEADER_FILL = UiPalette.HEADER_FILL;
    public static final int CANVAS_PICKER_HEADER_TEXT = UiPalette.HEADER_TEXT;
    public static final int CANVAS_PICKER_INPUT_FILL = UiPalette.SLOT_INNER;
    public static final int CANVAS_PICKER_INPUT_BORDER = UiPalette.SLOT_BORDER_DARK;
    // 浮层中被选中 / 悬停的行
    public static final int CANVAS_PICKER_ROW_SELECTED = UiPalette.CONTROL_SELECTED;
    // 浮层正文
    public static final int CANVAS_PICKER_TEXT = UiPalette.TEXT_PRIMARY;
    // 浮层次要文字与占位文字
    public static final int CANVAS_PICKER_TEXT_MUTED = UiPalette.TEXT_SECONDARY;
    public static final int CANVAS_PICKER_TEXT_DISABLED = UiPalette.TEXT_DISABLED;

    // ---- 帮助浮层 ----

    // 帮助浮层底色
    public static final int HELP_FILL = 0xFFE4E4E4;
    // 帮助浮层描边
    public static final int HELP_BORDER = 0xFF3C3C3C;
    // 帮助浮层标题
    public static final int HELP_TEXT = UiPalette.TEXT_PRIMARY;
    // 帮助浮层正文
    public static final int HELP_TEXT_MUTED = UiPalette.TEXT_SECONDARY;

    // ---- 焦点 ----

    // 键盘焦点轮廓
    public static final int FOCUS_OUTLINE = UiPalette.FOCUS_OUTLINE;
}
