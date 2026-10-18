package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 内建条件类型的叶卡片摘要：把条件 JSON 组装成一句玩家可读的短文本，例如「12:00~18:00（当天内）」。
 *
 * <p><b>适用范围</b>：只处理本模组命名空间下的内建条件类型，共 10 种（biome、catalyst_present、dimension、
 * fluid_present、light_level、outdoor、surrounding_blocks、time_of_day、weather、y_level）。
 * 未注册或第三方类型返回 {@code null}，由 {@code NaturalSummary.conditionLeaf} 按字段名兜底，行为不变。
 *
 * <p><b>文案约定</b>：摘要不重复条件类型名（卡片标题行已显示类型名），也不输出 JSON 字段名、全限定 ID 或
 * 英文枚举名；取值缺失时退回「未设置」，让玩家看出参数还没填。通用格式化（引用、区间、枚举、布尔、连接）
 * 见 {@link SummaryFormats}。
 */
public final class BuiltinConditionSummaries {

    // ---- 摘要语言键 ----
    private static final String COND = SummaryFormats.KEY_PREFIX + "cond.";
    // 取值缺失时的通用短语，与其它摘要共用
    private static final String UNSET_KEY = SummaryFormats.KEY_PREFIX + "value_empty";
    private static final String DIMENSION_KEY = COND + "dimension";
    private static final String BIOME_KEY = COND + "biome";
    private static final String BIOME_AXIS_KEY = COND + "biome.axis";
    private static final String BIOME_MODE_KEY = COND + "biome.mode";
    private static final String WEATHER_UNSET_KEY = COND + "weather.unset";
    private static final String OUTDOOR_KEY = COND + "outdoor";
    private static final String SIDE_ENTRY_KEY = COND + "surrounding_blocks.entry";
    private static final String CATALYST_ENTRY_KEY = COND + "catalyst_present.entry";
    private static final String FLUID_ANY_KEY = COND + "fluid_present.any";
    private static final String TIME_KEY = COND + "time_of_day";
    private static final String TIME_CROSSING_KEY = COND + "time_of_day.crossing";
    private static final String Y_ANY_KEY = COND + "y_level.any";
    private static final String LIGHT_ANY_KEY = COND + "light_level.any";

    // ---- 条件 JSON 字段名 ----
    private static final String FIELD_DIMENSIONS = "dimensions";
    private static final String FIELD_BIOMES = "biomes";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_WEATHER = "weather";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_FROM = "from";
    private static final String FIELD_TO = "to";
    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";
    // 周围方块的六个方向字段名，顺序即卡片展示顺序
    private static final List<String> SIDE_FIELDS = List.of("up", "down", "north", "south", "east", "west");
    // 气候模式的六个采样轴，顺序与编辑器表单一致
    private static final List<String> CLIMATE_AXES =
            List.of("temperature", "humidity", "continentalness", "erosion", "depth", "weirdness");

    // ---- 业务默认值 ----
    private static final String MODE_CLIMATE = "climate";

    // 工具类
    private BuiltinConditionSummaries() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成内建条件类型的摘要文本。
     *
     * @param condition 条件叶的 JSON 对象，可为 {@code null}
     * @param type      条件类型 id，可为 {@code null}
     * @return 摘要文本；不属于本模组内建类型或数据形态不符时返回 {@code null}，由调用方走通用兜底
     */
    public static @Nullable Component summary(@Nullable RuleValue condition, @Nullable ResourceLocation type) {
        if (condition == null || type == null || !condition.isMapping()) {
            return null;
        }
        if (!TypeLabels.OWN_NAMESPACE.equals(type.getNamespace())) {
            return null;
        }
        return switch (type.getPath()) {
            case "dimension" -> dimension(condition);
            case "biome" -> biome(condition, type);
            case "weather" -> weather(condition, type);
            case "outdoor" -> Component.translatable(OUTDOOR_KEY);
            case "surrounding_blocks" -> surroundingBlocks(condition, type);
            case "catalyst_present" -> catalystPresent(condition);
            case "fluid_present" -> fluidPresent(condition);
            case "time_of_day" -> timeOfDay(condition);
            case "y_level" -> bounded(condition, Y_ANY_KEY);
            case "light_level" -> bounded(condition, LIGHT_ANY_KEY);
            default -> null;
        };
    }

