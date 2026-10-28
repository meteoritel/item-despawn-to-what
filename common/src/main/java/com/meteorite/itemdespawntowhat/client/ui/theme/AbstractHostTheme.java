package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 宿主主题的公共壳：集中像素与扁平两套主题逐项相同的宿主级度量与共用外壳原语。
 *
 * <p>两套主题的真实差异只有调色板取值、文本色、控件样式、滑块皮肤与结构绘制原语；
 * 度量来自 {@link UiTheme}、图标与外壳原语来自 {@link UiFlatSkin}，都是同一份共同来源，
 * 实现类不再各抄一遍。新增宿主级度量时只需改 {@link UiTheme} 与 {@link UiHostTheme} 各一处。</p>
 */
abstract class AbstractHostTheme implements UiHostTheme {

    @Override
    public int padding() {
        return UiTheme.PADDING;
    }

    @Override
    public int rowHeight() {
        return UiTheme.ROW_HEIGHT;
    }

    @Override
    public int textOffset() {
        return UiTheme.TEXT_OFFSET;
    }

    @Override
    public int headerHeight() {
        return UiTheme.HEADER_HEIGHT;
    }

    @Override
    public int border() {
        return UiTheme.BORDER;
    }

    @Override
    public int selectMarkerWidth() {
        return UiTheme.SELECT_MARKER_WIDTH;
    }

    @Override
    public UiIcon icon(int row, boolean disabled) {
        return UiFlatSkin.icon(row, disabled);
    }

    @Override
    public void drawHelp(GuiGraphics graphics, UiRect rect) {
        UiFlatSkin.drawHelp(graphics, rect);
    }

    @Override
    public void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) {
        UiFlatSkin.drawSurface(graphics, rect, fill, border);
    }
}
