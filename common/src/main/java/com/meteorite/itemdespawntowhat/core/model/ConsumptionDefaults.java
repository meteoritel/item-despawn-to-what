package com.meteorite.itemdespawntowhat.core.model;

import net.minecraft.resources.ResourceLocation;

/**
 * 消耗类效果的公共常量。
 * 规则默认隐式包含 consume_source；当规则显式声明了任何消耗效果时，以显式声明为准。
 */
public final class ConsumptionDefaults {

    // 消耗源物品效果的类型 id
    public static final ResourceLocation CONSUME_SOURCE_ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "consume_source");
    // 消耗催化剂效果的类型 id
    public static final ResourceLocation CONSUME_CATALYST_ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "consume_catalyst");
    // 消耗浸润流体效果的类型 id
    public static final ResourceLocation CONSUME_FLUID_ID = ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", "consume_fluid");

    private ConsumptionDefaults() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 是否为消耗类效果
    public static boolean isConsumption(ResourceLocation typeId) {
        return CONSUME_SOURCE_ID.equals(typeId) || CONSUME_CATALYST_ID.equals(typeId) || CONSUME_FLUID_ID.equals(typeId);
    }
}
