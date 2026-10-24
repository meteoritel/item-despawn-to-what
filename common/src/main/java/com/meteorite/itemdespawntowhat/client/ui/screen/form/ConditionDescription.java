package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorRegistries;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeEditorDescriptor;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine;
import com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine.Tone;
import com.meteorite.itemdespawntowhat.client.ui.shared.SummaryFormats;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 条件叶的参数说明：把条件 JSON 拆成按信息类型排版的语义行，供条件画布与规则列表的悬停提示共用。
 *
 * <p><b>职责</b>：只生成参数行，不生成标题与结构状态；标题行和问题标记由调用方追加，
 * 保证问题颜色不会被取值颜色覆盖。</p>
 *
 * <p><b>按信息类型分行</b>：注册表引用按条目逐行列出；区间、时刻与数值按「标签 + 取值」对齐显示；
 * 方向映射每个已填方向一行；物品数量按「名称 × 数量」的条目展示；枚举取值单独一行；
 * 无参数条件用一句说明代替空白。列表类条目超过上限时追加总数提示，不隐瞒条目数。</p>
 *
 * <p><b>语气</b>：引用用 {@link Tone#REFERENCE}，数值与时刻用 {@link Tone#METRIC}，
 * 枚举与开关用 {@link Tone#VALUE}，说明与未设置提示用 {@link Tone#NOTE}；具体颜色由渲染方按主题映射。</p>
 *
 * <p>未注册的第三方条件回落到 {@link NaturalSummary#conditionParams(RuleValue)}，无法读取时只提示查看原始 JSON。</p>
 */
public final class ConditionDescription {

    // ---- 文案键 ----
    private static final String UI = TypeLabels.UI_PREFIX;
    private static final String TOOLTIP = UI + "tooltip.condition.";
    private static final String SUMMARY = SummaryFormats.KEY_PREFIX;
    private static final String RANGE_LABEL_KEY = TOOLTIP + "range";
    private static final String NOTE_LABEL_KEY = TOOLTIP + "note";
    private static final String HEIGHT_LABEL_KEY = TOOLTIP + "height";
    private static final String LIGHT_LABEL_KEY = TOOLTIP + "light";
    private static final String TIME_SPAN_LABEL_KEY = TOOLTIP + "time_span";
    private static final String ITEMS_HEADING_KEY = TOOLTIP + "items_heading";
    private static final String UNREADABLE_KEY = TOOLTIP + "unreadable";
    private static final String UNSET_KEY = SUMMARY + "value_empty";
    private static final String MORE_KEY = SUMMARY + "cond.more";

    private ConditionDescription() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成条件叶的参数说明行。
     *
     * @param condition 条件叶的 JSON 对象，可为 {@code null}
     * @param indent 基础缩进层级，调用方按标题所在层级给出
     * @return 参数说明行；条件无法读取时返回一条提示，永不返回空列表
     */
    public static List<DescriptionLine> lines(@Nullable RuleValue condition, int indent) {
        if (condition == null || !condition.isMapping()) {
            return List.of(note(UNREADABLE_KEY, indent));
        }
        ResourceLocation type = typeOf(condition);
        if (type == null) {
            return List.of(note(UNREADABLE_KEY, indent));
        }
        if (!TypeLabels.OWN_NAMESPACE.equals(type.getNamespace())) {
            return generic(condition, type, indent);
        }
        return switch (type.getPath()) {
            case "dimension" -> refEntries(condition.child(RuleFields.CONDITION_DIMENSIONS), RuleCatalogType.DIMENSION, indent);
            case "biome" -> biome(condition, type, indent);
            case "weather" -> weather(condition, type, indent);
            case "outdoor" -> List.of(note(SUMMARY + "cond.outdoor", indent));
            case "surrounding_blocks" -> surroundingBlocks(condition, type, indent);
            case "catalyst_present" -> catalystPresent(condition, indent);
            case "fluid_present" -> fluidPresent(condition, indent);
            case "time_of_day" -> timeOfDay(condition, indent);
            case "y_level" -> bounded(condition, "cond.y_level.any", HEIGHT_LABEL_KEY, indent);
            case "light_level" -> bounded(condition, "cond.light_level.any", LIGHT_LABEL_KEY, indent);
            default -> generic(condition, type, indent);
        };
    }

    // 注册表引用：一条一行，超过上限时追加总数
    private static List<DescriptionLine> refEntries(@Nullable RuleValue list, RuleCatalogType type, int indent) {
        if (list == null || !list.isList()) {
            return List.of(note(UNSET_KEY, indent));
        }
        List<String> raws = list.textItems();
        if (raws.isEmpty()) {
            return List.of(note(UNSET_KEY, indent));
        }
        List<DescriptionLine> lines = new ArrayList<>();
        int shown = Math.min(raws.size(), SummaryFormats.MAX_ENTRIES);
        for (int index = 0; index < shown; index++) {
            lines.add(DescriptionLine.entry(SummaryFormats.ref(raws.get(index), type), Tone.REFERENCE, indent));
        }
        if (raws.size() > shown) {
            lines.add(DescriptionLine.note(Component.translatable(MORE_KEY, raws.size()), indent));
        }
        return lines;
    }

    // 群系：先显示模式，再按模式显示群系列表或气候区间
    private static List<DescriptionLine> biome(RuleValue condition, ResourceLocation type, int indent) {
        List<DescriptionLine> lines = new ArrayList<>();
        String mode = condition.text(RuleFields.CONDITION_MODE, "");
        Component modeText = SummaryFormats.enumText(mode, SUMMARY + "cond.biome.mode");
        lines.add(field(fieldLabel(type, RuleFields.CONDITION_MODE, "field.biome.mode"), modeText, Tone.VALUE, indent));
        if (RuleFields.CONDITION_MODE_CLIMATE.equals(mode)) {
            boolean anyRange = false;
            for (String axis : RuleFields.CONDITION_AXES) {
                Component range = rangeOf(condition.child(axis));
                if (range != null) {
                    lines.add(field(fieldLabel(type, axis, "field.biome." + axis), range, Tone.METRIC, indent));
                    anyRange = true;
                }
            }
            if (!anyRange) {
                lines.add(field(Component.translatable(RANGE_LABEL_KEY), null, Tone.NOTE, indent));
            }
        } else {
            lines.addAll(refEntries(condition.child(RuleFields.CONDITION_BIOMES), RuleCatalogType.BIOME, indent));
        }
        return lines;
    }

    // 天气：取值单独一行；未选择或枚举无法识别时显示未设置
    private static List<DescriptionLine> weather(RuleValue condition, ResourceLocation type, int indent) {
        Component value = SummaryFormats.enumText(condition.text(RuleFields.CONDITION_WEATHER, ""), weatherEnumPrefix(type));
        return List.of(value == null ? note(UNSET_KEY, indent)
                : DescriptionLine.text(value, Tone.VALUE, indent));
    }

    // 周围方块：每个已填方向单独一行
    private static List<DescriptionLine> surroundingBlocks(RuleValue condition, ResourceLocation type, int indent) {
        List<DescriptionLine> lines = new ArrayList<>();
        for (String side : RuleFields.CONDITION_SIDES) {
            String raw = condition.text(side, "").trim();
            if (raw.isEmpty()) {
                continue;
            }
            lines.add(field(fieldLabel(type, side, "field.surrounding_blocks." + side),
                    SummaryFormats.ref(raw, RuleCatalogType.BLOCK), Tone.REFERENCE, indent));
        }
        if (lines.isEmpty()) {
            lines.add(note(UNSET_KEY, indent));
        }
        return lines;
    }

    // 催化剂：逐物品显示「名称 × 数量」，数量优先取 item_counts，其次 count
    private static List<DescriptionLine> catalystPresent(RuleValue condition, int indent) {
        RuleValue items = condition.child(RuleFields.CONDITION_ITEMS);
        if (items == null || !items.isList()) {
            return List.of(note(UNSET_KEY, indent));
        }
        List<String> raws = items.textItems();
        if (raws.isEmpty()) {
            return List.of(note(UNSET_KEY, indent));
        }
        RuleValue counts = condition.child(RuleFields.ITEM_COUNTS);
        int defaultCount = countOf(condition.child(RuleFields.CONDITION_COUNT));
        List<DescriptionLine> lines = new ArrayList<>();
        if (raws.size() > 1) {
            lines.add(DescriptionLine.section(Component.translatable(ITEMS_HEADING_KEY, raws.size()), indent));
        }
        for (String raw : raws) {
            int count = countOf(childOf(counts, raw));
            if (count <= 0) {
                count = countOf(childOf(counts, canonical(raw)));
            }
            if (count <= 0) {
                count = defaultCount;
            }
            Component entry = SummaryFormats.ref(raw, RuleCatalogType.ITEM);
            Component text = count > 0
                    ? Component.translatable(SUMMARY + "cond.catalyst_present.entry", entry, count)
                    : entry;
            lines.add(DescriptionLine.entry(text, Tone.REFERENCE, indent));
        }
        return lines;
    }

    // 流体：列表为空时退回单数 fluid，两者皆空表示任意流体
    private static List<DescriptionLine> fluidPresent(RuleValue condition, int indent) {
        RuleValue list = condition.child(RuleFields.FLUIDS);
        if (list != null && list.isList() && !list.textItems().isEmpty()) {
            return refEntries(list, RuleCatalogType.FLUID, indent);
        }
        String single = condition.text(RuleFields.FLUID, "").trim();
        if (!single.isEmpty()) {
            return List.of(DescriptionLine.entry(SummaryFormats.ref(single, RuleCatalogType.FLUID),
                    Tone.REFERENCE, indent));
        }
        return List.of(note(SUMMARY + "cond.fluid_present.any", indent));
    }

    // 时间：时钟区间与是否跨零点分队两行，区间用数值语气、时段标记用取值语气
    private static List<DescriptionLine> timeOfDay(RuleValue condition, int indent) {
        Integer from = integerOf(condition.child(RuleFields.CONDITION_FROM));
        Integer to = integerOf(condition.child(RuleFields.CONDITION_TO));
        Component span = from == null || to == null ? null
                : SummaryFormats.range(SummaryFormats.clockText(from), SummaryFormats.clockText(to));
        if (span == null) {
            return List.of(note(UNSET_KEY, indent));
        }
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(field(Component.translatable(RANGE_LABEL_KEY), span, Tone.METRIC, indent));
        Component crossing = SummaryFormats.flag(from > to, SUMMARY + "cond.time_of_day.crossing");
        if (crossing != null) {
            lines.add(field(Component.translatable(TIME_SPAN_LABEL_KEY), crossing, Tone.VALUE, indent));
        }
        return lines;
    }

    // 数值区间：两端都未填时显示该维度的「任意」说明
    private static List<DescriptionLine> bounded(RuleValue condition, String anyKeySuffix, String labelKey,
                                                 int indent) {
        Component value = rangeOf(condition);
        if (value == null) {
            return List.of(note(SUMMARY + anyKeySuffix, indent));
        }
        return List.of(field(Component.translatable(labelKey), value, Tone.METRIC, indent));
    }

    // 非内建条件：只读描述符给出只读提示，其余交给通用摘要
    private static List<DescriptionLine> generic(RuleValue condition, ResourceLocation type, int indent) {
        List<DescriptionLine> lines = new ArrayList<>();
        TypeEditorDescriptor descriptor = EditorRegistries.CONDITIONS.find(type);
        if (descriptor != null && descriptor.readOnly()) {
            lines.add(note(UI + "tree.parameters_readonly", indent));
        }
        Component params = NaturalSummary.conditionParams(condition);
        if (params != null) {
            lines.add(DescriptionLine.field(Component.translatable(NOTE_LABEL_KEY), params, Tone.NOTE, indent));
        }
        if (lines.isEmpty()) {
            lines.add(note(UNREADABLE_KEY, indent));
        }
        return lines;
    }

    // 字段行：标签弱化交给渲染方，取值缺失时退化为说明语气
    private static DescriptionLine field(Component label, @Nullable Component value, Tone tone, int indent) {
        return value == null
                ? DescriptionLine.field(label, Component.translatable(UNSET_KEY), Tone.NOTE, indent)
                : DescriptionLine.field(label, value, tone, indent);
    }

    // 纯提示行：整行使用弱化语气
    private static DescriptionLine note(String key, int indent) {
        return DescriptionLine.note(Component.translatable(key), indent);
    }

    // 字段标签优先取描述符注册的键，缺字段时按内建条件键名兜底
    private static Component fieldLabel(ResourceLocation type, String field, String fallbackSuffix) {
        TypeEditorDescriptor descriptor = EditorRegistries.CONDITIONS.find(type);
        EditorField editorField = descriptor == null ? null : descriptor.field(field);
        return Component.translatable(editorField == null ? UI + fallbackSuffix : editorField.labelKey());
    }

    // 天气枚举键前缀：分组名优先取描述符，缺失时与表单一致地回落
    private static String weatherEnumPrefix(ResourceLocation type) {
        TypeEditorDescriptor descriptor = EditorRegistries.CONDITIONS.find(type);
        EditorField editorField = descriptor == null ? null : descriptor.field(RuleFields.CONDITION_WEATHER);
        String group = editorField == null ? null : editorField.enumGroup();
        return UI + "enum." + (group == null || group.isBlank() ? "weather_kind" : group);
    }

    private static @Nullable Component rangeOf(@Nullable RuleValue range) {
        if (range == null || !range.isMapping()) {
            return null;
        }
        return SummaryFormats.range(
                SummaryFormats.numberText(range.child(RuleFields.CONDITION_MIN)),
                SummaryFormats.numberText(range.child(RuleFields.CONDITION_MAX)));
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
