package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;

public abstract class BaseItemToEntityConfig extends BaseConversionConfig{

    // 附近实体数量限制，所有实体类型共用，子类在无参构造器中修改
    @SerializedName("result_limit")
    protected int resultLimit = 30;
    // 缓存的结果实体类型
    //protected transient EntityType<?> cachedResultEntityType;

    public BaseItemToEntityConfig() {
    }

    public BaseItemToEntityConfig(ResourceLocation item, ResourceLocation result) {
        super(item, result);
    }

    public int getResultLimit() {
        return resultLimit;
    }

    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }

    @Override
    protected int getResultCapacityInStartItems(ItemEntity itemEntity) {
        int current = countNearbyResult(itemEntity);
        int remaining = getResultLimit() - current;
        LOGGER.debug("current entity size = {}, remain = {}", current, remaining);
        if (remaining <= 0) {
            return 0;
        }
        return remaining / Math.max(1, getResultMultiple());
    }

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= getResultLimit();
    }

}
