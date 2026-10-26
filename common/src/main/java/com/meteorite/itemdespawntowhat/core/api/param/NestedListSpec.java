package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 嵌套对象列表参数声明（如箭雨药水效果）。
 * <p>{@code fields} 描述列表元素内部字段，供编辑器逐项渲染；元素内部校验由它自己的 codec 负责。</p>
 */
public record NestedListSpec<T>(ParamMeta meta, Codec<T> codec, ParamList fields) implements ParamSpec {

    // 可空嵌套列表：缺失解码为 null
    public static <T> NestedListSpec<T> nullable(String name, Codec<T> codec, ParamList fields) {
        return new NestedListSpec<>(ParamMeta.of(name, ParamPresence.NULLABLE, false), codec, fields);
    }

    public NestedListSpec<T> label(String suffix) {
        return new NestedListSpec<>(meta.withLabel(suffix), codec, fields);
    }

    public NestedListSpec<T> hint(String suffix) {
        return new NestedListSpec<>(meta.withHint(suffix), codec, fields);
    }

    public NestedListSpec<T> initial(List<T> value) {
        return new NestedListSpec<>(meta.withInitial(value), codec, fields);
    }

    public NestedListSpec<T> editorRequired(boolean value) {
        return new NestedListSpec<>(meta.withEditorRequired(value), codec, fields);
    }

    // 可空嵌套列表：返回 Optional，由调用方在 apply 时 orElse(null)
    public <O> RecordCodecBuilder<O, Optional<List<T>>> fieldForNullable(
            Function<O, Optional<List<T>>> getter) {
        return codec.listOf().optionalFieldOf(name()).forGetter(getter);
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
