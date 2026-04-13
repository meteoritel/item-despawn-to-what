package com.meteorite.itemdespawntowhat.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

// 服务端编辑锁：控制同一时间只允许一个玩家占用配置编辑会话。
public final class EditSessionLockManager {
    private static UUID currentEditor;

    private EditSessionLockManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static synchronized boolean tryAcquire(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (currentEditor == null || currentEditor.equals(playerId)) {
            currentEditor = playerId;
            return true;
        }
        return false;
    }

    public static synchronized boolean isOwnedBy(ServerPlayer player) {
        return player != null && currentEditor != null && currentEditor.equals(player.getUUID());
    }

    public static synchronized void release(ServerPlayer player) {
        if (isOwnedBy(player)) {
            currentEditor = null;
        }
    }

    public static synchronized void clear() {
        currentEditor = null;
    }
}
