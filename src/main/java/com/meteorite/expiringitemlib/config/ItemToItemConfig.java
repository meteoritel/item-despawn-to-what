package com.meteorite.expiringitemlib.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public class ItemToItemConfig extends BaseConversionConfig{

    public ItemToItemConfig(ResourceLocation item, ResourceLocation resultItem) {
        super(item);
        this.resultId = resultItem;
    }

    // 物品转化需要保证转化前后结果不同，防止循环转化
    @Override
    public boolean shouldProcess() {
        // 输入输出不能为空，且输入和输出不能相同
        if (itemId == null || resultId == null || itemId == resultId ) {
            return false;
        }

        // 确保内部ID存在
        if (this.getInternalId() == null || this.getInternalId().isEmpty()) {
            this.setInternalId(UUID.randomUUID().toString());
        }

        return true;
    }

    public Item hasResultItem() {
        return BuiltInRegistries.ITEM.get(resultId);
    }

    // 对于物掉落物结果，计算所有的stack堆叠之和
    @Override
    public int countNearbyResult(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        AABB box = new AABB(
                itemEntity.getX() - RADIUS, itemEntity.getY() - RADIUS, itemEntity.getZ() - RADIUS,
                itemEntity.getX() + RADIUS, itemEntity.getY() + RADIUS, itemEntity.getZ() + RADIUS);


        return serverLevel.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)
                .stream()
                .map(ItemEntity::getItem)
                .filter(itemStack -> BuiltInRegistries.ITEM.getKey(itemStack.getItem()).equals(resultId))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    @Override
    public int getResultLimit() {
        return super.getResultLimit() * 64;
    }

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= this.getResultLimit();
    }
}
