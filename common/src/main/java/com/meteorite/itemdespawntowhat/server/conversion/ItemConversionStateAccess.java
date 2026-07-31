package com.meteorite.itemdespawntowhat.server.conversion;

import net.minecraft.world.entity.item.ItemEntity;

/**
 * 物品转换状态的跨平台读写接口。
 */
public interface ItemConversionStateAccess {

    boolean isTracked(ItemEntity itemEntity);

    void setTracked(ItemEntity itemEntity, boolean tracked);

    int getCheckTimer(ItemEntity itemEntity);

    void setCheckTimer(ItemEntity itemEntity, int timer);

    String getSelectedConfigId(ItemEntity itemEntity);

    void setSelectedConfigId(ItemEntity itemEntity, String selectedConfigId);

    boolean isConversionLocked(ItemEntity itemEntity);

    void setConversionLocked(ItemEntity itemEntity, boolean locked);

    default void resetProgress(ItemEntity itemEntity) {
        setCheckTimer(itemEntity, 0);
        setSelectedConfigId(itemEntity, "");
        setConversionLocked(itemEntity, false);
    }

    default void clearConversionState(ItemEntity itemEntity) {
        setTracked(itemEntity, false);
        resetProgress(itemEntity);
    }
}
