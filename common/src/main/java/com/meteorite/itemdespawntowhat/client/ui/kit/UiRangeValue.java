package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 不可变区间状态：两个端点值加两个「端点是否存在」标志。
 *
 * <p>无约束端（宿主没有配置该侧边界）用 {@link #isPresent(UiRangeEnd)} 表达，数值一律为
 * {@link Double#NaN}，绝不编码成 0——否则「未配置」会与「配置为 0」不可区分。</p>
 *
 * <p>两个端点都存在的普通区间要求 {@code low <= high}；单端点或双端点缺失时只校验存在端有限。</p>
 */
public record UiRangeValue(double low, double high, boolean lowPresent, boolean highPresent) {

    /** 两端都不存在（无约束区间）。 */
    public static final UiRangeValue EMPTY = new UiRangeValue(Double.NaN, Double.NaN, false, false);

    public UiRangeValue {
        if (lowPresent && !Double.isFinite(low)) {
            throw new IllegalArgumentException("Present low end must be finite");
        }
        if (highPresent && !Double.isFinite(high)) {
            throw new IllegalArgumentException("Present high end must be finite");
        }
        if (!lowPresent && !Double.isNaN(low)) {
            throw new IllegalArgumentException("Absent low end must be NaN");
        }
        if (!highPresent && !Double.isNaN(high)) {
            throw new IllegalArgumentException("Absent high end must be NaN");
        }
        if (lowPresent && highPresent && low > high) {
            throw new IllegalArgumentException("Range low must not exceed high");
        }
    }

    // 两端都存在的普通区间
    public static UiRangeValue of(double low, double high) {
        return new UiRangeValue(low, high, true, true);
    }

    // 只有一端存在
    public static UiRangeValue single(double value, boolean lowEnd) {
        return lowEnd ? new UiRangeValue(value, Double.NaN, true, false)
                : new UiRangeValue(Double.NaN, value, false, true);
    }

    public boolean isPresent(UiRangeEnd end) {
        return end == UiRangeEnd.LOW ? lowPresent : highPresent;
    }

    // 端点数值；端点不存在时返回 NaN
    public double value(UiRangeEnd end) {
        return end == UiRangeEnd.LOW ? low : high;
    }

    // 设置端点数值并标记为存在；可能抛 IllegalArgumentException（low > high）
    public UiRangeValue withValue(UiRangeEnd end, double value) {
        return end == UiRangeEnd.LOW
                ? new UiRangeValue(value, high, true, highPresent)
                : new UiRangeValue(low, value, lowPresent, true);
    }

    // 标记端点不存在，数值置为 NaN
    public UiRangeValue withAbsent(UiRangeEnd end) {
        return end == UiRangeEnd.LOW
                ? new UiRangeValue(Double.NaN, high, false, highPresent)
                : new UiRangeValue(low, Double.NaN, lowPresent, false);
    }

    // 两端都不存在
    public boolean isEmpty() {
        return !lowPresent && !highPresent;
    }

    // 两端都存在
    public boolean bothPresent() {
        return lowPresent && highPresent;
    }
}
