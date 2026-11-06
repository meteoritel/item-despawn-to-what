package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeMetrics;
import net.minecraft.client.gui.GuiGraphics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceSkin;

/** kit 完整原版外观的业务 Adapter；状态色、代码、画布和业务图标归宿主。 */
final class UiMinecraftHostTheme implements UiHostTheme {
    static final UiHostTheme INSTANCE = new UiMinecraftHostTheme();
    private final UiMinecraftTheme controls = UiMinecraftTheme.INSTANCE;
    private final UiHostPalette palette;

    private UiMinecraftHostTheme() {
        palette = UiMinecraftPalette.hostPalette();
    }

    @Override public UiHostPalette palette() { return palette; }
    @Override public UiControlStyle controlStyle(UiControlRole role) { return controls.controlStyle(role); }
    @Override public UiControlSkin controlSkin(UiControlRole role) { return controls.controlSkin(role); }
    @Override public UiSliderSkin sliderSkin(UiSliderRole role) { return controls.sliderSkin(role); }
    @Override public UiSurfaceStyle surfaces() { return palette.surfaces(); }
    @Override public UiThemeMetrics metrics() { return controls.metrics(); }
    @Override public UiIcon icon(UiHostIcon role, boolean disabled) { return UiFlatSkin.icon(role, disabled, true); }

    /** 原版立体边：上/左高光、下/右阴影，颜色由调用方按业务语义给出。 */
    @Override
    public void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark) {
        if (rect.width() < 2 || rect.height() < 2) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right() - 1, rect.y() + 1, light);
        graphics.fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.bottom() - 1, light);
        graphics.fill(rect.x() + 1, rect.bottom() - 1, rect.right(), rect.bottom(), dark);
        graphics.fill(rect.right() - 1, rect.y() + 1, rect.right(), rect.bottom() - 1, dark);
    }

    /** 显式颜色的平面几何原语，供业务高亮与状态色块使用。 */
    @Override
    public void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border) {
        UiFlatSkin.drawSurface(graphics, rect, fill, border);
    }

    @Override public UiSurfaceSkin surfaceSkin(UiSurfaceRole role) { return controls.surfaceSkin(role); }
}
