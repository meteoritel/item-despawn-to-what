package com.meteorite.itemdespawntowhat.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeDefinition;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

public interface IPlatformHelper {

    /** 第三方转换类型注册入口；平台可在启动事件中转发调用。 */
    default <T extends BaseConversionConfig> ConversionType registerConversionType(
            ResourceLocation id, ConversionTypeDefinition<T> definition) {
        return ConversionTypeRegistry.register(id, definition);
    }

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
