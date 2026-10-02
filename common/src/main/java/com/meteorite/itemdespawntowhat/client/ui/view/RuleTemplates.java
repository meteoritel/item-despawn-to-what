package com.meteorite.itemdespawntowhat.client.ui.view;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 内置模板目录：9 个生效效果各对应一个模板，顺序与旧第一屏一致。
 */
public final class RuleTemplates {

    private static final List<RuleTemplate> ALL = List.of(
            template("item_to_item", "spawn_item"),
            template("item_to_mob", "spawn_entity"),
            template("item_to_block", "place_block"),
            template("item_to_xp_orb", "spawn_xp"),
            template("item_to_lightning", "lightning"),
            template("item_to_explosion", "explosion"),
            template("item_to_arrow_rain", "arrow_rain"),
            template("item_to_weather", "weather"),
            template("item_to_loot", "loot_table")
    );

    private RuleTemplates() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 全部模板（顺序固定）
    public static List<RuleTemplate> all() {
        return ALL;
    }

    // 按 UI id 查找模板
    public static @Nullable RuleTemplate byUiId(ResourceLocation uiId) {
        for (RuleTemplate template : ALL) {
            if (template.uiId().equals(uiId)) {
                return template;
            }
        }
        return null;
    }

    // 按效果类型反查模板（规则列表屏做来源标注用）
    public static @Nullable RuleTemplate byEffectType(@Nullable ResourceLocation effectType) {
        if (effectType == null) {
            return null;
        }
        for (RuleTemplate template : ALL) {
            if (template.effectType().equals(effectType)) {
                return template;
            }
        }
        return null;
    }

    private static RuleTemplate template(String uiPath, String effectPath) {
        return new RuleTemplate(
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, uiPath),
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, effectPath));
    }
}
