package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlPainter;
import java.util.EnumMap;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiTooltipSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeMetrics;

/** 像素与扁平 Adapter 共用度量、语义图标和稳定 skin 缓存。 */
abstract class AbstractHostTheme implements UiHostTheme {
    private final EnumMap<UiControlRole, UiControlSkin> skins = new EnumMap<>(UiControlRole.class);
    @Override public UiControlSkin controlSkin(UiControlRole role) {
        return skins.computeIfAbsent(role, key -> new UiControlSkin(controlStyle(key), UiControlPainter.FILL,
                controlTextColor(key), palette().surfaces().disabledText()));
    }
    private static final UiThemeMetrics METRICS = new UiThemeMetrics(
            UiTheme.PADDING, UiTheme.ROW_HEIGHT, UiTheme.TEXT_OFFSET, UiTheme.HEADER_HEIGHT,
            UiTheme.BORDER, UiTheme.SELECT_MARKER_WIDTH);

    private UiTooltipSkin tooltipSkin;
    @Override public UiTooltipSkin tooltipSkin() {
        if (tooltipSkin == null) tooltipSkin = UiTooltipSkin.flat(surfaces());
        return tooltipSkin;
    }

    @Override
    public UiSurfaceStyle surfaces() { return palette().surfaces(); }

    @Override
    public final UiThemeMetrics metrics() {
        return METRICS;
    }

    @Override
    public UiIcon icon(UiHostIcon role, boolean disabled) {
        return UiFlatSkin.icon(role, disabled, false);
    }

    @Override
    public void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) {
        UiFlatSkin.drawSurface(graphics, rect, fill, border);
    }
}
