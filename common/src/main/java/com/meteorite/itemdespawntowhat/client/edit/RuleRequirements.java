package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/*** 缺少必填项的导航清单；只检查可静态确认的输入与内置产出，不替代服务端校验。 */
public final class RuleRequirements {
    /*** 路径用于已有字段定位，标签使用宿主本地化 key。 */
    public record Missing(String path, String labelKey) { }
    private static final String UI = "gui.itemdespawntowhat.edit.";
    private RuleRequirements() { }

    public static List<Missing> find(JsonObject rule) {
        List<Missing> result = new ArrayList<>();
        JsonObject source = object(rule.get("source"));
        if (array(source.get("items")).isEmpty()) result.add(new Missing("source.items", UI + "rule.source.items"));
        boolean any = inspect(array(rule.get("effects")), "effects", result);
        JsonArray outcomes = array(rule.get("outcomes"));
        for (int i = 0; i < outcomes.size(); i++) {
            any |= inspect(array(object(outcomes.get(i)).get("effects")), "outcomes[" + i + "].effects", result);
        }
        if (!any) result.add(new Missing("effects", UI + "required.results"));
        return List.copyOf(result);
    }

    private static boolean inspect(JsonArray actions, String path, List<Missing> missing) {
        boolean any = !actions.isEmpty();
        for (int i = 0; i < actions.size(); i++) {
            JsonObject action = object(actions.get(i));
            String type = text(action, "type");
            if (java.util.Set.of("itemdespawntowhat:consume_source", "itemdespawntowhat:consume_catalyst", "itemdespawntowhat:consume_fluid").contains(type)) continue;
            any = true;
            String field = switch (type) {
                case "itemdespawntowhat:spawn_entity" -> switch (text(action, "variant")) {
                    case "item" -> "item";
                    case "entity" -> "entity";
                    case "experience" -> "";
                    default -> "variant";
                };
                case "itemdespawntowhat:place_block" -> action.has("use_source_block") && action.get("use_source_block").isJsonPrimitive()
                        && action.get("use_source_block").getAsBoolean() ? "" : "block";
                case "itemdespawntowhat:loot_table" -> "loot_table";
                default -> "";
            };
            if (!field.isEmpty() && text(action, field).isBlank()) missing.add(new Missing(path + "[" + i + "]." + field,
                    UI + "required." + field));
        }
        return any;
    }

    private static JsonObject object(JsonElement value) { return value instanceof JsonObject object ? object : new JsonObject(); }
    private static JsonArray array(JsonElement value) { return value instanceof JsonArray array ? array : new JsonArray(); }
    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
}
