package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.core.api.param.BoolSpec;
import com.meteorite.itemdespawntowhat.core.api.param.DoubleSpec;
import com.meteorite.itemdespawntowhat.core.api.param.EnumSpec;
import com.meteorite.itemdespawntowhat.core.api.param.FloatSpec;
import com.meteorite.itemdespawntowhat.core.api.param.IntSpec;
import com.meteorite.itemdespawntowhat.core.api.param.MapSpec;
import com.meteorite.itemdespawntowhat.core.api.param.NestedListSpec;
import com.meteorite.itemdespawntowhat.core.api.param.NestedSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamList;
import com.meteorite.itemdespawntowhat.core.api.param.ParamShape;
import com.meteorite.itemdespawntowhat.core.api.param.ParamSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ParamUnit;
import com.meteorite.itemdespawntowhat.core.api.param.RefListSpec;
import com.meteorite.itemdespawntowhat.core.api.param.RefSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ResLocListSpec;
import com.meteorite.itemdespawntowhat.core.api.param.ResLocSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 core 侧的类型参数声明投影成编辑器字段描述。
 * <p>字段顺序、层级、label/hint key、可空与必填、取值域与滑块窗口都由同一份 {@link ParamList} 推导，
 * 客户端不再逐类型手写字段表；因此新增参数只需在 core 侧声明一次。</p>
 * <p>本地 SPI 类型沿用相同投影入口；空参数声明由 {@link EditorRegistries} 回退为只读 JSON 摘要。</p>
 */
public final class ParamProjection {

    // 字段 label/hint key 前缀
    private static final String FIELD = TypeLabels.UI_PREFIX + "field.";

    private ParamProjection() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 投影整组参数；顺序即声明顺序，也就是表单展示顺序
    public static List<EditorField> fields(ParamList<?> params, ResourceLocation typeId) {
        List<EditorField> fields = new ArrayList<>();
        for (ParamSpec<?> spec : params.all()) {
            if (!spec.editorVisible()) {
                continue;
            }
            fields.add(field(spec, typeId));
        }
        return fields;
    }

    // 投影单个参数
    public static EditorField field(ParamSpec<?> spec, ResourceLocation typeId) {
        String label = labelKey(spec, typeId);
        EditorField field = switch (spec) {
            case IntSpec intSpec -> intField(intSpec, label);
            case FloatSpec floatSpec -> decimalField(floatSpec.name(), label, floatSpec.min(), floatSpec.max(),
                    floatSpec.editorRequired());
            case DoubleSpec doubleSpec -> doubleField(doubleSpec, label);
            case BoolSpec boolSpec -> requiredByEditor(
                    EditorField.bool(boolSpec.name(), label), boolSpec.editorRequired());
            case EnumSpec<?> enumSpec -> enumField(enumSpec, label);
            case RefSpec refSpec -> refField(refSpec, label);
            case RefListSpec refListSpec -> requiredByEditor(
                    EditorField.tagList(refListSpec.name(), label, refListSpec.registry()),
                    refListSpec.editorRequired());
            case ResLocSpec resLocSpec -> EditorField.resourceLocation(resLocSpec.name(), label);
            case ResLocListSpec resLocListSpec -> requiredByEditor(
                    EditorField.rlList(resLocListSpec.name(), label, resLocListSpec.registry()),
                    resLocListSpec.editorRequired());
            case MapSpec mapSpec -> EditorField.itemCounts(mapSpec.name(), label, mapSpec.keyRegistry(),
                    mapSpec.minValue(), mapSpec.maxValue());
            case NestedSpec<?> nestedSpec -> nestedField(nestedSpec, label, typeId);
            case NestedListSpec<?> nestedListSpec -> nestedListField(nestedListSpec, label, typeId);
        };
        field = field.withTier(spec.tier()).withDefaultValue(BuiltinEditorDefaults.defaultValue(spec));
        if (spec.hintSuffix() != null) {
            field = field.withHint(FIELD + spec.hintSuffix());
        }
        if (!spec.presets().isEmpty()) {
            field = field.withPresets(spec.presets().stream()
                    .map(preset -> new EditorPreset(preset.value(), TypeLabels.UI_PREFIX + preset.labelSuffix()))
                    .toArray(EditorPreset[]::new));
        }
        return field;
    }

