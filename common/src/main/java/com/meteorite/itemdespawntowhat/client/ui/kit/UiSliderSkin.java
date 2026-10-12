package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;

/**
 * 滑块外观：把样式与画法配成一对，由宿主一次注入，避免样式来自一套主题而画法来自另一套。
 *
 * <p>两者本就互相依赖——{@link UiSliderPainter.Frame} 直接携带 {@link UiSliderStyle}——
 * 因此注入面收成一个值对象：宿主拿到 {@link UiSliderSkin} 后分别交给
 * {@link UiScalarSlider#setStyle(UiSliderStyle)} 与 {@link UiScalarSlider#setPainter(UiSliderPainter)}。</p>
 *
 * @param style   颜色与尺寸，不可为 null
 * @param painter 绘制策略，不可为 null
 */
public record UiSliderSkin(UiSliderStyle style, UiSliderPainter painter) {

    public UiSliderSkin {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(painter, "painter");
    }

    /** kit 自带的像素外观：没有主题的宿主可直接使用，等价于 {@link UiSliderStyle#pixelDefaults()} 配 {@link PixelSliderPainter#INSTANCE}。 */
    public static UiSliderSkin pixelDefaults() {
        return new UiSliderSkin(UiSliderStyle.pixelDefaults(), PixelSliderPainter.INSTANCE);
    }
}
