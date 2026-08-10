package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.conversion.ConversionConfig;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.util.ItemReturnUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 内置执行器共享的消耗、轮数计算与物品返还基类。
 */
public abstract class AbstractConversionExecutor<C extends ConversionConfig> implements ConversionExecutor<C> {
    @Override
    public abstract boolean performConversion(C config, ItemEntity itemEntity, ServerLevel level);

    protected final int computeActualRounds(BaseConversionConfig config, ItemEntity entity, int stackSize,
                                            int resultCapacityInRounds) {
        int sourceMultiple = Math.max(1, config.getSourceMultiple());
        int startRounds = stackSize / sourceMultiple;
        CatalystItems catalysts = config.getCatalystItems();
        int catalystRounds = catalysts != null && catalysts.hasAnyCatalyst() && catalysts.isCatalystConsume()
                ? catalysts.getMaxConvertibleRounds(entity, sourceMultiple) : Integer.MAX_VALUE;
        return Math.max(0, Math.min(startRounds, Math.min(catalystRounds, resultCapacityInRounds)));
    }

    protected final void consumeAllOthers(BaseConversionConfig config, ItemEntity entity, int count) {
        CatalystItems catalysts = config.getCatalystItems();
        if (catalysts != null && catalysts.hasAnyCatalyst() && catalysts.isCatalystConsume()) {
            catalysts.consumeFromLevel(entity, count);
        }
        InnerFluid fluid = config.getInnerFluid();
        if (fluid != null && fluid.hasInnerFluid()) {
            fluid.consumeFluidFromLevel(entity);
        }
    }

    protected final void addRemainingItems(BaseConversionConfig config, ItemEntity entity, ServerLevel level,
                                           int remaining) {
        addRemainingItems(config, entity, level, remaining, 0.5, 0.5, 0.5);
    }

    protected final void addRemainingItems(BaseConversionConfig config, ItemEntity entity, ServerLevel level,
                                           int remaining, double offsetX, double offsetY, double offsetZ) {
        if (remaining <= 0) return;
        ItemStack stack = entity.getItem().copy();
        stack.setCount(remaining);
        BlockPos pos = entity.blockPosition();
        ItemReturnUtil.spawnLockedItem(level, stack,
                pos.getX() + 0.5 + offsetX,
                pos.getY() + 0.5 + offsetY,
                pos.getZ() + 0.5 + offsetZ);
    }

    protected final int resultCapacityInRounds(int current, int rawLimit, int resultMultiple) {
        int remaining = rawLimit - current;
        return remaining <= 0 ? 0 : remaining / Math.max(1, resultMultiple);
    }
}
