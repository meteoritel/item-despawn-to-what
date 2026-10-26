package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * 单个资源位置参数声明（如战利品表 id）。
 * <p>资源位置只是标识符，能否解析成真实资源由类型在使用处决定。</p>
 */
public record ResLocSpec(ParamMeta meta) implements ParamSpec {

    // 必填资源位置
    public static ResLocSpec required(String name) {
        return new ResLocSpec(ParamMeta.of(name, ParamPresence.REQUIRED, true));
    }

    public ResLocSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    public ResLocSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public <O> RecordCodecBuilder<O, ResourceLocation> fieldFor(Function<O, ResourceLocation> getter) {
        return valueCodec().fieldOf(name()).forGetter(getter);
    }

    @Override
    public Codec<ResourceLocation> valueCodec() {
        return ResourceLocation.CODEC;
    }

    @Override
    public boolean check(@Nullable Object value, IssueCollector issues, String fieldPath) {
        return ParamChecks.required(value, name(), issues, ParamChecks.child(fieldPath, name()));
    }

    @Override
    public @Nullable Object defaultValue() {
        return null;
    }

    private ResLocSpec withMeta(ParamMeta value) {
        return new ResLocSpec(value);
    }
}
