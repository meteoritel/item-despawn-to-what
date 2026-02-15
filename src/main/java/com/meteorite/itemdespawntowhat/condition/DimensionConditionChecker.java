package com.meteorite.itemdespawntowhat.condition;


import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class DimensionConditionChecker extends AbstractConditionChecker {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CONDITION_KEY = "dimension";

    private ResourceKey<Level> dimensionKey;

    @Override
    public AbstractConditionChecker parse(Map<String, String> conditions) {
        String dimensionStr = getConditionValue(conditions, CONDITION_KEY, "");

        if (dimensionStr.isEmpty()) {
            return null; // 无需检查此条件
        }

        this.dimensionKey = parseDimensionKey(dimensionStr);
        if (this.dimensionKey == null) {
            LOGGER.warn("Invalid dimension key: {}", dimensionStr);
            return null;
        }

        return this;
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        return level.dimension().equals(dimensionKey);
    }

    private ResourceKey<Level> parseDimensionKey(String dimensionStr) {
        try {
            ResourceLocation location = ResourceLocation.parse(dimensionStr);
            return ResourceKey.create(Registries.DIMENSION, location);
        } catch (Exception e) {
            LOGGER.error("Failed to parse dimension: {}", dimensionStr, e);
            return null;
        }
    }
}
