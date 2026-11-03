package com.meteorite.itemdespawntowhat.client.ui.kit.api.layout;

/**
 * 固定行距内容的命中度量；行间留白由实际行高决定。
 */
public final class UiRowMetrics {
    private UiRowMetrics() {
    }

    /**
     * 按内容纵坐标查找行，负坐标、行间留白和内容范围外返回 -1。
     *
     * @param contentY 保留小数精度的内容纵坐标
     * @param rowPitch 相邻行起点的距离，最小为 1
     * @param rowHeight 每行实际可命中的高度
     * @param rowCount 行数
     * @return 命中行下标，未命中为 -1
     */
    public static int fixedRowIndex(double contentY, int rowPitch, int rowHeight, int rowCount) {
        int pitch = Math.max(1, rowPitch);
        if (!(contentY >= 0 && contentY < (double) pitch * rowCount) || contentY % pitch >= rowHeight) {
            return -1;
        }
        return (int) (contentY / pitch);
    }
}
