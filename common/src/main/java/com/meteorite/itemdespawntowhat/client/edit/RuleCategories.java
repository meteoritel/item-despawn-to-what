package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import org.jetbrains.annotations.Nullable;
import java.util.Set;

/**
 * 未声明分类时的虚拟分类判定：只依据全部方案中的产出动作，既不改文件路径，也不重复列出规则。
 * 快照条目已携带分类时由调用方直接采用该值，不进入这里。
 */
public final class RuleCategories {
    private RuleCategories() { }

    /**
     * 优先采用快照下发的分类，未下发时回落到按产出的判定。
     *
     * @param declared 快照条目携带的显式分类，允许为 null 或空白
     * @param id 规则 id，允许为 null
     * @param rule 规则体，允许为 null
     * @return 分类标识；缺省时等同 {@link #category(String, RuleValue)}
     */
    public static String category(@Nullable String declared, @Nullable String id, @Nullable RuleValue rule) {
        return declared == null || declared.isBlank() ? category(id, rule) : declared;
    }

    /**
     * 按规则体判定分类。
     *
     * @param rule 规则体，允许为 null
     * @return 分类标识；无规则体时按无产出归入扩展分类
     */
    public static String category(@Nullable RuleValue rule) {
        return categoryJson(rule == null || !rule.isMapping() ? null : rule.unwrap().getAsJsonObject());
    }

    /**
     * 按规则 id 与规则体判定分类，用于调试测试规则这类需要独立归组的规则。
     *
     * <p>测试规则的内容仍是普通产出动作，仅凭规则体无法与真实规则区分；
     * 因此先按 id 判定归属，再回落到常规的产出动作判定。</p>
     *
     * @param id 规则 id，允许为 null
     * @param rule 规则体，允许为 null
     * @return 分类标识；测试规则固定为 {@link DebugTestRules#CATEGORY}
     */
    public static String category(@Nullable String id, @Nullable RuleValue rule) {
        if (DebugTestRules.isTestRule(id)) {
            return DebugTestRules.CATEGORY;
        }
        return category(rule);
    }

    // 分类判定：输入为规则 JSON 对象
    private static String categoryJson(@Nullable JsonObject rule) {
        Set<String> outputs = new LinkedHashSet<>();
        if (rule != null) {
            scan(rule.get("effects"), outputs);
            JsonElement raw = rule.get("outcomes");
            if (raw instanceof JsonArray outcomes) {
                for (JsonElement outcome : outcomes) {
                    if (outcome.isJsonObject()) scan(outcome.getAsJsonObject().get("effects"), outputs);
                }
            }
        }
        if (outputs.isEmpty() || outputs.contains("extension")) return "extension";
        Set<String> roots = new LinkedHashSet<>();
        outputs.forEach(value -> roots.add(value.startsWith("entity.") ? "entity" : value));
        if (roots.size() > 1) return "mixed";
        if (roots.contains("entity")) return outputs.size() > 1 ? "entity.mixed" : outputs.iterator().next();
        return roots.iterator().next();
    }

    private static void scan(JsonElement raw, Set<String> outputs) {
        if (!(raw instanceof JsonArray actions)) return;
        for (JsonElement value : actions) {
            if (!value.isJsonObject()) { outputs.add("extension"); continue; }
            JsonObject action = value.getAsJsonObject();
            String type = text(action, "type");
            if (!type.startsWith(TypeLabels.OWN_NAMESPACE + ":")) { outputs.add("extension"); continue; }
            switch (type.substring(type.indexOf(':') + 1)) {
                case "consume_source", "consume_catalyst", "consume_fluid" -> { }
                case "spawn_entity" -> {
                    String variant = text(action, "variant");
                    outputs.add(Set.of("item", "entity", "experience").contains(variant) ? "entity." + variant : "extension");
                }
                case "place_block" -> outputs.add("block");
                case "loot_table" -> outputs.add("loot");
                case "lightning", "explosion", "arrow_rain", "weather" -> outputs.add("world");
                default -> outputs.add("extension");
            }
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
}
