package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.ConditionEditorRegistry;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.SummaryFormats;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiDescriptionTooltip;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 条件叶的参数说明：把条件 JSON 拆成「标签：取值」的多行文本，供条件树画布的悬停提示展示。
 *
 * <p><b>职责</b>：只生成参数明细行，不生成标题与结构状态；标题行和问题标记由
 * {@code UiConditionGraphEditor} 统一追加，保证问题颜色不会被值文本覆盖。</p>
 *
 * <p><b>可读性</b>：内建条件按类型给出玩家语义（时钟、区间、方向、物品数量），一行一个参数；
 * 标签用弱化色、取值用正文色，长列表由 {@link SummaryFormats} 截断并提示总数。
 * 未注册的第三方条件回落到 {@link NaturalSummary#conditionParams(RuleValue)}，无法读取时只提示查看原始 JSON。</p>
 */
final class ConditionDescription {

    // ---- 文案键 ----
    private static final String UI = TypeLabels.UI_PREFIX;
    private static final String TOOLTIP = UI + "tooltip.condition.";
    private static final String SUMMARY = SummaryFormats.KEY_PREFIX;
    private static final String RANGE_LABEL_KEY = TOOLTIP + "range";
    private static final String NOTE_LABEL_KEY = TOOLTIP + "note";
    private static final String UNREADABLE_KEY = TOOLTIP + "unreadable";
    private static final String UNSET_KEY = SUMMARY + "value_empty";

    // ---- 条件 JSON 字段名 ----
    private static final String FIELD_DIMENSIONS = "dimensions";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_BIOMES = "biomes";
    private static final String FIELD_WEATHER = "weather";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_FROM = "from";
    private static final String FIELD_TO = "to";
    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";
    private static final String MODE_CLIMATE = "climate";
    private static final List<String> SIDE_FIELDS = List.of("up", "down", "north", "south", "east", "west");
    private static final List<String> CLIMATE_AXES =
            List.of("temperature", "humidity", "continentalness", "erosion", "depth", "weirdness");

    private ConditionDescription() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成条件叶的参数说明行。
     *
     * @param condition 条件叶的 JSON 对象，可为 {@code null}
     * @param theme 宿主主题，用于标签与取值的分色
     * @return 参数说明行；条件无法读取时返回一条提示，永不返回空列表
     */
    static List<UiDescriptionTooltip.Line> lines(@Nullable RuleValue condition, UiHostTheme theme) {
        if (condition == null || !condition.isMapping()) {
            return List.of(note(UNREADABLE_KEY, theme));
        }
        ResourceLocation type = typeOf(condition);
        if (type == null) {
            return List.of(note(UNREADABLE_KEY, theme));
        }
        if (!TypeLabels.OWN_NAMESPACE.equals(type.getNamespace())) {
            return generic(condition, type, theme);
        }
        return switch (type.getPath()) {
            case "dimension" -> dimension(condition, type, theme);
            case "biome" -> biome(condition, type, theme);
            case "weather" -> weather(condition, type, theme);
            case "outdoor" -> outdoor(theme);
            case "surrounding_blocks" -> surroundingBlocks(condition, type, theme);
            case "catalyst_present" -> catalystPresent(condition, type, theme);
            case "fluid_present" -> fluidPresent(condition, type, theme);
            case "time_of_day" -> timeOfDay(condition, theme);
            case "y_level" -> bounded(condition, "cond.y_level.any", theme);
            case "light_level" -> bounded(condition, "cond.light_level.any", theme);
            default -> generic(condition, type, theme);
        };
    }

    // 维度：命中任意一个维度即满足
    private static List<UiDescriptionTooltip.Line> dimension(RuleValue condition, ResourceLocation type,
            UiHostTheme theme) {
        Component value = SummaryFormats.refs(condition.child(FIELD_DIMENSIONS), RuleCatalogType.DIMENSION);
        return List.of(row(fieldLabel(type, FIELD_DIMENSIONS, "field.dimension.dimensions"),
                value == null ? unset() : value, theme));
    }

    // 群系：先显示模式，再按模式显示群系列表或气候区间
    private static List<UiDescriptionTooltip.Line> biome(RuleValue condition, ResourceLocation type,
            UiHostTheme theme) {
        List<UiDescriptionTooltip.Line> lines = new ArrayList<>();
        String mode = condition.text(FIELD_MODE, "");
        Component modeText = SummaryFormats.enumText(mode, SUMMARY + "cond.biome.mode");
        lines.add(row(fieldLabel(type, FIELD_MODE, "field.biome.mode"),
                modeText == null ? unset() : modeText, theme));
        if (MODE_CLIMATE.equals(mode)) {
            boolean anyRange = false;
            for (String axis : CLIMATE_AXES) {
                Component range = rangeOf(condition.child(axis));
                if (range != null) {
                    lines.add(row(fieldLabel(type, axis, "field.biome." + axis), range, theme));
                    anyRange = true;
                }
            }
            if (!anyRange) {
                lines.add(row(Component.translatable(RANGE_LABEL_KEY), unset(), theme));
            }
        } else {
            Component biomes = SummaryFormats.refs(condition.child(FIELD_BIOMES), RuleCatalogType.BIOME);
            lines.add(row(fieldLabel(type, FIELD_BIOMES, "field.biome.biomes"),
                    biomes == null ? unset() : biomes, theme));
        }
        return lines;
    }

    // 天气：未选择或枚举无法识别时统一显示未设置
    private static List<UiDescriptionTooltip.Line> weather(RuleValue condition, ResourceLocation type,
            UiHostTheme theme) {
        String kind = condition.text(FIELD_WEATHER, "");
        Component value = SummaryFormats.enumText(kind, weatherEnumPrefix(type));
        return List.of(row(fieldLabel(type, FIELD_WEATHER, "field.weather.weather"),
                value == null ? unset() : value, theme));
    }

    // 露天：无参数条件，用一句说明代替空白
    private static List<UiDescriptionTooltip.Line> outdoor(UiHostTheme theme) {
        return List.of(row(Component.translatable(NOTE_LABEL_KEY),
                Component.translatable(SUMMARY + "cond.outdoor"), theme));
    }

    // 周围方块：每个已填方向单独一行
    private static List<UiDescriptionTooltip.Line> surroundingBlocks(RuleValue condition, ResourceLocation type,
            UiHostTheme theme) {
        List<UiDescriptionTooltip.Line> lines = new ArrayList<>();
        for (String side : SIDE_FIELDS) {
            String raw = condition.text(side, "").trim();
            if (raw.isEmpty()) {
                continue;
            }
            lines.add(row(fieldLabel(type, side, "field.surrounding_blocks." + side),
                    SummaryFormats.ref(raw, RuleCatalogType.BLOCK), theme));
        }
        if (lines.isEmpty()) {
            lines.add(note(UNSET_KEY, theme));
        }
        return lines;
    }

    // 催化剂：逐物品显示「名称 × 数量」，数量优先取 item_counts，其次 count
    private static List<UiDescriptionTooltip.Line> catalystPresent(RuleValue condition, ResourceLocation type,
            UiHostTheme theme) {
        Component label = fieldLabel(type, FIELD_ITEMS, "field.catalyst_present.items");
        RuleValue items = condition.child(FIELD_ITEMS);
        if (items == null || !items.isList()) {
            return List.of(row(label, unset(), theme));
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
                    ? Component.translatable(SUMMARY + "cond.catalyst_present.entry", entry, count)
                    : entry);
        }
        Component value = entries.isEmpty() ? unset() : SummaryFormats.joinCapped(entries, SummaryFormats.MAX_ENTRIES);
        return List.of(row(label, value, theme));
    }

    // 流体：列表为空时退回单数 fluid，两者皆空表示任意流体
    private static List<UiDescriptionTooltip.Line> fluidPresent(RuleValue condition, ResourceLocation type,
            UiHostTheme theme) {
        Component value = SummaryFormats.refs(condition.child(RuleFields.FLUIDS), RuleCatalogType.FLUID);
        if (value == null) {
            String single = condition.text(RuleFields.FLUID, "").trim();
            if (!single.isEmpty()) {
                value = SummaryFormats.ref(single, RuleCatalogType.FLUID);
            }
        }
        if (value == null) {
            value = Component.translatable(SUMMARY + "cond.fluid_present.any");
        }
        return List.of(row(fieldLabel(type, RuleFields.FLUIDS, "field.fluid_present.fluid"), value, theme));
    }

    // 时间：时钟区间 + 是否跨零点
    private static List<UiDescriptionTooltip.Line> timeOfDay(RuleValue condition, UiHostTheme theme) {
        Integer from = integerOf(condition.child(FIELD_FROM));
        Integer to = integerOf(condition.child(FIELD_TO));
        Component label = Component.translatable(RANGE_LABEL_KEY);
        if (from == null || to == null) {
            return List.of(row(label, unset(), theme));
        }
        Component span = SummaryFormats.range(SummaryFormats.clockText(from), SummaryFormats.clockText(to));
        if (span == null) {
            return List.of(row(label, unset(), theme));
        }
        Component crossing = SummaryFormats.flag(from > to, SUMMARY + "cond.time_of_day.crossing");
        Component value = crossing == null
                ? span
                : Component.translatable(SUMMARY + "cond.time_of_day", span, crossing);
        return List.of(row(label, value, theme));
    }

    // 数值区间：两端都未填时显示该维度的「任意」说明
    private static List<UiDescriptionTooltip.Line> bounded(RuleValue condition, String anyKeySuffix,
            UiHostTheme theme) {
        Component value = rangeOf(condition);
        if (value == null) {
            value = Component.translatable(SUMMARY + anyKeySuffix);
        }
        return List.of(row(Component.translatable(RANGE_LABEL_KEY), value, theme));
    }

    // 非内建条件：只读描述符给出只读提示，其余交给通用摘要
    private static List<UiDescriptionTooltip.Line> generic(RuleValue condition, ResourceLocation type,
            UiHostTheme theme) {
        List<UiDescriptionTooltip.Line> lines = new ArrayList<>();
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.find(type);
        if (descriptor != null && descriptor.readOnly()) {
            lines.add(note(UI + "tree.parameters_readonly", theme));
        }
        Component params = NaturalSummary.conditionParams(condition);
        if (params != null) {
            lines.add(row(Component.translatable(NOTE_LABEL_KEY), params, theme));
        }
        if (lines.isEmpty()) {
            lines.add(note(UNREADABLE_KEY, theme));
        }
        return lines;
    }

    // 一行参数：标签弱化、取值正文色，行色与取值一致
    private static UiDescriptionTooltip.Line row(Component label, Component value, UiHostTheme theme) {
        int muted = theme.palette().canvasTextMuted();
        int text = theme.palette().canvasText();
        Component line = Component.translatable(SUMMARY + "field",
                label.copy().withStyle(style -> style.withColor(muted)),
                value.copy().withStyle(style -> style.withColor(text)));
        return new UiDescriptionTooltip.Line(line, text);
    }

    // 纯提示行：整行使用弱化色
    private static UiDescriptionTooltip.Line note(String key, UiHostTheme theme) {
        int muted = theme.palette().canvasTextMuted();
        return new UiDescriptionTooltip.Line(Component.translatable(key).withStyle(style -> style.withColor(muted)),
                muted);
    }

    private static Component unset() {
        return Component.translatable(UNSET_KEY);
    }

    // 字段标签优先取描述符注册的键，缺字段时按内建条件键名兜底
    private static Component fieldLabel(ResourceLocation type, String field, String fallbackSuffix) {
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.find(type);
        EditorField editorField = descriptor == null ? null : descriptor.field(field);
        return Component.translatable(editorField == null ? UI + fallbackSuffix : editorField.labelKey());
    }

    // 天气枚举键前缀：分组名优先取描述符，缺失时与表单一致地回落
    private static String weatherEnumPrefix(ResourceLocation type) {
        TypeEditorDescriptor descriptor = ConditionEditorRegistry.find(type);
        EditorField editorField = descriptor == null ? null : descriptor.field(FIELD_WEATHER);
        String group = editorField == null ? null : editorField.enumGroup();
        return UI + "enum." + (group == null || group.isBlank() ? "weather_kind" : group);
    }

    private static @Nullable Component rangeOf(@Nullable RuleValue range) {
        if (range == null || !range.isMapping()) {
            return null;
        }
        return SummaryFormats.range(
                SummaryFormats.numberText(range.child(FIELD_MIN)),
                SummaryFormats.numberText(range.child(FIELD_MAX)));
    }

    private static @Nullable ResourceLocation typeOf(RuleValue condition) {
        RuleValue type = condition.child(RuleFields.TYPE);
        if (type == null || !type.isScalar()) {
            return null;
        }
        String text = type.asText("");
        return text.isBlank() ? null : ResourceLocation.tryParse(text);
    }

    private static @Nullable RuleValue childOf(@Nullable RuleValue parent, String key) {
        return parent == null || !parent.isMapping() ? null : parent.child(key);
    }

    private static int countOf(@Nullable RuleValue value) {
        return value != null && value.isNumber() ? Math.max(0, value.asInteger(0)) : 0;
    }

    private static @Nullable Integer integerOf(@Nullable RuleValue value) {
        return value != null && value.isNumber() ? value.asInteger(0) : null;
    }

    // item_counts 的键是 TaggedId.serialized()；归一化失败时退回原文
    private static String canonical(String raw) {
        String value = raw == null ? "" : raw.trim();
        TaggedId tagged = TaggedId.parse(value).result().orElse(null);
        return tagged == null ? value : tagged.serialized();
    }
}
