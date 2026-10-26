package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * 整数参数声明：覆盖必填、可省略与可空三种出现形态。
 * <p>codec 的严格程度与语义校验分开记录：{@code codecBounded} 为真时解码期就落在 [min, max] 内，
 * 为假时解码期接受任意整数、由 {@link #check} 在语义校验阶段报错（条件类型沿用这种「解码宽、校验严」的形态）。</p>
 */
public record IntSpec(ParamMeta meta, int min, int max, int codecDefault, boolean codecBounded,
                      @Nullable Integer windowMin, @Nullable Integer windowMax) implements ParamSpec {

    public IntSpec {
        if (min > max) {
            throw new IllegalArgumentException("参数 " + meta.name() + " 的下界不能大于上界");
        }
    }

    // 可省略整数：缺失按默认值补齐
    public static IntSpec defaulted(String name, int defaultValue, int min, int max) {
        return new IntSpec(ParamMeta.of(name, ParamPresence.DEFAULTED, false), min, max, defaultValue, true,
                null, null);
    }

    // 必填整数：缺失即解码失败
    public static IntSpec required(String name, int min, int max) {
        return new IntSpec(ParamMeta.of(name, ParamPresence.REQUIRED, true), min, max, 0, true, null, null);
    }

    // 可空整数：缺失解码为 null，解码期不限界
    public static IntSpec nullable(String name, int min, int max) {
        return new IntSpec(ParamMeta.of(name, ParamPresence.NULLABLE, false), min, max, 0, false, null, null);
    }

    // 解码期改用无边界 Codec.INT（语义校验仍按 min/max）
    public IntSpec widened() {
        return new IntSpec(meta, min, max, codecDefault, false, windowMin, windowMax);
    }

    // 可空字段也使用带边界 codec
    public IntSpec bounded() {
        return new IntSpec(meta, min, max, codecDefault, true, windowMin, windowMax);
    }

    public IntSpec unit(ParamUnit value) {
        return withMeta(meta.withUnit(value));
    }

    public IntSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    public IntSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public IntSpec initial(int value) {
        return withMeta(meta.withInitial(value));
    }

    public IntSpec editorRequired(boolean value) {
        return withMeta(meta.withEditorRequired(value));
    }

    /** 不在编辑器中展示：仍参与编解码与校验 */
    public IntSpec hidden() {
        return withMeta(meta.withEditorVisible(false));
    }

    public IntSpec presets(ParamPreset... values) {
        return withMeta(meta.withPresets(values));
    }

    // 滑块常用窗口；窗口不是提交硬限额
    public IntSpec window(int min, int max) {
        return new IntSpec(meta, this.min, this.max, codecDefault, codecBounded, min, max);
    }

    // 按 presence 生成字段构造器：必填用 fieldOf，可省略用带默认值的 optionalFieldOf
    public <O> RecordCodecBuilder<O, Integer> fieldFor(Function<O, Integer> getter) {
        Codec<Integer> codec = valueCodec();
        MapCodec<Integer> field = presence() == ParamPresence.REQUIRED
                ? codec.fieldOf(name())
                : codec.optionalFieldOf(name(), codecDefault);
        return field.forGetter(getter);
    }

    // 可空字段：返回 Optional，由调用方在 apply 时 orElse(null)
    public <O> RecordCodecBuilder<O, Optional<Integer>> fieldForNullable(Function<O, Optional<Integer>> getter) {
        return valueCodec().optionalFieldOf(name()).forGetter(getter);
    }

    /** 校验字段的出现形态与声明值域，并以声明名称定位问题。 */
    @Override
    public boolean check(@Nullable Object value, IssueCollector issues, String fieldPath) {
        String path = ParamChecks.child(fieldPath, name());
        if (value == null) {
            return !required() || ParamChecks.required(null, name(), issues, path);
        }
        return ParamChecks.inRange((Integer) value, min, max, name(), issues, path);
    }

    /** 使用声明值域校验整数，并保留业务专用的错误文案。 */
    public boolean check(int value, IssueCollector issues, String fieldPath, String message) {
        return ParamChecks.inRange(value, min, max, issues, ParamChecks.child(fieldPath, name()), message);
    }

    @Override
    public @Nullable Object defaultValue() {
        return presence() == ParamPresence.DEFAULTED ? codecDefault : null;
    }

    @Override
    public Codec<Integer> valueCodec() {
        return codecBounded ? Codec.intRange(min, max) : Codec.INT;
    }

    private IntSpec withMeta(ParamMeta value) {
        return new IntSpec(value, min, max, codecDefault, codecBounded, windowMin, windowMax);
    }
}