    // 维度：命中任一即满足，未填维度时不加前缀
    private static Component dimension(RuleValue condition) {
        Component value = SummaryFormats.refs(condition.child(FIELD_DIMENSIONS), RuleCatalogType.DIMENSION);
        return value == null ? Component.translatable(UNSET_KEY) : Component.translatable(DIMENSION_KEY, value);
    }

    // 群系：模式词 + 明细（climate 模式展示气候区间，其余展示群系列表）
    private static Component biome(RuleValue condition, ResourceLocation type) {
        String mode = condition.text(FIELD_MODE, "");
        Component modeText = SummaryFormats.enumText(mode, BIOME_MODE_KEY);
        if (MODE_CLIMATE.equals(mode)) {
            Component axes = climateAxes(condition, type);
            return modeText == null ? axes : Component.translatable(BIOME_KEY, modeText, axes);
        }
        Component biomes = biomes(condition);
        return modeText == null ? biomes : Component.translatable(BIOME_KEY, modeText, biomes);
    }

    // 气候区间：逐轴展示，轴名取描述符标签，缺失轴不影响其余轴
    private static Component climateAxes(RuleValue condition, ResourceLocation type) {
        List<Component> axes = new ArrayList<>();
        for (String axis : CLIMATE_AXES) {
            RuleValue range = condition.child(axis);
            if (range == null) {
                continue;
            }
            Component text = SummaryFormats.range(
                    SummaryFormats.numberText(range.child(FIELD_MIN)),
                    SummaryFormats.numberText(range.child(FIELD_MAX)));
            if (text != null) {
                axes.add(Component.translatable(BIOME_AXIS_KEY, axisLabel(type, axis), text));
            }
        }
        if (axes.isEmpty()) {
            return Component.translatable(UNSET_KEY);
        }
        return SummaryFormats.joinCapped(axes, SummaryFormats.MAX_ENTRIES);
    }

    // 群系列表：id 或标签都渲染成显示名
    private static Component biomes(RuleValue condition) {
        Component value = SummaryFormats.refs(condition.child(FIELD_BIOMES), RuleCatalogType.BIOME);
        return value == null ? Component.translatable(UNSET_KEY) : value;
    }

    // 天气：复用表单枚举文案；未选择与取值未知分开处理，避免误报
    private static @Nullable Component weather(RuleValue condition, ResourceLocation type) {
        String kind = condition.text(FIELD_WEATHER, "");
        if (kind.isBlank()) {
            return Component.translatable(WEATHER_UNSET_KEY);
        }
        String group = weatherEnumGroup(type);
        return SummaryFormats.enumText(kind, TypeLabels.UI_PREFIX + "enum." + group);
    }

    // 周围方块：逐方向「方向 + 方块名」，方向名与方块名都走本地化
    private static @Nullable Component surroundingBlocks(RuleValue condition, ResourceLocation type) {
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.find(type);
        if (descriptor == null) {
            return null;
        }
        List<Component> sides = new ArrayList<>();
        for (String side : SIDE_FIELDS) {
            EditorField field = descriptor.field(side);
            if (field == null) {
                continue;
            }
            String raw = condition.text(side, "").trim();
            if (raw.isEmpty()) {
                continue;
            }
            sides.add(Component.translatable(SIDE_ENTRY_KEY,
                    Component.translatable(field.labelKey()),
                    SummaryFormats.ref(raw, RuleCatalogType.BLOCK)));
        }
        if (sides.isEmpty()) {
            return Component.translatable(UNSET_KEY);
        }
        return SummaryFormats.join(sides);
    }

