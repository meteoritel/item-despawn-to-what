package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * 数值与文本互转工具：显示侧「整数直出、否则定点小数」，解析侧只接受有限值。
 *
 * <p>「3 显示成 3、3.5 显示成 3.50」这条规则在本项目多处重复出现，本类把它收敛成唯一实现：
 * 整数判定带 {@link #INTEGER_TOLERANCE} 的浮点噪声容差（消除 0.1 连加这类尾数造成的显示跳变），
 * 且只在 {@link #MAX_EXACT_INTEGER} 以内直出整数，避免超出 long 可表示范围时输出饱和值。
 * 非有限值的文本由调用方给出（{@link #plain(double, int, String)}），kit 不规定「N/A」还是「-」。</p>
 *
 * <p>解析统一为「去首尾空白 → 空串判非法 → 解析 → 只接受有限值」，非法一律返回 {@code null} 而不是抛异常；
 * 整数解析先用 {@code long} 承接再判范围，超出 int 直接判非法，不静默钳制。</p>
 *
 * <p>严格入口（{@link #plainExact(double, int, double)}、{@link #isExactIntegral(double, double)}）与容差入口并存：
 * 容差整数判定只用于 {@link #plain(double, int)} 这类接受浮点噪声的场景；
 * 界面显示点必须按原有语义选择入口，两者不可互相替换。</p>
 */
public final class UiNumbers {

    /** 整数判定容差：与最近整数的距离小于该值即按整数显示。 */
    public static final double INTEGER_TOLERANCE = 1.0E-6D;

    /** 可精确表示整数的 double 上界（2^53）：超出后不再直出整数文本。 */
    public static final double MAX_EXACT_INTEGER = 9007199254740992.0D;

    /** 「凡能安全转 long 就直出整数」的上界（{@link Long#MAX_VALUE} 的 double 表示）。 */
    public static final double MAX_LONG_AS_DOUBLE = (double) Long.MAX_VALUE;

    // 工具类，禁止实例化
    private UiNumbers() {
    }

    /**
     * 定点小数文本：值为整数时直出整数字符串，否则保留指定位数的小数。
     *
     * <p>非有限值（{@code NaN} / 正负无穷）不是可显示数值，按 {@link Double#toString(double)} 直出，
     * 由调用方决定是否改用 {@link #plain(double, int, String)} 的替换文本。</p>
     *
     * @param value    待显示数值
     * @param decimals 小数位数，必须 {@code >= 0}
     */
    public static String plain(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return plainFinite(value, decimals);
    }

    /**
     * 定点小数文本，非有限值改用调用方给出的文本。
     *
     * @param value         待显示数值
     * @param decimals      小数位数，必须 {@code >= 0}
     * @param nonFiniteText 非有限值的显示文本（例如 {@code "-"} 或空串）
     */
    public static String plain(double value, int decimals, String nonFiniteText) {
        Objects.requireNonNull(nonFiniteText, "nonFiniteText");
        if (!Double.isFinite(value)) {
            return nonFiniteText;
        }
        return plainFinite(value, decimals);
    }

    /**
     * 严格整数判定：无浮点容差，仅当有限、与最近整数严格相等且绝对值不超过 {@code integerLimit} 时为真。
     *
     * <p>显示点的「能转 long 就直出」与策略层的「整数域」校验共用这一份语义。</p>
     *
     * @param value        待判定数值
     * @param integerLimit 允许直出整数的绝对值上界
     */
    public static boolean isExactIntegral(double value, double integerLimit) {
        return Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) <= integerLimit;
    }

    /**
     * 严格定点小数文本：整数在 {@code integerLimit} 内按 {@code long} 直出，否则按 {@code decimals} 定点输出。
     *
     * <p>不做去尾零，定点文本保留 {@code decimals} 位小数（{@code 3.5} 在 decimals=2 时为 {@code "3.50"}）；
     * 需要与「整数直出 + 去尾零」一致的调用方自行组合 {@link #trimTrailingZeros(String)}。
     * 非有限值按 {@link Double#toString(double)} 直出。</p>
     *
     * @param value        待显示数值
     * @param decimals     小数位数，必须 {@code >= 0}
     * @param integerLimit 直出整数的绝对值上界
     */
    public static String plainExact(double value, int decimals, double integerLimit) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        if (isExactIntegral(value, integerLimit)) {
            return Long.toString((long) value);
        }
        return fixed(value, decimals);
    }

    /**
     * 严格定点小数文本，直出整数的上界取 {@link #MAX_EXACT_INTEGER}。
     *
     * @param value    待显示数值
     * @param decimals 小数位数，必须 {@code >= 0}
     */
    public static String plainExact(double value, int decimals) {
        return plainExact(value, decimals, MAX_EXACT_INTEGER);
    }

    /**
     * 去掉定点小数文本的尾零与孤立小数点；不含小数点的文本原样返回。
     *
     * <p>整数文本（如 {@code "120"}）不得被去尾零，因此仅当文本含小数点时才裁剪。</p>
     *
     * @param fixedText 定点小数文本，不可为 null
     */
    public static String trimTrailingZeros(String fixedText) {
        Objects.requireNonNull(fixedText, "fixedText");
        int dot = fixedText.indexOf('.');
        if (dot < 0) {
            return fixedText;
        }
        int end = fixedText.length();
        while (end > dot + 1 && fixedText.charAt(end - 1) == '0') {
            end--;
        }
        if (end == dot + 1) {
            return fixedText.substring(0, dot);
        }
        return fixedText.substring(0, end);
    }

    /**
     * 取整直出（{@link Math#round(double)} 语义，向正无穷取整）：整数域显示入口。
     *
     * <p>与 {@code %.0f} 不等价：{@code %.0f} 是远离零的 HALF_UP（{@code -3.5} 得 {@code -4}），
     * 本方法取 {@code floor(value + 0.5)}（{@code -3.5} 得 {@code -3}）。</p>
     *
     * @param value 待显示数值
     */
    public static String roundedText(double value) {
        return Long.toString(Math.round(value));
    }

    /**
     * 小数解析：去首尾空白后只接受有限值。
     *
     * @param text 待解析文本，可为 null
     * @return 有限数值；空串、非法格式或非有限值返回 null
     */
    public static @Nullable Double parseFinite(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /**
     * 整数解析：去首尾空白后按十进制整数解析，超出 {@code int} 范围判非法。
     *
     * @param text 待解析文本，可为 null
     * @return 可精确放入 int 的整数；空串、非法格式或越界返回 null
     */
    public static @Nullable Integer parseInt(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(trimmed);
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String plainFinite(double value, int decimals) {
        if (Math.abs(value - Math.rint(value)) < INTEGER_TOLERANCE && Math.abs(value) <= MAX_EXACT_INTEGER) {
            return Long.toString(Math.round(value));
        }
        return fixed(value, decimals);
    }

    // 定点小数格式化；精度是参数，格式串因此不是编译期常量
    private static String fixed(double value, int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("Decimals must be >= 0");
        }
        String pattern = "%." + decimals + "f";
        return String.format(Locale.ROOT, pattern, value);
    }
}
