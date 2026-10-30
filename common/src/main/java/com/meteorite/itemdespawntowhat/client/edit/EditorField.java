package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.core.api.param.ClimateDomain;
import com.meteorite.itemdespawntowhat.core.api.param.ParamTier;
import com.meteorite.itemdespawntowhat.core.api.param.SelectionIntent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑器字段声明：形状、类型化值域、层级、必填性、注册表与本地化元数据。
 * <p>字段顺序即表单顺序，实际值只保存在规则 JSON 中。数值的合法域、窗口、步长与精度
 * 由 {@link NumericDomain} 一次声明；等级、百分比与时间单位在控件中换算。
 */
public record EditorField(String name, String labelKey, EditorFieldType type, FieldDomain domain,
                          boolean nullable, boolean required, @Nullable String registry,
                          @Nullable String enumGroup, @Nullable String hintKey,
                          List<EditorField> subFields, List<EditorPreset> presets, ParamTier tier,
                          @Nullable RuleValue defaultValue, SelectionIntent selectionIntent) {

    /** 固定声明集合；值域通过类型表达，不接受待回解的字符串。 */
    public EditorField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(domain, "domain");
        if (name.isBlank() || labelKey.isBlank()) {
            throw new IllegalArgumentException("字段名与 label key 不能为空");
        }
        subFields = subFields == null ? List.of() : List.copyOf(subFields);
        presets = presets == null ? List.of() : List.copyOf(presets);
        tier = tier == null ? ParamTier.PRIMARY : tier;
        selectionIntent = selectionIntent == null ? SelectionIntent.DEFAULT : selectionIntent;
    }

    /** 保持原完整构造入口，选择意图默认为 DEFAULT。 */
    public EditorField(String name, String labelKey, EditorFieldType type, FieldDomain domain,
                       boolean nullable, boolean required, @Nullable String registry,
                       @Nullable String enumGroup, @Nullable String hintKey,
                       List<EditorField> subFields, List<EditorPreset> presets, ParamTier tier,
                       @Nullable RuleValue defaultValue) {
        this(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, presets, tier, defaultValue, SelectionIntent.DEFAULT);
    }

    /** 保持未声明参数层级时的原构造方式，默认归入主区。 */
    public EditorField(String name, String labelKey, EditorFieldType type, FieldDomain domain,
                       boolean nullable, boolean required, @Nullable String registry,
                       @Nullable String enumGroup, @Nullable String hintKey,
                       List<EditorField> subFields, List<EditorPreset> presets) {
        this(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, presets, ParamTier.PRIMARY, null, SelectionIntent.DEFAULT);
    }

    /** 保持未声明展示默认值时的构造方式。 */
    public EditorField(String name, String labelKey, EditorFieldType type, FieldDomain domain,
                       boolean nullable, boolean required, @Nullable String registry,
                       @Nullable String enumGroup, @Nullable String hintKey,
                       List<EditorField> subFields, List<EditorPreset> presets, ParamTier tier) {
        this(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, presets, tier, null, SelectionIntent.DEFAULT);
    }

    /** 替换嵌套字段，保留对象字段的其余声明。 */
    public EditorField withSubFields(List<EditorField> fields) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                fields, presets, tier, defaultValue, selectionIntent);
    }

    /** 省略字段的展示默认值；未编辑时仍保留原有省略形态。 */
    public EditorField withDefaultValue(@Nullable RuleValue value) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, presets, tier, value, selectionIntent);
    }

    // ---- 构造工厂 ----

    /**
     * 必填单行文本
     */
    public static EditorField text(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.TEXT, FieldDomain.None.INSTANCE, false, true, null, null, null);
    }

    /**
     * 可选单行文本
     */
    public static EditorField optionalText(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.TEXT, FieldDomain.None.INSTANCE, true, false, null, null, null);
    }

    /**
     * 可选多行文本
     */
    public static EditorField longText(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.LONG_TEXT, FieldDomain.None.INSTANCE, true, false, null, null, null);
    }

    /**
     * 必填整数，带后端合法上下界（滑块窗口默认同域）
     */
    public static EditorField integer(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.INTEGER, new FieldDomain.Numeric(NumericDomain.integers(min, max)), false, true, null, null, null);
    }

    /**
     * 可空整数（留空表示不约束），带后端合法上下界
     */
    public static EditorField optionalInteger(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.INTEGER, new FieldDomain.Numeric(NumericDomain.integers(min, max)), true, false, null, null, null);
    }

    /**
     * 整数字段：后端合法域与滑块常用窗口分开记录（窗口不是提交硬限额）
     */
    public static EditorField integerSlider(String name, String labelKey, int min, int max,
                                            int windowMin, int windowMax) {
        return create(name, labelKey, EditorFieldType.INTEGER, new FieldDomain.Numeric(NumericDomain.integers(min, max, windowMin, windowMax)), false, true, null, null, null);
    }

    /**
     * 注册表引用各自保存数量，滑条默认显示常用范围 1..64；注册表决定候选与展示名。
     */
    public static EditorField itemCounts(String name, String labelKey, String registry, int min, int max) {
        return create(name, labelKey, EditorFieldType.ITEM_COUNTS, new FieldDomain.Numeric(NumericDomain.integers(min, max, min, Math.min(max, 64))), true, false, registry, null, null);
    }

    /**
     * 物品数量字段：沿用物品注册表提示
     */
    public static EditorField itemCounts(String name, String labelKey, int min, int max) {
        return itemCounts(name, labelKey, "minecraft:item", min, max);
    }

    /**
     * 刻数：0 显示「立即」；界面允许按秒显示，但 JSON 仍是完整刻数
     */
    public static EditorField ticks(String name, String labelKey, int min, int max) {
        return create(name, labelKey, EditorFieldType.TICKS, new FieldDomain.Numeric(NumericDomain.integers(min, max)), false, true, null, null, null);
    }

    /**
     * 必填小数，带上下界（默认步长 0.1 / 精细 0.01）
     */
    public static EditorField decimal(String name, String labelKey, double min, double max) {
        return decimal(name, labelKey, min, max, 0.1D, 0.01D);
    }

    /**
     * 必填小数，带上下界与显式步长
     */
    public static EditorField decimal(String name, String labelKey, double min, double max,
                                      double step, double shiftStep) {
        return create(name, labelKey, EditorFieldType.DECIMAL, new FieldDomain.Numeric(NumericDomain.decimals(min, max, step, shiftStep)), false, true, null, null, null);
    }

    /**
     * 百分比字段：JSON 按声明域取值，界面显示原值 ×100；显示按原始精度往返，不做舍入
     */
    public static EditorField percent(String name, String labelKey, double min, double max) {
        return percent(name, labelKey, min, max, 0.01D, 0.001D);
    }

    /**
     * 百分比字段（显式步长档）
     */
    public static EditorField percent(String name, String labelKey, double min, double max,
                                      double step, double shiftStep) {
        return create(name, labelKey, EditorFieldType.PERCENT, new FieldDomain.Numeric(NumericDomain.decimals(min, max, step, shiftStep)), false, true, null, null, null);
    }

    /**
     * 药水等级字段：JSON 存 amplifier（0 起算，后端 0..255），界面显示等级 1..256
     */
    public static EditorField amplifier(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.AMPLIFIER, new FieldDomain.Numeric(NumericDomain.integers(0, FieldNumbers.MAX_AMPLIFIER, 0, FieldNumbers.MAX_AMPLIFIER)), false, true, null, null, null);
    }

    /**
     * 布尔开关
     */
    public static EditorField bool(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.BOOLEAN, FieldDomain.None.INSTANCE, false, true, null, null, null);
    }

    /**
     * 枚举字段（带 i18n 组名）
     */
    public static EditorField enumIn(String name, String labelKey, String enumGroup, String... values) {
        return create(name, labelKey, EditorFieldType.ENUM, new FieldDomain.Choices(List.of(values)), false, true, null, enumGroup, null);
    }

    /**
     * 枚举字段（单个按钮轮换取值）。
     *
     * <p>候选文案较长、一行放不下全部候选时使用：控件占满整列，只显示当前取值，
     * 点击切换到下一个候选，配置数据与 {@link #enumIn} 完全一致。</p>
     */
    public static EditorField enumCycle(String name, String labelKey, String enumGroup, String... values) {
        return create(name, labelKey, EditorFieldType.ENUM, new FieldDomain.Choices(List.of(values), true), false, true, null, enumGroup, null);
    }

    /**
     * 可带标签的 id 字段
     */
    public static EditorField tag(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG, FieldDomain.None.INSTANCE, false, true, registry, null, null);
    }

    /**
     * 可空、可带标签的 id 字段
     */
    public static EditorField optionalTag(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG, FieldDomain.None.INSTANCE, true, false, registry, null, null);
    }

    /**
     * 可带标签的 id 列表
     */
    public static EditorField tagList(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.TAG_LIST, FieldDomain.None.INSTANCE, true, false, registry, null, null);
    }

    /**
     * 纯资源位置列表（不给标签模式）
     */
    public static EditorField rlList(String name, String labelKey, String registry) {
        return create(name, labelKey, EditorFieldType.RL_LIST, FieldDomain.None.INSTANCE, true, false, registry, null, null);
    }

    /**
     * 资源位置字段（不允许标签）
     */
    public static EditorField resourceLocation(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.RESOURCE_LOCATION, FieldDomain.None.INSTANCE, false, true, null, null, null);
    }

    /** 气候区间字段的端点域与交互步长来自 core 共享声明。 */
    public static EditorField climateRange(String name, String labelKey) {
        NumericDomain numbers = NumericDomain.decimals(ClimateDomain.MIN.min(), ClimateDomain.MAX.max(),
                ClimateDomain.STEP, ClimateDomain.FINE_STEP);
        return create(name, labelKey, EditorFieldType.CLIMATE_RANGE, new FieldDomain.Numeric(numbers), true, false, null, null, null);
    }

    /**
     * 子列表字段（对象数组，字段定义由 subFields 给出）
     */
    public static EditorField subList(String name, String labelKey, EditorField... subFields) {
        return create(name, labelKey, EditorFieldType.SUBLIST, FieldDomain.None.INSTANCE, true, false, null, null, List.of(subFields));
    }

    /**
     * 卡片列表字段：对象数组，每项的引用与数值折进一张紧凑卡片
     */
    public static EditorField cardList(String name, String labelKey, EditorField... subFields) {
        return create(name, labelKey, EditorFieldType.CARD_LIST, FieldDomain.None.INSTANCE, true, false, null, null, List.of(subFields));
    }

    /**
     * 单个嵌套对象字段：subFields 描述对象内部字段
     */
    public static EditorField object(String name, String labelKey, EditorField... subFields) {
        return create(name, labelKey, EditorFieldType.OBJECT, FieldDomain.None.INSTANCE, true, false, null, null, List.of(subFields));
    }

    /**
     * 条件树字段
     */
    public static EditorField conditionTree(String name, String labelKey) {
        return create(name, labelKey, EditorFieldType.CONDITION_TREE, FieldDomain.None.INSTANCE, true, false, null, null, null);
    }

    /**
     * 只读说明行（不写入 JSON，label key 即说明文案）
     */
    public static EditorField note(String labelKey) {
        return create("__note", labelKey, EditorFieldType.NOTE, FieldDomain.None.INSTANCE, true, false, null, null, null);
    }

    /**
     * 原始 JSON 只读展示（第三方类型回退）
     */
    public static EditorField rawJson() {
        return create("__raw_json", TypeLabels.UI_PREFIX + "field.raw_json", EditorFieldType.RAW_JSON, FieldDomain.None.INSTANCE, true, false, null, null, null);
    }

    private static EditorField create(String name, String labelKey, EditorFieldType type, FieldDomain domain,
                                      boolean nullable, boolean required, @Nullable String registry,
                                      @Nullable String enumGroup, @Nullable List<EditorField> subFields) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, null,
                subFields, null, ParamTier.PRIMARY, null, SelectionIntent.DEFAULT);
    }

    // 类型的独立事实集中于穷举分类；字段自身仍持有 nullable、registry 和取值域。
    private enum Shape {
        TEXT, LONG_TEXT, NUMBER, BOOLEAN, ENUM, REFERENCE, LIST, ITEM_COUNTS,
        CLIMATE_RANGE, SUBLIST, CARD_LIST, OBJECT, CONDITION_TREE, NOTE, RAW_JSON
    }

    private enum NumberKind { NONE, INTEGER, TICKS, DECIMAL, PERCENT, AMPLIFIER }
    private enum ReferenceKind { NONE, ID, TAGGED }

    private record Classification(Shape shape, NumberKind number, ReferenceKind reference) {
        private static Classification plain(Shape shape) {
            return new Classification(shape, NumberKind.NONE, ReferenceKind.NONE);
        }

        private static Classification numeric(NumberKind number) {
            return new Classification(Shape.NUMBER, number, ReferenceKind.NONE);
        }

        private static Classification reference(Shape shape, ReferenceKind reference) {
            return new Classification(shape, NumberKind.NONE, reference);
        }
    }

    private static final Map<EditorFieldType, Classification> CLASSIFICATIONS = classifications();

    private static Map<EditorFieldType, Classification> classifications() {
        Map<EditorFieldType, Classification> result = new EnumMap<>(EditorFieldType.class);
        for (EditorFieldType type : EditorFieldType.values()) {
            result.put(type, switch (type) {
                case TEXT -> Classification.plain(Shape.TEXT);
                case LONG_TEXT -> Classification.plain(Shape.LONG_TEXT);
                case INTEGER -> Classification.numeric(NumberKind.INTEGER);
                case TICKS -> Classification.numeric(NumberKind.TICKS);
                case DECIMAL -> Classification.numeric(NumberKind.DECIMAL);
                case PERCENT -> Classification.numeric(NumberKind.PERCENT);
                case AMPLIFIER -> Classification.numeric(NumberKind.AMPLIFIER);
                case BOOLEAN -> Classification.plain(Shape.BOOLEAN);
                case ENUM -> Classification.plain(Shape.ENUM);
                case RESOURCE_LOCATION, REGISTRY_ID -> Classification.reference(Shape.REFERENCE, ReferenceKind.ID);
                case TAG -> Classification.reference(Shape.REFERENCE, ReferenceKind.TAGGED);
                case TAG_LIST -> Classification.reference(Shape.LIST, ReferenceKind.TAGGED);
                case RL_LIST -> Classification.reference(Shape.LIST, ReferenceKind.ID);
                case STRING_LIST -> Classification.plain(Shape.LIST);
                case ITEM_COUNTS -> Classification.plain(Shape.ITEM_COUNTS);
                case CLIMATE_RANGE -> Classification.plain(Shape.CLIMATE_RANGE);
                case SUBLIST -> Classification.plain(Shape.SUBLIST);
                case CARD_LIST -> Classification.plain(Shape.CARD_LIST);
                case OBJECT -> Classification.plain(Shape.OBJECT);
                case CONDITION_TREE -> Classification.plain(Shape.CONDITION_TREE);
                case NOTE -> Classification.plain(Shape.NOTE);
                case RAW_JSON -> Classification.plain(Shape.RAW_JSON);
            });
        }
        return Map.copyOf(result);
    }

    private Classification classification() {
        return CLASSIFICATIONS.get(type);
    }

    /** 只读说明行。 */
    public boolean isNote() {
        return classification().shape() == Shape.NOTE;
    }

    /** 第三方参数的原始 JSON 只读展示。 */
    public boolean isRawJson() {
        return classification().shape() == Shape.RAW_JSON;
    }

    /** 展示行不承载字段值，也不参与写入与校验。 */
    public boolean isDisplayOnly() {
        return isNote() || isRawJson();
    }

    /** 单行文本。 */
    public boolean isText() {
        return classification().shape() == Shape.TEXT;
    }

    /** 多行文本。 */
    public boolean isLongText() {
        return classification().shape() == Shape.LONG_TEXT;
    }

    /** 使用单值数值控件的字段。 */
    public boolean isNumeric() {
        return classification().shape() == Shape.NUMBER;
    }

    /** 使用普通整数交互的字段；amplifier 另有等级换算。 */
    public boolean isIntegerLike() {
        return classification().number() == NumberKind.INTEGER || classification().number() == NumberKind.TICKS;
    }

    /** JSON 保存完整刻数，界面可以按秒显示。 */
    public boolean isTicks() {
        return classification().number() == NumberKind.TICKS;
    }

    /** 界面按百分比显示的数值。 */
    public boolean isPercent() {
        return classification().number() == NumberKind.PERCENT;
    }

    /** JSON 保存从零起算的 amplifier，界面显示等级。 */
    public boolean isAmplifier() {
        return classification().number() == NumberKind.AMPLIFIER;
    }

    /** 布尔开关。 */
    public boolean isBoolean() {
        return classification().shape() == Shape.BOOLEAN;
    }

    /** 固定枚举选项。 */
    public boolean isEnum() {
        return classification().shape() == Shape.ENUM;
    }

    /** 单个资源引用。 */
    public boolean isReference() {
        return classification().shape() == Shape.REFERENCE;
    }

    /** 可由目录面板挑选的单个引用或引用列表。 */
    public boolean isCatalogReference() {
        return classification().reference() != ReferenceKind.NONE;
    }

    /** 引用允许使用标签。 */
    public boolean allowsTags() {
        return classification().reference() == ReferenceKind.TAGGED;
    }

    /** 允许标签的引用列表。 */
    public boolean isTagList() {
        return isListShaped() && allowsTags();
    }

    /** 不使用目录的字符串列表。 */
    public boolean isStringList() {
        return isListShaped() && !isCatalogReference();
    }

    /** 标量列表；嵌套对象列表另由 sublist 表示。 */
    public boolean isListShaped() {
        return classification().shape() == Shape.LIST;
    }

    /** 支持目录多选的引用列表。 */
    public boolean isMultiCatalog() {
        return isListShaped() && isCatalogReference();
    }

    /** 引用到数量的映射。 */
    public boolean isItemCounts() {
        return classification().shape() == Shape.ITEM_COUNTS;
    }

    /** 两端可空的气候区间。 */
    public boolean isClimateRange() {
        return classification().shape() == Shape.CLIMATE_RANGE;
    }

    /** 可增删的嵌套对象列表。 */
    public boolean isSublist() {
        return classification().shape() == Shape.SUBLIST;
    }

    /** 可增删的嵌套对象列表，每条元素按卡片渲染。 */
    public boolean isCardList() {
        return classification().shape() == Shape.CARD_LIST;
    }

    /** 固定子字段组成的嵌套对象。 */
    public boolean isObject() {
        return classification().shape() == Shape.OBJECT;
    }

    /** 控件自行绘制标签，表单不预留标签列。 */
    public boolean hidesRowLabel() {
        return isClimateRange() || isBoolean() || isCardList();
    }

    // ---- 派生 ----

    /**
     * 变为可空字段
     */
    public EditorField optional() {
        return new EditorField(name, labelKey, type, domain, true, false, registry, enumGroup, hintKey,
                subFields, presets, tier, defaultValue, selectionIntent);
    }

    /**
     * 变为必填字段
     */
    public EditorField asRequired() {
        return new EditorField(name, labelKey, type, domain, false, true, registry, enumGroup, hintKey,
                subFields, presets, tier, defaultValue, selectionIntent);
    }

    /**
     * 附加提示文案 key（显示在字段下方或 tooltip）
     */
    public EditorField withHint(String key) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, key,
                subFields, presets, tier, defaultValue, selectionIntent);
    }

    /**
     * 附加预设值按钮
     */
    public EditorField withPresets(EditorPreset... values) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, List.of(values), tier, defaultValue, selectionIntent);
    }

    /** 替换数值声明；其余字段元数据保持，移除数值域用 null 表达。 */
    public EditorField withNumbers(@Nullable NumericDomain newNumbers) {
        FieldDomain replacement = newNumbers == null ? FieldDomain.None.INSTANCE : new FieldDomain.Numeric(newNumbers);
        return new EditorField(name, labelKey, type, replacement, nullable, required, registry, enumGroup, hintKey,
                subFields, presets, tier, defaultValue, selectionIntent);
    }

    /** 只改展示标签，不重建或丢失字段声明。 */
    public EditorField withLabel(String key) {
        return new EditorField(name, key, type, domain, nullable, required, registry, enumGroup, hintKey, subFields, presets, tier, defaultValue, selectionIntent);
    }

    /** 编辑器字段的展示层级。 */
    public ParamTier tier() { return tier; }

    /** 返回带指定展示层级的字段声明。 */
    public EditorField withTier(ParamTier value) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, presets, value, defaultValue, selectionIntent);
    }

    /** 返回带参数声明选择意图的字段描述。 */
    public EditorField withSelectionIntent(SelectionIntent value) {
        return new EditorField(name, labelKey, type, domain, nullable, required, registry, enumGroup, hintKey,
                subFields, presets, tier, defaultValue, value);
    }

    /** 数值声明；非数值域返回 null。 */
    public @Nullable NumericDomain numbers() {
        return domain instanceof FieldDomain.Numeric(var values) ? values : null;
    }

    /** 回填精度来自数值声明；无声明时使用最短往返表示。 */
    public int displayPrecision() {
        NumericDomain value = numbers();
        return value == null ? -1 : value.displayPrecision();
    }

    /** 枚举 wire 值；非枚举字段没有候选。 */
    public List<String> enumValues() {
        return isEnum() && domain instanceof FieldDomain.Choices choices ? choices.values() : List.of();
    }

    /** 枚举是否用单个按钮轮换取值；分段展示的枚举与非枚举字段返回 false。 */
    public boolean enumCycling() {
        return isEnum() && domain instanceof FieldDomain.Choices choices && choices.cycling();
    }

    /** JSON 数值合法下界，未声明时使用调用方缺省值。 */
    public double doubleMin(double fallback) {
        NumericDomain value = numbers();
        return value == null || value.backendMin() == null ? fallback : value.backendMin();
    }

    /** JSON 数值合法上界，未声明时使用调用方缺省值。 */
    public double doubleMax(double fallback) {
        NumericDomain value = numbers();
        return value == null || value.backendMax() == null ? fallback : value.backendMax();
    }

    /** 整数合法下界。 */
    public int intMin(int fallback) { return (int) Math.round(doubleMin(fallback)); }

    /** 整数合法上界。 */
    public int intMax(int fallback) { return (int) Math.round(doubleMax(fallback)); }

    /** 常用窗口下界；窗口不限制合法精确输入。 */
    public double sliderMin(double fallback) {
        NumericDomain value = numbers();
        return value == null ? fallback : value.sliderMin(fallback);
    }

    /** 常用窗口上界；窗口不限制合法精确输入。 */
    public double sliderMax(double fallback) {
        NumericDomain value = numbers();
        return value == null ? fallback : value.sliderMax(fallback);
    }
}
