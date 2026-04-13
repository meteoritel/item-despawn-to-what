package com.meteorite.itemdespawntowhat.server.configedit;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

// 当服务端有人在编辑时开启全局锁
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
