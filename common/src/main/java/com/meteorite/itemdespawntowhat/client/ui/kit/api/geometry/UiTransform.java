package com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry;

import net.minecraft.client.gui.GuiGraphics;

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

}
