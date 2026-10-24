package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.EntityPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.EventPreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** 管理页和动作导航共用产出图标；图标只表示类型，文字保留完整方案信息。 */
public final class RulePreviewIcons {
    private static final float DISABLED_BRIGHTNESS = 0.35F;
    // 图标缓存上限：列表规模有限，超出后淘汰最久未用的项，避免缓存无限增长
    private static final int MAX_CACHED_ICONS = 256;
    // 未填 age 时按成年实体预览；缓存键与图标构造共用这一默认值
    private static final int DEFAULT_AGE = 0;
    // 标签引用前缀；具体展开交给 TagPreviewIcons
    private static final String TAG_PREFIX = "#";
    // 内置效果类型：预览只区分这几种，命名空间与类型标签保持同一来源
    private static final String OWN_EFFECT_PREFIX = TypeLabels.OWN_NAMESPACE + ":";
    private static final String SPAWN_ENTITY_TYPE = OWN_EFFECT_PREFIX + "spawn_entity";
    private static final String PLACE_BLOCK_TYPE = OWN_EFFECT_PREFIX + "place_block";
    private static final String LOOT_TABLE_TYPE = OWN_EFFECT_PREFIX + "loot_table";
    private static final String EXPLOSION_TYPE = OWN_EFFECT_PREFIX + "explosion";
    // 经验产出没有独立类型，固定用经验球实体图标
    private static final String EXPERIENCE_ORB = "minecraft:experience_orb";
    // 效果专属参数名；RuleFields 只登记跨类型通用字段
    private static final String FIELD_VARIANT = "variant";
    private static final String FIELD_ITEM = "item";
    private static final String FIELD_ENTITY = "entity";
    private static final String FIELD_BLOCK = "block";
    private static final String FIELD_AGE = "age";
    private static final String FIELD_USE_SOURCE_BLOCK = "use_source_block";
    // 源方块图标缓存键前缀；同一条规则的首个源物品决定图标
    private static final String SOURCE_BLOCK_KEY = "source-block/";
    private static final Map<String, UiIcon> ICONS = new LinkedHashMap<>(32, 0.75F, true);
    private RulePreviewIcons() { }
    public static void clear() { ICONS.clear(); }

    // 图标与文本分别处理禁用色；缓冲必须在调色前后提交，避免污染相邻的启用条目。
    static void render(GuiGraphics graphics, UiIcon icon, int x, int y, boolean active) {
        if (active) {
            icon.render(graphics, x, y);
            return;
        }
        graphics.flush();
        float[] shaderColor = RenderSystem.getShaderColor();
        float red = shaderColor[0], green = shaderColor[1], blue = shaderColor[2], alpha = shaderColor[3];
        graphics.setColor(red * DISABLED_BRIGHTNESS, green * DISABLED_BRIGHTNESS,
                blue * DISABLED_BRIGHTNESS, alpha);
        try {
            icon.render(graphics, x, y);
        } finally {
            try { graphics.flush(); }
            finally { graphics.setColor(red, green, blue, alpha); }
        }
    }

    public static @Nullable UiIcon action(@Nullable RuleValue action) {
        if (action == null) return null;
        // 事件图标按种类前缀建好，不参与下面的动作键缓存；天气多态也不会撞键。
        UiIcon event = EventPreviewIcons.icon(action);
        if (event != null) return event;
        String key = text(action, RuleFields.TYPE) + "/" + text(action, FIELD_VARIANT) + "/" + text(action, FIELD_ITEM) + "/"
                + text(action, FIELD_ENTITY) + "/" + text(action, FIELD_BLOCK) + "/" + (action.has(FIELD_AGE) ? action.child(FIELD_AGE) : DEFAULT_AGE);
        if (ICONS.containsKey(key)) return ICONS.get(key);
        UiIcon icon = createAction(action);
        ICONS.put(key, icon);
        trimCache();
        return icon;
    }

