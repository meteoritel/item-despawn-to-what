package com.meteorite.itemdespawntowhat.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.MinecraftServer;

// 获得玩家当前的各种状态的实用方法
public class PlayerStateChecker {

    // 如果客户端世界未加载，游戏应处于开始界面，此时isSinglePlayerMode是false，因为世界加载前不会为其赋值
    public static boolean isClientWorldLoaded(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null;
    }

    // 游戏模式，true为单人，但是主页面此值还未赋值，加载世界后这个值才有意义
    public static boolean isSinglePlayerMode(Minecraft minecraft) {
        return minecraft.hasSingleplayerServer();
    }

    // 游戏模式，多人服务器模式，因为上一条的原因，所以还要有双端的通讯才能保证处于多人模式
    public static boolean isMultiPlayerMode(Minecraft minecraft) {
        return !minecraft.hasSingleplayerServer()
                && minecraft.getConnection() != null;
    }

    // 单人世界加载完成，单人服务端加载完成
    public static boolean isSinglePlayerServerReady(Minecraft minecraft) {
        if (!isSinglePlayerMode(minecraft)) {
            return false;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        return server != null && server.isRunning();
    }

    // 多人服务器已连接
    public static boolean isMultiPlayerServerConnected(Minecraft minecraft) {
        if (!isMultiPlayerMode(minecraft)) {
            return false;
        }
        ClientPacketListener connection = minecraft.getConnection();
        return connection != null && connection.getConnection().isConnected();
    }
}
