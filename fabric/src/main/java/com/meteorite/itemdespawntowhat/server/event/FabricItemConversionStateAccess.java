package com.meteorite.itemdespawntowhat.server.event;

import com.meteorite.itemdespawntowhat.server.conversion.ItemConversionStateAccess;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Fabric Mixin 转换状态的公共访问适配器。
 */
final class FabricItemConversionStateAccess implements ItemConversionStateAccess {
    static final FabricItemConversionStateAccess INSTANCE = new FabricItemConversionStateAccess();

    private FabricItemConversionStateAccess() {
    }

    @Override
    public boolean isTracked(ItemEntity itemEntity) {
        return state(itemEntity).itemdespawntowhat$isTracked();
    }

    @Override
    public void setTracked(ItemEntity itemEntity, boolean tracked) {
        state(itemEntity).itemdespawntowhat$setTracked(tracked);
    }

    @Override
    public int getCheckTimer(ItemEntity itemEntity) {
        return state(itemEntity).itemdespawntowhat$getCheckTimer();
    }

    @Override
    public void setCheckTimer(ItemEntity itemEntity, int timer) {
        state(itemEntity).itemdespawntowhat$setCheckTimer(timer);
    }

    @Override
    public String getSelectedConfigId(ItemEntity itemEntity) {
        return state(itemEntity).itemdespawntowhat$getSelectedConfigId();
    }

    @Override
    public void setSelectedConfigId(ItemEntity itemEntity, String selectedConfigId) {
        state(itemEntity).itemdespawntowhat$setSelectedConfigId(selectedConfigId);
    }

    @Override
    public boolean isConversionLocked(ItemEntity itemEntity) {
        return state(itemEntity).itemdespawntowhat$isConversionLocked();
    }

    @Override
    public void setConversionLocked(ItemEntity itemEntity, boolean locked) {
        state(itemEntity).itemdespawntowhat$setConversionLocked(locked);
    }

    private static ItemConversionState state(ItemEntity itemEntity) {
        return (ItemConversionState) itemEntity;
    }
}
