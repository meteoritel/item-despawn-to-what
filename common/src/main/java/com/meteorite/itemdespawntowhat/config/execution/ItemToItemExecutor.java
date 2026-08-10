package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** 物品到物品转换执行器。 */
public final class ItemToItemExecutor extends AbstractConversionExecutor<ItemToItemConfig> {
    @Override public boolean performConversion(ItemToItemConfig config, ItemEntity entity, ServerLevel level) {
        Item resultItem = config.getResultItem();
        ItemStack original = entity.getItem();
        int originalSize = original.getCount();
        int resultMultiple = config.getResultMultiple();
        int rounds = computeActualRounds(config, entity, originalSize, resultCapacityInRounds(config, entity));
        if (rounds <= 0) return false;
        int consumed = rounds * config.getSourceMultiple();
        entity.makeFakeItem();
        consumeAllOthers(config, entity, consumed);
        BlockPos pos = entity.blockPosition();
        ItemStack resultStack = new ItemStack(resultItem, rounds);
        for (int i = 0; i < resultMultiple; i++) {
            ItemEntity result = new ItemEntity(level,
                    pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3,
                    pos.getY() + 0.1,
                    pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3,
                    resultStack.copy());
            result.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2,
                    (level.random.nextDouble() - 0.5) * 0.1);
            level.addFreshEntity(result);
        }
        addRemainingItems(config, entity, level, originalSize - consumed);
        return true;
    }

    @Override public int countNearbyResult(ItemToItemConfig config, ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return 0;
        AABB box = searchBox(entity.blockPosition());
        int total = 0;
        for (ItemEntity nearby : level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
            ItemStack stack = nearby.getItem();
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(config.getResultId())) {
                long updated = (long) total + stack.getCount();
                total = updated >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) updated;
            }
        }
        return total;
    }

    @Override public boolean isResultLimitExceeded(ItemToItemConfig config, ItemEntity entity) {
        return countNearbyResult(config, entity) >= rawResultLimit(config);
    }

    @Override public int getResultCapacityInRounds(ItemToItemConfig config, ItemEntity entity) {
        return resultCapacityInRounds(config, entity);
    }

    private int rawResultLimit(ItemToItemConfig config) {
        return config.getResultLimit() * Math.max(1, config.getResultMaxStackSize());
    }

    private int resultCapacityInRounds(ItemToItemConfig config, ItemEntity entity) {
        return resultCapacityInRounds(countNearbyResult(config, entity), rawResultLimit(config), config.getResultMultiple());
    }

    private AABB searchBox(BlockPos pos) {
        int radius = 6;
        return new AABB(pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
                pos.getX() + radius, pos.getY() + radius, pos.getZ() + radius);
    }
}
