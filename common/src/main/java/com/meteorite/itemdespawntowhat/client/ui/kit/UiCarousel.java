package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 固定尺寸的图标轮播。
 * <p>默认按固定间隔自动推进；宿主一旦手动选中某一项（点击条目、键盘选择或步进），轮播固定在该项，
 * 直到宿主显式调用 {@link #resume(long)} 恢复自动推进。
 */
public final class UiCarousel {
    // 自动推进间隔（毫秒）
    private static final long INTERVAL_MS = 1500L;
    private final List<UiIcon> icons;
    private int selected;
    private long selectedAt;
    // 手动选中后固定展示，暂停自动推进
    private boolean pinned;

    public UiCarousel(List<UiIcon> icons) { this.icons = List.copyOf(icons); }

    /**
     * 当前应展示的成员下标；自动推进时按固定间隔循环，手动选中后固定返回该项，成员为空时返回 -1。
     *
     * @param milliseconds 宿主时间（毫秒）
     * @return 成员下标
     */
    public int index(long milliseconds) {
        if (icons.isEmpty()) return -1;
        if (pinned) return selected;
        return Math.floorMod(selected + (int) (Math.max(0, milliseconds - selectedAt) / INTERVAL_MS), icons.size());
    }

    /**
     * 手动选中并停止自动推进；宿主清除选中态后调用 {@link #resume(long)} 恢复轮播。
     *
     * @param index 目标下标，越界时取模
     * @param milliseconds 宿主时间（毫秒）
     */
    public void select(int index, long milliseconds) {
        if (icons.isEmpty()) return;
        selected = Math.floorMod(index, icons.size());
        selectedAt = milliseconds;
        pinned = true;
    }

    /**
     * 恢复自动推进，从调用时刻重新计时。
     *
     * @param milliseconds 宿主时间（毫秒）
     */
    public void resume(long milliseconds) {
        pinned = false;
        selectedAt = milliseconds;
    }

    /**
     * 相对当前项步进；手动步进同样停止自动推进。
     *
     * @param direction -1 上一项，+1 下一项
     * @param milliseconds 宿主时间（毫秒）
     */
    public void step(int direction, long milliseconds) { select(index(milliseconds) + direction, milliseconds); }

    /** 绘制当前项；图标按区域等比缩放并居中。 */
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
