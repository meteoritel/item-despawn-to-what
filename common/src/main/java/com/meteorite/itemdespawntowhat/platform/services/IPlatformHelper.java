package com.meteorite.itemdespawntowhat.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    Path getConfigDir();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /** Send a custom payload to the server (client-side only). */
    default void sendToServer(CustomPacketPayload payload) {
        throw new UnsupportedOperationException("sendToServer not implemented for " + getPlatformName());
    }

    /** Send a custom payload to a specific player (server-side only). */
    default void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        throw new UnsupportedOperationException("sendToPlayer not implemented for " + getPlatformName());
    }
}
