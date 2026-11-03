package com.meteorite.itemdespawntowhat.client.ui.kit.api.style;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.number.UiSliderStyle;
import net.minecraft.resources.ResourceLocation;

/** 复用原版 GUI sprites 的默认主题；资源包替换原版 sprites 后自然生效。 */
public final class UiMinecraftTheme implements UiStyleSource {
    /** 无宿主主题时直接复用的不可变实例。 */
    public static final UiMinecraftTheme INSTANCE = new UiMinecraftTheme();
    private static final ResourceLocation BUTTON = sprite("button");
    private static final ResourceLocation DISABLED = sprite("button_disabled");
    private static final ResourceLocation HIGHLIGHTED = sprite("button_highlighted");
    private static final ResourceLocation SLIDER = sprite("slider");
    private static final ResourceLocation SLIDER_HIGHLIGHTED = sprite("slider_highlighted");
    private static final ResourceLocation HANDLE = sprite("slider_handle");
    private static final ResourceLocation HANDLE_HIGHLIGHTED = sprite("slider_handle_highlighted");
    private static final UiControlStyle CONTROL = new UiControlStyle(0xFF777777, 0xFF999999, 0xFF555555,
            0xFF888888, 0xFF444444, 0xFFFFFFFF, 0xFF000000, 0xFF808080);
    private static final UiControlStyle LABEL = new UiControlStyle(0, 0, 0, 0, 0, 0xFFFFFFFF, 0xFF000000, 0xFF808080);
    private static final UiControlSkin BUTTON_SKIN = new UiControlSkin(CONTROL, (graphics, frame) -> {
        UiRect rect = frame.bounds();
        ResourceLocation sprite = frame.state() == UiControlStyle.State.DISABLED ? DISABLED
                : frame.focused() || frame.state() == UiControlStyle.State.HOVER
                || frame.state() == UiControlStyle.State.PRESSED || frame.state() == UiControlStyle.State.SELECTED ? HIGHLIGHTED : BUTTON;
        graphics.blitSprite(sprite, rect.x(), rect.y(), rect.width(), rect.height());
    }, 0xFFFFFFFF, 0xFFA0A0A0);
    private static final UiControlSkin LABEL_SKIN = new UiControlSkin(LABEL, UiControlPainter.FILL, 0xFFFFFFFF, 0xFFA0A0A0);
    private static final UiSliderSkin SLIDER_SKIN = new UiSliderSkin(new UiSliderStyle(
            0xFF000000, 0xFF808080, 0xFFC6C6C6, 0xFFFFFFFF, 0xFF555555, 0xFFFFFFFF,
            0xFFFF5555, 0xFFFFAA00, 0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFA0A0A0, 0xFFFF5555, 12, 8, 0), (graphics, frame) -> {
        if (!frame.enabled()) graphics.setColor(1, 1, 1, 0.5F);
        try {
        UiRect track = frame.track();
        graphics.blitSprite(frame.focused() ? SLIDER_HIGHLIGHTED : SLIDER, track.x(), track.y(), track.width(), track.height());
        UiRect thumb = frame.thumb();
        graphics.blitSprite(frame.hovered() || frame.dragging() ? HANDLE_HIGHLIGHTED : HANDLE,
                thumb.x(), thumb.y(), thumb.width(), thumb.height());
        } finally { if (!frame.enabled()) graphics.setColor(1, 1, 1, 1); }
        if (frame.error()) graphics.renderOutline(frame.bounds().x(), frame.bounds().y(), frame.bounds().width(), frame.bounds().height(), frame.style().errorColor());
    });

    private UiMinecraftTheme() { }
    private static ResourceLocation sprite(String name) { return ResourceLocation.withDefaultNamespace("widget/" + name); }
    @Override public UiControlStyle controlStyle(UiControlRole role) { return role == UiControlRole.LABEL ? LABEL : CONTROL; }
    @Override public UiControlSkin controlSkin(UiControlRole role) { return role == UiControlRole.LABEL ? LABEL_SKIN : BUTTON_SKIN; }
    @Override public UiSliderSkin sliderSkin(UiSliderRole role) { return SLIDER_SKIN; }
}
