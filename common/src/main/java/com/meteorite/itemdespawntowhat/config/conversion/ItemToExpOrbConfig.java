package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 将源物品转换为经验值的配置与执行逻辑。
 */
public class ItemToExpOrbConfig extends BaseItemToEntityConfig{
    private static final String XP_ORB_ID = "minecraft:experience_orb";

    // 每个物品转化为几点经验值
    @SerializedName("xp_per_item")
    private int xpPerItem = 1;

    public ItemToExpOrbConfig() {
        super(ConfigType.ITEM_TO_XP_ORB);
        this.resultId = XP_ORB_ID;
        // 经验转换仍使用统一结果上限，避免一次生成过多经验球
        this.resultLimit = ConversionLimits.MAX_RESULT_LIMIT;
    }

    // 允许结果字段为空，因为经验球没有变体
    @Override
    protected boolean isResultIdRequired() {
        return false;
    }

    @Override
    protected boolean additionalCheck() {
        if (xpPerItem <= 0 || xpPerItem > ConversionLimits.MAX_XP_PER_ITEM) {
            LOGGER.warn("xpPerItem should be in range [1, {}], current is {}",
                    ConversionLimits.MAX_XP_PER_ITEM, xpPerItem);
            return false;
        }

        return super.additionalCheck();
    }

    @Override
    public boolean performConversion(ItemEntity itemEntity, ServerLevel serverLevel) {
        BlockPos pos = itemEntity.blockPosition();

        ItemStack originalStack = itemEntity.getItem();
        int originalStackSize = originalStack.getCount();
        int resultMultiple = getResultMultiple();
        int rounds = computeActualRounds(itemEntity, originalStackSize);

        if (rounds <= 0) {
            LOGGER.debug("No capacity for entity conversion of {}", resultId);
            return false;
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
        return true;
    }

    public int getXpPerItem() {
        return xpPerItem;
    }

    public void setXpPerItem(int xpPerItem) {
        this.xpPerItem = xpPerItem;
    }
}
