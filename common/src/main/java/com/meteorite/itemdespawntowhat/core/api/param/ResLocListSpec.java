package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 资源位置列表参数声明：列表字段缺省为空表，可标记为非空必填。
 * <p>维度列表用它声明（{@code dimension} 条件），编辑器按注册表名限制候选项。</p>
 */
public record ResLocListSpec(ParamMeta meta, String registry, boolean nonEmpty) implements ParamSpec<List<ResourceLocation>> {

    // 可省略列表：缺失为空表
    public static ResLocListSpec defaultedEmpty(String name, String registry) {
        return new ResLocListSpec(ParamMeta.of(name, ParamPresence.DEFAULTED, false), registry, false);
    }

    // 标记为非空必填
    public ResLocListSpec requireNonEmpty() {
        return new ResLocListSpec(meta.withEditorRequired(true), registry, true);
    }

    public ResLocListSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    /** 设置编辑器展示层级。 */
    public ResLocListSpec tier(ParamTier value) { return withMeta(meta.withTier(value)); }

    public ResLocListSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public ResLocListSpec initial(List<ResourceLocation> value) {
        return withMeta(meta.withInitial(value));
    }

    @Override
    public Codec<List<ResourceLocation>> valueCodec() {
        return ResourceLocation.CODEC.listOf();
    }

    @Override
    public boolean check(@Nullable List<ResourceLocation> value, IssueCollector issues, String fieldPath) {
        if (!nonEmpty) {
            return true;
        }
        return ParamChecks.notEmpty(value, name(), issues, ParamChecks.child(fieldPath, name()));
    }

    @Override
    public List<ResourceLocation> defaultValue() {
        return List.of();
    }

    private ResLocListSpec withMeta(ParamMeta value) {
        return new ResLocListSpec(value, registry, nonEmpty);
    }
}
