package com.meteorite.itemdespawntowhat.condition.checker;


import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 检查物品实体所在维度。
 */
public final class DimensionConditionChecker implements ConditionChecker {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ResourceKey<Level> dimensionKey;

    public DimensionConditionChecker(String dimension) {
        this.dimensionKey = parseDimensionKey(dimension);
        if (this.dimensionKey == null) {
            throw new IllegalArgumentException("Invalid dimension: " + dimension);
        }
    }

    @Override
    public String debugName() {
        return "dimension";
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        if (dimensionKey == null) {
            return true;
        }
        return level.dimension().equals(dimensionKey);
    }

    private static ResourceKey<Level> parseDimensionKey(String dimensionStr) {
        try {
            ResourceLocation location = ResourceLocation.parse(dimensionStr);
            return ResourceKey.create(Registries.DIMENSION, location);
        } catch (Exception e) {
            LOGGER.error("Failed to parse dimension: {}", dimensionStr, e);
            return null;
        }
    }
}
