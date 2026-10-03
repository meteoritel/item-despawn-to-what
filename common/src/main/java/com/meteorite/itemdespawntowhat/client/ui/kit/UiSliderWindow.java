package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 不可变轨道显示窗口：把合法数值域的一段映射到整条轨道。
 *
 * <p>显示窗口只影响几何映射，不影响合法性：窗口外的合法值仍然可以被显示、被精确编辑，
 * {@link #fraction(double)} 故意不做钳制，返回值小于 0 或大于 1 供控件画溢出标记；
 * 只有把指针位置换算成数值时（{@link #valueAt(double)}）才把比例钳制到 [0,1]，
 * 因此点击轨道不会产生窗口外的值。</p>
 */
public record UiSliderWindow(double min, double max) {

    public UiSliderWindow {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("Slider window bounds must be finite");
        }
        if (!(max > min)) {
            throw new IllegalArgumentException("Slider window max must be greater than min");
        }
    }

    // 显式构造
    public static UiSliderWindow of(double min, double max) {
        return new UiSliderWindow(min, max);
    }

    // 窗口跨度
    public double span() {
        return max - min;
    }

    // 值是否落在显示窗口内
    public boolean contains(double value) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    // 未钳制的轨道比例；<0 或 >1 表示值在显示窗口之外
    public double fraction(double value) {
        return (value - min) / span();
    }

    // 把任意比例钳制到 [0,1]
    public double clampFraction(double fraction) {
        if (Double.isNaN(fraction)) return 0.0D;
        return Math.clamp(fraction, 0.0D, 1.0D);
    }

    // 由轨道比例换算数值；比例先钳制，保证点击轨道不产生窗口外的值
    public double valueAt(double fraction) {
        return min + clampFraction(fraction) * span();
    }
}
