package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeMetrics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderSkin;
import net.minecraft.client.gui.GuiGraphics;
import java.util.EnumMap;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;

/** 直接复用 kit 原版主题，业务图标与画布语义由宿主扁平主题补齐。 */
final class UiMinecraftHostTheme implements UiHostTheme {
    static final UiHostTheme INSTANCE = new UiMinecraftHostTheme();
    private final UiMinecraftTheme controls = UiMinecraftTheme.INSTANCE;
    private final UiHostPalette palette;
    private final EnumMap<UiHostSurface, UiControlSkin> surfaceSkins;

    private UiMinecraftHostTheme() {
        UiHostPalette base = UiHostThemes.flatTheme().palette();
        palette = new UiHostPalette(controls.surfaces(),
                new UiHostPalette.Controls(0xFFFFFFFF, 0xFF777777, 0xFF999999, 0xFF555577,
                        0xFF444444, 0xFFAAAAAA, 0xFF101010, 0xFF7299CF, 0xFF8FB4E5,
                        0xFF4A5D78, 0xFFFF7777, 0xFF383838, 0xFF8FB4FF, 0xFF000000,
                        0xFF808080, 0xFF101010, 0xFF777777),
                new UiHostPalette.Status(0xFFFFCC66, 0xFF88CC88, 0xFFFFFFFF, 0xFFAAAAAA,
                        0xFFFF7777, 0xFFFFCC66, 0xFFFFFFFF, 0xFFAAAAAA, 0xFFFFFFFF, 0xFF222222),
                base.code(), base.canvas(),
                new UiHostPalette.Description(0xFFFFFFAA, 0xFF99CCFF, 0xFF99DDDD, 0xFF99CCFF, 0xFFFFCC66, 0xFFFF9999));
        surfaceSkins = UiHostSurfaceSkins.create(palette, false, true);
    }
    @Override public UiHostPalette palette() { return palette; }
    @Override public UiControlStyle controlStyle(UiControlRole role) { return controls.controlStyle(role); }
    @Override public UiControlSkin controlSkin(UiControlRole role) { return controls.controlSkin(role); }
    @Override public UiSliderSkin sliderSkin(UiSliderRole role) { return controls.sliderSkin(role); }
    @Override public UiSurfaceStyle surfaces() { return controls.surfaces(); }
    @Override public UiThemeMetrics metrics() { return controls.metrics(); }
    @Override public UiIcon icon(UiHostIcon role, boolean disabled) { return UiFlatSkin.icon(role, disabled, true); }

    @Override public void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) { UiHostThemes.flatTheme().drawSurface(graphics, rect, fill, border); }
    @Override public void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark) { UiHostThemes.flatTheme().drawBevel(graphics, rect, light, dark); }

    @Override public UiControlSkin surfaceSkin(UiHostSurface role) { return surfaceSkins.get(role); }
}
