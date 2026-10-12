package com.meteorite.itemdespawntowhat.client.ui.widget;

import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphLayout.BORDER;
import static com.meteorite.itemdespawntowhat.client.ui.widget.ConditionGraphRenderer.drawBorder;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 右侧面板的几何、滑入动画与外观；内容由选择器或参数面板绘制。 */
final class ConditionGraphSidePanel {
    private static final int SIDE_PANEL_MAX_WIDTH = 220;
    static final int SIDE_PANEL_PADDING = 4;
    static final int SIDE_PANEL_HEADER = 14;
    private static final long SIDE_PANEL_ANIM_MS = 160L;
    private static final int SIDE_PANEL_LAYER = UiRenderLayers.FOREGROUND + UiRenderLayers.ICON;

    private final UiHostTheme theme;
    private final Font font;

    private float openness;
    private float from;
    private float target;
    private long startedAt;

    ConditionGraphSidePanel(UiHostTheme theme, Font font) {
        this.theme = theme;
        this.font = font;
    }

    void start(float next) {
        tick();
        from = openness;
        target = next;
        startedAt = Util.getMillis();
    }

    void tick() {
        if (openness == target) return;
        long duration = Math.max(1L, Math.round(SIDE_PANEL_ANIM_MS * Math.abs(target - from)));
        float progress = Math.clamp((Util.getMillis() - startedAt) / (float) duration, 0.0F, 1.0F);
        float remaining = 1.0F - progress;
        float eased = 1.0F - remaining * remaining * remaining;
        openness = progress >= 1.0F ? target : from + (target - from) * eased;
    }

    void reset() {
        openness = 0.0F;
        from = 0.0F;
        target = 0.0F;
    }

    UiRect bounds(UiRect viewport) {
        int width = Math.min(SIDE_PANEL_MAX_WIDTH, viewport.width());
        int slide = Math.round(width * (1.0F - openness));
        return new UiRect(viewport.right() - width + slide, viewport.y(), width, viewport.height());
    }

    boolean isClosed() { return openness <= 0.0F; }

    void render(GuiGraphics graphics, UiRect viewport, Component title, Runnable content) {
        UiRect rect = bounds(viewport);
        if (rect.width() <= 0 || rect.height() <= 0) return;
        UiTransform.enableScissor(graphics, viewport);
        try {
            UiRenderLayers.draw(graphics, SIDE_PANEL_LAYER, () -> {
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), theme.palette().canvasPickerFill());
                graphics.fill(rect.x() + BORDER, rect.y() + BORDER, rect.right() - BORDER,
                        rect.y() + SIDE_PANEL_HEADER, theme.palette().canvasPickerHeaderFill());
                drawBorder(graphics, rect, theme.palette().canvasPickerBorder());
                graphics.drawString(font, TextScroll.trimToWidth(font, title.getString(),
                                Math.max(0, rect.width() - SIDE_PANEL_PADDING * 2)),
                        rect.x() + SIDE_PANEL_PADDING, rect.y() + (SIDE_PANEL_HEADER - font.lineHeight) / 2,
                        theme.palette().canvasPickerHeaderText(), false);
                content.run();
            });
        } finally {
            UiTransform.disableScissor(graphics);
        }
    }
}
