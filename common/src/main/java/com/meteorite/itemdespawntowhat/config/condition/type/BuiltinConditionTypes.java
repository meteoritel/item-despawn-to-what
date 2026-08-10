package com.meteorite.itemdespawntowhat.config.condition.type;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.resources.ResourceLocation;

/**
 * 提供内置条件类型的稳定标识符常量。
 */
public final class BuiltinConditionTypes {
    public static final ConditionType DIMENSION = get("dimension");
    public static final ConditionType OUTDOOR = get("outdoor");
    public static final ConditionType SURROUNDING_BLOCKS = get("surrounding_blocks");
    public static final ConditionType CATALYST_PRESENT = get("catalyst_present");
    public static final ConditionType FLUID_PRESENT = get("fluid_present");
    public static final ConditionType BIOME = get("biome");
    public static final ConditionType WEATHER = get("weather");

    private BuiltinConditionTypes() {
    }

    private static ConditionType get(String path) {
        return ConditionTypeRegistry.require(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path));
    }
}
