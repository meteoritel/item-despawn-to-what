package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** 物品到生物转换执行器。 */
public final class ItemToMobExecutor extends AbstractConversionExecutor<ItemToMobConfig> {
    @Override public boolean performConversion(ItemToMobConfig config, ItemEntity entity, ServerLevel level) {
        EntityType<?> type = config.getResultEntityType(level.random);
        if (type == null) return false;
        Entity test = type.create(level);
        if (!(test instanceof Mob)) {
            if (test != null) test.discard();
            ConfigExtractorManager.removeConfigByInternalId(config.getInternalId());
            return false;
        }
        test.discard();
        ItemStack original = entity.getItem();
        int size = original.getCount();
        int rounds = computeActualRounds(config, entity, size, resultCapacityInRounds(config, entity));
        if (rounds <= 0) return false;
        int consumed = rounds * config.getSourceMultiple();
        int count = rounds * config.getResultMultiple();
        entity.makeFakeItem();
        consumeAllOthers(config, entity, consumed);
        BlockPos pos = entity.blockPosition();
        for (int i = 0; i < count; i++) {
            Entity spawned = type.create(level);
            if (spawned != null) {
                spawned.moveTo(pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                        pos.getY(), pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5, 0, 0);
                if (spawned instanceof AgeableMob ageable) ageable.setAge(config.getEntityAge());
                level.addFreshEntity(spawned);
            }
        }
        addRemainingItems(config, entity, level, size - consumed);
        return true;
    }

    @Override public int countNearbyResult(ItemToMobConfig config, ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return 0;
        int total = 0;
        for (EntityType<?> type : config.getResultEntityTypes()) {
            total += level.getEntities(type,
                    searchBox(entity.blockPosition(), config.getSearchRadius()), Entity::isAlive).size();
        }
        return total;
    }

    @Override public boolean isResultLimitExceeded(ItemToMobConfig config, ItemEntity entity) {
        return countNearbyResult(config, entity) >= config.getResultLimit();
    }

    @Override public int getResultCapacityInRounds(ItemToMobConfig config, ItemEntity entity) {
        return resultCapacityInRounds(config, entity);
    }

    private int resultCapacityInRounds(ItemToMobConfig config, ItemEntity entity) {
        return resultCapacityInRounds(countNearbyResult(config, entity), config.getResultLimit(), config.getResultMultiple());
    }

}
