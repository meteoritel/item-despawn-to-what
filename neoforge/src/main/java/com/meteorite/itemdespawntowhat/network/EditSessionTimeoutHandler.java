package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge 服务端编辑会话超时事件入口。
 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public final class EditSessionTimeoutHandler {
    private EditSessionTimeoutHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EditSessionTimeoutService.tick(event.getServer());
    }
}
