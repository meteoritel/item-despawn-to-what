package com.meteorite.itemdespawntowhat.client.ui.handler;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;

import java.util.List;

public final class ConfigNetworkSender {
    private ConfigNetworkSender() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T extends BaseConversionConfig> void sendToServer(
            ConfigType configType, List<T> configs, BaseConfigHandler<T> handler) {
        String jsonData = handler.serializeToJson(configs);
        if (SaveConfigChunker.requiresChunking(jsonData)) {
            SaveConfigChunker.sendChunks(configType, jsonData);
        } else {
            SaveConfigChunker.sendSingle(configType, jsonData);
        }
    }
}
