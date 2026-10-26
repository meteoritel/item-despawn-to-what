package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * 单个嵌套对象参数声明（如气候区间或任意对象）。
 * <p>嵌套对象内部字段的取值域由它自己的 codec 与校验负责；普通对象还需声明内部字段，
 * 编辑器据此投影固定子表单，特殊形态（如气候区间）则由专用控件承接。</p>
 */
public record NestedSpec<T>(ParamMeta meta, Codec<T> codec, ParamList fields) implements ParamSpec {

    public NestedSpec {
        fields = fields == null ? ParamList.EMPTY : fields;
        if (meta.shape() == ParamShape.DEFAULT && fields.isEmpty()) {
            throw new IllegalArgumentException("参数 " + meta.name() + " 的嵌套对象需要字段声明");
        }
    }

    // 普通嵌套对象：可空，缺失解码为 null；字段声明供编辑器投影
    public static <T> NestedSpec<T> object(String name, Codec<T> codec, ParamList fields) {
        return new NestedSpec<>(ParamMeta.of(name, ParamPresence.NULLABLE, false), codec, fields);
    }

    /** 气候区间：两端可空，后端与编辑器共同消费 {@link ClimateDomain} 的端点与交互域。 */
    public static <T> NestedSpec<T> climate(String name, Codec<T> codec) {
        return new NestedSpec<>(ParamMeta.of(name, ParamPresence.NULLABLE, false)
                .withShape(ParamShape.CLIMATE_RANGE), codec, ParamList.EMPTY);
    }

    // 编辑器特殊形态（由专用控件承接，不需要内部字段声明）
    public NestedSpec<T> shape(ParamShape value) {
        return new NestedSpec<>(meta.withShape(value), codec, fields);
    }

    public NestedSpec<T> label(String suffix) {
        return new NestedSpec<>(meta.withLabel(suffix), codec, fields);
    }

    public NestedSpec<T> hint(String suffix) {
        return new NestedSpec<>(meta.withHint(suffix), codec, fields);
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
