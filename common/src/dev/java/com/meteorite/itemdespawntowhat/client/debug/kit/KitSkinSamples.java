package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiControl;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiNineSlice;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemeMetrics;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiTooltipSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiMinecraftTheme;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSkinImages;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiSurfaceRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiThemes;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 可独立编译的宿主皮肤配方；默认、改色、资源映射与 painter 使用相同展示内容。 */
public final class KitSkinSamples {
    private KitSkinSamples() { }

    /** 配方只在打开示例时创建，后续切换复用完整主题。 */
    public static List<UiStyleSource> themes() {
        UiMinecraftTheme base = UiMinecraftTheme.INSTANCE;
        UiStyleSource colors = UiThemes.builder(base)
                .colors(c -> c.withText(0xFFE0F4FC, c.secondaryText(), c.disabledText())
                        .withHeader(0xC0325268, c.headerText())
                        .withSelection(0x8065BCD4, c.modalDim())
                        .withInput(0xB018202A, 0xFF65BCD4, c.inputText())
                        .withPanel(0xC0203440, 0xFF65BCD4)
                        .withTooltip(0xF0102028, 0xFF65BCD4, 0xFF264C60, 0xFFFFFFFF))
                .surface(UiSurfaceRole.WINDOW, skin -> skin.withFill(0xC0182834))
                .build();
        UiSkinImages buttons = UiSkinImages.sprites(
                ResourceLocation.withDefaultNamespace("widget/button"),
                ResourceLocation.withDefaultNamespace("widget/button_highlighted"));
        UiSkinImages track = UiSkinImages.sprites(
                ResourceLocation.withDefaultNamespace("widget/slider"),
                ResourceLocation.withDefaultNamespace("widget/slider_highlighted"));
        UiSkinImages handle = UiSkinImages.sprites(
                ResourceLocation.withDefaultNamespace("widget/slider_handle"),
                ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted"));
        // 原版石头贴图提供无需产品资源的九宫格示例；宿主可直接替换 texture 描述。
        UiSkinImages tiles = new UiSkinImages(UiSkinImages.Image.nineSlice(new UiNineSlice(
                ResourceLocation.withDefaultNamespace("textures/block/stone.png"), 16, 2)).tinted(0xC0345268));
        UiStyleSource images = UiThemes.builder(base)
                .control(UiControlRole.SECONDARY, buttons.control(base.controlSkin(UiControlRole.SECONDARY)))
                .surface(UiSurfaceRole.WINDOW, tiles.surface(base.surfaceSkin(UiSurfaceRole.WINDOW)))
                .slider(UiSliderRole.COMPACT, track.slider(base.sliderSkin(UiSliderRole.COMPACT), handle))
                .build();
        UiStyleSource painter = UiThemes.builder(colors)
                .control(UiControlRole.SECONDARY, skin -> skin.withStyle(new UiControlStyle(
                        0xFF345268, 0xFF486C84, 0xFF24384A, 0xFF45647C, 0xFF303A44,
                        0xFF65BCD4, skin.style().scrollbarTrack(), skin.style().scrollbarThumb()))
                        .withPainter(UiControlPainter.FILL))
                .tooltip(UiTooltipSkin.flat(colors.surfaces()))
                .metrics(new UiThemeMetrics(5, 22, 7, 24, 1, 5))
                .surface(UiSurfaceRole.WINDOW, skin -> skin.withPainter((g, r, state, current) ->
                        g.fillGradient(r.x(), r.y(), r.right(), r.bottom(),
                                current.colors().top(), current.colors().bottom())))
                .build();
        return List.of(base, colors, images, painter);
    }

    /** 单个按钮覆盖不影响同区域其它控件，换主题后仍保留。 */
    static void customize(UiControl control) {
        UiControlSkin base = UiMinecraftTheme.INSTANCE.controlSkin(UiControlRole.SECONDARY);
        control.setSkin(base.withPainter((g, frame) -> {
            UiControlPainter.FILL.paint(g, frame);
            g.fill(frame.bounds().x(), frame.bounds().bottom() - 2, frame.bounds().right(),
                    frame.bounds().bottom(), frame.style().focusOutline());
        }));
    }
}
