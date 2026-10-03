package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.math.BigDecimal;

/**
 * 不可变数值策略：合法数值域（min..max）、粗细两档步长、整数策略与拖动灵敏度。
 *
 * <p>合法域与轨道显示窗口是两件事：本记录只描述「哪些值合法」。超出显示窗口但仍在合法域内的
 * 值必须继续精确显示与编辑，因此 {@link #snap(double, boolean)} 只按域钳制，不做窗口钳制。</p>
 *
 * <p>步长吸附使用十进制运算（{@link BigDecimal#valueOf(double)} 基于最短十进制表示），
 * 避免 0.1 连加出现 0.30000000000000004 这类尾数；min 本身也是一个合法档位，
 * 所有档位都是 {@code min + k * step}。</p>
 *
 * <p>构造即校验：非有限边界、max &le; min、步长 &le; 0、细调系数不在 (0,1]，以及整数策略下
 * 边界或步长非整数（步长必须 &ge; 1），都会抛出 {@link IllegalArgumentException}，
 * 让非法策略在装配期暴露而不是在拖动期产生怪值。</p>
 */
public record UiNumberPolicy(double min, double max, double coarseStep, double fineStep,
                             boolean integerOnly, double fineDragFactor) {

    public UiNumberPolicy {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("Number policy bounds must be finite");
        }
        if (!(max > min)) {
            throw new IllegalArgumentException("Number policy max must be greater than min");
        }
        if (!Double.isFinite(coarseStep) || coarseStep <= 0.0D) {
            throw new IllegalArgumentException("Number policy coarse step must be > 0");
        }
        if (!Double.isFinite(fineStep) || fineStep <= 0.0D) {
            throw new IllegalArgumentException("Number policy fine step must be > 0");
        }
        if (!Double.isFinite(fineDragFactor) || fineDragFactor <= 0.0D || fineDragFactor > 1.0D) {
            throw new IllegalArgumentException("Number policy fine drag factor must be in (0, 1]");
        }
        if (integerOnly && (!isIntegral(min) || !isIntegral(max) || !isIntegral(coarseStep) || !isIntegral(fineStep))) {
            throw new IllegalArgumentException("Integer number policy requires integral bounds and steps >= 1");
        }
    }

    // 小数策略：粗档与细档相同
    public static UiNumberPolicy of(double min, double max, double step) {
        return new UiNumberPolicy(min, max, step, step, false, 0.25D);
    }

    // 小数策略：分别指定粗档与细档
    public static UiNumberPolicy of(double min, double max, double coarseStep, double fineStep) {
        return new UiNumberPolicy(min, max, coarseStep, fineStep, false, 0.25D);
    }

    // 整数策略：边界与步长必须是整数，细档默认 1
    public static UiNumberPolicy integer(int min, int max, int coarseStep) {
        return new UiNumberPolicy(min, max, coarseStep, 1.0D, true, 0.25D);
    }

    // 整数策略：分别指定粗档与细档
    public static UiNumberPolicy integer(int min, int max, int coarseStep, int fineStep) {
        return new UiNumberPolicy(min, max, coarseStep, fineStep, true, 0.25D);
    }

    // 返回换用另一细调拖动灵敏度的新策略（不修改原记录）
    public UiNumberPolicy withFineDragFactor(double factor) {
        return new UiNumberPolicy(min, max, coarseStep, fineStep, integerOnly, factor);
    }

    // 域跨度
    public double span() {
        return max - min;
    }

    // 按档位取步长
    public double step(boolean fine) {
        return fine ? fineStep : coarseStep;
    }

    // 把值钳制到合法域；NaN 取 min
    public double clamp(double value) {
        if (Double.isNaN(value)) return min;
        return Math.clamp(value, min, max);
    }

    // 吸附到最近的合法档位并钳制；非有限值按 min 处理
    public double snap(double value, boolean fine) {
        return clamp(snapUnclamped(value, fine));
    }

    // 吸附到最近的合法档位，但不做域钳制（调用方自行决定钳制顺序）
    public double snapUnclamped(double value, boolean fine) {
        if (!Double.isFinite(value)) return min;
        double step = step(fine);
        if (step == 1.0D && isIntegral(min)) {
            return Math.rint(value);
        }
        BigDecimal decimalValue = BigDecimal.valueOf(value);
        BigDecimal decimalOrigin = BigDecimal.valueOf(min);
        BigDecimal decimalStep = BigDecimal.valueOf(step);
        int scale = Math.max(decimalValue.scale(), Math.max(decimalOrigin.scale(), decimalStep.scale()));
        BigDecimal scaled = decimalValue.movePointRight(scale).subtract(decimalOrigin.movePointRight(scale));
        BigDecimal scaledStep = decimalStep.movePointRight(scale).abs();
        BigDecimal quotient = scaled.divideToIntegralValue(scaledStep);
        BigDecimal remainder = scaled.subtract(quotient.multiply(scaledStep)).abs();
        if (remainder.multiply(BigDecimal.valueOf(2L)).compareTo(scaledStep) >= 0) {
            quotient = scaled.signum() < 0 ? quotient.subtract(BigDecimal.ONE) : quotient.add(BigDecimal.ONE);
        }
        return decimalOrigin.add(quotient.multiply(decimalStep)).doubleValue();
    }

    /**
     * 拖动灵敏度：每个像素对应的数值增量。
     *
     * @param trackPixels 轨道可用像素宽度
     * @param windowSpan  显示窗口跨度
     * @param fine        Shift 细调时按 fineDragFactor 降低灵敏度
     */
    public double dragValuePerPixel(int trackPixels, double windowSpan, boolean fine) {
        if (trackPixels <= 0 || !Double.isFinite(windowSpan) || windowSpan <= 0.0D) return 0.0D;
        double span = fine ? windowSpan * fineDragFactor : windowSpan;
        return span / trackPixels;
    }

    private static boolean isIntegral(double value) {
        return Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) <= 9007199254740992.0D;
    }
}