    // 访问序链表保证淘汰的是最久未用的键；两处写入共用同一上限。
    private static void trimCache() {
        while (ICONS.size() > MAX_CACHED_ICONS) ICONS.remove(ICONS.keySet().iterator().next());
    }

    private static @Nullable UiIcon createAction(@Nullable RuleValue action) {
        if (action == null) return null;
        String type = text(action, RuleFields.TYPE);
        if (type.equals(SPAWN_ENTITY_TYPE)) {
            return switch (text(action, FIELD_VARIANT)) {
                case "item" -> CatalogPreviewIcons.iconFor(RuleCatalogType.ITEM, text(action, FIELD_ITEM));
                case "entity" -> text(action, FIELD_ENTITY).startsWith(TAG_PREFIX) ? TagPreviewIcons.resolve(RuleCatalogType.ENTITY, text(action, FIELD_ENTITY)).icon()
                        : EntityPreviewIcons.icon(text(action, FIELD_ENTITY), age(action));
                case "experience" -> EntityPreviewIcons.icon(EXPERIENCE_ORB, DEFAULT_AGE);
                default -> null;
            };
        }
        return switch (type) {
            case PLACE_BLOCK_TYPE -> CatalogPreviewIcons.iconFor(RuleCatalogType.BLOCK, text(action, FIELD_BLOCK));
            case LOOT_TABLE_TYPE -> new UiIcon.Item(new ItemStack(Items.CHEST));
            case EXPLOSION_TYPE -> new UiIcon.Item(new ItemStack(Items.TNT));
            default -> null;
        };
    }

    public static @Nullable UiIcon rule(@Nullable RuleValue rule) {
        if (rule == null) return null;
        UiIcon icon = actions(rule.child(RuleFields.EFFECTS), rule);
        if (icon != null) return icon;
        RuleValue outcomes = rule.child(RuleFields.OUTCOMES);
        if (outcomes != null && outcomes.isList()) for (RuleValue outcome : outcomes.items()) {
            if (outcome.isMapping()) { icon = actions(outcome.child(RuleFields.CANDIDATE_EFFECTS), rule); if (icon != null) return icon; }
        }
        return null;
    }

    private static @Nullable UiIcon actions(@Nullable RuleValue value, RuleValue rule) {
        if (value != null && value.isList()) for (RuleValue entry : value.items()) {
            if (entry.isMapping()) {
                if (text(entry, RuleFields.TYPE).equals(PLACE_BLOCK_TYPE) && boolOf(entry.child(FIELD_USE_SOURCE_BLOCK))) {
                    String key = SOURCE_BLOCK_KEY + RuleRecipeView.firstSource(rule);
                    if (!ICONS.containsKey(key)) {
                        ICONS.put(key, RuleRecipeView.firstSourceIcon(rule));
                        trimCache();
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
        RuleValue first = firstAction(rule.child(RuleFields.EFFECTS));
        if (first == null) {
            RuleValue outcomes = rule.child(RuleFields.OUTCOMES);
            if (outcomes != null && outcomes.isList()) for (RuleValue outcome : outcomes.items()) {
                if (outcome.isMapping()) { first = firstAction(outcome.child(RuleFields.CANDIDATE_EFFECTS)); if (first != null) break; }
            }
        }
        if (first == null || !text(first, RuleFields.TYPE).equals(SPAWN_ENTITY_TYPE)) return false;
        return switch (text(first, FIELD_VARIANT)) {
            case "entity" -> EntityPreviewIcons.unavailable(text(first, FIELD_ENTITY), age(first));
            case "experience" -> EntityPreviewIcons.unavailable(EXPERIENCE_ORB, DEFAULT_AGE);
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
        RuleValue value = action.child(FIELD_AGE);
        if (value == null || !value.isScalar()) return DEFAULT_AGE;
        try { return Integer.parseInt(value.asText().trim()); }
        catch (RuntimeException exception) { return DEFAULT_AGE; }
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
