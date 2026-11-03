package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;

/** 滚动视口内部共用的轨道与滑块几何，尺寸采用同一坐标尺度。 */
final class UiScrollbarGeometry {
    private UiScrollbarGeometry() {}

    static UiRect track(UiRect viewport, int width) {
        int actual = Math.clamp(width, 0, viewport.width());
        return new UiRect(viewport.right() - actual, viewport.y(), actual, viewport.height());
    }

    // 行对齐视口的有效内容高度与视觉轨道高度可以不同。
    static int thumbHeight(int track, int view, int content, int minimum) {
        if (track <= 0 || content <= view) return Math.max(0, track);
        return Math.clamp((int) Math.round((double) track * view / content), Math.clamp(minimum, 0, track), track);
    }
}
