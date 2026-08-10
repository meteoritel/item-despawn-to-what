package com.meteorite.itemdespawntowhat.config.execution;

import com.meteorite.itemdespawntowhat.config.conversion.ConversionConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 转换行为策略 SPI。
 */
public interface ConversionExecutor<C extends ConversionConfig> {
    boolean performConversion(C config, ItemEntity itemEntity, ServerLevel level);

    default int countNearbyResult(C config, ItemEntity itemEntity) {
        return 0;
    }

    default boolean isResultLimitExceeded(C config, ItemEntity itemEntity) {
        return false;
    }

    default int getResultCapacityInRounds(C config, ItemEntity itemEntity) {
        return Integer.MAX_VALUE;
    }
}
