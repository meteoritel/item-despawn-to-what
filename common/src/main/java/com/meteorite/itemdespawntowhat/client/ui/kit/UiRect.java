package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 排版阶段创建的逻辑矩形，右边界和下边界不参与命中。
 *
 * <p>负宽高在构造期抛 {@link IllegalArgumentException}：这是矩形自身的不变式。kit 的公开几何入口
 * （{@code UiControl.setBounds}、{@code UiDocument.setViewport}、{@code UiScrollView.setViewport}）
 * 会先把负尺寸钳到合法范围再构造，调用方不需要自行钳制；直接 new 的调用方必须自己保证非负。</p>
 */
public record UiRect(int x, int y, int width, int height) {
    public UiRect {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative UI extent");
    }

    public int right() { return x + width; }
    public int bottom() { return y + height; }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
    }

    /**
     * 行内小方块的几何：贴本矩形左边缘、纵向居中。
     *
     * <p>「方框 + 文字」这类行内控件（复选框等）用它同时决定绘制矩形与命中范围，
     * 交互点因此只落在方框内，点击行内文字区域不触发。方框边长超过行高或行宽时按本矩形取小，
     * 避免方框越出自身行。</p>
     *
     * @param size 方块边长
     * @return 行内方块矩形；边长非正或本矩形为空时返回零尺寸矩形
     */
    public UiRect centeredSquare(int size) {
        int side = Math.clamp(size, 0, Math.min(width, height));
        return new UiRect(x, y + (height - side) / 2, side, side);
    }
}
