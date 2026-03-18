package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.event.ItemConversionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class ItemToMobConfig extends BaseItemToEntityConfig{

    // 生成实体的age（如果需要）
    @SerializedName("entity_age")
    private int entityAge = -24000;

    // 缓存的结果实体类型
    private transient EntityType<?> cachedResultEntityType;

    public ItemToMobConfig() {
    }

    public ItemToMobConfig(ResourceLocation item, ResourceLocation result) {
        super(item, result);
    }

    // ========== 缓存与校验 ========== //
    @Override
    protected void initResultCache() {
        cachedResultEntityType = BuiltInRegistries.ENTITY_TYPE.get(resultId);
    }

    // 确保实体不为空，名字没有拼写错
    @Override
    protected boolean additionalCheck() {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(resultId)) {
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

        Entity testEntity = entityType.create(serverLevel);
        if (testEntity == null) {
            LOGGER.warn("EntityType '{}' returned null on create(), skipping conversion.", resultId);
            return;
        }
        if (!(testEntity instanceof Mob)) {
            LOGGER.warn(
                    "Entity type '{}' (class: {}) is not a Mob subclass. " +
                            "Marking item as invalid for this config to prevent future attempts.",
                    resultId, testEntity.getClass().getSimpleName()
            );
            testEntity.discard();
            // 直接移除出缓存
            ConfigExtractorManager.removeConfigByInternalId(getInternalId());
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

    // 生物实体按照数量计数
    @Override
    protected int countNearbyResult(ServerLevel level, BlockPos pos) {
        return level.getEntities(getResultEntityType(), buildSearchBox(pos), Entity::isAlive).size();
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
}
