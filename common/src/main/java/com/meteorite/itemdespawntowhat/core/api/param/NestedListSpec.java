package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 嵌套对象列表参数声明（如箭雨药水效果）。
 * <p>{@code fields} 描述列表元素内部字段，供编辑器逐项渲染；codec 与递归声明校验共同约束元素值域。</p>
 */
public record NestedListSpec<T>(ParamMeta meta, Codec<T> codec, ParamList<? super T> fields) implements ParamSpec<List<T>> {

    // 可空嵌套列表：缺失解码为 null
    public static <T> NestedListSpec<T> nullable(String name, Codec<T> codec, ParamList<? super T> fields) {
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

    @Override
    public Codec<List<T>> valueCodec() {
        return codec.listOf();
    }

    @Override
    public boolean check(@Nullable List<T> value, IssueCollector issues, String fieldPath) {
        if (value == null) {
            return true;
        }
        boolean valid = true;
        String path = ParamChecks.child(fieldPath, name());
        for (int i = 0; i < value.size(); i++) {
            String itemPath = ParamChecks.index(path, i);
            T item = value.get(i);
            valid &= ParamChecks.required(item, name(), issues, itemPath);
            if (item != null) {
                valid &= fields.check(item, issues, itemPath);
            }
        }
        return valid;
    }

    @Override
    public @Nullable List<T> defaultValue() {
        return null;
    }
}
