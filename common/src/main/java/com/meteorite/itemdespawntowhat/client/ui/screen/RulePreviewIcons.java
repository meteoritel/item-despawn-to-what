package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/*** 管理页和动作导航共用产出图标；图标只表示类型，文字保留完整方案信息。 */
public final class RulePreviewIcons {
    private static final java.util.Map<String, UiIcon> ICONS = new java.util.LinkedHashMap<>(32, 0.75F, true);
    private RulePreviewIcons() { }
    public static void clear() { ICONS.clear(); }

    public static @Nullable UiIcon action(@Nullable JsonObject action) {
        if (action == null) return null;
        String key = text(action, "type") + "/" + text(action, "variant") + "/" + text(action, "item") + "/"
                + text(action, "entity") + "/" + text(action, "block") + "/" + (action.has("age") ? action.get("age") : "0");
        if (ICONS.containsKey(key)) return ICONS.get(key);
        UiIcon icon = createAction(action);
        ICONS.put(key, icon);
        while (ICONS.size() > 256) ICONS.remove(ICONS.keySet().iterator().next());
        return icon;
    }

    private static @Nullable UiIcon createAction(@Nullable JsonObject action) {
        if (action == null) return null;
        String type = text(action, "type");
        if (type.equals("itemdespawntowhat:spawn_entity")) {
            return switch (text(action, "variant")) {
                case "item" -> RuleEditorP4Panels.iconFor(RuleCatalogType.ITEM, text(action, "item"));
                case "entity" -> text(action, "entity").startsWith("#") ? new UiIcon.Item(new ItemStack(Items.NAME_TAG))
                        : EntityPreviewIcons.icon(text(action, "entity"), age(action));
                case "experience" -> EntityPreviewIcons.icon("minecraft:experience_orb", 0);
                default -> null;
            };
        }
        return switch (type) {
            case "itemdespawntowhat:place_block" -> RuleEditorP4Panels.iconFor(RuleCatalogType.BLOCK, text(action, "block"));
            case "itemdespawntowhat:loot_table" -> new UiIcon.Item(new ItemStack(Items.CHEST));
            case "itemdespawntowhat:lightning" -> new UiIcon.Item(new ItemStack(Items.LIGHTNING_ROD));
            case "itemdespawntowhat:explosion" -> new UiIcon.Item(new ItemStack(Items.TNT));
            case "itemdespawntowhat:arrow_rain" -> new UiIcon.Item(new ItemStack(Items.ARROW));
            default -> null;
        };
    }

    public static @Nullable UiIcon rule(@Nullable JsonObject rule) {
        if (rule == null) return null;
        UiIcon icon = actions(rule.get("effects"));
        if (icon != null) return icon;
        if (rule.get("outcomes") instanceof JsonArray outcomes) for (JsonElement outcome : outcomes) {
            if (outcome.isJsonObject()) { icon = actions(outcome.getAsJsonObject().get("effects")); if (icon != null) return icon; }
        }
        return null;
    }

    private static @Nullable UiIcon actions(JsonElement value) {
        if (value instanceof JsonArray array) for (JsonElement entry : array) {
            if (entry.isJsonObject()) { UiIcon icon = action(entry.getAsJsonObject()); if (icon != null) return icon; }
        }
        return null;
    }

    // 只报告实际展示的首个产出图标，避免其他动作的失败误标到当前预览。
    public static boolean unavailable(@Nullable JsonObject rule) {
        if (rule == null) return false;
        JsonObject first = firstAction(rule.get("effects"));
        if (first == null && rule.get("outcomes") instanceof JsonArray outcomes) for (JsonElement outcome : outcomes) {
            if (outcome.isJsonObject()) { first = firstAction(outcome.getAsJsonObject().get("effects")); if (first != null) break; }
        }
        if (first == null || !text(first, "type").equals("itemdespawntowhat:spawn_entity")) return false;
        return switch (text(first, "variant")) {
            case "entity" -> EntityPreviewIcons.unavailable(text(first, "entity"), age(first));
            case "experience" -> EntityPreviewIcons.unavailable("minecraft:experience_orb", 0);
            default -> false;
        };
    }

    private static @Nullable JsonObject firstAction(JsonElement value) {
        if (value instanceof JsonArray array) for (JsonElement entry : array) {
            if (entry.isJsonObject() && action(entry.getAsJsonObject()) != null) return entry.getAsJsonObject();
        }
        return null;
    }

    // 损坏的旧草稿仍须能进入列表；参数错误由表单/服务端报告。
    private static int age(JsonObject action) {
        try { return action.has("age") ? action.get("age").getAsInt() : 0; }
        catch (RuntimeException exception) { return 0; }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
}
