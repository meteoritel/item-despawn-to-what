package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 由宿主时间与样式驱动的进度条，支持平滑计数进度与未知总量的往返动画。
 * 不读取全局时钟，不拥有任务、取消或网络语义。
 */
public final class UiProgressBar {
    private static final double SMOOTHING_MILLIS = 160.0;
    private static final long MAX_FRAME_MILLIS = 250;
    private static final long ANIMATION_MILLIS = 1400;
    private static final double MOVING_SEGMENT_RATIO = 0.25;
    private static final int BORDER_WIDTH = 1;

    /**
     * 外部注入的进度条配色。
     * @param track 轨道颜色
     * @param fill 已完成区域颜色
     * @param border 边框颜色
     * @param highlight 动画区段颜色
     */
    public record Style(int track, int fill, int border, int highlight) { }

    private final Style style;
    private double displayed;
    private long lastMillis;
    private boolean initialized;

    /**
     * 创建独立动画实例；同一实例只供同一 UI 线程与进度条使用。
     * @param style 外部配色
     * @throws NullPointerException style 为 null 时
     */
    public UiProgressBar(Style style) { this.style = java.util.Objects.requireNonNull(style); }

    /**
     * 新任务或阶段切换时重置平滑状态，不从上一阶段的计数反向插值。
     * @param value 零到一的进度，非有限数按零处理
     * @param milliseconds 宿主单调时间，单位毫秒
     */
    public void reset(double value, long milliseconds) {
        displayed = normalize(value);
        lastMillis = milliseconds;
        initialized = true;
    }

    /**
     * 绘制进度与动画；未知总量时不把动画位置解释为完成比例。
     * @param graphics 当前绘制上下文
     * @param bounds 进度条矩形
     * @param value 零到一的真实完成比例
     * @param indeterminate 是否未知总量
     * @param milliseconds 宿主单调时间，单位毫秒
     */
    public void render(GuiGraphics graphics, UiRect bounds, double value, boolean indeterminate, long milliseconds) {
        double target = normalize(value);
        if (!initialized) { reset(target, milliseconds); }
        long elapsed = Math.clamp(milliseconds - lastMillis, 0L, MAX_FRAME_MILLIS);
        lastMillis = milliseconds;
        displayed += (target - displayed) * (1.0 - Math.exp(-elapsed / SMOOTHING_MILLIS));
        if (bounds.width() <= BORDER_WIDTH * 2 || bounds.height() <= BORDER_WIDTH * 2) { return; }
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), style.border());
        int left = bounds.x() + BORDER_WIDTH;
        int top = bounds.y() + BORDER_WIDTH;
        int width = bounds.width() - BORDER_WIDTH * 2;
        int bottom = bounds.bottom() - BORDER_WIDTH;
        graphics.fill(left, top, left + width, bottom, style.track());
        double cycle = Math.floorMod(milliseconds, ANIMATION_MILLIS) / (double) ANIMATION_MILLIS;
        int filled = indeterminate ? width : Math.clamp((int) Math.round(displayed * width), 0, width);
        if (!indeterminate) { graphics.fill(left, top, left + filled, bottom, style.fill()); }
        if (filled == 0) { return; }
        int segment = Math.max(1, (int) Math.round(filled * MOVING_SEGMENT_RATIO));
        // 三角波在轨道内往返，始终裁切在已完成区域或未知进度轨道中。
        double travel = 1.0 - Math.abs(cycle * 2.0 - 1.0);
        int start = (int) Math.round((filled - segment) * travel);
        graphics.fill(left + start, top, left + start + segment, bottom, style.highlight());
    }

    private static double normalize(double value) { return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 0.0; }
}
