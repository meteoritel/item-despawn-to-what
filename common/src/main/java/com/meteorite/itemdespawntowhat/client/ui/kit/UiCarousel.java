package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/*** 固定尺寸的图标轮播；宿主传入时间和成员，展开后的选择也沿用同一索引。 */
public final class UiCarousel {
    private final List<UiIcon> icons;
    private int selected;
    private long selectedAt;

    public UiCarousel(List<UiIcon> icons) { this.icons = List.copyOf(icons); }

    public int index(long milliseconds) {
        if (icons.isEmpty()) return -1;
        return Math.floorMod(selected + (int) (Math.max(0, milliseconds - selectedAt) / 1500), icons.size());
    }

    // 手动选中后重新计时，不把上一轮自动切换的时间带入下一项。
    public void select(int index, long milliseconds) {
        if (icons.isEmpty()) return;
        selected = Math.floorMod(index, icons.size());
        selectedAt = milliseconds;
    }

    public void step(int direction, long milliseconds) { select(index(milliseconds) + direction, milliseconds); }

    public void render(GuiGraphics graphics, UiRect box, long milliseconds) {
        int index = index(milliseconds);
        if (index < 0) return;
        UiIcon icon = icons.get(index);
        if (icon instanceof UiIcon.Rendered rendered) rendered.painter().render(graphics, box);
        else {
            float scale = Math.min((float) box.width() / icon.width(), (float) box.height() / icon.height());
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(box.x() + (box.width() - icon.width() * scale) / 2,
                        box.y() + (box.height() - icon.height() * scale) / 2, 0);
                graphics.pose().scale(scale, scale, 1);
                icon.render(graphics, 0, 0);
            } finally { graphics.pose().popPose(); }
        }
    }
}
