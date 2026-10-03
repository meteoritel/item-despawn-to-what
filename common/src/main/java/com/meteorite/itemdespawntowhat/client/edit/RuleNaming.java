package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.model.CombinationMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 只读自动命名与短摘要服务（主计划 §6）：本地化模板 + Component 参数。
 * <p>解析优先级：非空 {@code display_name} → 自动标题 → 安全兜底（规则 id 文本 / 未完成占位）。
 * <p>标题只用于展示：不写 JSON、不决定覆盖文件名、不改变游标指纹；{@code notes} 不参与命名。
 * <p>资源名通过 {@link NameSource} 注入；目录未加载或缺失时回落 {@code namespace:path} 文本
 * （标签保留前导 {@code #}），绝不发起请求。本类不 import 界面、网络与服务端运行时。
 * <p>所有取值都容忍畸变 JSON：任何异常都回落到安全文本，不让命名失败阻止界面打开。
 */
public final class RuleNaming {

    // 命名模板键前缀
    public static final String PREFIX = TypeLabels.UI_PREFIX + "name.";

    // 代表项筛选时要跳过的固定成本效果
    private static final String CONSUME_SOURCE = "consume_source";
    private static final String CONSUME_CATALYST = "consume_catalyst";

    // 工具类
    private RuleNaming() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 名称来源：由界面注入（客户端注册表/语言文件），未加载或缺失返回 null。
     */
    public interface NameSource {

        // 解析资源显示名；不可用返回 null
        @Nullable Component label(ResourceLocation id);

        // 空实现：全部回落到 id 文本
        NameSource NONE = id -> null;
    }

    // 规则标题（传草稿视图）
    public static Component ruleTitle(@Nullable RuleDraft draft, NameSource source) {
        return ruleTitle(draft == null ? null : draft.view(), source);
    }

    /**
     * 规则标题：display_name → 自动模板 → 安全兜底（id 文本）。
     * <p>多候选规则用候选轮询/优先模板，其余（含顶层单结果）用单结果模板。
     */
    public static Component ruleTitle(@Nullable JsonObject rule, NameSource source) {
        if (rule == null) {
            return Component.translatable(PREFIX + "unfinished_no_result");
        }
        try {
            String displayName = string(rule, RuleFields.DISPLAY_NAME, null);
            if (displayName != null && !displayName.isBlank()) {
                return Component.literal(displayName.trim());
            }
            Component sourcePart = sourceTitle(rule, source);
            int candidates = sizeOf(rule, RuleFields.OUTCOMES);
            if (candidates > 1) {
                return Component.translatable(PREFIX + multiResultKey(rule), sourcePart, candidates);
            }
            return Component.translatable(PREFIX + "single_result", sourcePart, resultTitle(rule, source));
        } catch (RuntimeException error) {
            return safeFallback(rule);
        }
    }

    // 源物品短摘要：未选源 → 未完成占位；多项 → 多输入模板
    public static Component sourceTitle(@Nullable JsonObject rule, NameSource source) {
        List<JsonElement> entries = entries(rule, RuleFields.SOURCE, RuleFields.SOURCE_ITEMS);
        if (entries.isEmpty()) {
            return Component.translatable(PREFIX + "unfinished_no_source");
        }
        Component first = entryLabel(entries.getFirst(), source);
        int others = entries.size() - 1;
        if (others <= 0) {
            return first;
        }
        return Component.translatable(PREFIX + "multi_input", first, others);
    }

    // 结果短摘要：候选列表 → 第一个候选；顶层 effects → 效果列表
    public static Component resultTitle(@Nullable JsonObject rule, NameSource source) {
        JsonArray outcomes = arrayOf(rule, RuleFields.OUTCOMES);
        if (outcomes != null && !outcomes.isEmpty()) {
            return candidateTitle(asObject(outcomes.get(0)), 1, source);
        }
        JsonArray effects = arrayOf(rule, RuleFields.EFFECTS);
        if (effects == null || effects.isEmpty()) {
            return Component.translatable(PREFIX + "unfinished_no_result");
        }
        return effectListTitle(effects, source);
    }

    // 候选标题："结果 %1$s：%2$s"
    public static Component candidateTitle(@Nullable JsonObject candidate, int ordinal, NameSource source) {
        JsonArray effects = candidate == null ? null : arrayOf(candidate, RuleFields.CANDIDATE_EFFECTS);
        Component body = effects == null || effects.isEmpty()
                ? Component.translatable(PREFIX + "unfinished_no_result")
                : effectListTitle(effects, source);
        return Component.translatable(PREFIX + "candidate", ordinal, body);
    }

    // 效果列表摘要：代表项 + 「另有 n 项」
    public static Component effectListTitle(@Nullable JsonArray effects, NameSource source) {
        List<JsonObject> list = objects(effects);
        if (list.isEmpty()) {
            return Component.translatable(PREFIX + "unfinished_no_result");
        }
        Component first = effectTitle(representative(list), source);
        int others = list.size() - 1;
        if (others <= 0) {
            return first;
        }
        return Component.translatable(PREFIX + "multi_effect", first, others);
    }

    /**
     * 单个效果的短标题。
     * <p>本模组 12 种效果走本地化短标题；第三方或未知类型走 {@link TypeLabels#effectLabel(ResourceLocation)}
     * （其自身已有 literal(id) 兜底）。
     */
    public static Component effectTitle(@Nullable JsonObject effect, NameSource source) {
        if (effect == null) {
            return Component.translatable(PREFIX + "unfinished_no_result");
        }
        String rawType = string(effect, RuleFields.TYPE, "");
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        if (type == null) {
            return rawType.isBlank() ? Component.translatable(PREFIX + "unfinished_no_result") : Component.literal(rawType);
        }
        return switch (type.getPath()) {
            case "spawn_item" -> withLabel(PREFIX + "effect.spawn_item", effect, "item", source, type);
            case "spawn_entity" -> withLabel(PREFIX + "effect.spawn_entity", effect, "entity", source, type);
            case "place_block" -> booleanOf(effect, "use_source_block", false)
                    ? Component.translatable(PREFIX + "effect.place_block_source")
                    : withLabel(PREFIX + "effect.place_block", effect, "block", source, type);
            case "spawn_xp" -> Component.translatable(PREFIX + "effect.spawn_xp");
            case "loot_table" -> Component.translatable(PREFIX + "effect.loot_table");
            case "lightning" -> Component.translatable(PREFIX + "effect.lightning");
            case "arrow_rain" -> Component.translatable(PREFIX + "effect.arrow_rain");
            case "weather" -> Component.translatable(PREFIX + weatherKey(effect));
            case "explosion" -> Component.translatable(PREFIX + "effect.explosion");
            case "consume_fluid" -> Component.translatable(PREFIX + "effect.consume_fluid");
            case "consume_source" -> Component.translatable(PREFIX + "effect.consume_source");
            case "consume_catalyst" -> Component.translatable(PREFIX + "effect.consume_catalyst");
            default -> TypeLabels.effectLabel(type);
        };
    }

    // 多候选模板键：combination=priority 用「按顺序选择」，其余（含缺省 round_robin）用「轮询」
    private static String multiResultKey(JsonObject rule) {
        String combination = string(rule, RuleFields.COMBINATION, CombinationMode.ROUND_ROBIN.key());
        return CombinationMode.PRIORITY.key().equalsIgnoreCase(combination)
                ? "multi_result_priority"
                : "multi_result_cycle";
    }

    // 代表项：第一个非固定成本效果；全是成本效果时取第一项（如实命名成本）
    private static JsonObject representative(List<JsonObject> effects) {
        for (JsonObject effect : effects) {
            String path = typePath(effect);
            if (!CONSUME_SOURCE.equals(path) && !CONSUME_CATALYST.equals(path)) {
                return effect;
            }
        }
        return effects.getFirst();
    }

    // 带资源名参数的效果短标题；资源名缺失时回落类型标签
    private static Component withLabel(String key, JsonObject effect, String field, NameSource source, ResourceLocation type) {
        Component label = entryLabel(effect.get(field), source);
        return label == null ? TypeLabels.effectLabel(type) : Component.translatable(key, label);
    }

    // 天气效果短标题：clear → 放晴，其余按 thundering 分降雨/雷雨
    private static String weatherKey(JsonObject effect) {
        String mode = string(effect, "mode", "rain");
        if ("clear".equalsIgnoreCase(mode)) {
            return "effect.weather_clear";
        }
        return booleanOf(effect, "thundering", false) ? "effect.weather_thunder" : "effect.weather_rain";
    }

    // 效果类型 path（无法解析时返回空串）
    private static String typePath(JsonObject effect) {
        String raw = string(effect, RuleFields.TYPE, "");
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? "" : id.getPath();
    }

    // 条目名解析：标签保留 # 前缀；目录名不可用时回落 namespace:path 文本
    private static @Nullable Component entryLabel(@Nullable JsonElement element, NameSource source) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        String raw = element.getAsString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        boolean tag = raw.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
        if (id == null) {
            return Component.literal(raw);
        }
        Component label = source == null ? null : source.label(id);
        if (label != null) {
            return label;
        }
        return Component.literal(tag ? "#" + id : id.toString());
    }

    // 安全兜底：id 文本；连 id 都没有时用未完成占位
    private static Component safeFallback(JsonObject rule) {
        String id = string(rule, RuleFields.ID, null);
        if (id == null || id.isBlank()) {
            return Component.translatable(PREFIX + "unfinished_no_result");
        }
        return Component.literal(id);
    }

    // 取对象字段中的源条目数组
    private static List<JsonElement> entries(@Nullable JsonObject rule, String field, String listField) {
        JsonObject container = asObject(rule == null ? null : rule.get(field));
        JsonArray array = arrayOf(container, listField);
        List<JsonElement> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && !element.getAsString().isBlank()) {
                out.add(element);
            }
        }
        return out;
    }

    // 数组长度（非数组按 0）
    private static int sizeOf(@Nullable JsonObject object, String field) {
        JsonArray array = arrayOf(object, field);
        return array == null ? 0 : array.size();
    }

    // 取对象字段中的数组
    private static @Nullable JsonArray arrayOf(@Nullable JsonObject object, String field) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(field);
        return element instanceof JsonArray array ? array : null;
    }

    // 数组中的对象项
    private static List<JsonObject> objects(@Nullable JsonArray array) {
        List<JsonObject> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                out.add(element.getAsJsonObject());
            }
        }
        return out;
    }

    // 转对象（可能为 null）
    private static @Nullable JsonObject asObject(@Nullable JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    // 读字符串字段（缺省时返回 fallback）
    private static String string(JsonObject object, String field, @Nullable String fallback) {
        JsonElement element = object.get(field);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return fallback;
    }

    // 读布尔字段（缺省时返回 fallback）
    private static boolean booleanOf(JsonObject object, String field, boolean fallback) {
        JsonElement element = object.get(field);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return fallback;
    }
}