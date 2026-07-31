package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.network.handler.SaveConfigChunkAccumulator;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ForceCloseEditorPayload;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 跨平台配置编辑会话超时检查服务。
 */
public final class EditSessionTimeoutService {
    private static final long CHECK_INTERVAL_MS = 20_000L;
    private static final long TIMEOUT_MS = 300_000L;
    private static long lastCheckTime;

    private EditSessionTimeoutService() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;

        UUID timedOutPlayerId = EditSessionLockManager.checkTimeout(TIMEOUT_MS);
        if (timedOutPlayerId == null) {
            return;
        }

        SaveConfigChunkAccumulator.clear(timedOutPlayerId);
        ServerPlayer player = server.getPlayerList().getPlayer(timedOutPlayerId);
        if (player != null) {
            Services.PLATFORM.sendToPlayer(player, new ForceCloseEditorPayload());
            player.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.idle_timeout"));
        }
    }
}
