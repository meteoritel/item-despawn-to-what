package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 滑块外观档位：同一主题按行高给出多套几何，样式与画法成对注入，见 {@link UiSliderSkin}。
 *
 * <p>档位只描述几何尺度，不描述颜色；两档共用同一套输入与数值契约，切换档位不改变控件行为。</p>
 */
public enum UiSliderRole {
    /** 紧凑档：常规表单行的细轨道与窄手柄。 */
    COMPACT,
    /** 宽松档：加高行的粗轨道与宽手柄，便于瞄准。 */
    ROOMY
}
