package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * 单个嵌套对象参数声明（如气候区间）。
 * <p>嵌套对象内部字段的取值域由它自己的 codec 与校验负责，声明只出现字段名与编辑器形态。</p>
 */
public record NestedSpec<T>(ParamMeta meta, Codec<T> codec) implements ParamSpec {

    // 可空嵌套对象：缺失解码为 null
    public static <T> NestedSpec<T> nullable(String name, Codec<T> codec) {
        return new NestedSpec<>(ParamMeta.of(name, ParamPresence.NULLABLE, false), codec);
    }

    // 编辑器特殊形态（气候区间等）
    public NestedSpec<T> shape(ParamShape value) {
        return new NestedSpec<>(meta.withShape(value), codec);
    }

    public NestedSpec<T> label(String suffix) {
        return new NestedSpec<>(meta.withLabel(suffix), codec);
    }

    public NestedSpec<T> hint(String suffix) {
        return new NestedSpec<>(meta.withHint(suffix), codec);
    }

    // 可空嵌套对象：返回 Optional，由调用方在 apply 时 orElse(null)
    public <O> RecordCodecBuilder<O, Optional<T>> fieldForNullable(Function<O, Optional<T>> getter) {
        return valueCodec().optionalFieldOf(name()).forGetter(getter);
    }

    @Override
    public Codec<T> valueCodec() {
        return codec;
    }

    @Override
    public boolean check(@Nullable Object value, IssueCollector issues, String fieldPath) {
        return true;
    }

    @Override
    public @Nullable Object defaultValue() {
        return null;
    }
}
