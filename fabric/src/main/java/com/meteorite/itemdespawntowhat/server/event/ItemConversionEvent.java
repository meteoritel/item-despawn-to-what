package com.meteorite.itemdespawntowhat.server.event;

import com.meteorite.itemdespawntowhat.server.conversion.ConversionTracker;
import com.meteorite.itemdespawntowhat.server.task.LevelTaskManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Fabric 服务端物品转换事件入口。
 */
public final class ItemConversionEvent {
    private static final int VANILLA_ITEM_LIFESPAN = 6_000;
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
                ConversionTracker.trackIfEligible(itemEntity);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world instanceof ServerLevel serverLevel) {
                LevelTaskManager.tick(serverLevel);
                ConversionTracker.tick(serverLevel, ItemConversionEvent::getItemLifespan);
            }
        });
    }

    // Fabric/vanilla 1.21.1 没有 NeoForge 的 ItemStack 寿命扩展 API。
    private static int getItemLifespan(ItemEntity itemEntity, ServerLevel level) {
        return VANILLA_ITEM_LIFESPAN;
    }
}
