package com.meteorite.itemdespawntowhat.server.event;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.server.conversion.ItemConversionStateAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * NeoForge persistent data 转换状态的公共访问适配器。
 */
final class NeoForgeItemConversionStateAccess implements ItemConversionStateAccess {
    static final NeoForgeItemConversionStateAccess INSTANCE = new NeoForgeItemConversionStateAccess();

    private static final String CHECK_TAG = ItemDespawnToWhat.MOD_ID + ":needs_check";
    private static final String TIMER_TAG = ItemDespawnToWhat.MOD_ID + ":check_timer";
    private static final String SELECTED_CONFIG_TAG = ItemDespawnToWhat.MOD_ID + ":selected_config";
    private static final String CONVERSION_LOCK_TAG = ItemDespawnToWhat.MOD_ID + ":conversion_lock";

    private NeoForgeItemConversionStateAccess() {
    }

    @Override
    public boolean isTracked(ItemEntity itemEntity) {
        return data(itemEntity).getBoolean(CHECK_TAG);
    }

    @Override
    public void setTracked(ItemEntity itemEntity, boolean tracked) {
        if (tracked) {
            data(itemEntity).putBoolean(CHECK_TAG, true);
        } else {
            data(itemEntity).remove(CHECK_TAG);
        }
    }

    @Override
    public int getCheckTimer(ItemEntity itemEntity) {
        return data(itemEntity).getInt(TIMER_TAG);
    }

    @Override
    public void setCheckTimer(ItemEntity itemEntity, int timer) {
        data(itemEntity).putInt(TIMER_TAG, Math.max(0, timer));
    }

    @Override
    public String getSelectedConfigId(ItemEntity itemEntity) {
        return data(itemEntity).getString(SELECTED_CONFIG_TAG);
    }

    @Override
    public void setSelectedConfigId(ItemEntity itemEntity, String selectedConfigId) {
        if (selectedConfigId == null || selectedConfigId.isEmpty()) {
            data(itemEntity).remove(SELECTED_CONFIG_TAG);
        } else {
            data(itemEntity).putString(SELECTED_CONFIG_TAG, selectedConfigId);
        }
    }

    @Override
    public boolean isConversionLocked(ItemEntity itemEntity) {
        return data(itemEntity).getBoolean(CONVERSION_LOCK_TAG);
    }

    @Override
    public void setConversionLocked(ItemEntity itemEntity, boolean locked) {
        if (locked) {
            data(itemEntity).putBoolean(CONVERSION_LOCK_TAG, true);
        } else {
            data(itemEntity).remove(CONVERSION_LOCK_TAG);
        }
    }

    @Override
    public void resetProgress(ItemEntity itemEntity) {
        CompoundTag data = data(itemEntity);
        data.remove(TIMER_TAG);
        data.remove(SELECTED_CONFIG_TAG);
        data.remove(CONVERSION_LOCK_TAG);
    }

    @Override
    public void clearConversionState(ItemEntity itemEntity) {
        data(itemEntity).remove(CHECK_TAG);
        resetProgress(itemEntity);
    }

    private static CompoundTag data(ItemEntity itemEntity) {
        return itemEntity.getPersistentData();
    }
}
