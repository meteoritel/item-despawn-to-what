package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderSkin;
import net.minecraft.client.gui.GuiGraphics;
import java.util.EnumMap;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlPainter;

/**
 * 默认宿主主题：现行灰色像素外观，绘制与用色逐项转发到既有 {@link UiTheme} 与 {@link UiPalette}。
 *
 * <p>本类是「宿主入口默认提供现行主题」的落点：映射不复制数值，只把静态入口改为
 * 可注入的实例方法，因此既有画面不变，控件与屏幕也不再静态取色。度量与共用外壳原语
 * 继承自 {@link AbstractHostTheme}，本类只保留像素皮肤特有的用色、样式与绘制。</p>
 */
public final class UiPixelTheme extends AbstractHostTheme {

    // 不可变主题实例持有自己的调色板
    static final UiPixelTheme INSTANCE = new UiPixelTheme();

    private final UiHostPalette palette = UiHostPalette.pixelDefaults();

    private UiPixelTheme() {
    }

    /** 本主题拥有的不可变语义调色板。 */
    @Override
    public UiHostPalette palette() {
        return palette;
    }

    // 功能栏与工具列格子：常态与控件同底，悬停/按下/选中给出像素反馈

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
            case PRIMARY, DANGER -> palette().controls().textOnDark();
            case DISABLED -> palette().surfaces().disabledText();
            case SECONDARY, LABEL -> palette().surfaces().text();
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

    private final EnumMap<UiControlRole, UiControlSkin> skins = new EnumMap<>(UiControlRole.class);

    @Override
    public UiControlSkin controlSkin(UiControlRole role) {
        return skins.computeIfAbsent(role, key -> new UiControlSkin(controlStyle(key), (graphics, frame) -> {
            UiControlPainter.FILL.paint(graphics, frame);
            if (key != UiControlRole.LABEL) drawBevel(graphics, frame.bounds(), palette().controls().controlBevelLight(), palette().controls().controlBevelDark());
        }, controlTextColor(key), palette().surfaces().disabledText()));
    }

    private static final UiSliderSkin COMPACT_SKIN = new UiSliderSkin(UiTheme.sliderStyle(), UiTheme.cardSliderPainter());
    private static final UiSliderSkin ROOMY_SKIN = new UiSliderSkin(UiTheme.cardSliderStyle(), UiTheme.cardSliderPainter());

    @Override
    public UiSliderSkin sliderSkin(UiSliderRole role) {
        return switch (role) {
            case COMPACT -> COMPACT_SKIN;
            case ROOMY -> ROOMY_SKIN;
        };
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

    private final EnumMap<UiHostSurface, UiControlSkin> surfaceSkins = UiHostSurfaceSkins.create(palette, true, false);
    @Override public UiControlSkin surfaceSkin(UiHostSurface role) { return surfaceSkins.get(role); }
}
