package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeContext;
import net.minecraft.client.gui.GuiGraphics;

/** 根持有的宿主主题上下文；通用控件与业务外壳共享一次发布后的主题。 */
public final class UiHostThemeContext extends UiThemeContext<UiHostTheme> implements UiHostTheme {
    /** 创建具有宿主扩展的稳定主题来源。 */
    public UiHostThemeContext(UiHostTheme initial) { super(initial); }
    @Override public UiHostPalette palette() { return current().palette(); }
    @Override public UiControlStyle stripStyle() { return current().stripStyle(); }
    @Override public UiIcon icon(int row, boolean disabled) { return current().icon(row, disabled); }
    @Override public void drawHelp(GuiGraphics graphics, UiRect rect) { current().drawHelp(graphics, rect); }
    @Override public void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) { current().drawSurface(graphics, rect, fill, border); }
    @Override public void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark) { current().drawBevel(graphics, rect, light, dark); }
    @Override public void drawSegment(GuiGraphics graphics, UiRect rect, boolean enabled, boolean selected, boolean hovered) { current().drawSegment(graphics, rect, enabled, selected, hovered); }
    @Override public void drawCard(GuiGraphics graphics, UiRect rect) { current().drawCard(graphics, rect); }
    @Override public void drawPanel(GuiGraphics graphics, UiRect rect) { current().drawPanel(graphics, rect); }
}
