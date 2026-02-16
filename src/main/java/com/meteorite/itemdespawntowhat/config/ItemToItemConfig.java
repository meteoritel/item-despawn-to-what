package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class ItemToItemConfig extends BaseConversionConfig{

    @SerializedName("result_limit")
    private int resultLimit;

    public ItemToItemConfig() {
        super();
        this.configType = ConfigType.ITEM_TO_ITEM;
    }

    public ItemToItemConfig(ResourceLocation item, ResourceLocation resultItem) {
        super(item, resultItem);
        this.resultLimit = DEFAULT_RESULT_LIMIT;
        this.configType = ConfigType.ITEM_TO_ITEM;
    }

    // 物品转化需要保证转化前后结果不同，防止循环转化
    @Override
    public boolean shouldProcess() {
        return super.shouldProcess() && this.itemId != this.resultId;
    }

    public Item hasResultItem() {
        return BuiltInRegistries.ITEM.get(resultId);
    }

    // 对于物掉落物结果，计算所有的stack堆叠之和
    @Override
    public int countNearbyResult(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        AABB box = new AABB(
                itemEntity.getX() - MAX_RADIUS, itemEntity.getY() - MAX_RADIUS, itemEntity.getZ() - MAX_RADIUS,
                itemEntity.getX() + MAX_RADIUS, itemEntity.getY() + MAX_RADIUS, itemEntity.getZ() + MAX_RADIUS);

        return serverLevel.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)
                .stream()
                .map(ItemEntity::getItem)
                .filter(itemStack -> BuiltInRegistries.ITEM.getKey(itemStack.getItem()).equals(resultId))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public int getResultLimit() {
        return resultLimit <= 0 ? DEFAULT_RESULT_LIMIT : resultLimit * 64;
    }
    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= this.getResultLimit();
    }
}
