package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class ItemToBlockConfig extends BaseConversionConfig{

    // 最大检测半径的限制，默认为6
    @SerializedName("radius_limit")
    private int radius;
    @SerializedName("block_of_item")
    private boolean enableItemBlock;

    public ItemToBlockConfig() {
        super();
        this.configType = ConfigType.ITEM_TO_BLOCK;
    }

    public ItemToBlockConfig(ResourceLocation item, ResourceLocation resultBlock) {
        super(item, resultBlock);
        this.radius = MAX_RADIUS;
        this.enableItemBlock = false;
        this.configType = ConfigType.ITEM_TO_BLOCK;
    }

    // 当开启了使用物品对应方块，且物品是方块物品，就直接使用对应的方块
    public Block getResultBlock() {
        return BuiltInRegistries.BLOCK.get(this.getResultId());
    }

    @Override
    public String getResultDescriptionId() {
        return getResultBlock().getDescriptionId();
    }

    @Override
    public ItemStack getResultIcon() {
        Block block = getResultBlock();
        if (block == null) {
            return new ItemStack(Items.BARRIER);
        }
        return block.asItem() == Items.AIR
                ? new ItemStack(Items.BARRIER)
                : new ItemStack(block.asItem());
    }

    // 确保方块形式存在
    @Override
    public boolean shouldProcess() {
        if (enableItemBlock) {
            return isValidResourceLocation(itemId)
                    && conversionTime > 0
                    && conversionTime <= 300
                    && resultMultiple > 0
                    && radius >= 0
                    && this.getResultBlock() != null;
        } else {
            return super.shouldProcess() && this.getResultBlock() != null && radius >= 0;
        }
    }

    // 这个类不会用到这个方法
    @Override
    public int countNearbyResult(ItemEntity itemEntity) {
        return 0;
    }
    // 这个类不会用到这个方法，无条件通过
    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return false;
    }

    // 最小检测半径为0
    public int getRadius() {
        return Math.max(0,radius);
    }
    public void setRadius(int radius) {
        this.radius = radius;
    }
    public boolean isEnableItemBlock() {
        return enableItemBlock;
    }
    public void setEnableItemBlock(boolean enableItemBlock) {
        this.enableItemBlock = enableItemBlock;
    }

    @Override
    public ResourceLocation getResultId() {
        if (enableItemBlock && getStartItem() instanceof BlockItem blockItem) {
            return BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        } else {
            return resultId;
        }
    }
}
