package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;

/**
 * 按可用宽度换行的流式排布：把已量好宽度的子项从左到右铺进多行，放不下就另起一行。
 *
 * <p>纯几何工具：不持有控件、不绘制、不分发输入。调用方给出各项宽度与可用宽度，取回每项的相对矩形
 * 与行数，再整体偏移到屏幕坐标。因此**测量与实际摆放共用同一份结果**——行数算内容高度，矩形用于
 * setBounds 与命中，不会出现「测量按一种宽度、命中按另一种宽度」的漂移。</p>
 *
 * <p>与 {@link UiLinearLayout} 的分工：{@code UiLinearLayout} 是有状态的一维分配器（固定 / 内容 /
 * 剩余规则加 min·max 钳制），本类是无状态的换行几何；两者都不做嵌套容器，换行只由本类负责。</p>
 *
 * <p>换行规则：行首项永远放在当前行（即使它比可用宽度还宽，也不会产生空行或死循环）；其后各项在
 * 「已放置内容的右边界 + 间距 + 本项宽度 &gt; 可用宽度」时另起一行。所有尺寸按 kit 的几何口径**钳到非负**
 * 而不抛异常（见包文档「公开行为约定」），因此不会构造出非法矩形。</p>
 */
public final class UiFlowLayout {

    /** 下标越界或空结果时返回的零矩形，调用方无需自行判空。 */
    private static final UiRect ZERO = new UiRect(0, 0, 0, 0);

    // 工具类，禁止实例化
    private UiFlowLayout() {
    }

    /**
     * 排布一批已量好宽度的子项。
     *
     * @param itemWidths     各项宽度，负值按 0 处理
     * @param availableWidth 每行可用宽度，负值按 0 处理
     * @param gap            同一行相邻项的间距，负值按 0 处理
     * @param lineHeight     每行行高，负值按 0 处理
     * @return 一次排布的结果，坐标以左上角为原点
     */
    public static Wrap wrap(int[] itemWidths, int availableWidth, int gap, int lineHeight) {
        Objects.requireNonNull(itemWidths, "itemWidths");
        int limit = Math.max(0, availableWidth);
        int spacing = Math.max(0, gap);
        int rowHeight = Math.max(0, lineHeight);
        UiRect[] bounds = new UiRect[itemWidths.length];
        int row = 0;
        int cursor = 0;
        for (int index = 0; index < itemWidths.length; index++) {
            int width = Math.max(0, itemWidths[index]);
            if (cursor > 0 && cursor + width > limit) {
                row++;
                cursor = 0;
            }
            bounds[index] = new UiRect(cursor, row * (rowHeight + spacing), width, rowHeight);
            cursor += width + spacing;
        }
        int rowCount = itemWidths.length == 0 ? 0 : row + 1;
        return new Wrap(bounds, rowCount, rowHeight, spacing);
    }

    /** 一次换行排布的结果：每项相对矩形、行数；行尾不追加间距。 */
    public static final class Wrap {
        private final UiRect[] bounds;
        private final int rowCount;
        private final int lineHeight;
        private final int gap;

        private Wrap(UiRect[] bounds, int rowCount, int lineHeight, int gap) {
            this.bounds = bounds;
            this.rowCount = rowCount;
            this.lineHeight = lineHeight;
            this.gap = gap;
        }

        /** 子项数量。 */
        public int size() {
            return bounds.length;
        }

        /** 排出的行数；没有子项时为 0。 */
        public int rowCount() {
            return rowCount;
        }

        /** 每行行高。 */
        public int lineHeight() {
            return lineHeight;
        }

        /** 同一行相邻项的间距。 */
        public int gap() {
            return gap;
        }

        /**
         * 第 {@code index} 项的相对矩形，左上角为原点；下标越界返回零矩形。
         */
        public UiRect bounds(int index) {
            if (index < 0 || index >= bounds.length) {
                return ZERO;
            }
            return bounds[index];
        }

        /**
         * 内容总高：{@code rowCount * lineHeight + (rowCount - 1) * gap}，行尾不含间距。
         */
        public int height() {
            int rows = rowCount();
            if (rows == 0) {
                return 0;
            }
            return rows * lineHeight + (rows - 1) * gap;
        }
    }
}
