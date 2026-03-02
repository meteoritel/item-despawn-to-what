package com.meteorite.itemdespawntowhat.condition.checker;


import com.meteorite.itemdespawntowhat.condition.ConditionContext;
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
    private static final String KEY = "dimension";

    private ResourceKey<Level> dimensionKey;

    @Override
    public AbstractConditionChecker parse(Map<String, String> conditions) {
        String dimensionStr = getConditionValue(conditions, "");

        if (dimensionStr.isEmpty()) {
            return null; // 无需检查此条件
        }

        ResourceKey<Level> parsed = parseDimensionKey(dimensionStr);
        if (parsed == null) {
            LOGGER.warn("Invalid dimension key: {}", dimensionStr);
            return null;
        }

        this.dimensionKey = parsed;
        return this;
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        return level.dimension().equals(dimensionKey);
    }

    @Override
    public String getConditionKey() {
        return KEY;
    }

    @Override
    public boolean shouldApply(ConditionContext ctx) {
        return ctx.dimension() != null && !ctx.dimension().isEmpty();
    }

    @Override
    public void applyCondition(Map<String, String> conditions, ConditionContext ctx) {
        conditions.put(getConditionKey(), ctx.dimension());
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
