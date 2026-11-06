package com.meteorite.itemdespawntowhat.client.ui.theme;

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
import java.util.EnumMap;

/**
 * 原版方案的主题 Adapter：控件复用 kit 的 {@link UiMinecraftTheme}（原版 button / slider sprites），
 * 宿主侧只覆盖语义表面、业务色与结构绘制。
 *
 * <p>表面取 {@link UiMinecraftPalette} 的半透明深色：窗口压在原版模糊世界与暗色渐变上绘制，
 * 标题带、面板与卡片逐级提亮，内凹下沉，避免整窗不透明色块与纯黑列表；分隔线、滚动条和立体边
 * 用半透明结构色，控件外观仍由 kit 的原版 sprite 决定。skin 在构造期一次生成，
 * 同一角色重复查询返回同一实例。</p>
 */
final class UiMinecraftHostTheme implements UiHostTheme {
    static final UiHostTheme INSTANCE = new UiMinecraftHostTheme();
    private final UiMinecraftTheme controls = UiMinecraftTheme.INSTANCE;
    private final UiHostPalette palette;
    private final EnumMap<UiHostSurface, UiControlSkin> surfaceSkins;

    private UiMinecraftHostTheme() {
        UiHostPalette base = UiHostThemes.flatTheme().palette();
        palette = UiMinecraftPalette.hostPalette(base);
        surfaceSkins = UiHostSurfaceSkins.create(palette, false, true);
    }

    @Override public UiHostPalette palette() { return palette; }
    @Override public UiControlStyle controlStyle(UiControlRole role) { return controls.controlStyle(role); }
    @Override public UiControlSkin controlSkin(UiControlRole role) { return controls.controlSkin(role); }
    @Override public UiSliderSkin sliderSkin(UiSliderRole role) { return controls.sliderSkin(role); }
    @Override public UiSurfaceStyle surfaces() { return palette.surfaces(); }
    @Override public UiThemeMetrics metrics() { return controls.metrics(); }
    @Override public UiIcon icon(UiHostIcon role, boolean disabled) { return UiFlatSkin.icon(role, disabled, true); }

    /** 半透明分隔线：原版皮肤的分栏线不压黑内容，只做 1 像素亮分隔。 */
    @Override
    public void drawDivider(GuiGraphics graphics, int x, int y, int width) {
        if (width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, UiMinecraftPalette.DIVIDER);
    }

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

    /** 滚动条轨道：半透明黑贴在内凹底上，不再单独造一块纯黑。 */
    @Override
    public void drawScrollTrack(GuiGraphics graphics, UiRect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiMinecraftPalette.SCROLL_TRACK);
    }

    /** 滚动条滑块：半透明亮灰，暗底上可辨认。 */
    @Override
    public void drawScrollThumb(GuiGraphics graphics, UiRect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), UiMinecraftPalette.SCROLL_THUMB);
    }

    @Override public UiControlSkin surfaceSkin(UiHostSurface role) { return surfaceSkins.get(role); }
}
