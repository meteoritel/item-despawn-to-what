package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIconSheet;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** 宿主图标的语义角色解析与扁平表面几何；颜色与画法由主题 Adapter 配套提供。 */
public final class UiFlatSkin {

    // 工具类，禁止实例化
    private UiFlatSkin() {
    }

    // ---- 图标图集 ----

    // 深色字形图集：现有主题是浅灰底，默认使用这一套
    private static final ResourceLocation ICON_SHEET_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Constants.MOD_ID, "textures/gui/editor/icons_dark.png");
    // 单元格 16×16，2 列状态，14 行图标；行数与列数必须显式给出，切片按贴图真实尺寸换算 UV
    private static final int ICON_CELL = 16;
    private static final int ICON_COLUMNS = 2;
    private static final int ICON_ROWS = 14;
    private static final UiIconSheet ICONS =
            new UiIconSheet(ICON_SHEET_TEXTURE, ICON_CELL, ICON_CELL, ICON_COLUMNS, ICON_ROWS);

    private static final UiIconSheet LIGHT_ICONS = new UiIconSheet(ResourceLocation.fromNamespaceAndPath(
            Constants.MOD_ID, "textures/gui/editor/icons_light.png"), ICON_CELL, ICON_CELL, ICON_COLUMNS, ICON_ROWS);
    private static final UiIcon[][][] RESOLVED = resolveIcons();

    private static UiIcon[][][] resolveIcons() {
        UiIcon[][][] icons = new UiIcon[2][UiHostIcon.values().length][2];
        for (UiHostIcon role : UiHostIcon.values()) {
            int row = switch (role) {
                case UNDO -> 0; case REDO -> 1; case VIEW_TREE -> 2; case VIEW_JSON -> 3;
                case HELP -> 4; case ADD_CONDITION -> 5; case NEW_BRANCH -> 6; case INVERT -> 7;
                case REMOVE -> 8; case MOVE_UP -> 9; case MOVE_DOWN -> 10; case WRAP_ALL -> 11;
            };
            for (int state = 0; state < 2; state++) {
                icons[0][role.ordinal()][state] = ICONS.cell(row, state);
                icons[1][role.ordinal()][state] = LIGHT_ICONS.cell(row, state);
            }
        }
        return icons;
    }

    static UiIcon icon(UiHostIcon role, boolean disabled, boolean light) {
        return RESOLVED[light ? 1 : 0][role.ordinal()][disabled ? 1 : 0];
    }

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

    // 键盘焦点轮廓（1 像素白框）
    public static void drawFocusOutline(GuiGraphics graphics, UiRect rect) {
        drawOutline(graphics, rect, UiFlatPalette.FOCUS_OUTLINE);
    }
}
