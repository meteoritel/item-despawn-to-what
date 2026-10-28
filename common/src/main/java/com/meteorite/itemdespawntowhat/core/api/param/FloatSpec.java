package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

/**
 * 单精度浮点参数声明：值域在解码期与语义校验两处都按 [min, max] 生效。
 * <p>原版字段为 float（如爆炸威力、战利品幸运值），单独建模避免与 double 参数互相转换时改变 JSON 表示。</p>
 */
public record FloatSpec(ParamMeta meta, float min, float max, float codecDefault) implements ParamSpec<Float> {

    public FloatSpec {
        if (min > max) {
            throw new IllegalArgumentException("参数 " + meta.name() + " 的下界不能大于上界");
        }
    }

    // 可省略单精度浮点：缺失按默认值补齐
    public static FloatSpec defaulted(String name, float defaultValue, float min, float max) {
        return new FloatSpec(ParamMeta.of(name, ParamPresence.DEFAULTED, false), min, max, defaultValue);
    }

    public FloatSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    /** 设置编辑器展示层级。 */
    public FloatSpec tier(ParamTier value) { return withMeta(meta.withTier(value)); }

    public FloatSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public FloatSpec initial(float value) {
        return withMeta(meta.withInitial(value));
    }

    @Override
    public Codec<Float> valueCodec() {
        return Codec.floatRange(min, max);
    }

    @Override
    public boolean check(@Nullable Float value, IssueCollector issues, String fieldPath) {
        String path = ParamChecks.child(fieldPath, name());
        if (value == null) {
            return !required() || ParamChecks.required(null, name(), issues, path);
        }
        return ParamChecks.inRange(value.doubleValue(), min, max, name(), issues, path);
    }

    @Override
    public @Nullable Float defaultValue() {
        return meta.defaultValue(codecDefault);
    }

    private FloatSpec withMeta(ParamMeta value) {
        return new FloatSpec(value, min, max, codecDefault);
    }
}
