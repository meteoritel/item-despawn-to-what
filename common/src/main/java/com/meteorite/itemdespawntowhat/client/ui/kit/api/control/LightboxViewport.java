package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;

/** 灯箱内容共用的连续缩放与屏幕像素偏移；输入会话仍由实际 Content 拥有。 */
final class LightboxViewport {
    static final double MIN_SCALE = 0.05;
    static final double MAX_SCALE = 8.0;
    private static final double STEP = 1.25;

    private final int contentWidth;
    private final int contentHeight;
    private UiRect viewport = new UiRect(0, 0, 0, 0);
    private double scale = 1.0;
    private double minScale = MIN_SCALE;
    private double maxScale = MAX_SCALE;
    private double offsetX;
    private double offsetY;

    LightboxViewport(int contentWidth, int contentHeight) {
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
    }

    UiRect viewport() { return viewport; }

    void setViewport(UiRect viewport) {
        this.viewport = viewport;
        clampOffsets();
    }

    double containScale() {
        return Math.min(viewport.width() / (double) contentWidth, viewport.height() / (double) contentHeight);
    }

    void fit(double fitScale, double minimum, double maximum) {
        minScale = minimum;
        maxScale = maximum;
        scale = Math.clamp(fitScale, minimum, maximum);
        offsetX = 0;
        offsetY = 0;
        clampOffsets();
    }

    boolean zoom(double amount, double anchorX, double anchorY) {
        if (amount == 0 || !Double.isFinite(anchorX) || !Double.isFinite(anchorY)) return false;
        double target = Math.clamp(amount > 0 ? scale * STEP : scale / STEP, minScale, maxScale);
        if (target == scale) return false;
        double centerX = viewport.x() + viewport.width() / 2.0;
        double centerY = viewport.y() + viewport.height() / 2.0;
        double contentX = (anchorX - centerX - offsetX) / scale;
        double contentY = (anchorY - centerY - offsetY) / scale;
        scale = target;
        offsetX = anchorX - centerX - contentX * scale;
        offsetY = anchorY - centerY - contentY * scale;
        clampOffsets();
        return true;
    }

    boolean zoomBy(int direction) {
        return zoom(direction, viewport.x() + viewport.width() / 2.0, viewport.y() + viewport.height() / 2.0);
    }

    boolean panBy(double screenDx, double screenDy) {
        if (!Double.isFinite(screenDx) || !Double.isFinite(screenDy)) return false;
        double previousX = offsetX;
        double previousY = offsetY;
        offsetX += screenDx;
        offsetY += screenDy;
        clampOffsets();
        return offsetX != previousX || offsetY != previousY;
    }

    int zoomPercent() { return (int) Math.round(scale * 100.0); }

    UiRect contentBounds() {
        double width = contentWidth * scale;
        double height = contentHeight * scale;
        return new UiRect((int) Math.round(viewport.x() + (viewport.width() - width) / 2.0 + offsetX),
                (int) Math.round(viewport.y() + (viewport.height() - height) / 2.0 + offsetY),
                Math.max(1, (int) Math.round(width)), Math.max(1, (int) Math.round(height)));
    }

    private void clampOffsets() {
        double limitX = Math.max(0, (contentWidth * scale - viewport.width()) / 2.0);
        double limitY = Math.max(0, (contentHeight * scale - viewport.height()) / 2.0);
        offsetX = Math.clamp(offsetX, -limitX, limitX);
        offsetY = Math.clamp(offsetY, -limitY, limitY);
    }
}
