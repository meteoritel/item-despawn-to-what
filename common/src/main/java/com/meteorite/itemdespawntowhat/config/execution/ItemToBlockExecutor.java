package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.catalogue.InnerFluid;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import com.meteorite.itemdespawntowhat.server.task.PlaceBlockTask;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/** 物品到方块转换执行器。 */
public final class ItemToBlockExecutor extends AbstractConversionExecutor<ItemToBlockConfig> {
    @Override public boolean performConversion(ItemToBlockConfig config, ItemEntity entity, ServerLevel level) {
        int size = entity.getItem().getCount();
        int capacity = ConversionLimits.MAX_BLOCK_PLACEMENTS / Math.max(1, config.getResultMultiple());
        int rounds = computeActualRounds(config, entity, size, capacity);
        if (rounds <= 0) return false;
        Block result = config.getResultBlock(entity);
        if (result == Blocks.AIR) return false;
        int consumed = rounds * config.getSourceMultiple();
        entity.makeFakeItem();
        consumeAllOthers(config, entity, consumed);
        boolean consumeFluid = config.getInnerFluid() == null || !config.getInnerFluid().hasInnerFluid()
                || config.getInnerFluid().isConsumeFluid();
        int remaining = size - consumed;
        LevelTaskManager.addTask(level, new PlaceBlockTask(entity.blockPosition(), result, config.getRadius(),
                config.getBlockPlaceShape(), consumeFluid, rounds * config.getResultMultiple(),
                Constants.blockPlaceIntervalTicks,
                () -> addRemainingItems(config, entity, level, remaining, 0, 1, 0)));
        return true;
    }

    @Override public int getResultCapacityInRounds(ItemToBlockConfig config, ItemEntity entity) {
        return ConversionLimits.MAX_BLOCK_PLACEMENTS / Math.max(1, config.getResultMultiple());
    }
}
