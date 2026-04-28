package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.network.payload.s2c.ForceCloseEditorPayload;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class EditSessionTimeoutHandler {
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_MS = 20_000L;
    private static final long TIMEOUT_MS = 300_000L;

    private EditSessionTimeoutHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            if (now - lastCheckTime < CHECK_INTERVAL_MS) return;
            lastCheckTime = now;

            UUID timedOut = EditSessionLockManager.checkTimeout(TIMEOUT_MS);
            if (timedOut != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(timedOut);
                if (player != null) {
                    Services.PLATFORM.sendToPlayer(player, new ForceCloseEditorPayload());
                    player.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.idle_timeout"));
                }
            }
        });
    }
}