    // 字段 label key：显式后缀优先，否则按「类型片段.字段名」推导
    public static String labelKey(ParamSpec<?> spec, ResourceLocation typeId) {
        String suffix = spec.labelSuffix() != null
                ? spec.labelSuffix()
                : TypeLabels.typeSegment(typeId) + "." + spec.name();
        return FIELD + suffix;
    }

    // 整数：可空字段保留留空语义；刻数用刻数控件；有常用窗口的用滑块；其余按单位选形式
    private static EditorField intField(IntSpec spec, String label) {
        boolean hasWindow = spec.windowMin() != null && spec.windowMax() != null;
        if (spec.nullable()) {
            NumericDomain domain = hasWindow
                    ? NumericDomain.integers(spec.min(), spec.max(), spec.windowMin(), spec.windowMax())
                    : NumericDomain.integers(spec.min(), spec.max());
            return EditorField.optionalInteger(spec.name(), label, spec.min(), spec.max()).withNumbers(domain);
        }
        EditorField field;
        if (spec.unit() == ParamUnit.TICKS) {
            field = EditorField.ticks(spec.name(), label, spec.min(), spec.max());
            if (hasWindow) {
                field = field.withNumbers(NumericDomain.integers(spec.min(), spec.max(),
                        spec.windowMin(), spec.windowMax()));
            }
        } else if (hasWindow) {
            field = EditorField.integerSlider(spec.name(), label, spec.min(), spec.max(),
                    spec.windowMin(), spec.windowMax());
        } else if (spec.unit() == ParamUnit.LEVEL) {
            field = EditorField.amplifier(spec.name(), label);
        } else {
            field = EditorField.integer(spec.name(), label, spec.min(), spec.max());
        }
        return requiredByEditor(field, spec.editorRequired());
    }

    // 双精度：百分比字段按声明域换算显示，其余用小数
    private static EditorField doubleField(DoubleSpec spec, String label) {
        EditorField field = spec.unit() == ParamUnit.PERCENT
                ? EditorField.percent(spec.name(), label, spec.min(), spec.max())
                : EditorField.decimal(spec.name(), label, spec.min(), spec.max());
        return requiredByEditor(field, spec.editorRequired());
    }

    private static EditorField decimalField(String name, String label, float min, float max, boolean required) {
        return requiredByEditor(EditorField.decimal(name, label, min, max), required);
    }

    // 枚举：wire 取值（小写名）作为选项，组名决定每项文案
    private static EditorField enumField(EnumSpec<?> spec, String label) {
        EditorField field = EditorField.enumIn(spec.name(), label, spec.enumGroup(),
                spec.wireValues().toArray(String[]::new));
        return requiredByEditor(field, spec.editorRequired());
    }

    // 单个注册表引用：可空字段用可留空选择器，再按声明的编辑器必填标记调整
    private static EditorField refField(RefSpec spec, String label) {
        EditorField field = spec.nullable()
                ? EditorField.optionalTag(spec.name(), label, spec.registry())
                : EditorField.tag(spec.name(), label, spec.registry());
        return requiredByEditor(field, spec.editorRequired());
    }

    // 嵌套对象：气候区间用专用控件，普通对象按自身字段声明投影为固定子表单
    private static EditorField nestedField(NestedSpec<?> spec, String label, ResourceLocation typeId) {
        if (spec.shape() == ParamShape.CLIMATE_RANGE) {
            return EditorField.climateRange(spec.name(), label);
        }
        if (spec.fields().isEmpty()) {
            throw new IllegalArgumentException("参数 " + spec.name() + " 的嵌套对象缺少编辑器字段声明");
        }
        List<EditorField> subFields = fields(spec.fields(), typeId);
        return EditorField.object(spec.name(), label, subFields.toArray(EditorField[]::new));
    }

    // 嵌套对象列表：子字段沿用元素自己的参数声明
    private static EditorField nestedListField(NestedListSpec<?> spec, String label, ResourceLocation typeId) {
        List<EditorField> subFields = fields(spec.fields(), typeId);
        return requiredByEditor(EditorField.subList(spec.name(), label, subFields.toArray(EditorField[]::new)),
                spec.editorRequired());
    }

    // 按声明的编辑器必填标记调整字段
    private static EditorField requiredByEditor(EditorField field, boolean required) {
        return required ? field.asRequired() : field.optional();
    }
}
