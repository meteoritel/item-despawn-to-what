package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** 物品到物品转换执行器。 */
public final class ItemToItemExecutor extends AbstractConversionExecutor<ItemToItemConfig> {
    @Override public boolean performConversion(ItemToItemConfig config, ItemEntity entity, ServerLevel level) {
        Item resultItem = config.getResultItem(level.random);
        ItemStack original = entity.getItem();
        int originalSize = original.getCount();
        int resultMultiple = config.getResultMultiple();
        int rounds = computeActualRounds(config, entity, originalSize, resultCapacityInRounds(config, entity));
        if (rounds <= 0) return false;
        int consumed = rounds * config.getSourceMultiple();
        entity.makeFakeItem();
        consumeAllOthers(config, entity, consumed);
        BlockPos pos = entity.blockPosition();
        for (int i = 0; i < resultMultiple; i++) {
            int remaining = rounds;
            while (remaining > 0) {
                int stackCount = Math.min(remaining, resultItem.getDefaultMaxStackSize());
                ItemEntity result = new ItemEntity(level,
                        pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3,
                        pos.getY() + 0.1,
                        pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3,
                        new ItemStack(resultItem, stackCount));
                result.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2,
                        (level.random.nextDouble() - 0.5) * 0.1);
                level.addFreshEntity(result);
                remaining -= stackCount;
            }
        }
        addRemainingItems(config, entity, level, originalSize - consumed);
        return true;
    }

    @Override public int countNearbyResult(ItemToItemConfig config, ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return 0;
        AABB box = searchBox(entity.blockPosition(), config.getSearchRadius());
        int total = 0;
        for (ItemEntity nearby : level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
            ItemStack stack = nearby.getItem();
            if (config.matchesResultItem(stack.getItem())) {
                long updated = (long) total + stack.getCount();
                total = updated >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) updated;
            }
        }
        return total;
    }

    @Override public boolean isResultLimitExceeded(ItemToItemConfig config, ItemEntity entity) {
        return countNearbyResult(config, entity) >= config.getResultLimit();
    }

    @Override public int getResultCapacityInRounds(ItemToItemConfig config, ItemEntity entity) {
        return resultCapacityInRounds(config, entity);
    }

    private int resultCapacityInRounds(ItemToItemConfig config, ItemEntity entity) {
        return resultCapacityInRounds(
                countNearbyResult(config, entity), config.getResultLimit(), config.getResultMultiple());
    }
}
