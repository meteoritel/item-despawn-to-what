package com.meteorite.itemdespawntowhat.config.conversion;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

public abstract class BaseItemToEntityConfig extends BaseConversionConfig{

    @SerializedName("result_limit")
    protected int resultLimit = 30;

    public BaseItemToEntityConfig() {
    }

    public BaseItemToEntityConfig(String item, String result) {
        super(item, result);
    }

    @Override
    public final int countNearbyResult(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel level)) return 0;
        return countNearbyResult(level, itemEntity.blockPosition());
    }

    // 统一构建检测 AABB
    protected AABB buildSearchBox(BlockPos pos) {
        return new AABB(
                pos.getX() - MAX_RADIUS, pos.getY() - MAX_RADIUS, pos.getZ() - MAX_RADIUS,
                pos.getX() + MAX_RADIUS, pos.getY() + MAX_RADIUS, pos.getZ() + MAX_RADIUS
        );
    }

    @Override
    protected int getResultCapacityInRounds(ItemEntity itemEntity) {
        int current = countNearbyResult(itemEntity);
        int remaining = getRawResultLimit() - current;
        LOGGER.debug("current entity size = {}, remain = {}", current, remaining);
        if (remaining <= 0) {
            return 0;
        }
        return remaining / Math.max(1, getResultMultiple());
    }

    @Override
    public boolean isResultLimitExceeded(ItemEntity itemEntity) {
        return this.countNearbyResult(itemEntity) >= getRawResultLimit();
    }

    // 子类方法
    // 子类实现具体的实体/物品计数逻辑
    protected abstract int countNearbyResult(ServerLevel level, BlockPos pos);

    // ========== setter & getter ========== //

    public int getResultLimit() {
        return resultLimit;
    }

    protected int getRawResultLimit() {
        return resultLimit;
    }

    public void setResultLimit(int resultLimit) {
        this.resultLimit = resultLimit;
    }

}
