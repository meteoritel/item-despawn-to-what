package com.meteorite.itemdespawntowhat.client.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑器字段描述：字段名、label 本地化 key、字段类型、取值域、可空与必填、注册表、
 * 枚举组名、提示文案、子字段与预设值。
 * <p>字段顺序即表单展示顺序，因此调用方用 {@link java.util.List} 承载。
 * <p>约定：
 * <ul>
 *   <li>{@code domain}：数值类型为 {@code "min..max"}；{@link EditorFieldType#ENUM} 为逗号分隔取值列表；其余为 {@code null}。</li>
 *   <li>{@code registry}：{@link EditorFieldType#REGISTRY_ID}/{@link EditorFieldType#TAG}/{@link EditorFieldType#TAG_LIST} 的注册表名。</li>
 *   <li>{@code enumGroup}：枚举 i18n key 的组名（{@code gui.itemdespawntowhat.edit.enum.<组>.<值>}）。</li>
 * </ul>
 * <p>字段值与类型定义解耦：描述只声明形状，真正的取值仍然存放在规则 JSON 里。
 */
public record EditorField(String name, String labelKey, EditorFieldType type, @Nullable String domain,
                          boolean nullable, boolean required, @Nullable String registry,
                          @Nullable String enumGroup, @Nullable String hintKey,
                          List<EditorField> subFields, List<EditorPreset> presets) {

    public EditorField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(type, "type");
        if (name.isBlank() || labelKey.isBlank()) {
            throw new IllegalArgumentException("字段名与 label key 不能为空");
        }
        subFields = subFields == null ? List.of() : List.copyOf(subFields);
        presets = presets == null ? List.of() : List.copyOf(presets);
    }

    // ---- 构造工厂 ----

    // 必填单行文本
    public static EditorField text(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.TEXT, null, false, true, null, null, null, null, null);
    }

    // 可选单行文本
    public static EditorField optionalText(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.TEXT, null, true, false, null, null, null, null, null);
    }

    // 可选多行文本
    public static EditorField longText(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.LONG_TEXT, null, true, false, null, null, null, null, null);
    }

    // 必填整数，带上下界
    public static EditorField integer(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.INTEGER, min + ".." + max, false, true, null, null, null, null, null);
    }

    // 可空整数（留空表示不约束），带上下界
    public static EditorField optionalInteger(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.INTEGER, min + ".." + max, true, false, null, null, null, null, null);
    }

    // 刻数：0 显示「立即」
    public static EditorField ticks(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.TICKS, min + ".." + max, false, true, null, null, null, null, null);
    }

    // 必填小数，带上下界
    public static EditorField decimal(String name, String labelKey, double min, double max) {
        return create(name, labelKey, EditorFieldType.DECIMAL, min + ".." + max, false, true, null, null, null, null, null);
    }

    // 概率字段：JSON 0..1，界面 0..100 百分比
    public static EditorField percent(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.PERCENT, "0.0..1.0", false, true, null, null, null, null, null);
    }

    // 药水等级字段：JSON amplifier，界面 1..255 罗马数字
    public static EditorField amplifier(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.AMPLIFIER, "1..255", false, true, null, null, null, null, null);
    }

    // 布尔开关
    public static EditorField bool(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.BOOLEAN, null, false, true, null, null, null, null, null);
    }

    // 枚举字段，取值由 values 给出（无枚举组名时直接用域值列表）
    public static EditorField enumOf(String name, String labelKey, String... values) {
        return create(name, labelKey, EditorFieldType.ENUM, String.join(",", values), false, true, null, null, null, null, null);
    }

    // 枚举字段（带 i18n 组名；与无组名重载区分，方法名不同避免可变参数歧义）
    public static EditorField enumIn(String name, String labelKey, String enumGroup, String... values) {
        return create(name, labelKey, EditorFieldType.ENUM, String.join(",", values), false, true, null, enumGroup, null, null, null);
    }

    // 注册表 id 字段（不允许标签）
    public static EditorField registryId(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.REGISTRY_ID, null, false, true, registry, null, null, null, null);
    }

    // 可空注册表 id 字段
    public static EditorField optionalRegistryId(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.REGISTRY_ID, null, true, false, registry, null, null, null, null);
    }

    // 可带标签的 id 字段
    public static EditorField tag(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG, null, false, true, registry, null, null, null, null);
    }

    // 可空、可带标签的 id 字段
    public static EditorField optionalTag(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG, null, true, false, registry, null, null, null, null);
    }

    // 可带标签的 id 列表
    public static EditorField tagList(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG_LIST, null, true, false, registry, null, null, null, null);
    }

    // 纯资源位置列表（不给标签模式）
    public static EditorField rlList(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.RL_LIST, null, true, false, registry, null, null, null, null);
    }

    // 字符串列表
    public static EditorField stringList(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.STRING_LIST, null, true, false, null, null, null, null, null);
    }

    // 资源位置字段（不允许标签）
    public static EditorField resourceLocation(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.RESOURCE_LOCATION, null, false, true, null, null, null, null, null);
    }

    // 气候区间字段
    public static EditorField climateRange(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.CLIMATE_RANGE, "-1.0..1.0", true, false, null, null, null, null, null);
    }

    // 子列表字段（对象数组，字段定义由 subFields 给出）
    public static EditorField subList(String name, String labelKey, EditorField... subFields) {
        return create(name, labelKey, EditorFieldType.SUBLIST, null, true, false, null, null, null, List.of(subFields), null);
    }

    // 条件树字段
    public static EditorField conditionTree(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.CONDITION_TREE, null, true, false, null, null, null, null, null);
    }

    // 只读说明行（不写入 JSON，label key 即说明文案）
    public static EditorField note(String labelKey) {
        return create("__note", labelKey, EditorFieldType.NOTE, null, true, false, null, null, null, null, null);
    }

    // 原始 JSON 只读展示（第三方类型回退）
    public static EditorField rawJson() {
        return create("__raw_json", "gui.itemdespawntowhat.edit.field.raw_json", EditorFieldType.RAW_JSON,
                null, true, false, null, null, null, null, null);
    }

    private static EditorField create(String name, String labelKey, EditorFieldType type, @Nullable String domain,
                                      boolean nullable, boolean required, @Nullable String registry,
                                      @Nullable String enumGroup, @Nullable String hintKey,
                                      @Nullable List<EditorField> subFields, @Nullable List<EditorPreset> presets) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey, subFields, presets);
    }

    // ---- 派生 ----

    // 变为可空字段
    public EditorField optional() {
        return new EditorField(name, labelKey, type, domain, true, false, registry, enumGroup, hintKey, subFields, presets);
    }

    // 变为必填字段
    public EditorField asRequired() {
        return new EditorField(name, labelKey, type, domain, false, true, registry, enumGroup, hintKey, subFields, presets);
    }

    // 附加提示文案 key（显示在字段下方或 tooltip）
    public EditorField withHint(String key) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, key, subFields, presets);
    }

    // 附加预设值按钮
    public EditorField withPresets(EditorPreset... values) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, List.of(values));
    }

    // 枚举取值列表
    public List<String> enumValues() {
        if (type != EditorFieldType.ENUM || domain == null || domain.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : domain.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    // 数值下界（解析失败返回 fallback）
    public int intMin(int fallback) {
        double value = doubleMin(fallback);
        return (int) Math.round(value);
    }

    // 数值上界
    public int intMax(int fallback) {
        double value = doubleMax(fallback);
        return (int) Math.round(value);
    }

    // 数值下界（小数）
    public double doubleMin(double fallback) {
        String[] parts = numericDomain();
        if (parts == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(parts[0]);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    // 数值上界（小数）
    public double doubleMax(double fallback) {
        String[] parts = numericDomain();
        if (parts == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(parts[1]);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    // 是否有有效数值域
    public boolean hasNumericDomain() {
        return numericDomain() != null;
    }

    private @Nullable String[] numericDomain() {
        if (domain == null) {
            return null;
        }
        int index = domain.indexOf("..");
        if (index <= 0) {
            return null;
        }
        return new String[]{domain.substring(0, index), domain.substring(index + 2)};
    }
}
