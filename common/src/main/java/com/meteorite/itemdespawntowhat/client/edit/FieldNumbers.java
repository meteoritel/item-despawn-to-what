package com.meteorite.itemdespawntowhat.client.edit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.jetbrains.annotations.Nullable;

/**
 * 数值字段的文本与 JSON 值互转（docs/plan/plan-gui-rule-update-fields.md §1/§4）。
 * <ul>
 *   <li>概率按原始精度往返：0.123456 显示为 12.3456%，写回仍是 0.123456；</li>
 *   <li>刻数按十进制换算秒（0.05 秒为最小可表示单位），秒×20 不是整数时拒绝而不是静默取整；</li>
 *   <li>trigger_after_seconds 本身存秒，直接按秒解析，不经过刻数换算；</li>
 *   <li>药水等级：JSON 存 amplifier（0 起算），界面显示 1..256，写回减一。</li>
 * </ul>
 * <p>纯函数、无客户端状态：显示与解析共用同一套规则，保证「显示格式不反写舍入值」。
 */
public final class FieldNumbers {

    // 一秒对应的刻数
    public static final double TICKS_PER_SECOND = 20.0D;

    // 秒模式下最小可表示的秒步长
    public static final double MIN_SECONDS_STEP = 0.05D;

    // amplifier 的后端上界（0 起算）
    public static final int MAX_AMPLIFIER = 255;

    // 界面等级下界（1 起算）
    public static final int MIN_LEVEL = 1;

    // 界面等级上界：amplifier 255 对应等级 256
    public static final int MAX_LEVEL = MAX_AMPLIFIER + 1;

    // 工具类
    private FieldNumbers() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 概率：JSON 0..1 → 界面百分比文本，保留原始精度（不做 %.1f 舍入）
    public static String percentText(double chance) {
        if (!Double.isFinite(chance)) {
            return Double.toString(chance);
        }
        return plain(BigDecimal.valueOf(chance).multiply(BigDecimal.valueOf(100))) + "%";
    }

    // 界面百分比文本 → JSON 0..1；空或非法返回 null（取值域校验交给调用方）
    public static @Nullable Double parsePercent(@Nullable String text) {
        BigDecimal value = parseDecimal(text);
        if (value == null) {
            return null;
        }
        return value.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64).doubleValue();
    }

    // 刻数 → 秒文本（十进制换算，去掉多余的尾零）
    public static String ticksToSecondsText(int ticks) {
        return plain(BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(TICKS_PER_SECOND), MathContext.DECIMAL64));
    }

    // 秒文本 → 刻数；非法或秒×20 不是整数时返回 null（不静默取整）
    public static @Nullable Integer parseSecondsToTicks(@Nullable String text) {
        BigDecimal seconds = parseDecimal(text);
        if (seconds == null) {
            return null;
        }
        BigDecimal ticks = seconds.multiply(BigDecimal.valueOf(TICKS_PER_SECOND));
        if (ticks.stripTrailingZeros().scale() > 0) {
            return null;
        }
        try {
            return ticks.intValueExact();
        } catch (ArithmeticException error) {
            return null;
        }
    }

    // 触发秒数字段：本身就是秒，按整数秒解析，不做刻数换算
    public static @Nullable Integer parseSecondsDirect(@Nullable String text) {
        BigDecimal value = parseDecimal(text);
        if (value == null || value.stripTrailingZeros().scale() > 0) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException error) {
            return null;
        }
    }

    // amplifier（JSON，0 起算）→ 界面等级（1 起算）
    public static int levelOf(int amplifier) {
        return amplifier + MIN_LEVEL;
    }

    // 界面等级文本 → amplifier；非法或越界 1..256 返回 null
    public static @Nullable Integer amplifierOf(@Nullable String levelText) {
        BigDecimal value = parseDecimal(levelText);
        if (value == null || value.stripTrailingZeros().scale() > 0) {
            return null;
        }
        int level;
        try {
            level = value.intValueExact();
        } catch (ArithmeticException error) {
            return null;
        }
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            return null;
        }
        return level - MIN_LEVEL;
    }

    // 按显示精度格式化（-1 表示最短往返表示，不做舍入）
    public static String text(double value, int displayPrecision) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        if (displayPrecision < 0) {
            return plain(BigDecimal.valueOf(value));
        }
        return BigDecimal.valueOf(value).setScale(displayPrecision, RoundingMode.HALF_UP).toPlainString();
    }

    // 解析十进制文本（容忍首尾空白与百分号）；非法返回 null
    private static @Nullable BigDecimal parseDecimal(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.endsWith("%")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    // 去尾零输出，且避免 BigDecimal 的负 scale 科学计数
    private static String plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY).toPlainString() : stripped.toPlainString();
    }
}
