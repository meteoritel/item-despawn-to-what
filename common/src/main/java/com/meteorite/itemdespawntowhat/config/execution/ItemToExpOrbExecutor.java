package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;

/** 物品到经验球转换执行器。 */
public final class ItemToExpOrbExecutor extends AbstractConversionExecutor<ItemToExpOrbConfig> {
    @Override public boolean performConversion(ItemToExpOrbConfig config, ItemEntity entity, ServerLevel level) {
        int size = entity.getItem().getCount();
        int rounds = computeActualRounds(config, entity, size, resultCapacityInRounds(config, entity));
        if (rounds <= 0) return false;
        int consumed = rounds * config.getSourceMultiple();
        int xp = rounds * config.getResultMultiple() * config.getXpPerItem();
        entity.makeFakeItem();
        consumeAllOthers(config, entity, consumed);
        BlockPos pos = entity.blockPosition();
        ExperienceOrb.award(level, new Vec3(pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                pos.getY() + 0.2, pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5), xp);
        addRemainingItems(config, entity, level, size - consumed);
        return true;
    }

    @Override public int getResultCapacityInRounds(ItemToExpOrbConfig config, ItemEntity entity) {
        return resultCapacityInRounds(config, entity);
    }

    private int resultCapacityInRounds(ItemToExpOrbConfig config, ItemEntity entity) {
        int unitsPerRound = config.getResultMultiple() * config.getXpPerItem();
        return ConversionLimits.MAX_RESULT_UNITS / Math.max(1, unitsPerRound);
    }
}
