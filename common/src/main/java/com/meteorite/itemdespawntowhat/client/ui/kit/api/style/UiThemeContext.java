package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderSkin;
import net.minecraft.client.gui.GuiGraphics;
import java.util.Objects;

/**
 * 每个 UI 根持有的稳定主题来源。子控件共享本实例；替换只发布主题与版本，编辑状态继续由控件持有。
 * 在 GUI 线程调用；几何变化前的交互取消与布局刷新由根负责。
 *
 * @param <T> 宿主可保留自己的主题扩展
 */
public class UiThemeContext<T extends UiStyleSource> implements UiStyleSource {
    private T current;
    private long revision;

    /** 创建指定初始主题的上下文。 */
    public UiThemeContext(T initial) { current = Objects.requireNonNull(initial, "initial"); }
    /** 创建可替换、默认使用原版主题的上下文。 */
    public static UiThemeContext<UiStyleSource> minecraft() { return new UiThemeContext<>(UiMinecraftTheme.INSTANCE); }
    /** 当前不可变主题。 */
    public final T current() { return current; }
    /** 发布新主题；同一实例保持版本。 */
    public final void setTheme(T next) {
        Objects.requireNonNull(next, "theme");
        if (current == next) return;
        current = next;
        revision++;
    }
    @Override public final long revision() { return revision; }
    @Override public UiControlStyle controlStyle(UiControlRole role) { return current.controlStyle(role); }
    @Override public UiControlSkin controlSkin(UiControlRole role) { return current.controlSkin(role); }
    @Override public UiSliderSkin sliderSkin(UiSliderRole role) { return current.sliderSkin(role); }
    @Override public UiSurfaceStyle surfaces() { return current.surfaces(); }
    @Override public UiThemeMetrics metrics() { return current.metrics(); }
    @Override public int textColor(boolean enabled) { return current.textColor(enabled); }
    @Override public int secondaryTextColor() { return current.secondaryTextColor(); }
    @Override public int controlTextColor(UiControlRole role) { return current.controlTextColor(role); }
    @Override public int padding() { return current.padding(); }
    @Override public int rowHeight() { return current.rowHeight(); }
    @Override public int textOffset() { return current.textOffset(); }
    @Override public int headerHeight() { return current.headerHeight(); }
    @Override public int border() { return current.border(); }
    @Override public int selectMarkerWidth() { return current.selectMarkerWidth(); }
    @Override public void drawWindow(GuiGraphics graphics, UiRect rect) { current.drawWindow(graphics, rect); }
    @Override public void drawInset(GuiGraphics graphics, UiRect rect) { current.drawInset(graphics, rect); }
    @Override public void drawHeader(GuiGraphics graphics, UiRect rect) { current.drawHeader(graphics, rect); }
    @Override public void drawFocusOutline(GuiGraphics graphics, UiRect rect) { current.drawFocusOutline(graphics, rect); }
    @Override public void drawSelection(GuiGraphics graphics, UiRect rect) { current.drawSelection(graphics, rect); }
    @Override public void drawSelectMarker(GuiGraphics graphics, UiRect rect) { current.drawSelectMarker(graphics, rect); }
    @Override public void drawScrollTrack(GuiGraphics graphics, UiRect rect) { current.drawScrollTrack(graphics, rect); }
    @Override public void drawScrollThumb(GuiGraphics graphics, UiRect rect) { current.drawScrollThumb(graphics, rect); }
    @Override public void drawDivider(GuiGraphics graphics, int x, int y, int width) { current.drawDivider(graphics, x, y, width); }
    @Override public void drawChip(GuiGraphics graphics, UiRect rect, boolean selected, boolean hovered) { current.drawChip(graphics, rect, selected, hovered); }
}
