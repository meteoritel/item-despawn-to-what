package com.meteorite.itemdespawntowhat.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Fabric 服务端编辑会话超时事件入口。
 */
public final class EditSessionTimeoutHandler {
    private EditSessionTimeoutHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EditSessionTimeoutService::tick);
    }
}
