package com.meteorite.itemdespawntowhat.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;

// 获得玩家当前的各种状态的实用方法
public final class PlayerStateChecker {

//    // 如果客户端世界未加载，游戏应处于开始界面，此时是纯客户端判断
//    public static boolean isClientWorldLoaded(Minecraft minecraft) {
//        return minecraft.level != null && minecraft.player != null;
//    }

    // 单人模式：本地聚合服务端已经加载并在运行
    public static boolean isSinglePlayerMode(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        return server != null && server.isRunning();
    }

    // 局域网本地服务端：已经连接到 LAN 服务器
    public static boolean isLanServerMode(Minecraft minecraft) {
        ServerData serverData = minecraft.getCurrentServer();
        return serverData != null && serverData.isLan() && isConnectionReady(minecraft);
    }

    // 远程服务器：已经连接到非 LAN 服务器
    public static boolean isRemoteServerMode(Minecraft minecraft) {
        ServerData serverData = minecraft.getCurrentServer();
        return serverData != null && !serverData.isLan() && isConnectionReady(minecraft);
    }

    // 游戏模式，多人服务器模式（LAN 或远程）
    public static boolean isMultiPlayerMode(Minecraft minecraft) {
        return isLanServerMode(minecraft) || isRemoteServerMode(minecraft);
    }

    // 单人世界加载完成，单人服务端加载完成
    public static boolean isSinglePlayerServerReady(Minecraft minecraft) {
        return isSinglePlayerMode(minecraft);
    }

    // 多人服务器已连接
    public static boolean isMultiPlayerServerConnected(Minecraft minecraft) {
        return isMultiPlayerMode(minecraft);
    }

    private static boolean isConnectionReady(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        return connection != null && connection.getConnection().isConnected();
    }
}
