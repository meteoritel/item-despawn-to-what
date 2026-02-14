package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

public class ItemToEntityConfig extends BaseConversionConfig{

    // 生成数量限制
    @SerializedName("result_limit")
    private int resultLimit;
    // 生成实体的age（如果需要）
    @SerializedName("entity_age")
    protected int entityAge;

    public ItemToEntityConfig() {
        super();
    }

    public ItemToEntityConfig(ResourceLocation item, ResourceLocation resultEntity) {
        super(item);
        this.resultLimit = DEFAULT_RESULT_LIMIT;
        this.resultId = resultEntity;
        this.entityAge = 0;
    }

    public EntityType<?> getResultEntityType() {
        return BuiltInRegistries.ENTITY_TYPE.get(resultId);
    }

    // 确保实体不为空，名字没有拼写错
    @Override
    public boolean shouldProcess() {
        return super.shouldProcess() && this.getResultEntityType() != null;
    }

    // 其他实体就直接按照数量计数
    @Override
    public int countNearbyResult(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        AABB box = new AABB(
                itemEntity.getX() - MAX_RADIUS, itemEntity.getY() - MAX_RADIUS, itemEntity.getZ() - MAX_RADIUS,
                itemEntity.getX() + MAX_RADIUS, itemEntity.getY() + MAX_RADIUS, itemEntity.getZ() + MAX_RADIUS);

        return serverLevel.getEntitiesOfClass(this.getResultEntityType().getBaseClass(), box, Entity::isAlive)
                .size();
    }

    public int getResultLimit() {
        return resultLimit <= 0 ? DEFAULT_RESULT_LIMIT : resultLimit;
    }
    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= this.getResultLimit();
    }

    public int getEntityAge() {
        return entityAge;
    }

    public void setEntityAge(int entityAge) {
        this.entityAge = entityAge;
    }
}
