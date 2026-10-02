package com.meteorite.itemdespawntowhat.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/** 两端共用的平台环境查询与服务端发包能力。 */
public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    Path getConfigDir();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    // 向指定玩家发送服务端负载。
    default void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        throw new UnsupportedOperationException("sendToPlayer not implemented for " + getPlatformName());
    }
}
