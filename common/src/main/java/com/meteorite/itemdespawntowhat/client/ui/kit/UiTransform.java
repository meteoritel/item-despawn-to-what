package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/** 内容到调用方 GUI 坐标的正向、逆向变换；平移使用未缩放的内容单位。 */
public final class UiTransform {
    /** 默认档位表：保持画布出现之前的 50% / 100% / 200% / 300% 四档。 */
    private static final double[] DEFAULT_SCALES = {0.5, 1, 2, 3};
    /** 默认档位下标：100% 所在档。 */
    private static final int DEFAULT_ZOOM_INDEX = 1;

    /** 本实例自己的档位表；由 {@link #ofScales(double...)} 注入，升序在构造期校验。 */
    private final double[] scales;
    /** {@link #reset()} 回到的档位：恰好 100% 的档；表里没有 100% 时取最接近的一档。 */
    private final int resetIndex;

    private double originX;
    private double originY;
    private double panX;
    private double panY;
    private UiRect viewport = new UiRect(0, 0, 0, 0);
    private int zoomIndex;

    /** 默认四档；行为与画布出现之前完全一致。 */
    public UiTransform() {
        this.scales = DEFAULT_SCALES.clone();
        this.resetIndex = DEFAULT_ZOOM_INDEX;
        this.zoomIndex = DEFAULT_ZOOM_INDEX;
    }

    private UiTransform(double[] scales) {
        this.scales = scales;
        this.resetIndex = indexNearestUnscaled(scales);
        this.zoomIndex = resetIndex;
    }

    /**
     * 用给定档位表构造实例：数组不得为空，元素必须为正有限值且严格升序。
     * 与默认构造的区别只有档位；{@link #reset()} 回到最接近 100% 的一档。
     */
    public static UiTransform ofScales(double... scales) {
        if (scales == null || scales.length == 0) throw new IllegalArgumentException("Empty zoom scales");
        double[] copy = new double[scales.length];
        for (int i = 0; i < scales.length; i++) {
            double value = scales[i];
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid zoom scale: " + value);
            if (i > 0 && value <= copy[i - 1]) throw new IllegalArgumentException("Zoom scales must be ascending");
            copy[i] = value;
        }
        return new UiTransform(copy);
    }

    private static int indexNearestUnscaled(double[] scales) {
        for (int i = 0; i < scales.length; i++) {
            if (scales[i] >= 1.0) return i;
        }
        return scales.length - 1;
    }

    public double scale() { return scales[zoomIndex]; }

    public int zoomIndex() { return zoomIndex; }

    /** 当前档位的整数百分比：1.0 档为 100。 */
    public int scalePercent() { return (int) Math.round(scale() * 100.0); }

    public double panX() { return panX; }

    public double panY() { return panY; }

    public double toLocalX(double x) { return (x - originX) / scale() - panX; }

    public double toLocalY(double y) { return (y - originY) / scale() - panY; }

    public double toScreenX(double x) { return originX + (x + panX) * scale(); }

    public double toScreenY(double y) { return originY + (y + panY) * scale(); }

    // 恢复持久化的档位与平移：档位越界时夹紧，非有限平移归零，避免旧存档把视图置成非法值。
    public void restore(int zoomIndex, double panX, double panY) {
        this.zoomIndex = Math.clamp(zoomIndex, 0, scales.length - 1);
        this.panX = Double.isFinite(panX) ? panX : 0.0;
        this.panY = Double.isFinite(panY) ? panY : 0.0;
    }

    /** 原点由视口设置；移动视口不改变平移和缩放状态。 */
    public void setOrigin(double x, double y) { originX = x; originY = y; }

    /**
     * 设置视口：左上角即内容原点，尺寸为负时按 0 钳制（与 kit 其它几何入口同口径）。
     * 视口只用于裁剪、适配与居中，不改变缩放与平移。
     */
    public void setViewport(int x, int y, int width, int height) {
        viewport = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        setOrigin(x, y);
    }

    /** 最近一次 {@link #setViewport} 的矩形；未设置时为 (0,0,0,0)。 */
    public UiRect viewport() { return viewport; }

    public void panBy(double screenDx, double screenDy) {
        if (!Double.isFinite(screenDx) || !Double.isFinite(screenDy)) return;
        panX += screenDx / scale();
        panY += screenDy / scale();
    }

