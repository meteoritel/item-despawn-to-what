package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.style.UiStyleSource;
import net.minecraft.client.gui.GuiGraphics;


/**
 * 原版文本适配共用的内边距与最小尺寸。
 */
final class TextInputSupport {
    static final int PADDING_X = 3;
    static final int PADDING_Y = 2;
    static final int MIN_WIDTH = 24;

    static void drawBackground(GuiGraphics graphics, UiRect bounds, UiStyleSource theme) {
        theme.drawInset(graphics, bounds);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, theme.surfaces().inputFill());
    }

    private TextInputSupport() {
    }

}
