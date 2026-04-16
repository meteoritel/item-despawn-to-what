package com.meteorite.itemdespawntowhat.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.MinecraftServer;

// 获得玩家当前的状态，纯客户端方法
public final class PlayerStateChecker {

    private PlayerStateChecker() {
    }

    // 单人模式：本地聚合服务端已经加载并在运行
    public static boolean isSinglePlayerMode(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        return server != null && server.isRunning();
    }

    // 多人服务器模式：已经连接到任意联机服务器
    public static boolean isMultiPlayerMode(Minecraft minecraft) {
        return minecraft.getCurrentServer() != null && isConnectionReady(minecraft);
    }

    private static boolean isConnectionReady(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        return connection != null && connection.getConnection().isConnected();
    }
}
