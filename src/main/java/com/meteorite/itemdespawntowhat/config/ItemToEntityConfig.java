package com.meteorite.itemdespawntowhat.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;


public class ItemToEntityConfig extends BaseConversionConfig{

    public ItemToEntityConfig(ResourceLocation item, ResourceLocation resultEntity) {
        super(item);
        this.resultId = resultEntity;
        this.entityAge = 0;
    }

    public EntityType<?> getResultEntityType() {
        return BuiltInRegistries.ENTITY_TYPE.get(resultId);
    }

    // 其他实体就直接按照数量计数
    @Override
    public int countNearbyResult(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        AABB box = new AABB(
                itemEntity.getX() - RADIUS, itemEntity.getY() - RADIUS, itemEntity.getZ() - RADIUS,
                itemEntity.getX() + RADIUS, itemEntity.getY() + RADIUS, itemEntity.getZ() + RADIUS);

        return serverLevel.getEntitiesOfClass(this.getResultEntityType().getBaseClass(), box, Entity::isAlive)
                .size();
    }

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= this.getResultLimit();
    }
}
