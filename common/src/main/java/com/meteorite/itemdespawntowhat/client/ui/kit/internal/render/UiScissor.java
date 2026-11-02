package com.meteorite.itemdespawntowhat.client.ui.kit.internal.render;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/** kit 内部共用的 pose 裁剪换算；调用方指定取整方向与是否提交批次。 */
public final class UiScissor {
    private UiScissor() {}

    /**
     * 两条裁剪入口共用的换算核心：把当前 pose 坐标系里的矩形换算成整型 GUI 像素后设置 scissor。
     * 这里用完整 2x2 变换（含 m01/m10）；kit 只支持轴对齐的平移与缩放，此时与只取对角线等价。
     * outward=true 向外取整（floor/ceil，宁可多留 1 像素），false 向内取整（ceil/floor，宁可少留）。
     * flush 由调用方按绘制来源选择：视口切换提交批次，原版即时提交的文本保持现有批次。
     */
    public static void enable(GuiGraphics graphics, double left, double top, double right, double bottom,
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
