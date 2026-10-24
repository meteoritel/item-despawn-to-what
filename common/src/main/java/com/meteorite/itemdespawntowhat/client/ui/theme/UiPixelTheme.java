package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderSkin;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 默认宿主主题：现行灰色像素外观，绘制与用色逐项转发到既有 {@link UiTheme} 与 {@link UiPalette}。
 *
 * <p>本类是「宿主入口默认提供现行主题」的落点：映射不复制数值，只把静态入口改为
 * 可注入的实例方法，因此既有画面不变，控件与屏幕也不再静态取色。度量与共用外壳原语
 * 继承自 {@link AbstractHostTheme}，本类只保留像素皮肤特有的用色、样式与绘制。</p>
 */
public final class UiPixelTheme extends AbstractHostTheme {

    // 无状态主题，全局共用一个实例
    static final UiPixelTheme INSTANCE = new UiPixelTheme();

    private UiPixelTheme() {
    }

    // 功能栏与工具列格子：常态与控件同底，悬停/按下/选中给出像素反馈
    private static final UiControlStyle STRIP_STYLE = new UiControlStyle(
            UiPalette.CONTROL_FILL, UiPalette.CONTROL_HOVER, UiPalette.CONTROL_PRESSED, UiPalette.CONTROL_SELECTED,
            UiPalette.CONTROL_DISABLED, UiPalette.FOCUS_OUTLINE, UiPalette.SLOT_FILL, UiPalette.CONTROL_BEVEL_DARK);

    @Override
    public int textColor(boolean enabled) {
        return UiTheme.textColor(enabled);
    }

    @Override
    public int secondaryTextColor() {
        return UiTheme.secondaryTextColor();
    }

    @Override
    public int controlTextColor(UiControlRole role) {
        // 主要/危险底色较深用反白文字，禁用用禁用文本色，其余用正文色
        return switch (role) {
            case PRIMARY, DANGER -> palette().textOnDark();
            case DISABLED -> palette().textDisabled();
            case SECONDARY, LABEL -> palette().textPrimary();
        };
    }

    @Override
    public UiControlStyle controlStyle(UiControlRole role) {
        return switch (role) {
            case PRIMARY -> UiTheme.primaryStyle();
            case DANGER -> UiTheme.dangerStyle();
            case DISABLED -> UiTheme.disabledStyle();
            case LABEL -> UiTheme.labelStyle();
            case SECONDARY -> UiTheme.secondaryStyle();
        };
    }

    @Override
    public UiSliderSkin sliderSkin(UiSliderRole role) {
        return switch (role) {
            case COMPACT -> new UiSliderSkin(UiTheme.sliderStyle(), UiTheme.cardSliderPainter());
            case ROOMY -> new UiSliderSkin(UiTheme.cardSliderStyle(), UiTheme.cardSliderPainter());
        };
    }

    // 功能栏与工具列格子：像素页不使用，配色取控件底色与面板描边
    @Override
    public UiControlStyle stripStyle() {
        return STRIP_STYLE;
    }

    @Override
    public void drawWindow(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawWindow(graphics, rect);
    }

    @Override
    public void drawInset(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawInset(graphics, rect);
    }

    @Override
    public void drawHeader(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawHeader(graphics, rect);
    }

    @Override
    public void drawDivider(GuiGraphics graphics, int x, int y, int width) {
        UiTheme.drawDivider(graphics, x, y, width);
    }

    @Override
    public void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark) {
        UiTheme.drawBevel(graphics, rect, light, dark);
    }

    @Override
    public void drawFocusOutline(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawFocusOutline(graphics, rect);
    }

    @Override
    public void drawSelection(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawSelection(graphics, rect);
    }

    @Override
    public void drawSelectMarker(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawSelectMarker(graphics, rect);
    }

    @Override
    public void drawScrollTrack(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawScrollTrack(graphics, rect);
    }

    @Override
    public void drawScrollThumb(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawScrollThumb(graphics, rect);
    }

    @Override
    public void drawSegment(GuiGraphics graphics, UiRect rect, boolean enabled, boolean selected, boolean hovered) {
        UiTheme.drawSegment(graphics, rect, enabled, selected, hovered);
    }

    @Override
    public void drawCard(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawCard(graphics, rect);
    }

    @Override
    public void drawPanel(GuiGraphics graphics, UiRect rect) {
        UiTheme.drawPanel(graphics, rect);
    }

    @Override
    public void drawChip(GuiGraphics graphics, UiRect rect, boolean selected, boolean hovered) {
        UiTheme.drawChip(graphics, rect, selected, hovered);
    }
}
