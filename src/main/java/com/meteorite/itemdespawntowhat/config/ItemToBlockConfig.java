package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;

public class ItemToBlockConfig extends BaseConversionConfig{

    // 最大检测半径的限制，默认为6
    @SerializedName("radius_limit")
    private int radius;

    public ItemToBlockConfig() {
        super();
    }

    public ItemToBlockConfig(ResourceLocation item, ResourceLocation resultBlock) {
        super(item);
        this.radius = MAX_RADIUS;
        this.resultId = resultBlock;
    }

    public Block getResultBlock() {
        return BuiltInRegistries.BLOCK.get(resultId);
    }

    // 确保方块形式存在
    @Override
    public boolean shouldProcess() {
        return super.shouldProcess() && this.getResultBlock() != null;
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
}
