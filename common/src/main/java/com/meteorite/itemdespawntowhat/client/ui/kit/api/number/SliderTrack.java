package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;

/**
 * 手柄中心可移动的共同轨道几何，绘制与指针换算使用相同宽度。
 */
final class SliderTrack {
    private SliderTrack() {
    }

    static int left(UiRect bounds, int thumbWidth) {
        return bounds.x() + thumbWidth;
    }

    static int right(UiRect bounds, int thumbWidth) {
        return left(bounds, thumbWidth) + width(bounds, thumbWidth);
    }

    static int width(UiRect bounds, int thumbWidth) {
        return Math.max(1, bounds.width() - thumbWidth * 2);
    }
}
