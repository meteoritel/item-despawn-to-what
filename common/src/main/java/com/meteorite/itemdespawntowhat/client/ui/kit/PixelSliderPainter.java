package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 纯色像素滑块绘制：不依赖贴图，作为 kit 默认 painter 与示例实现。
 */
public final class PixelSliderPainter implements UiSliderPainter {

    /** 无状态单例，可安全共享。 */
    public static final PixelSliderPainter INSTANCE = new PixelSliderPainter();

    private PixelSliderPainter() {
    }

    @Override
    public void paint(GuiGraphics graphics, Frame frame) {
        UiSliderStyle style = frame.style();

        UiRect track = frame.track();
        graphics.fill(track.x(), track.y(), track.right(), track.bottom(), style.resolveTrack(frame.enabled()));

        UiRect filled = frame.filled();
        if (filled.width() > 0 && filled.height() > 0) {
            graphics.fill(filled.x(), filled.y(), filled.right(), filled.bottom(), style.resolveFill(frame.enabled()));
        }

        UiRect textSlot = frame.textSlot();
        if (textSlot.width() > 0 && textSlot.height() > 0) {
            graphics.fill(textSlot.x(), textSlot.y(), textSlot.right(), textSlot.bottom(), style.textSlotColor());
        }

        // 值超出显示窗口时在轨道两端各画一个溢出标记
        if (frame.overflowBelow()) {
            int y = Math.max(0, track.y() - 1);
            graphics.fill(track.x() - 1, y, track.x() + 1, y + 1, style.overflowColor());
        }
        if (frame.overflowAbove()) {
            int y = Math.max(0, track.y() - 1);
            graphics.fill(track.right() - 1, y, track.right() + 1, y + 1, style.overflowColor());
        }

        UiRect thumb = frame.thumb();
        if (thumb.width() > 0 && thumb.height() > 0) {
            graphics.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(),
                    style.resolveThumb(frame.enabled(), frame.hovered() || frame.pressed() || frame.dragging()));
        }

        UiRect bounds = frame.bounds();
        if (frame.error() && bounds.height() > 0) {
            graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), style.errorColor());
        }
        if (frame.focused() && bounds.width() > 0 && bounds.height() > 0) {
            int color = style.focusColor();
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
            graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
            graphics.fill(bounds.x(), bounds.y() + 1, bounds.x() + 1, bounds.bottom() - 1, color);
            graphics.fill(bounds.right() - 1, bounds.y() + 1, bounds.right(), bounds.bottom() - 1, color);
        }
    }
}
