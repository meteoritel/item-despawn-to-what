package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 枚举参数声明：字段名、wire 取值集合与编辑器枚举组名记录在一处。
 * <p>codec 仍由类型自己传入（沿用 {@code EnumCodecs.lowerCase}），声明只记录可选值与默认值，
 * 编辑器据 {@link #wireValues()} 生成选项、据 {@link #enumGroup()} 取组标签。</p>
 */
public record EnumSpec<E extends Enum<E>>(ParamMeta meta, Codec<E> codec, List<E> values, String enumGroup,
                                        @Nullable E codecDefault) implements ParamSpec {

    public EnumSpec {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("参数 " + meta.name() + " 的枚举取值不能为空");
        }
    }

    // 必填枚举：wire 上必须出现
    @SafeVarargs
    public static <E extends Enum<E>> EnumSpec<E> required(String name, Codec<E> codec, String enumGroup,
                                                           E... values) {
        return new EnumSpec<>(ParamMeta.of(name, ParamPresence.REQUIRED, true), codec, List.of(values), enumGroup,
                null);
    }

    // 可省略枚举：缺失按默认值补齐
    @SafeVarargs
    public static <E extends Enum<E>> EnumSpec<E> defaulted(String name, Codec<E> codec, E defaultValue,
                                                            String enumGroup, E... values) {
        return new EnumSpec<>(ParamMeta.of(name, ParamPresence.DEFAULTED, true), codec, List.of(values), enumGroup,
                defaultValue);
    }

    public EnumSpec<E> label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    public EnumSpec<E> hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public EnumSpec<E> initial(E value) {
        return withMeta(meta.withInitial(value));
    }

    public EnumSpec<E> editorRequired(boolean value) {
        return withMeta(meta.withEditorRequired(value));
    }

    // JSON 取值：枚举名的小写形式
    public List<String> wireValues() {
        return values.stream().map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
    }

    public <O> RecordCodecBuilder<O, E> fieldFor(Function<O, E> getter) {
        return presence() == ParamPresence.REQUIRED
                ? codec.fieldOf(name()).forGetter(getter)
                : codec.optionalFieldOf(name(), codecDefault).forGetter(getter);
    }

    @Override
    public boolean check(@Nullable Object value, IssueCollector issues, String fieldPath) {
        if (!required()) {
            return true;
        }
        return ParamChecks.required(value, name(), issues, ParamChecks.child(fieldPath, name()));
    }

    @Override
    public @Nullable Object defaultValue() {
        return presence() == ParamPresence.DEFAULTED ? codecDefault : null;
    }

    private EnumSpec<E> withMeta(ParamMeta value) {
        return new EnumSpec<>(value, codec, values, enumGroup, codecDefault);
    }
}
