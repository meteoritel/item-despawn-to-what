package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 宿主样式来源：kit 不持有全局主题，控件样式一律经本接口由宿主注入。
 *
 * <p>实现方是宿主主题（本项目的 {@code client/ui/theme}），消费方是界面控件与屏幕；kit 只约定
 * 返回值的类型（{@link UiControlStyle}、{@link UiSliderSkin}），不实现任何视觉规范，
 * 因此换主题只改宿主实现，不动 kit，也不依赖全局静态状态。</p>
 *
 * <p>实现约束：每个角色都要返回非空值；同一角色在多次调用间保持稳定（控件在装配时取一次并长期持有）。
 * 没有主题的宿主不必实现本接口，kit 控件各自带有像素默认值。</p>
 */
public interface UiStyleSource {

    /**
     * 控件结构样式。
     *
     * @param role 控件语义角色
     * @return 该角色的不可变样式，不得为 null
     */
    UiControlStyle controlStyle(UiControlRole role);

    /**
     * 滑块外观。
     *
     * @param role 外观档位
     * @return 该档位的样式与画法，不得为 null
     */
    UiSliderSkin sliderSkin(UiSliderRole role);
}
