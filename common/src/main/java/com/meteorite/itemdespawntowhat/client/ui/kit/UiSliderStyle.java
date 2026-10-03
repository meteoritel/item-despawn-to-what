package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 不可变滑块样式：表达轨道、已填充段、手柄、焦点、禁用、错误、溢出与文字槽用色。
 *
 * <p>kit 不引用宿主主题（client/ui/theme），宿主把主题色映射成本记录后注入控件；没有主题的
 * 宿主可用 {@link #pixelDefaults()}。所有用色方法都按「启用/悬停/错误」解析出最终 ARGB，
 * 控件自身不判断主题语义。</p>
 */
public record UiSliderStyle(
        int trackColor,
        int fillColor,
        int thumbColor,
        int thumbHoverColor,
        int disabledColor,
        int focusColor,
        int errorColor,
        int overflowColor,
        int textSlotColor,
        int labelTextColor,
        int valueTextColor,
        int disabledTextColor,
        int errorTextColor,
        int trackHeight,
        int thumbWidth,
        int thumbOverhang) {

    public UiSliderStyle {
        if (trackHeight < 1) throw new IllegalArgumentException("Slider track height must be >= 1");
        if (thumbWidth < 1) throw new IllegalArgumentException("Slider thumb width must be >= 1");
        if (thumbOverhang < 0) throw new IllegalArgumentException("Slider thumb overhang must be >= 0");
    }

    // 无主题宿主的像素灰阶默认值
    public static UiSliderStyle pixelDefaults() {
        return new UiSliderStyle(0xFF8B8B8B, 0xFF6E86A8, 0xFFC6C6C6, 0xFFD8D8D8, 0xFFA0A0A0,
                0xFFFFFFFF, 0xFFA44A44, 0xFFFFAA00, 0x00000000,
                0xFFE0E0E0, 0xFFFFFFFF, 0xFF9A9A9A, 0xFFA44A44,
                4, 3, 2);
    }

    // 轨道底色；禁用时统一用禁用色
    public int resolveTrack(boolean enabled) {
        return enabled ? trackColor : disabledColor;
    }

    // 已填充段颜色；禁用时统一用禁用色
    public int resolveFill(boolean enabled) {
        return enabled ? fillColor : disabledColor;
    }

    // 手柄颜色；禁用优先，其次悬停/按下
    public int resolveThumb(boolean enabled, boolean hovered) {
        if (!enabled) return disabledColor;
        return hovered ? thumbHoverColor : thumbColor;
    }

    // 标签文字颜色：禁用 > 错误 > 正常
    public int resolveLabelText(boolean enabled, boolean error) {
        if (!enabled) return disabledTextColor;
        return error ? errorTextColor : labelTextColor;
    }

    // 数值文字颜色：禁用 > 错误 > 正常
    public int resolveValueText(boolean enabled, boolean error) {
        if (!enabled) return disabledTextColor;
        return error ? errorTextColor : valueTextColor;
    }
}
