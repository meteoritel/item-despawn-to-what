package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

/**
 * 单个注册表引用参数声明（物品、方块、实体、流体等）。
 * <p>注册表名只用于编辑器选择器的候选范围；引用是否真实存在由类型在 validateParams 里查 RefChecks。</p>
 */
public record RefSpec(ParamMeta meta, String registry) implements ParamSpec<TaggedId> {

    // 必填引用
    public static RefSpec required(String name, String registry) {
        return new RefSpec(ParamMeta.of(name, ParamPresence.REQUIRED, true), registry);
    }

    // 可空引用：缺失解码为 null
    public static RefSpec nullable(String name, String registry) {
        return new RefSpec(ParamMeta.of(name, ParamPresence.NULLABLE, false), registry);
    }

    public RefSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    public RefSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public RefSpec initial(TaggedId value) {
        return withMeta(meta.withInitial(value));
    }

    public RefSpec editorRequired(boolean value) {
        return withMeta(meta.withEditorRequired(value));
    }

    /** 不在编辑器中展示：仍参与编解码与校验 */
    public RefSpec hidden() {
        return withMeta(meta.withEditorVisible(false));
    }

    // 可空引用：返回 Optional，由调用方在 apply 时 orElse(null)

    @Override
    public Codec<TaggedId> valueCodec() {
        return TaggedId.CODEC;
    }

    @Override
    public boolean check(@Nullable TaggedId value, IssueCollector issues, String fieldPath) {
        if (!required()) {
            return true;
        }
        return ParamChecks.required(value, name(), issues, ParamChecks.child(fieldPath, name()));
    }

    @Override
    public @Nullable TaggedId defaultValue() {
        return null;
    }

    private RefSpec withMeta(ParamMeta value) {
        return new RefSpec(value, registry);
    }
}
