package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * 布尔开关参数声明：缺省按默认值补齐，没有取值范围。
 * <p>布尔字段在编辑器里始终需要明确选择，因此默认按必填投影。</p>
 */
public record BoolSpec(ParamMeta meta, boolean codecDefault) implements ParamSpec {

    // 可省略布尔：缺失按默认值补齐
    public static BoolSpec defaulted(String name, boolean defaultValue) {
        return new BoolSpec(ParamMeta.of(name, ParamPresence.DEFAULTED, true), defaultValue);
    }

    public BoolSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    public BoolSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public BoolSpec initial(boolean value) {
        return withMeta(meta.withInitial(value));
    }

    public BoolSpec editorRequired(boolean value) {
        return withMeta(meta.withEditorRequired(value));
    }

    public <O> RecordCodecBuilder<O, Boolean> fieldFor(Function<O, Boolean> getter) {
        return valueCodec().optionalFieldOf(name(), codecDefault).forGetter(getter);
    }

    @Override
    public Codec<Boolean> valueCodec() {
        return Codec.BOOL;
    }

    @Override
    public boolean check(@Nullable Object value, IssueCollector issues, String fieldPath) {
        return true;
    }

    @Override
    public @Nullable Object defaultValue() {
        return presence() == ParamPresence.DEFAULTED ? codecDefault : null;
    }

    private BoolSpec withMeta(ParamMeta value) {
        return new BoolSpec(value, codecDefault);
    }
}
