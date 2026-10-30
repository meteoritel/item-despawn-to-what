package com.meteorite.itemdespawntowhat.core.api.param;

import org.jetbrains.annotations.Nullable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Optional;

import java.util.List;
import java.util.Objects;

/**
 * 参数的声明元数据：字段名、wire presence、语义单位、编辑器投影信息与新建初值。
 * <p>由各 spec 记录持有，类型定义在声明参数时通过 with 方法补充例外信息；默认值只服务于可省略字段。</p>
 */
public record ParamMeta(
        String name,
        ParamPresence presence,
        ParamUnit unit,
        ParamShape shape,
        @Nullable String labelSuffix,
        @Nullable String hintSuffix,
        @Nullable Object initialValue,
        boolean editorRequired,
        boolean editorVisible,
        List<ParamPreset> presets,
        ParamTier tier,
        SelectionIntent selectionIntent) {

    public ParamMeta {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(presence, "presence");
        if (name.isBlank()) {
            throw new IllegalArgumentException("参数名不能为空");
        }
        unit = unit == null ? ParamUnit.PLAIN : unit;
        shape = shape == null ? ParamShape.DEFAULT : shape;
        presets = presets == null ? List.of() : List.copyOf(presets);
        tier = tier == null ? ParamTier.PRIMARY : tier;
        selectionIntent = selectionIntent == null ? SelectionIntent.DEFAULT : selectionIntent;
    }

    /** 保持旧构造入口，未声明选择约束时使用默认意图。 */
    public ParamMeta(String name, ParamPresence presence, ParamUnit unit, ParamShape shape,
                     @Nullable String labelSuffix, @Nullable String hintSuffix, @Nullable Object initialValue,
                     boolean editorRequired, boolean editorVisible, List<ParamPreset> presets, ParamTier tier) {
        this(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue, editorRequired,
                editorVisible, presets, tier, SelectionIntent.DEFAULT);
    }

    /** 保持未声明字段层级时的原构造方式，默认归入主区。 */
    public ParamMeta(String name, ParamPresence presence, ParamUnit unit, ParamShape shape,
                     @Nullable String labelSuffix, @Nullable String hintSuffix, @Nullable Object initialValue,
                     boolean editorRequired, boolean editorVisible, List<ParamPreset> presets) {
        this(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue, editorRequired,
                editorVisible, presets, ParamTier.PRIMARY, SelectionIntent.DEFAULT);
    }

    // 基础元数据：必填参数默认在编辑器里也强制填写
    static ParamMeta of(String name, ParamPresence presence, boolean editorRequired) {
        return new ParamMeta(name, presence, ParamUnit.PLAIN, ParamShape.DEFAULT, null, null, null,
                editorRequired, true, List.of(), ParamTier.PRIMARY);
    }

    public ParamMeta withUnit(ParamUnit value) {
        return new ParamMeta(name, presence, value, shape, labelSuffix, hintSuffix, initialValue,
                editorRequired, editorVisible, presets, tier, selectionIntent);
    }

    public ParamMeta withShape(ParamShape value) {
        return new ParamMeta(name, presence, unit, value, labelSuffix, hintSuffix, initialValue,
                editorRequired, editorVisible, presets, tier, selectionIntent);
    }

    public ParamMeta withLabel(@Nullable String value) {
        return new ParamMeta(name, presence, unit, shape, value, hintSuffix, initialValue,
                editorRequired, editorVisible, presets, tier, selectionIntent);
    }

    public ParamMeta withHint(@Nullable String value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, value, initialValue,
                editorRequired, editorVisible, presets, tier, selectionIntent);
    }

    public ParamMeta withInitial(@Nullable Object value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, value,
                editorRequired, editorVisible, presets, tier, selectionIntent);
    }

    public ParamMeta withEditorRequired(boolean value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue,
                value, editorVisible, presets, tier, selectionIntent);
    }

    /** 不在编辑器中展示该参数：仍参与编解码与校验，但界面不提供输入入口。 */
    public ParamMeta withEditorVisible(boolean value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue,
                editorRequired, value, presets, tier, selectionIntent);
    }

    public ParamMeta withPresets(ParamPreset... values) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue,
                editorRequired, editorVisible, List.of(values), tier, selectionIntent);
    }

    /** 返回带指定编辑器展示层级的元数据。 */
    public ParamMeta withTier(ParamTier value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue,
                editorRequired, editorVisible, presets, value, selectionIntent);
    }

    /** 返回带指定目录选择意图的元数据。 */
    public ParamMeta withSelectionIntent(SelectionIntent value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue,
                editorRequired, editorVisible, presets, tier, value);
    }

    /** 从 wire presence 派生非空字段构件；可空字段使用 nullableField。 */
    public <V> MapCodec<V> field(Codec<V> codec, @Nullable V defaultValue) {
        return switch (presence) {
            case REQUIRED -> codec.fieldOf(name);
            case DEFAULTED -> codec.optionalFieldOf(name, Objects.requireNonNull(defaultValue));
            case NULLABLE -> throw new IllegalStateException("可空参数应使用 nullableField: " + name);
        };
    }

    /** 可空字段在 codec 层经 Optional 中转，记录构造时才落为 null。 */
    public <V> MapCodec<Optional<V>> nullableField(Codec<V> codec) {
        return codec.optionalFieldOf(name);
    }

    /** 只有可省略字段拥有缺省值，必填与可空字段返回 null。 */
    public <V> @Nullable V defaultValue(V value) {
        return presence == ParamPresence.DEFAULTED ? value : null;
    }

}
