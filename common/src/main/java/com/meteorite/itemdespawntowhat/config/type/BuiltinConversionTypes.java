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
    public static final ConversionType ITEM_TO_WORLD_EFFECT = get("item_to_world_effect");

    private static final Set<ResourceLocation> IDS = Set.of(
            ITEM_TO_ITEM.id(), ITEM_TO_MOB.id(), ITEM_TO_BLOCK.id(), ITEM_TO_XP_ORB.id(), ITEM_TO_WORLD_EFFECT.id());

    private BuiltinConversionTypes() {
    }

    public static boolean isBuiltin(ConversionType type) {
        return type != null && IDS.contains(type.id());
    }

    private static ConversionType get(String path) {
        return ConversionTypeRegistry.require(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path));
    }
}
