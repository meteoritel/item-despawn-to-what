package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

/**
 * 单个嵌套对象参数声明（如气候区间或任意对象）。
 * <p>普通嵌套对象以内部字段绑定递归校验真实值；内部字段声明同时供
 * 编辑器投影固定子表单，特殊形态（如气候区间）则由专用控件承接。</p>
 */
public record NestedSpec<T>(ParamMeta meta, Codec<T> codec, ParamList<? super T> fields,
                            @Nullable T codecDefault) implements ParamSpec<T> {

    public NestedSpec {
        fields = fields == null ? ParamList.empty() : fields;
        if (meta.shape() == ParamShape.DEFAULT && fields.isEmpty()) {
            throw new IllegalArgumentException("参数 " + meta.name() + " 的嵌套对象需要字段声明");
        }
    }

    /** 可空嵌套声明保留原有构造方式。 */
    public NestedSpec(ParamMeta meta, Codec<T> codec, ParamList<? super T> fields) {
        this(meta, codec, fields, null);
    }

    /** 对象省略时采用同一份非空默认值，子字段仍由所属对象声明。 */
    public static <T> NestedSpec<T> defaulted(String name, Codec<T> codec, ParamList<? super T> fields, T value) {
        return new NestedSpec<>(ParamMeta.of(name, ParamPresence.DEFAULTED, false), codec, fields, value);
    }

    // 普通嵌套对象：可空，缺失解码为 null；字段声明供编辑器投影
    public static <T> NestedSpec<T> object(String name, Codec<T> codec, ParamList<? super T> fields) {
        return new NestedSpec<>(ParamMeta.of(name, ParamPresence.NULLABLE, false), codec, fields);
    }

    /** 气候区间：两端可空，后端与编辑器共同消费 {@link ClimateDomain} 的端点与交互域。 */
    public static <T> NestedSpec<T> climate(String name, Codec<T> codec) {
        return new NestedSpec<>(ParamMeta.of(name, ParamPresence.NULLABLE, false)
                .withShape(ParamShape.CLIMATE_RANGE), codec, ParamList.empty());
    }

    // 编辑器特殊形态（由专用控件承接，不需要内部字段声明）
    public NestedSpec<T> shape(ParamShape value) {
        return new NestedSpec<>(meta.withShape(value), codec, fields, codecDefault);
    }

    public NestedSpec<T> label(String suffix) {
        return new NestedSpec<>(meta.withLabel(suffix), codec, fields, codecDefault);
    }

    /** 设置编辑器展示层级。 */
    public NestedSpec<T> tier(ParamTier value) { return new NestedSpec<>(meta.withTier(value), codec, fields, codecDefault); }

    public NestedSpec<T> hint(String suffix) {
        return new NestedSpec<>(meta.withHint(suffix), codec, fields, codecDefault);
    }

    @Override
    public Codec<T> valueCodec() {
        return codec;
    }

    @Override
    public boolean check(@Nullable T value, IssueCollector issues, String fieldPath) {
        String path = ParamChecks.child(fieldPath, name());
        return value == null ? nullable() || ParamChecks.required(null, name(), issues, path)
                : fields.check(value, issues, path);
    }

    @Override
    public @Nullable T defaultValue() {
        return meta.defaultValue(codecDefault);
    }
}
