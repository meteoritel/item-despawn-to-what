package com.meteorite.itemdespawntowhat.util;

import com.meteorite.itemdespawntowhat.server.event.ItemConversionEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class ItemReturnUtil {

    private ItemReturnUtil() {
    }

    public static void spawnLockedItem(ServerLevel serverLevel, ItemStack stack,
                                       double x, double y, double z) {
        ItemEntity returnItem = new ItemEntity(serverLevel, x, y, z, stack);
        returnItem.getPersistentData().putBoolean(ItemConversionEvent.CHECK_LOCK_TAG, true);
        serverLevel.addFreshEntity(returnItem);
    }
}
