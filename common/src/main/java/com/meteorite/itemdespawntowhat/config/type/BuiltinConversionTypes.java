package com.meteorite.itemdespawntowhat.config.type;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * 内置转换类型常量。
 */
public final class BuiltinConversionTypes {
    public static final ConversionType ITEM_TO_ITEM = get("item_to_item");
    public static final ConversionType ITEM_TO_MOB = get("item_to_mob");
    public static final ConversionType ITEM_TO_BLOCK = get("item_to_block");
    public static final ConversionType ITEM_TO_XP_ORB = get("item_to_xp_orb");
    public static final ConversionType ITEM_TO_LIGHTNING = get("item_to_lightning");
    public static final ConversionType ITEM_TO_EXPLOSION = get("item_to_explosion");
    public static final ConversionType ITEM_TO_ARROW_RAIN = get("item_to_arrow_rain");
    public static final ConversionType ITEM_TO_WEATHER = get("item_to_weather");
    public static final ConversionType ITEM_TO_LOOT = get("item_to_loot");

    private static final Set<ResourceLocation> IDS = Set.of(
            ITEM_TO_ITEM.id(), ITEM_TO_MOB.id(), ITEM_TO_BLOCK.id(), ITEM_TO_XP_ORB.id(),
            ITEM_TO_LIGHTNING.id(), ITEM_TO_EXPLOSION.id(), ITEM_TO_ARROW_RAIN.id(), ITEM_TO_WEATHER.id(),
            ITEM_TO_LOOT.id());

    private BuiltinConversionTypes() {
    }

    public static boolean isBuiltin(ConversionType type) {
        return type != null && IDS.contains(type.id());
    }

    private static ConversionType get(String path) {
        return ConversionTypeRegistry.require(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path));
    }
}