    // 缩放只改变绘制变换，锚点下方的内容坐标保持不变。
    public void zoomBy(int direction, double anchorX, double anchorY) {
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY)) return;
        double localX = toLocalX(anchorX);
        double localY = toLocalY(anchorY);
        zoomIndex = Math.clamp(zoomIndex + Integer.signum(direction), 0, scales.length - 1);
        panX = (anchorX - originX) / scale() - localX;
        panY = (anchorY - originY) / scale() - localY;
    }

    /**
     * 适配视图：在档位表里选最大能容纳内容的一档，再把内容中心对到视口中心。
     * 内容在最小档仍放不下时保持最小档；{@code paddingScreen} 为四周保留的屏幕像素。
     */
    public void fit(int contentX, int contentY, int contentW, int contentH, int paddingScreen) {
        int padding = Math.max(0, paddingScreen);
        int availableW = Math.max(1, viewport.width() - padding * 2);
        int availableH = Math.max(1, viewport.height() - padding * 2);
        int width = Math.max(0, contentW);
        int height = Math.max(0, contentH);
        int index = 0;
        for (int i = scales.length - 1; i >= 0; i--) {
            if (width * scales[i] <= availableW && height * scales[i] <= availableH) {
                index = i;
                break;
            }
        }
        zoomIndex = index;
        centerOn(contentX + width / 2.0, contentY + height / 2.0);
    }

    /** 把内容坐标点移到视口中心；视口未设置时以 (0,0) 为中心。 */
    public void centerOn(double localX, double localY) {
        if (!Double.isFinite(localX) || !Double.isFinite(localY)) return;
        panX = (viewport.x() + viewport.width() / 2.0 - originX) / scale() - localX;
        panY = (viewport.y() + viewport.height() / 2.0 - originY) / scale() - localY;
    }

    public void reset() {
        zoomIndex = resetIndex;
        panX = 0;
        panY = 0;
    }

    public void push(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(toScreenX(0), toScreenY(0), 0);
        graphics.pose().scale((float) scale(), (float) scale(), 1);
    }

    /** 按实例视口裁剪；进入内容变换之前调用，裁剪框不随内容平移、缩放。 */
    public void enableScissor(GuiGraphics graphics) {
        enableScissor(graphics, viewport);
    }

    // 1.21.1 的 enableScissor 不读 pose；先将轴对齐矩形换算为 GUI 屏幕坐标。
    // 在内容变换入栈前调用，因此裁剪框不会随内容平移、缩放而移动。
    public static void enableScissor(GuiGraphics graphics, UiRect viewport) {
        // 向内取整保证边界外不泄漏；切换前提交绘制批次，因为 blit 这类绘制在非 managed
        // 上下文里不会自动 endBatch（GuiGraphics.flushIfUnmanaged 只覆盖 fill/drawString）。
        pushScissor(graphics, viewport.x(), viewport.y(), viewport.right(), viewport.bottom(), false, true);
    }

    // 在已入栈的 pose 内绘制文本时使用（传入的矩形就是该 pose 坐标系下的矩形）。
    // 向外取整避免分数缩放时啃掉字形边缘；不提交批次是因为文本走 drawString，
    // 它在非 managed 上下文里会立刻 endBatch，额外 flush 只会多切一次批次。
    static void enableScissorInPose(GuiGraphics graphics, int left, int top, int right, int bottom) {
        pushScissor(graphics, left, top, right, bottom, true, false);
    }

    public static void disableScissor(GuiGraphics graphics) {
        graphics.flush();
        graphics.disableScissor();
    }

    /*
     * 两条裁剪入口共用的换算核心：把当前 pose 坐标系里的矩形换算成整型 GUI 像素后设置 scissor。
     * 这里用完整 2x2 变换（含 m01/m10）；kit 只支持轴对齐的平移与缩放，此时与只取对角线等价。
     * outward=true 向外取整（floor/ceil，宁可多留 1 像素），false 向内取整（ceil/floor，宁可少留）。
     */
    private static void pushScissor(GuiGraphics graphics, double left, double top, double right, double bottom,
                                    boolean outward, boolean flush) {
        Matrix4f matrix = graphics.pose().last().pose();
        double x1 = matrix.m00() * left + matrix.m10() * top + matrix.m30();
        double y1 = matrix.m01() * left + matrix.m11() * top + matrix.m31();
        double x2 = matrix.m00() * right + matrix.m10() * bottom + matrix.m30();
        double y2 = matrix.m01() * right + matrix.m11() * bottom + matrix.m31();
        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double maxX = Math.max(x1, x2);
        double maxY = Math.max(y1, y2);
        int x = (int) (outward ? Math.floor(minX) : Math.ceil(minX));
        int y = (int) (outward ? Math.floor(minY) : Math.ceil(minY));
        int rightPx = (int) (outward ? Math.ceil(maxX) : Math.floor(maxX));
        int bottomPx = (int) (outward ? Math.ceil(maxY) : Math.floor(maxY));
        if (flush) graphics.flush();
        graphics.enableScissor(x, y, Math.max(x, rightPx), Math.max(y, bottomPx));
    }
}
