package com.meteorite.itemdespawntowhat.core.api.param;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.ParamChecks;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * 注册表引用列表参数声明：列表字段缺省为空表，可标记为非空必填。
 * <p>列表内每一项是否真实存在由类型在 validateParams 里查 RefChecks。</p>
 */
public record RefListSpec(ParamMeta meta, String registry, boolean nonEmpty) implements ParamSpec {

    // 可省略列表：缺失为空表
    public static RefListSpec defaultedEmpty(String name, String registry) {
        return new RefListSpec(ParamMeta.of(name, ParamPresence.DEFAULTED, false), registry, false);
    }

    // 标记为语义必填（wire 上仍可省略，但空表不合法）
    // 语义要求非空；编辑器仍按可选展示（由 validateParams 按模式判定）
    public RefListSpec enforceNonEmpty() {
        return new RefListSpec(meta, registry, true);
    }

    // 语义与编辑器都要求非空
    public RefListSpec requireNonEmpty() {
        return new RefListSpec(meta.withEditorRequired(true), registry, true);
    }

    public RefListSpec label(String suffix) {
        return withMeta(meta.withLabel(suffix));
    }

    public RefListSpec hint(String suffix) {
        return withMeta(meta.withHint(suffix));
    }

    public RefListSpec initial(List<TaggedId> value) {
        return withMeta(meta.withInitial(value));
    }

    public <O> RecordCodecBuilder<O, List<TaggedId>> fieldFor(Function<O, List<TaggedId>> getter) {
        return TaggedId.CODEC.listOf().optionalFieldOf(name(), List.of()).forGetter(getter);
    }

    @Override
    public boolean check(@Nullable Object value, IssueCollector issues, String fieldPath) {
        if (!nonEmpty) {
            return true;
        }
        return ParamChecks.notEmpty((List<?>) value, name(), issues, ParamChecks.child(fieldPath, name()));
    }

    @Override
    public @Nullable Object defaultValue() {
        return List.of();
    }

    private RefListSpec withMeta(ParamMeta value) {
        return new RefListSpec(value, registry, nonEmpty);
    }
}