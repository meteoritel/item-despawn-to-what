package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 框选矩形：记录空白拖拽的锚点与当前点，按整数像素给出包围盒并命中内容矩形。
 *
 * <p>包围盒以「向外取整」定义——左上取 floor、右下取 ceil——保证视觉上被框到的像素都被算入。
 * 命中按半开区间 [min, max) 判定，与 {@link UiRect} 的命中口径一致；锚点与当前点重合时为空框，
 * 不与任何矩形相交。</p>
 */
public final class UiRubberBand {
    private boolean active;
    private double anchorX;
    private double anchorY;
    private double currentX;
    private double currentY;

    /** 开始框选；坐标非有限时忽略本次调用，框选保持未激活。 */
    public void begin(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        active = true;
        anchorX = x;
        anchorY = y;
        currentX = x;
        currentY = y;
    }

    /** 更新当前点；未激活或坐标非有限时忽略。 */
    public void update(double x, double y) {
        if (!active || !Double.isFinite(x) || !Double.isFinite(y)) return;
        currentX = x;
        currentY = y;
    }

    public void clear() { active = false; }

    public boolean active() { return active; }

    public int minX() { return (int) Math.floor(Math.min(anchorX, currentX)); }

    public int minY() { return (int) Math.floor(Math.min(anchorY, currentY)); }

    public int maxX() { return (int) Math.ceil(Math.max(anchorX, currentX)); }

    public int maxY() { return (int) Math.ceil(Math.max(anchorY, currentY)); }

    /** 与内容矩形 [x, x+w) × [y, y+h) 是否相交；空框或空矩形一律返回 false。 */
    public boolean intersects(int x, int y, int w, int h) {
        if (!active || w <= 0 || h <= 0) return false;
        return x < maxX() && x + w > minX() && y < maxY() && y + h > minY();
    }

    /** 绘制半透明框体与 1 像素描边；框为空（未激活或宽高为 0）时不绘制。 */
    public void draw(GuiGraphics graphics, int fillColor, int borderColor) {
        if (!active) return;
        int left = minX();
        int top = minY();
        int right = maxX();
        int bottom = maxY();
        if (right <= left || bottom <= top) return;
        graphics.fill(left, top, right, bottom, fillColor);
        graphics.fill(left, top, right, top + 1, borderColor);
        graphics.fill(left, bottom - 1, right, bottom, borderColor);
        graphics.fill(left, top + 1, left + 1, bottom - 1, borderColor);
        graphics.fill(right - 1, top + 1, right, bottom - 1, borderColor);
    }
}