    // 催化剂：逐物品「物品名 × 数量」；未配数量时只显示物品名（数量跟随同作用域消耗）
    private static Component catalystPresent(RuleValue condition) {
        RuleValue items = condition.child(FIELD_ITEMS);
        if (items == null || !items.isList()) {
            return Component.translatable(UNSET_KEY);
        }
        RuleValue counts = condition.child(RuleFields.ITEM_COUNTS);
        int defaultCount = countOf(condition.child(FIELD_COUNT));
        List<Component> entries = new ArrayList<>();
        for (String raw : items.textItems()) {
            int count = countOf(childOf(counts, raw));
            if (count <= 0) {
                count = countOf(childOf(counts, canonical(raw)));
            }
            if (count <= 0) {
                count = defaultCount;
            }
            Component entry = SummaryFormats.ref(raw, RuleCatalogType.ITEM);
            entries.add(count > 0
                    ? Component.translatable(CATALYST_ENTRY_KEY, entry, count)
                    : entry);
        }
        if (entries.isEmpty()) {
            return Component.translatable(UNSET_KEY);
        }
        return SummaryFormats.joinCapped(entries, SummaryFormats.MAX_ENTRIES);
    }

    // 流体：指定流体列表或「任意流体」，再补源流体要求
    private static Component fluidPresent(RuleValue condition) {
        List<Component> parts = new ArrayList<>();
        Component references = SummaryFormats.refs(condition.child(RuleFields.FLUIDS), RuleCatalogType.FLUID);
        if (references == null) {
            String single = condition.text(RuleFields.FLUID, "").trim();
            if (!single.isEmpty()) {
                references = SummaryFormats.ref(single, RuleCatalogType.FLUID);
            }
        }
        parts.add(references == null ? Component.translatable(FLUID_ANY_KEY) : references);
        return SummaryFormats.join(parts);
    }

    // 时间：按 24 小时制展示时钟区间，并说明是否跨零点
    private static Component timeOfDay(RuleValue condition) {
        Integer from = integerOf(condition.child(FIELD_FROM));
        Integer to = integerOf(condition.child(FIELD_TO));
        if (from == null || to == null) {
            return Component.translatable(UNSET_KEY);
        }
        Component span = SummaryFormats.range(SummaryFormats.clockText(from), SummaryFormats.clockText(to));
        Component crossing = SummaryFormats.flag(from > to, TIME_CROSSING_KEY);
        return crossing == null ? span : Component.translatable(TIME_KEY, span, crossing);
    }

    // 数值区间条件：两端都未填时提示该维度任意取值
    private static Component bounded(RuleValue condition, String anyKey) {
        Component value = SummaryFormats.range(
                SummaryFormats.numberText(condition.child(FIELD_MIN)),
                SummaryFormats.numberText(condition.child(FIELD_MAX)));
        return value == null ? Component.translatable(anyKey) : value;
    }

    // 取子字段句柄：父字段缺失或不是对象时返回 null
    private static @Nullable RuleValue childOf(@Nullable RuleValue parent, String key) {
        return parent == null || !parent.isMapping() ? null : parent.child(key);
    }

    // 读取整数取值：非数值返回 0，调用方据此判定「未配置」
    private static int countOf(@Nullable RuleValue value) {
        return value != null && value.isNumber() ? Math.max(0, value.asInteger(0)) : 0;
    }

    // 读取可为空的时间刻值
    private static @Nullable Integer integerOf(@Nullable RuleValue value) {
        return value != null && value.isNumber() ? value.asInteger(0) : null;
    }

    // counts 的键是 TaggedId.serialized()；归一化失败时退回原文
    private static String canonical(String raw) {
        String value = raw == null ? "" : raw.trim();
        TaggedId tagged = TaggedId.parse(value).result().orElse(null);
        return tagged == null ? value : tagged.serialized();
    }

    // 气候轴名取自描述符标签；描述符缺字段时退回字段名，保证仍可读
    private static Component axisLabel(ResourceLocation type, String axis) {
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.find(type);
        EditorField field = descriptor == null ? null : descriptor.field(axis);
        return field == null ? Component.literal(axis) : Component.translatable(field.labelKey());
    }

    // 天气枚举语言键中的分组名取自描述符，缺省与表单一致地回落字段名
    private static String weatherEnumGroup(ResourceLocation type) {
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.find(type);
        EditorField field = descriptor == null ? null : descriptor.field(FIELD_WEATHER);
        String group = field == null ? null : field.enumGroup();
        return group == null || group.isBlank() ? FIELD_WEATHER : group;
    }
}
