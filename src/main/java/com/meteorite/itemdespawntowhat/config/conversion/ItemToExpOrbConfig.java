package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ItemToExpOrbConfig extends BaseItemToEntityConfig{
    private static final String XP_ORB_ID = "minecraft:experience_orb";

    // 每个物品转化为几点经验值
    @SerializedName("xp_per_item")
    private int xpPerItem = 1;

    public ItemToExpOrbConfig() {
        this.resultId = XP_ORB_ID;
        // 经验球实体数量设为一个大值，限制极端结果倍率
        this.resultLimit = 99999;
    }

    // 允许结果字段为空，因为经验球没有变体
    @Override
    protected boolean isResultIdRequired() {
        return false;
    }

    @Override
    protected boolean additionalCheck() {
        if (xpPerItem <= 0) {
            LOGGER.warn("xpPerOrb must be at least 1, current is: {}", xpPerItem);
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
        int rounds = computeActualRounds(itemEntity, originalStackSize);

        if (rounds <= 0) {
            LOGGER.debug("No capacity for entity conversion of {}", resultId);
            return;
        }

        int actualConvertCount = rounds * getSourceMultiple();
        int totalXp = rounds * resultMultiple * xpPerItem;

        // 物品实体下一tick消失
        itemEntity.makeFakeItem();
        consumeAllOthers(itemEntity, actualConvertCount);

        // 生成经验球，使用原版方法自动拆分，避免出现超大经验球
        double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.5;
        double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.5;
        ExperienceOrb.award(serverLevel,
                new Vec3(pos.getX() + 0.5 + offsetX,
                        pos.getY() + 0.2,
                        pos.getZ() + 0.5 + offsetZ),
                totalXp);

        int itemsRemaining = originalStackSize - actualConvertCount;
        addRemainingItems(itemEntity, serverLevel, itemsRemaining);
    }

    // 附近经验球数量统计，暂时无意义，直接返回0,
    @Override
    protected int countNearbyResult(ServerLevel level, BlockPos pos) {
        return 0;
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

    public int getXpPerItem() {
        return xpPerItem;
    }

    public void setXpPerItem(int xpPerItem) {
        this.xpPerItem = xpPerItem;
    }
}
