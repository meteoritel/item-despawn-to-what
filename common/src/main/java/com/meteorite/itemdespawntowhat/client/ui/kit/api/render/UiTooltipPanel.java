package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiTransform;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;

/** 同一份提示外框与正文几何，统一 TOOLTIP 层和正文裁剪。 */
public record UiTooltipPanel(UiRect bounds, UiRect contentBounds) {
    /** 折行前的正文宽度预算；宿主边距与面板内边距先行扣除，零表示没有正文空间。 */
    public static int contentWidth(UiRect host, int maximum, int padding, int margin) {
        int available = Math.max(0, host.width() - Math.max(0, padding) * 2 - Math.max(0, margin) * 2);
        return Math.clamp(maximum, 0, available);
    }

    /** 测量完成后放置面板；padding 包含宿主背景所需的描边占用。 */
    public static UiTooltipPanel layout(UiRect host, int contentWidth, int contentHeight, int padding,
                                         int mouseX, int mouseY, int offset, int margin,
                                         UiTooltipPlacement.VerticalPreference preference) {
        int inset = Math.max(0, padding);
        UiRect outer = UiTooltipPlacement.place(host, Math.max(0, contentWidth) + inset * 2,
                Math.max(0, contentHeight) + inset * 2, mouseX, mouseY, offset, margin, preference);
        UiRect content = new UiRect(outer.x() + Math.min(inset, outer.width()),
                outer.y() + Math.min(inset, outer.height()),
                Math.max(0, outer.width() - inset * 2), Math.max(0, outer.height() - inset * 2));
        return new UiTooltipPanel(outer, content);
    }

    /** 背景按外框绘制，正文只在内容区绘制；空正文区域不绘制面板。 */
    public void render(GuiGraphics graphics, Consumer<UiRect> background, Consumer<UiRect> content) {
        if (contentBounds.width() <= 0 || contentBounds.height() <= 0) return;
        UiRenderLayers.draw(graphics, UiRenderLayers.TOOLTIP, () -> {
            background.accept(bounds);
            UiTransform.enableScissor(graphics, contentBounds);
            try {
                content.accept(contentBounds);
            } finally {
                UiTransform.disableScissor(graphics);
            }
        });
    }
}
