package com.meteorite.itemdespawntowhat.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;

public class ItemToBlockConfig extends BaseConversionConfig{

    public ItemToBlockConfig(ResourceLocation item, ResourceLocation resultBlock) {
        super(item);
        this.resultId = resultBlock;
    }

    public Block getResultBlock() {
        return BuiltInRegistries.BLOCK.get(resultId);
    }

    // 这个类不会用到这个方法
    @Override
    public int countNearbyResult(ItemEntity itemEntity) {
        return 0;
    }
    // 这个类不会用到这个方法
    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return false;
    }
}
