package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        this.configType = ConfigType.ITEM_TO_ENTITY;
    }

    public ItemToEntityConfig(ResourceLocation item, ResourceLocation resultEntity) {
        super(item, resultEntity);
        this.resultLimit = DEFAULT_RESULT_LIMIT;
        this.entityAge = 0;
        this.configType = ConfigType.ITEM_TO_ENTITY;
    }

    // 确保实体不为空，名字没有拼写错
    @Override
    public boolean shouldProcess() {
        return super.shouldProcess() && this.getResultEntityType() != null;
    }

    // ========== 转化逻辑 ========== //
    @Override
    public void performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        ResourceLocation resultEntityId = getResultId();
        EntityType<?> entityType = getResultEntityType();
        BlockPos pos = itemEntity.blockPosition();

        if (entityType == null) {
            LOGGER.warn("Unknown entity type: {}", resultEntityId);
            return;
        }

        ItemStack originalStack = itemEntity.getItem();
        int originalStackSize = originalStack.getCount();
        int resultMultiple = getResultMultiple();

        int actualConvertCount = computeActualConvertCount(itemEntity, originalStackSize);
        if (actualConvertCount <= 0) {
            LOGGER.debug("No capacity for entity conversion of {}", resultEntityId);
            return;
        }

        // 计算本次可以生成的实体数量和需要返还的物品数量
        int actualEntitiesToSpawn = actualConvertCount * resultMultiple;
        LOGGER.debug("Converting to entity: {} -> {} ({} entities from {} items, {} items remaining)",
                originalStack.getItem().getDescriptionId(), resultEntityId,
                actualEntitiesToSpawn, actualConvertCount, originalStackSize - actualConvertCount);

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();
        // 根据条件消耗催化剂与流体
        consumeAllOthers(itemEntity, actualConvertCount);

        // 生成实体
        for (int i = 0; i < actualEntitiesToSpawn; i++) {
            Entity resultEntity = entityType.create(serverLevel);
            if (resultEntity != null) {
                // 稍微分散位置，避免重叠
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.5;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.5;

                resultEntity.moveTo(pos.getX() + 0.5 + offsetX, pos.getY(), pos.getZ() + 0.5 + offsetZ, 0, 0);

                // 设置实体年龄（如果需要）
                if (resultEntity instanceof AgeableMob ageable) {
                    ageable.setAge(getEntityAge());
                }
                serverLevel.addFreshEntity(resultEntity);
            }
        }
        int itemsRemaining = originalStackSize - actualConvertCount;
        addRemainingItems(itemEntity, serverLevel, itemsRemaining);
    }

    // 实体直接按照数量计数
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

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= this.getResultLimit();
    }

    @Override
    protected int getResultCapacityInStartItems(ItemEntity itemEntity) {
        int current = countNearbyResult(itemEntity);
        int remaining = getResultLimit() - current;
        if (remaining <= 0) {
            return 0;
        }
        return remaining / Math.max(1, getResultMultiple());
    }

    // ========== 结果相关方法 ========== //
    public EntityType<?> getResultEntityType() {
        return BuiltInRegistries.ENTITY_TYPE.get(resultId);
    }

    @Override
    public String getResultDescriptionId() {
        return getResultEntityType().getDescriptionId();
    }

    // 实体的图标还在考虑中，暂时用物品代替
    @Override
    public ItemStack getResultIcon() {
        return BuiltInRegistries.ITEM.getOptional(resultId)
                .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.BARRIER));
    }

    public int getEntityAge() {
        return entityAge;
    }
    public void setEntityAge(int entityAge) {
        this.entityAge = entityAge;
    }
    public int getResultLimit() {
        return resultLimit <= 0 ? DEFAULT_RESULT_LIMIT : resultLimit;
    }
    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }
}
