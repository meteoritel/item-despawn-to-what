package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 纵向滚动条的统一几何；不保存滚动偏移，宽度和最小滑块由宿主明确提供。
 * <p>纯几何运算，输入高度与轨道尺寸使用同一坐标尺度。</p>
 * <pre>{@code
 * UiRect track = UiScrollbarGeometry.track(viewport, 4);
 * int height = UiScrollbarGeometry.thumbHeight(track.height(), contentHeight, 8);
 * int top = UiScrollbarGeometry.thumbTop(track.y(), track.height(), contentHeight, offset, 8);
 * }</pre>
 */
public final class UiScrollbarGeometry {
    private UiScrollbarGeometry() {}
    /** 在视口右侧取轨道，窄视口按实际宽度钳制。 */
    public static UiRect track(UiRect viewport, int width) {
        int actual = Math.clamp(width, 0, viewport.width());
        return new UiRect(viewport.right() - actual, viewport.y(), actual, viewport.height());
    }
    /** 按视口与内容比例计算滑块高度，无滚动内容时填满轨道。 */
    public static int thumbHeight(int view, int content, int minimum) {
        if (view <= 0 || content <= view) return Math.max(0, view);
        return Math.clamp((int) Math.round((double) view * view / content), Math.clamp(minimum, 0, view), view);
    }
    /** 偏移映射到滑块的轨道内位置。 */
    public static int thumbTop(int top, int view, int content, int offset, int minimum) {
        int max = Math.max(0, content - view);
        return top + (max == 0 ? 0 : (int) Math.round((double) (view - thumbHeight(view, content, minimum))
                * Math.clamp(offset, 0, max) / max));
    }
}
