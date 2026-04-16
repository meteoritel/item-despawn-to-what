package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

public class ItemToMobConfig extends BaseItemToEntityConfig{

    // 生成实体的age（如果需要）
    @SerializedName("entity_age")
    private int entityAge = -24000;

    // 缓存的结果实体类型
    private transient EntityType<?> cachedResultEntityType;

    public ItemToMobConfig() {
    }

    public ItemToMobConfig(String item, String result) {
        super(item, result);
    }

    private ResourceLocation resultRl() {
        return SafeParseUtil.parseResourceLocation(resultId);
    }

    // ========== 缓存与校验 ========== //
    @Override
    protected void initResultCache() {
        cachedResultEntityType = BuiltInRegistries.ENTITY_TYPE.get(resultRl());
    }

    // 确保实体不为空，名字没有拼写错
    @Override
    protected boolean additionalCheck() {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(resultRl())) {
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

        int rounds = computeActualRounds(itemEntity, originalStackSize);
        if (rounds <= 0) {
            LOGGER.debug("No capacity for entity conversion of {}", resultId);
            return;
        }

        int actualConvertCount = rounds * getSourceMultiple();
        // 计算本次可以生成的实体数量和需要返还的物品数量
        int actualEntitiesToSpawn = rounds * resultMultiple;
        LOGGER.debug("Converting to entity: {} -> {} ({} entities from {} items ({}x{}), {} items remaining)",
                originalStack.getItem().getDescriptionId(), resultId,
                actualEntitiesToSpawn, actualConvertCount, rounds, resultMultiple, originalStackSize - actualConvertCount);

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
        return BuiltInRegistries.ENTITY_TYPE.get(resultRl());
    }

    @Override
    public String getResultDescriptionId() {
        return getResultEntityType().getDescriptionId();
    }

    // 实体图标 fallback：优先刷怪蛋，找不到则 barrier
    @Override
    public ItemStack getResultIcon() {
        EntityType<?> type = getResultEntityType();
        if (type == null) {
            return new ItemStack(Items.BARRIER);
        }
        SpawnEggItem spawnEgg = SpawnEggItem.byId(type);
        return spawnEgg != null ? new ItemStack(spawnEgg) : new ItemStack(Items.BARRIER);
    }

    public int getEntityAge() {
        return entityAge;
    }

    public void setEntityAge(int entityAge) {
        this.entityAge = entityAge;
    }
}
