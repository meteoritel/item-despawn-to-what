package com.meteorite.itemdespawntowhat.client.ui.theme;

/***
 * 灰色像素主题调色板。
 * <p>所有颜色集中在这里，方便后续美术统一微调。颜色均为 ARGB 整数，
 * 命名前缀含义：WINDOW 窗口、PANEL 面板、CONTROL 控件、TEXT 文本、SCROLL 滚动条。
 * <p>状态色（选中、危险、成功、警告）数量刻意保持很少，且必须配合文字或图标使用，
 * 避免只靠颜色传达信息。
 */
public final class UiPalette {

    // 工具类，禁止实例化
    private UiPalette() {
    }

    // ---- 通用 ----

    // 完全透明
    public static final int TRANSPARENT = 0x00000000;
    // 窗口外描边（纯黑）
    public static final int SHADOW = 0xFF000000;
    // 弹窗背景遮罩
    public static final int MODAL_DIM = 0xB0000000;

    // ---- 窗口与面板 ----

    // 窗口底色（原版灰）
    public static final int WINDOW_FILL = 0xFFC6C6C6;
    // 窗口高光边
    public static final int WINDOW_BORDER_LIGHT = 0xFFFFFFFF;
    // 窗口阴影边
    public static final int WINDOW_BORDER_DARK = 0xFF555555;
    // 面板底色（略深，用于分区）
    public static final int PANEL_FILL = 0xFFB0B0B0;
    // 面板高光边
    public static final int PANEL_BORDER_LIGHT = 0xFFE0E0E0;
    // 面板阴影边
    public static final int PANEL_BORDER_DARK = 0xFF4A4A4A;
    // 分区标题栏底色
    public static final int HEADER_FILL = 0xFF8E8E8E;
    // 分区标题栏文字
    public static final int HEADER_TEXT = 0xFF1E1E1E;

    // ---- 槽位与内凹区域 ----

    // 内凹区域底色
    public static final int SLOT_FILL = 0xFF8B8B8B;
    // 内凹区域最深处（输入框内部）
    public static final int SLOT_INNER = 0xFF2E2E2E;
    // 内凹区域高光边（下、右）
    public static final int SLOT_BORDER_LIGHT = 0xFFE0E0E0;
    // 内凹区域阴影边（上、左）
    public static final int SLOT_BORDER_DARK = 0xFF4A4A4A;

    // ---- 控件底色 ----

    // 普通状态
    public static final int CONTROL_FILL = 0xFFC6C6C6;
    // 悬停状态
    public static final int CONTROL_HOVER = 0xFFDCDCDC;
    // 按下状态
    public static final int CONTROL_PRESSED = 0xFF9A9A9A;
    // 选中状态（灰蓝，必须同时有文字或图标标记）
    public static final int CONTROL_SELECTED = 0xFF9FB6CE;
    // 禁用状态
    public static final int CONTROL_DISABLED = 0xFFA0A0A0;
    // 控件外凸高光边
    public static final int CONTROL_BEVEL_LIGHT = 0xFFFFFFFF;
    // 控件外凸阴影边
    public static final int CONTROL_BEVEL_DARK = 0xFF555555;
    // 键盘焦点轮廓
    public static final int FOCUS_OUTLINE = 0xFFFFFFFF;

    // ---- 状态色 ----

    // 主要操作
    public static final int ACCENT = 0xFF6E86A8;
    // 主要操作悬停
    public static final int ACCENT_HOVER = 0xFF8499B8;
    // 主要操作按下
    public static final int ACCENT_PRESSED = 0xFF5A708F;
    // 危险操作
    public static final int DANGER = 0xFFA44A44;
    // 危险操作悬停
    public static final int DANGER_HOVER = 0xFFB85C56;
    // 危险操作按下
    public static final int DANGER_PRESSED = 0xFF8B3B36;
    // 成功
    public static final int SUCCESS = 0xFF5F9460;
    // 警告
    public static final int WARNING = 0xFFB8913C;

    // ---- 文本 ----

    // 主要文本
    public static final int TEXT_PRIMARY = 0xFF1E1E1E;
    // 次要文本
    public static final int TEXT_SECONDARY = 0xFF4A4A4A;
    // 禁用文本
    public static final int TEXT_DISABLED = 0xFF7A7A7A;
    // 深色底上的文本
    public static final int TEXT_ON_DARK = 0xFFFFFFFF;

    // ---- 滚动条 ----

    // 滚动条轨道
    public static final int SCROLL_TRACK = 0xFF8B8B8B;
    // 滚动条滑块
    public static final int SCROLL_THUMB = 0xFFD0D0D0;
}
