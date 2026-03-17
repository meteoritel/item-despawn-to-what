package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public class ItemToExpOrbConfig extends BaseItemToEntityConfig{
    /*
     * 每个经验球携带的经验值。
     * 原版单球上限为 2477（对应一颗 Large 经验球）；
     * 默认单球设置为1点经验
     */
    @SerializedName("xp_per_orb")
    private int xpPerOrb = 1;

    public ItemToExpOrbConfig() {
        // 经验球允许更多数量
        this.resultLimit = 100;
    }

    public ItemToExpOrbConfig(ResourceLocation item, ResourceLocation result) {
        super(item, result);
        this.resultLimit = 100;
    }

    //经验球无需从注册表查找 EntityType，无额外缓存。
    @Override
    protected void initResultCache() {
        // no-op
    }

    // 允许结果字段为空，因为经验球没有变体
    @Override
    protected boolean isResultIdRequired() {
        return false;
    }

    @Override
    protected boolean additionalCheck() {
        if (xpPerOrb <= 0) {
            LOGGER.warn("xpPerOrb must be at least 1, current is: {}", xpPerOrb);
            return false;
        }
        if (xpPerOrb > 2477) {
            LOGGER.warn("xpPerOrb exceeds vanilla single-orb cap (2477), current is: {}. " +
                    "Consider splitting into multiple orbs via resultMultiple.", xpPerOrb);
            // 仅警告，允许自定义模组扩展上限
        }
        if (resultLimit <= 0) {
            LOGGER.warn("resultLimit must be greater than 0, current is: {}", resultLimit);
            return false;
        }
        return true;
    }

    @Override
    public void performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        BlockPos pos = itemEntity.blockPosition();

        ItemStack originalStack = itemEntity.getItem();
        int originalStackSize = originalStack.getCount();
        int resultMultiple = getResultMultiple();
        int actualConvertCount = computeActualConvertCount(itemEntity, originalStackSize);

        if (actualConvertCount <= 0) {
            LOGGER.debug("No capacity for entity conversion of {}", resultId);
            return;
        }

        int orbsToSpawn = actualConvertCount * resultMultiple;

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();
        // 根据条件消耗催化剂与流体
        consumeAllOthers(itemEntity, actualConvertCount);

        for (int i = 0; i < orbsToSpawn; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.5;
            ExperienceOrb orb = new ExperienceOrb(
                    serverLevel,
                    pos.getX() + 0.5 + offsetX,
                    pos.getY() + 0.2,
                    pos.getZ() + 0.5 + offsetZ,
                    xpPerOrb);
            serverLevel.addFreshEntity(orb);
        }

        int itemsRemaining = originalStackSize - actualConvertCount;
        addRemainingItems(itemEntity, serverLevel, itemsRemaining);
    }

    // 附近数量统计
    @Override
    public int countNearbyResult(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) return 0;

        AABB box = new AABB(
                itemEntity.getX() - MAX_RADIUS, itemEntity.getY() - MAX_RADIUS, itemEntity.getZ() - MAX_RADIUS,
                itemEntity.getX() + MAX_RADIUS, itemEntity.getY() + MAX_RADIUS, itemEntity.getZ() + MAX_RADIUS);

        // ExperienceOrb 没有专用的 EntityType 过滤器重载，使用 getEntitiesOfClass
        return serverLevel.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive).size();
    }

    @Override
    public String getResultDescriptionId() {
        // 经验球在原版中的翻译键
        return "entity.minecraft.experience_orb";
    }

    @Override
    public ItemStack getResultIcon() {
        // 经验球无对应物品，用经验瓶代替
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    public int getXpPerOrb() {
        return xpPerOrb;
    }

    public void setXpPerOrb(int xpPerOrb) {
        this.xpPerOrb = xpPerOrb;
    }
}
