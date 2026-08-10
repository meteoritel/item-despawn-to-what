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

/**
 * 检查物品实体所在维度。
 */
public class DimensionConditionChecker extends AbstractConditionChecker {
    private static final Logger LOGGER = LogManager.getLogger();
    private ResourceKey<Level> dimensionKey;

    @Override
    public String debugName() {
        return "dimension";
    }

    @Override
    public AbstractConditionChecker createChecker(ConditionContext ctx) {
        DimensionConditionChecker checker = new DimensionConditionChecker();
        checker.dimensionKey = checker.parseDimensionKey(ctx.dimension());
        return checker.dimensionKey == null ? null : checker;
    }

    @Override
    public boolean checkCondition(ItemEntity itemEntity, ServerLevel level) {
        if (dimensionKey == null) {
            return true;
        }
        return level.dimension().equals(dimensionKey);
    }

    @Override
    public boolean shouldApply(ConditionContext ctx) {
        return ctx.dimension() != null && !ctx.dimension().isBlank();
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
