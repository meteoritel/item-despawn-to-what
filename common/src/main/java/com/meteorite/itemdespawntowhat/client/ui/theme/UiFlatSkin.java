package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIconSheet;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 条件树页与浮动调板共用的扁平皮肤：去掉立体凸凹与阴影，只用 1 像素同色描边和灰阶层次区分区域。
 *
 * <p><b>默认不改变任何现有行为。</b>本类不注册全局状态，也不改写 {@link UiTheme}：共享的 kit 原语只有在
 * 调用方显式注入 {@link #STRIP_STYLE} 时才会换成扁平外观，
 * 绘制原语也只有条件树页显式调用。其它编辑页读不到本类，画面与输入行为保持不变。</p>
 *
 * <p>图标统一取自 {@code textures/gui/editor} 下的图集，切片规格与
 * {@code tools/generate-editor-icons.py} 一致：单元格 16×16，2 列状态（0 常态 / 1 禁用），14 行。</p>
 */
public final class UiFlatSkin {

    // 工具类，禁止实例化
    private UiFlatSkin() {
    }

    // ---- 图标图集 ----

    // 深色字形图集：现有主题是浅灰底，默认使用这一套
    private static final ResourceLocation ICON_SHEET_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TypeLabels.OWN_NAMESPACE, "textures/gui/editor/icons_dark.png");
    // 单元格 16×16，2 列状态，14 行图标；行数与列数必须显式给出，切片按贴图真实尺寸换算 UV
    private static final int ICON_CELL = 16;
    private static final int ICON_COLUMNS = 2;
    private static final int ICON_ROWS = 14;
    private static final UiIconSheet ICONS =
            new UiIconSheet(ICON_SHEET_TEXTURE, ICON_CELL, ICON_CELL, ICON_COLUMNS, ICON_ROWS);

    // 图集行序（与生成脚本一致）
    // 功能栏
    public static final int ICON_UNDO = 0;
    public static final int ICON_REDO = 1;
    public static final int ICON_VIEW_TREE = 2;
    public static final int ICON_VIEW_JSON = 3;
    public static final int ICON_HELP = 4;
    // 工具列
    public static final int ICON_ADD_CONDITION = 5;
    public static final int ICON_NEW_BRANCH = 6;
    public static final int ICON_INVERT = 7;
    public static final int ICON_REMOVE = 8;
    public static final int ICON_MOVE_UP = 9;
    public static final int ICON_MOVE_DOWN = 10;
    public static final int ICON_WRAP_ALL = 11;

    // 图集列：0 常态 / 1 禁用
    private static final int COLUMN_NORMAL = 0;
    private static final int COLUMN_DISABLED = 1;

    // 取一个图标；disabled 为真时返回图集里的禁用字形
    public static UiIcon icon(int row, boolean disabled) {
        return ICONS.cell(row, disabled ? COLUMN_DISABLED : COLUMN_NORMAL);
    }

    // ---- 共享控件样式（显式注入才生效） ----

    // 功能栏与工具列格子：常态与外壳同色，悬停/按下/选中给出扁平反馈
    public static final UiControlStyle STRIP_STYLE = new UiControlStyle(
            UiFlatPalette.CHROME_FILL, UiFlatPalette.PANEL_CONTROL_HOVER, UiFlatPalette.PANEL_CONTROL_PRESSED,
            UiFlatPalette.PANEL_CONTROL_SELECTED, UiFlatPalette.CHROME_FILL,
            UiFlatPalette.FOCUS_OUTLINE, UiFlatPalette.PANEL_INSET_FILL, UiFlatPalette.PANEL_CONTROL_HOVER);

    // ---- 扁平绘制原语 ----

    // 扁平外框：纯色底 + 1 像素同色描边；尺寸太小直接填满描边色
    public static void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        drawOutline(graphics, rect, border);
    }

    // 1 像素同色描边
    public static void drawOutline(GuiGraphics graphics, UiRect rect, int color) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        if (rect.width() <= 2 || rect.height() <= 2) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        graphics.fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.bottom() - 1, color);
        graphics.fill(rect.right() - 1, rect.y() + 1, rect.right(), rect.bottom() - 1, color);
    }

    // 帮助浮层底板
    public static void drawHelp(GuiGraphics graphics, UiRect rect) {
        drawSurface(graphics, rect, UiFlatPalette.HELP_FILL, UiFlatPalette.HELP_BORDER);
    }

    // 键盘焦点轮廓（1 像素白框）
    public static void drawFocusOutline(GuiGraphics graphics, UiRect rect) {
        drawOutline(graphics, rect, UiFlatPalette.FOCUS_OUTLINE);
    }
}
