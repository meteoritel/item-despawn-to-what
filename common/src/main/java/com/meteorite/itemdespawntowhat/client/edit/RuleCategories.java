package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Set;

/*** 虚拟分类只依据全部方案中的产出动作，既不改文件路径，也不重复列出规则。 */
public final class RuleCategories {
    private RuleCategories() { }

    public static String category(JsonObject rule) {
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
