package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

/**
 * 双精度浮点参数声明：值域在解码期与语义校验两处都按 [min, max] 生效。
 * <p>概率字段常用它声明（JSON 0..1），界面百分比换算由 {@link ParamUnit#PERCENT} 表达；
 * 换算只改变显示单位，取值域仍以声明的 min/max 为准。</p>
 */
public record DoubleSpec(ParamMeta meta, double min, double max, double codecDefault) implements ParamSpec<Double> {

    public DoubleSpec {
        if (min > max) {
            throw new IllegalArgumentException("参数 " + meta.name() + " 的下界不能大于上界");
        }
    }

    // 可省略双精度浮点：缺失按默认值补齐
    public static DoubleSpec defaulted(String name, double defaultValue, double min, double max) {
        return new DoubleSpec(ParamMeta.of(name, ParamPresence.DEFAULTED, false), min, max, defaultValue);
    }

    public DoubleSpec unit(ParamUnit value) {
        return withMeta(meta.withUnit(value));
    }

    public DoubleSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    public DoubleSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public DoubleSpec initial(double value) {
        return withMeta(meta.withInitial(value));
    }

    @Override
    public Codec<Double> valueCodec() {
        return Codec.doubleRange(min, max);
    }

    /** 校验字段的出现形态、有限性与声明值域。 */
    @Override
    public boolean check(@Nullable Double value, IssueCollector issues, String fieldPath) {
        String path = ParamChecks.child(fieldPath, name());
        if (value == null) {
            return !required() || ParamChecks.required(null, name(), issues, path);
        }
        return ParamChecks.inRange(value, min, max, name(), issues, path);
    }

    /** 使用声明值域校验有限双精度值，并保留业务专用的错误文案。 */
    public boolean check(double value, IssueCollector issues, String fieldPath, String message) {
        return ParamChecks.inRange(value, min, max, issues, ParamChecks.child(fieldPath, name()), message);
    }

    @Override
    public @Nullable Double defaultValue() {
        return meta.defaultValue(codecDefault);
    }

    private DoubleSpec withMeta(ParamMeta value) {
        return new DoubleSpec(value, min, max, codecDefault);
    }
}
