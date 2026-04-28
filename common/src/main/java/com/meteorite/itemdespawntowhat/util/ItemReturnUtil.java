package com.meteorite.itemdespawntowhat.util;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class ItemReturnUtil {

    private ItemReturnUtil() {
    }

    public static void spawnLockedItem(ServerLevel serverLevel, ItemStack stack,
                                       double x, double y, double z) {
        ItemEntity returnItem = new ItemEntity(serverLevel, x, y, z, stack);
        returnItem.addTag(Constants.CHECK_LOCK_TAG);
        serverLevel.addFreshEntity(returnItem);
    }
}
