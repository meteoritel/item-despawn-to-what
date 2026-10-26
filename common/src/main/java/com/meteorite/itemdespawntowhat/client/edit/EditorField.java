package com.meteorite.itemdespawntowhat.client.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑器字段描述：字段名、label 本地化 key、字段类型、取值域、可空与必填、注册表、
 * 枚举组名、提示文案、子字段、预设值，以及数值字段的域元数据。
 * <p>字段顺序即表单展示顺序，因此调用方用 {@link java.util.List} 承载。
 * <p>约定：
 * <ul>
 *   <li>{@code domain}：数值类型为 {@code "min..max"}；{@link EditorFieldType#ENUM} 为逗号分隔取值列表；其余为 {@code null}。</li>
 *   <li>{@code registry}：{@link EditorFieldType#REGISTRY_ID}/{@link EditorFieldType#TAG}/{@link EditorFieldType#TAG_LIST} 的注册表名。</li>
 *   <li>{@code enumGroup}：枚举 i18n key 的组名（{@code gui.itemdespawntowhat.edit.enum.<组>.<值>}）。</li>
 *   <li>{@code numbers}：数值字段的「后端合法域 / 滑块常用窗口 / 步长 / 显示精度」元数据（见 {@link NumericDomain}），非数值字段为 {@code null}。</li>
 *   <li>{@code displayPrecision}：数值文本回填精度；{@code -1} 表示按最短往返表示回填、不做舍入（概率必须原始精度往返）。</li>
 * </ul>
 * <p>字段值与类型定义解耦：描述只声明形状，真正的取值仍然存放在规则 JSON 里。
 */
public record EditorField(String name, String labelKey, EditorFieldType type, @Nullable String domain,
                          boolean nullable, boolean required, @Nullable String registry,
                          @Nullable String enumGroup, @Nullable String hintKey,
                          List<EditorField> subFields, List<EditorPreset> presets,
                          @Nullable NumericDomain numbers, int displayPrecision) {

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

    // 兼容构造：不含数值域元数据（等价于无 numbers、最短往返显示）
    public EditorField(String name, String labelKey, EditorFieldType type, @Nullable String domain,
                       boolean nullable, boolean required, @Nullable String registry,
                       @Nullable String enumGroup, @Nullable String hintKey,
                       List<EditorField> subFields, List<EditorPreset> presets) {
        this(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey, subFields, presets,
                null, -1);
    }

    // ---- 构造工厂 ----

    // 必填单行文本
    public static EditorField text(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.TEXT, null, false, true, null, null, null, null);
    }

    // 可选单行文本
    public static EditorField optionalText(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.TEXT, null, true, false, null, null, null, null);
    }

    // 可选多行文本
    public static EditorField longText(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.LONG_TEXT, null, true, false, null, null, null, null);
    }

    // 必填整数，带后端合法上下界（滑块窗口默认同域）
    public static EditorField integer(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.INTEGER, min + ".." + max, false, true, null, null,
                null, NumericDomain.integers(min, max));
    }

    // 可空整数（留空表示不约束），带后端合法上下界
    public static EditorField optionalInteger(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.INTEGER, min + ".." + max, true, false, null, null,
                null, NumericDomain.integers(min, max));
    }

    // 整数字段：后端合法域与滑块常用窗口分开记录（窗口不是提交硬限额）
    public static EditorField integerSlider(String name, String labelKey, int min, int max,
                                            int windowMin, int windowMax) {
        return create(name, labelKey, EditorFieldType.INTEGER, min + ".." + max, false, true, null, null,
                null, NumericDomain.integers(min, max, windowMin, windowMax));
    }

    // 注册表引用各自保存数量，滑条默认显示常用范围 1..64；注册表决定候选与展示名。
    public static EditorField itemCounts(String name, String labelKey, String registry, int min, int max) {
        return create(name, labelKey, EditorFieldType.ITEM_COUNTS, min + ".." + max,
                true, false, registry, null, null,
                NumericDomain.integers(min, max, min, Math.min(max, 64)));
    }

    // 物品数量字段：沿用物品注册表提示
    public static EditorField itemCounts(String name, String labelKey, int min, int max) {
        return itemCounts(name, labelKey, "minecraft:item", min, max);
    }

    // 刻数：0 显示「立即」；界面允许按秒显示，但 JSON 仍是完整刻数
    public static EditorField ticks(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.TICKS, min + ".." + max, false, true, null, null,
                null, NumericDomain.integers(min, max));
    }

    // 必填小数，带上下界（默认步长 0.1 / 精细 0.01）
    public static EditorField decimal(String name, String labelKey, double min, double max) {
        return decimal(name, labelKey, min, max, 0.1D, 0.01D);
    }

    // 必填小数，带上下界与显式步长
    public static EditorField decimal(String name, String labelKey, double min, double max,
                                      double step, double shiftStep) {
        return create(name, labelKey, EditorFieldType.DECIMAL, min + ".." + max, false, true, null, null,
                null, NumericDomain.decimals(min, max, step, shiftStep));
    }

    // 百分比字段：JSON 按声明域取值，界面显示原值 ×100；显示按原始精度往返，不做舍入
    public static EditorField percent(String name, String labelKey, double min, double max) {
        return percent(name, labelKey, min, max, 0.01D, 0.001D);
    }

    // 百分比字段（显式步长档）
    public static EditorField percent(String name, String labelKey, double min, double max,
                                      double step, double shiftStep) {
        return create(name, labelKey, EditorFieldType.PERCENT, min + ".." + max, false, true, null, null,
                null, NumericDomain.decimals(min, max, step, shiftStep));
    }

    // 药水等级字段：JSON 存 amplifier（0 起算，后端 0..255），界面显示等级 1..256
    public static EditorField amplifier(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.AMPLIFIER, FieldNumbers.MIN_LEVEL + ".." + FieldNumbers.MAX_LEVEL,
                false, true, null, null, null,
                NumericDomain.integers(0, FieldNumbers.MAX_AMPLIFIER, 0, FieldNumbers.MAX_AMPLIFIER));
    }

    // 布尔开关
    public static EditorField bool(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.BOOLEAN, null, false, true, null, null, null, null);
    }

    // 枚举字段（带 i18n 组名）
    public static EditorField enumIn(String name, String labelKey, String enumGroup, String... values) {
        return create(name, labelKey, EditorFieldType.ENUM, String.join(",", values), false, true, null, enumGroup,
                null, null);
    }

    // 可带标签的 id 字段
    public static EditorField tag(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG, null, false, true, registry, null, null, null);
    }

    // 可空、可带标签的 id 字段
    public static EditorField optionalTag(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG, null, true, false, registry, null, null, null);
    }

    // 可带标签的 id 列表
    public static EditorField tagList(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG_LIST, null, true, false, registry, null, null, null);
    }

    // 纯资源位置列表（不给标签模式）
    public static EditorField rlList(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.RL_LIST, null, true, false, registry, null, null, null);
    }

    // 资源位置字段（不允许标签）
    public static EditorField resourceLocation(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.RESOURCE_LOCATION, null, false, true, null, null, null, null);
    }

    // 气候区间字段：端点可省略、域 -1..1、窄步长 0.1 / 精细 0.01
    public static EditorField climateRange(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.CLIMATE_RANGE, "-1.0..1.0", true, false, null, null,
                null, NumericDomain.decimals(-1.0D, 1.0D, 0.1D, 0.01D));
    }

    // 子列表字段（对象数组，字段定义由 subFields 给出）
    public static EditorField subList(String name, String labelKey, EditorField... subFields) {
        return create(name, labelKey, EditorFieldType.SUBLIST, null, true, false, null, null, List.of(subFields), null);
    }

    // 单个嵌套对象字段：subFields 描述对象内部字段
    public static EditorField object(String name, String labelKey, EditorField... subFields) {
        return create(name, labelKey, EditorFieldType.OBJECT, null, true, false, null, null,
                List.of(subFields), null);
    }

    // 条件树字段
    public static EditorField conditionTree(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.CONDITION_TREE, null, true, false, null, null, null, null);
    }

    // 只读说明行（不写入 JSON，label key 即说明文案）
    public static EditorField note(String labelKey) {
        return create("__note", labelKey, EditorFieldType.NOTE, null, true, false, null, null, null, null);
    }

    // 原始 JSON 只读展示（第三方类型回退）
    public static EditorField rawJson() {
        return create("__raw_json", TypeLabels.UI_PREFIX + "field.raw_json", EditorFieldType.RAW_JSON,
                null, true, false, null, null, null, null);
    }

    private static EditorField create(String name, String labelKey, EditorFieldType type, @Nullable String domain,
                                      boolean nullable, boolean required, @Nullable String registry,
                                      @Nullable String enumGroup, @Nullable List<EditorField> subFields,
                                      @Nullable NumericDomain numbers) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, null,
                subFields, null, numbers, -1);
    }

    // ---- 分类谓词 ----

    // 调用方按语义提问，不再自行 switch EditorFieldType；新增取值只需在谓词里补一处。

    public boolean isNote() {
        return type == EditorFieldType.NOTE;
    }

    public boolean isRawJson() {
        return type == EditorFieldType.RAW_JSON;
    }

    // 只读展示行：不承载字段值，不写入 JSON，也不参与校验
    public boolean isDisplayOnly() {
        return isNote() || isRawJson();
    }

    // 条件树由触发页统一编辑，不进入动作参数说明与结果页表单
    public boolean isConditionTree() {
        return type == EditorFieldType.CONDITION_TREE;
    }

    public boolean isText() {
        return type == EditorFieldType.TEXT;
    }

    public boolean isLongText() {
        return type == EditorFieldType.LONG_TEXT;
    }

    public boolean isNumeric() {
        return type == EditorFieldType.INTEGER || type == EditorFieldType.TICKS
                || type == EditorFieldType.DECIMAL || type == EditorFieldType.PERCENT
                || type == EditorFieldType.AMPLIFIER;
    }

    // 区间条与循环时间条按整数处理，与 DECIMAL/PERCENT 的数值控件不同
    public boolean isIntegerLike() {
        return type == EditorFieldType.INTEGER || type == EditorFieldType.TICKS;
    }

    // JSON 存完整刻数；0 显示「立即」，界面可按秒显示
    public boolean isTicks() {
        return type == EditorFieldType.TICKS;
    }

    // JSON 存 0..1，界面按百分比显示
    public boolean isPercent() {
        return type == EditorFieldType.PERCENT;
    }

    // JSON 存 amplifier（0 起算），界面显示等级
    public boolean isAmplifier() {
        return type == EditorFieldType.AMPLIFIER;
    }

    public boolean isBoolean() {
        return type == EditorFieldType.BOOLEAN;
    }

    public boolean isEnum() {
        return type == EditorFieldType.ENUM;
    }

    public boolean isReference() {
        return type == EditorFieldType.RESOURCE_LOCATION || type == EditorFieldType.REGISTRY_ID
                || type == EditorFieldType.TAG;
    }

    // 可由目录面板挑选的引用或引用列表
    public boolean isCatalogReference() {
        return isReference() || type == EditorFieldType.TAG_LIST || type == EditorFieldType.RL_LIST;
    }

    public boolean allowsTags() {
        return type == EditorFieldType.TAG || type == EditorFieldType.TAG_LIST;
    }

    public boolean isTagList() {
        return type == EditorFieldType.TAG_LIST;
    }

    public boolean isStringList() {
        return type == EditorFieldType.STRING_LIST;
    }

    public boolean isListShaped() {
        return type == EditorFieldType.TAG_LIST || type == EditorFieldType.RL_LIST
                || type == EditorFieldType.STRING_LIST;
    }

    // 目录多选列表；字符串列表不参与目录挑选
    public boolean isMultiCatalog() {
        return type == EditorFieldType.TAG_LIST || type == EditorFieldType.RL_LIST;
    }

    public boolean isItemCounts() {
        return type == EditorFieldType.ITEM_COUNTS;
    }

    public boolean isClimateRange() {
        return type == EditorFieldType.CLIMATE_RANGE;
    }

    public boolean isSublist() {
        return type == EditorFieldType.SUBLIST;
    }

    // 单个嵌套对象：展示为固定子表单，不提供增删
    public boolean isObject() {
        return type == EditorFieldType.OBJECT;
    }

    // 行标签由控件自带绘制（气候范围与布尔开关），表单不再预留标签列
    public boolean hidesRowLabel() {
        return type == EditorFieldType.CLIMATE_RANGE || type == EditorFieldType.BOOLEAN;
    }

    // ---- 派生 ----

    // 变为可空字段
    public EditorField optional() {
        return new EditorField(name, labelKey, type, domain, true, false, registry, enumGroup, hintKey,
                subFields, presets, numbers, displayPrecision);
    }

    // 变为必填字段
    public EditorField asRequired() {
        return new EditorField(name, labelKey, type, domain, false, true, registry, enumGroup, hintKey,
                subFields, presets, numbers, displayPrecision);
    }

    // 附加提示文案 key（显示在字段下方或 tooltip）
    public EditorField withHint(String key) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, key,
                subFields, presets, numbers, displayPrecision);
    }

    // 附加预设值按钮
    public EditorField withPresets(EditorPreset... values) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, List.of(values), numbers, displayPrecision);
    }

    // 替换数值域元数据（保留其余组件）
    public EditorField withNumbers(@Nullable NumericDomain newNumbers) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, presets, newNumbers, displayPrecision);
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

    // 滑块窗口下界（窗口缺省时回落后端域，仍缺省返回 fallback）
    public double sliderMin(double fallback) {
        return numbers == null ? doubleMin(fallback) : numbers.sliderMin(fallback);
    }

    // 滑块窗口上界（窗口缺省时回落后端域，仍缺省返回 fallback）
    public double sliderMax(double fallback) {
        return numbers == null ? doubleMax(fallback) : numbers.sliderMax(fallback);
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
