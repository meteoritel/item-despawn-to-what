package com.meteorite.itemdespawntowhat.core.api.param;

import org.jetbrains.annotations.Nullable;

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
        List<ParamPreset> presets) {

    public ParamMeta {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(presence, "presence");
        if (name.isBlank()) {
            throw new IllegalArgumentException("参数名不能为空");
        }
        unit = unit == null ? ParamUnit.PLAIN : unit;
        shape = shape == null ? ParamShape.DEFAULT : shape;
        presets = presets == null ? List.of() : List.copyOf(presets);
    }

    // 基础元数据：必填参数默认在编辑器里也强制填写
    static ParamMeta of(String name, ParamPresence presence, boolean editorRequired) {
        return new ParamMeta(name, presence, ParamUnit.PLAIN, ParamShape.DEFAULT, null, null, null,
                editorRequired, true, List.of());
    }

    public ParamMeta withUnit(ParamUnit value) {
        return new ParamMeta(name, presence, value, shape, labelSuffix, hintSuffix, initialValue,
                editorRequired, editorVisible, presets);
    }

    public ParamMeta withShape(ParamShape value) {
        return new ParamMeta(name, presence, unit, value, labelSuffix, hintSuffix, initialValue,
                editorRequired, editorVisible, presets);
    }

    public ParamMeta withLabel(@Nullable String value) {
        return new ParamMeta(name, presence, unit, shape, value, hintSuffix, initialValue,
                editorRequired, editorVisible, presets);
    }

    public ParamMeta withHint(@Nullable String value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, value, initialValue,
                editorRequired, editorVisible, presets);
    }

    public ParamMeta withInitial(@Nullable Object value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, value,
                editorRequired, editorVisible, presets);
    }

    public ParamMeta withEditorRequired(boolean value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue,
                value, editorVisible, presets);
    }

    /** 不在编辑器中展示该参数：仍参与编解码与校验，但界面不提供输入入口。 */
    public ParamMeta withEditorVisible(boolean value) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue,
                editorRequired, value, presets);
    }

    public ParamMeta withPresets(ParamPreset... values) {
        return new ParamMeta(name, presence, unit, shape, labelSuffix, hintSuffix, initialValue,
                editorRequired, editorVisible, List.of(values));
    }
}
