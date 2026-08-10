package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ItemToLootConfig;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 执行物品到战利品表结果的转化。
 */
public final class ItemToLootExecutor extends AbstractConversionExecutor<ItemToLootConfig> {
    @Override
    public boolean performConversion(ItemToLootConfig config, ItemEntity entity, ServerLevel level) {
        ResourceLocation lootTableId = SafeParseUtil.parseResourceLocation(config.getResultId());
        if (lootTableId == null) {
            return false;
        }
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
        int size = entity.getItem().getCount();
        int rounds = computeActualRounds(config, entity, size, resultCapacityInRounds(config, entity));
        if (rounds <= 0) {
            return false;
        }

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
                .withLuck(config.getLuck())
                .create(LootContextParamSets.CHEST);
        int rolls = rounds * config.getResultMultiple();
        int consumed = rounds * config.getSourceMultiple();
        entity.makeFakeItem();
        consumeAllOthers(config, entity, consumed);
        BlockPos pos = entity.blockPosition();
        int remainingUnits = Math.max(0, config.getResultLimit() - countNearbyResult(config, entity));
        for (int i = 0; i < rolls; i++) {
            remainingUnits = spawnLoot(level, pos, lootTable.getRandomItems(params, level.random),
                    lootResultTag(config), remainingUnits);
            if (remainingUnits <= 0) {
                break;
            }
        }
        addRemainingItems(config, entity, level, size - consumed);
        return true;
    }

    @Override
    public int countNearbyResult(ItemToLootConfig config, ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return 0;
        }
        int total = 0;
        for (ItemEntity nearby : level.getEntitiesOfClass(
                ItemEntity.class, searchBox(entity.blockPosition(), config.getSearchRadius()),
                nearby -> nearby.isAlive() && nearby.getTags().contains(lootResultTag(config)))) {
            long updated = (long) total + nearby.getItem().getCount();
            total = updated >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) updated;
        }
        return total;
    }

    @Override
    public boolean isResultLimitExceeded(ItemToLootConfig config, ItemEntity entity) {
        return countNearbyResult(config, entity) >= config.getResultLimit();
    }

    @Override
    public int getResultCapacityInRounds(ItemToLootConfig config, ItemEntity entity) {
        return resultCapacityInRounds(config, entity);
    }

    private int resultCapacityInRounds(ItemToLootConfig config, ItemEntity entity) {
        return resultCapacityInRounds(
                countNearbyResult(config, entity), config.getResultLimit(), config.getResultMultiple());
    }

    private int spawnLoot(ServerLevel level, BlockPos pos, List<ItemStack> stacks,
                          String resultTag, int remainingUnits) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || remainingUnits <= 0) {
                continue;
            }
            int count = Math.min(stack.getCount(), remainingUnits);
            ItemStack limitedStack = stack.copyWithCount(count);
            ItemEntity result = new ItemEntity(level,
                    pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3,
                    pos.getY() + 0.1,
                    pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3,
                    limitedStack);
            result.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2,
                    (level.random.nextDouble() - 0.5) * 0.1);
            result.addTag(resultTag);
            level.addFreshEntity(result);
            remainingUnits -= count;
        }
        return remainingUnits;
    }

    private String lootResultTag(ItemToLootConfig config) {
        return "itemdespawntowhat.loot." + config.getResultId();
    }
}
