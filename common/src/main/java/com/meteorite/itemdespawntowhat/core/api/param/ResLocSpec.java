package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 单个资源位置参数声明（如战利品表 id）。
 * <p>资源位置只是标识符，能否解析成真实资源由类型在使用处决定。</p>
 */
public record ResLocSpec(ParamMeta meta) implements ParamSpec<ResourceLocation> {

    // 必填资源位置
    public static ResLocSpec required(String name) {
        return new ResLocSpec(ParamMeta.of(name, ParamPresence.REQUIRED, true));
    }

    public ResLocSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    /** 设置编辑器展示层级。 */
    public ResLocSpec tier(ParamTier value) { return withMeta(meta.withTier(value)); }

    public ResLocSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    @Override
    public Codec<ResourceLocation> valueCodec() {
        return ResourceLocation.CODEC;
    }

    @Override
    public boolean check(@Nullable ResourceLocation value, IssueCollector issues, String fieldPath) {
        return ParamChecks.required(value, name(), issues, ParamChecks.child(fieldPath, name()));
    }

    @Override
    public @Nullable ResourceLocation defaultValue() {
        return null;
    }

    private ResLocSpec withMeta(ParamMeta value) {
        return new ResLocSpec(value);
    }
}
