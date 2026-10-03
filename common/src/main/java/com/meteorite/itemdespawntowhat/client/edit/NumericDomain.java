package com.meteorite.itemdespawntowhat.client.edit;

import java.math.BigDecimal;
import org.jetbrains.annotations.Nullable;

/**
 * 数值字段的域元数据：把「后端合法域 / 滑块常用窗口 / 交互步长 / 显示精度」分开记录
 * （docs/plan/plan-gui-rule-update-fields.md §1）。
 * <p>常用窗口只影响滑块手感，不是提交硬限额：窗口外的合法值仍可精确输入并保存。
 * <p>显示精度为 {@code -1} 表示按值的最短往返表示回填，不做任何舍入
 * （概率 0.123456 打开界面后必须仍是 0.123456）。
 * <p>步长只用于拖动/方向键吸附；回填与写回都不吸附。
 */
public record NumericDomain(@Nullable Double backendMin, @Nullable Double backendMax,
                            @Nullable Double windowMin, @Nullable Double windowMax,
                            double step, double shiftStep, int displayPrecision, boolean integer) {

    public NumericDomain {
        if (!(step > 0.0D)) {
            throw new IllegalArgumentException("交互步长必须为正数");
        }
        if (!(shiftStep > 0.0D)) {
            throw new IllegalArgumentException("精细步长必须为正数");
        }
    }

    // 整数域：滑块窗口默认与后端域一致，步长 1、显示精度 0
    public static NumericDomain integers(int min, int max) {
        return integers(min, max, min, max);
    }

    // 整数域：滑块窗口与后端域分开记录（旧界面人工上限只能作窗口）
    public static NumericDomain integers(int min, int max, int windowMin, int windowMax) {
        return new NumericDomain((double) min, (double) max, (double) windowMin, (double) windowMax,
                1.0D, 1.0D, 0, true);
    }

    // 小数域：默认按最短往返表示回填（displayPrecision = -1）
    public static NumericDomain decimals(double min, double max, double step, double shiftStep) {
        return decimals(min, max, step, shiftStep, -1);
    }

    // 小数域：显式给出显示精度
    public static NumericDomain decimals(double min, double max, double step, double shiftStep, int displayPrecision) {
        return new NumericDomain(min, max, min, max, step, shiftStep, displayPrecision, false);
    }

    // 只换滑块常用窗口，保留后端合法域、步长与显示精度
    public NumericDomain withWindow(double min, double max) {
        return new NumericDomain(backendMin, backendMax, min, max, step, shiftStep, displayPrecision, integer);
    }

    // 只换交互步长，保留其余元数据
    public NumericDomain withSteps(double newStep, double newShiftStep) {
        return new NumericDomain(backendMin, backendMax, windowMin, windowMax, newStep, newShiftStep,
                displayPrecision, integer);
    }

    // 后端合法域的 "min..max" 文本；任一端缺失时返回 null（表示不约束）
    public @Nullable String domainText() {
        if (backendMin == null || backendMax == null) {
            return null;
        }
        return numberText(backendMin) + ".." + numberText(backendMax);
    }

    // 滑块下界（窗口缺省时回落后端域，仍缺省返回 fallback）
    public double sliderMin(double fallback) {
        Double value = windowMin != null ? windowMin : backendMin;
        return value == null ? fallback : value;
    }

    // 滑块上界（窗口缺省时回落后端域，仍缺省返回 fallback）
    public double sliderMax(double fallback) {
        Double value = windowMax != null ? windowMax : backendMax;
        return value == null ? fallback : value;
    }

    // 域文本：整数不带小数点，小数用最短往返表示
    private String numberText(double value) {
        if (integer && value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
    }
}
