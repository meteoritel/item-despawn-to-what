package com.meteorite.itemdespawntowhat.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

// 服务端编辑锁：控制同一时间只允许一个玩家占用配置编辑会话。
public final class EditSessionLockManager {
    private static UUID currentEditor;
    private static volatile long lastActivityTime;

    private EditSessionLockManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static synchronized boolean tryAcquire(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (currentEditor == null || currentEditor.equals(playerId)) {
            currentEditor = playerId;
            lastActivityTime = System.currentTimeMillis();
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

    // 刷新最后一次活动时间，每次有编辑相关操作时调用
    public static void touch() {
        if (currentEditor != null) {
            lastActivityTime = System.currentTimeMillis();
        }
    }

    // 检查是否超时，超时则自动释放锁并返回被踢出的玩家 UUID
    public static synchronized UUID checkTimeout(long timeoutMs) {
        if (currentEditor != null && System.currentTimeMillis() - lastActivityTime > timeoutMs) {
            UUID timedOut = currentEditor;
            currentEditor = null;
            return timedOut;
        }
        return null;
    }
}
