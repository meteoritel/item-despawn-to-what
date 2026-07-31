package com.meteorite.itemdespawntowhat.server.event;

import com.meteorite.itemdespawntowhat.server.conversion.ItemConversionProcessor;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Fabric 服务端物品转换事件入口。
 */
public final class ItemConversionEvent {
    private static final int DEFAULT_ITEM_LIFESPAN = 6_000;
    private static final FabricItemConversionStateAccess STATE = FabricItemConversionStateAccess.INSTANCE;

    private static boolean registered;

    private ItemConversionEvent() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!world.isClientSide() && entity instanceof ItemEntity itemEntity) {
                ItemConversionProcessor.trackIfEligible(itemEntity, STATE);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world instanceof ServerLevel serverLevel) {
                LevelTaskManager.tick(serverLevel);
            }
        });
    }

    public static void tickTrackedItem(ItemEntity itemEntity) {
        ItemConversionProcessor.tickTrackedItem(itemEntity, STATE, DEFAULT_ITEM_LIFESPAN);
    }
}
