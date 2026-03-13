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
    private int resultLimit = 30;
    // 生成实体的age（如果需要）
    @SerializedName("entity_age")
    protected int entityAge = -24000;

    // 缓存的结果实体类型
    private transient EntityType<?> cachedResultEntityType;

    public ItemToEntityConfig() {
        super();
        this.configType = ConfigType.ITEM_TO_ENTITY;
    }

    public ItemToEntityConfig(ResourceLocation item, ResourceLocation resultEntity) {
        super(item, resultEntity);
        this.configType = ConfigType.ITEM_TO_ENTITY;
    }

    // ========== 缓存与校验 ========== //
    @Override
    protected void initResultCache() {
        cachedResultEntityType = BuiltInRegistries.ENTITY_TYPE.get(resultId);
    }

    // 确保实体不为空，名字没有拼写错
    @Override
    protected boolean additionalCheck() {
        boolean valid = BuiltInRegistries.ENTITY_TYPE.containsKey(resultId);
        if (!valid) {
            LOGGER.warn("Unknown entity type: resultId='{}'", resultId);
            return false;
        }

        if (resultLimit <= 0) {
            LOGGER.warn("ResultLimit should not be less than 0, current is: {}", resultLimit);
            return false;
        }
        return true;
    }
    // ========== 转化逻辑 ========== //
    @Override
    public void performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        EntityType<?> entityType = getResultEntityType();
        BlockPos pos = itemEntity.blockPosition();

        if (entityType == null) {
            LOGGER.warn("Unknown entity type: {}", resultId);
            return;
        }

        ItemStack originalStack = itemEntity.getItem();
        int originalStackSize = originalStack.getCount();
        int resultMultiple = getResultMultiple();

        int actualConvertCount = computeActualConvertCount(itemEntity, originalStackSize);
        if (actualConvertCount <= 0) {
            LOGGER.debug("No capacity for entity conversion of {}", resultId);
            return;
        }

        // 计算本次可以生成的实体数量和需要返还的物品数量
        int actualEntitiesToSpawn = actualConvertCount * resultMultiple;
        LOGGER.debug("Converting to entity: {} -> {} ({} entities from {} items, {} items remaining)",
                originalStack.getItem().getDescriptionId(), resultId,
                actualEntitiesToSpawn, actualConvertCount, originalStackSize - actualConvertCount);

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();
        // 根据条件消耗催化剂与流体
        consumeAllOthers(itemEntity, actualConvertCount);

        // 生成实体, 稍微分散位置，避免重叠
        for (int i = 0; i < actualEntitiesToSpawn; i++) {
            Entity resultEntity = entityType.create(serverLevel);
            if (resultEntity != null) {
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
        EntityType<?> targetType = getResultEntityType();

        return serverLevel.getEntities(targetType, box, Entity::isAlive).size();
    }

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= getResultLimit();
    }

    @Override
    protected int getResultCapacityInStartItems(ItemEntity itemEntity) {
        int current = countNearbyResult(itemEntity);
        int remaining = getResultLimit() - current;
        LOGGER.debug("current entity = {}, remain = {}", current, remaining);
        if (remaining <= 0) {
            return 0;
        }
        return remaining / Math.max(1, getResultMultiple());
    }

    // ========== 结果相关方法 ========== //
    public EntityType<?> getResultEntityType() {
        if (isCacheInitialized()) {
            return cachedResultEntityType;
        }
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
        return resultLimit;
    }

    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }
}
