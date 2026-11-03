package com.meteorite.itemdespawntowhat.client.ui.kit.api.render;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;

/** 提示面板的有界放置；尺寸包含正文、padding 和描边。 */
public final class UiTooltipPlacement {
    /** 首选顶部相对指针的位置；越界后先翻转，再钳制到宿主区域。 */
    public enum VerticalPreference {
        /** 顶部从指针上方 offset 处开始。 */
        ABOVE,
        /** 顶部从指针下方 offset 处开始。 */
        BELOW
    }

    private UiTooltipPlacement() {
    }

    /** 返回宿主边距内的外框；过大面板缩到可用尺寸，无可用空间时返回空矩形。 */
    public static UiRect place(UiRect host, int width, int height, int mouseX, int mouseY,
                               int offset, int margin, VerticalPreference preference) {
        int inset = Math.max(0, margin);
        int left = host.x() + Math.min(inset, host.width());
        int top = host.y() + Math.min(inset, host.height());
        int availableWidth = Math.max(0, host.width() - inset * 2);
        int availableHeight = Math.max(0, host.height() - inset * 2);
        int w = Math.clamp(width, 0, availableWidth);
        int h = Math.clamp(height, 0, availableHeight);
        int right = left + availableWidth;
        int bottom = top + availableHeight;
        int x = mouseX + offset;
        if (x + w > right) x = mouseX - offset - w;
        int y = preference == VerticalPreference.BELOW ? mouseY + offset : mouseY - offset;
        if (y + h > bottom) y = mouseY - offset - h;
        if (y < top) y = mouseY + offset;
        return new UiRect(Math.clamp(x, left, right - w), Math.clamp(y, top, bottom - h), w, h);
    }
}
