package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.EntityPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/*** 管理页和动作导航共用产出图标；图标只表示类型，文字保留完整方案信息。 */
public final class RulePreviewIcons {
    private static final java.util.Map<String, UiIcon> ICONS = new java.util.LinkedHashMap<>(32, 0.75F, true);
    private RulePreviewIcons() { }
    public static void clear() { ICONS.clear(); }

    public static @Nullable UiIcon action(@Nullable RuleValue action) {
        if (action == null) return null;
        String key = text(action, "type") + "/" + text(action, "variant") + "/" + text(action, "item") + "/"
                + text(action, "entity") + "/" + text(action, "block") + "/" + (action.has("age") ? action.child("age") : "0");
        if (ICONS.containsKey(key)) return ICONS.get(key);
        UiIcon icon = createAction(action);
        ICONS.put(key, icon);
        while (ICONS.size() > 256) ICONS.remove(ICONS.keySet().iterator().next());
        return icon;
    }

    private static @Nullable UiIcon createAction(@Nullable RuleValue action) {
        if (action == null) return null;
        String type = text(action, "type");
        if (type.equals("itemdespawntowhat:spawn_entity")) {
            return switch (text(action, "variant")) {
                case "item" -> CatalogPreviewIcons.iconFor(RuleCatalogType.ITEM, text(action, "item"));
                case "entity" -> text(action, "entity").startsWith("#") ? TagPreviewIcons.resolve(RuleCatalogType.ENTITY, text(action, "entity")).icon()
                        : EntityPreviewIcons.icon(text(action, "entity"), age(action));
                case "experience" -> EntityPreviewIcons.icon("minecraft:experience_orb", 0);
                default -> null;
            };
        }
        return switch (type) {
            case "itemdespawntowhat:place_block" -> CatalogPreviewIcons.iconFor(RuleCatalogType.BLOCK, text(action, "block"));
            case "itemdespawntowhat:loot_table" -> new UiIcon.Item(new ItemStack(Items.CHEST));
            case "itemdespawntowhat:lightning" -> new UiIcon.Item(new ItemStack(Items.LIGHTNING_ROD));
            case "itemdespawntowhat:explosion" -> new UiIcon.Item(new ItemStack(Items.TNT));
            case "itemdespawntowhat:arrow_rain" -> new UiIcon.Item(new ItemStack(Items.ARROW));
            default -> null;
        };
    }

    public static @Nullable UiIcon rule(@Nullable RuleValue rule) {
        if (rule == null) return null;
        UiIcon icon = actions(rule.child("effects"), rule);
        if (icon != null) return icon;
        RuleValue outcomes = rule.child("outcomes");
        if (outcomes != null && outcomes.isList()) for (RuleValue outcome : outcomes.items()) {
            if (outcome.isMapping()) { icon = actions(outcome.child("effects"), rule); if (icon != null) return icon; }
        }
        return null;
    }

    private static @Nullable UiIcon actions(@Nullable RuleValue value, RuleValue rule) {
        if (value != null && value.isList()) for (RuleValue entry : value.items()) {
            if (entry.isMapping()) {
                if (text(entry, "type").equals("itemdespawntowhat:place_block")
                        && entry.has("use_source_block") && boolOf(entry.child("use_source_block"))) {
                    String key = "source-block/" + RuleRecipeView.firstSource(rule);
                    if (!ICONS.containsKey(key)) {
                        ICONS.put(key, RuleRecipeView.firstSourceIcon(rule));
                        while (ICONS.size() > 256) ICONS.remove(ICONS.keySet().iterator().next());
                    }
                    return ICONS.get(key);
                }
                UiIcon icon = action(entry);
                if (icon != null) return icon;
            }
        }
        return null;
    }

    // 只报告实际展示的首个产出图标，避免其他动作的失败误标到当前预览。
    public static boolean unavailable(@Nullable RuleValue rule) {
        if (rule == null) return false;
        RuleValue first = firstAction(rule.child("effects"));
        if (first == null) {
            RuleValue outcomes = rule.child("outcomes");
            if (outcomes != null && outcomes.isList()) for (RuleValue outcome : outcomes.items()) {
                if (outcome.isMapping()) { first = firstAction(outcome.child("effects")); if (first != null) break; }
            }
        }
        if (first == null || !text(first, "type").equals("itemdespawntowhat:spawn_entity")) return false;
        return switch (text(first, "variant")) {
            case "entity" -> EntityPreviewIcons.unavailable(text(first, "entity"), age(first));
            case "experience" -> EntityPreviewIcons.unavailable("minecraft:experience_orb", 0);
            default -> false;
        };
    }

    private static @Nullable RuleValue firstAction(@Nullable RuleValue value) {
        if (value != null && value.isList()) for (RuleValue entry : value.items()) {
            if (entry.isMapping() && action(entry) != null) return entry;
        }
        return null;
    }

    // 损坏的旧草稿仍须能进入列表；参数错误由表单/服务端报告。
    private static int age(RuleValue action) {
        RuleValue value = action.child("age");
        if (value == null || !value.isScalar()) return 0;
        try { return Integer.parseInt(value.asText().trim()); }
        catch (RuntimeException exception) { return 0; }
    }

    // 兼容迁移前的 JsonElement#getAsBoolean：布尔标量直读，文本标量按 Boolean.parseBoolean 宽松解析
    private static boolean boolOf(@Nullable RuleValue value) {
        if (value == null || !value.isScalar()) return false;
        return value.isFlag() ? value.asFlag(false) : Boolean.parseBoolean(value.asText());
    }

    private static String text(RuleValue object, String key) {
        RuleValue value = object.child(key);
        return value != null && value.isText() ? value.asText() : "";
    }
}
