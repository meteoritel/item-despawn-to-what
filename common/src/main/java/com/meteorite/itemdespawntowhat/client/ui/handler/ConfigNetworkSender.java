package com.meteorite.itemdespawntowhat.client.ui.handler;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.io.ConfigJsonCodec;

import java.util.List;

/**
 * 将配置 JSON 按数据量选择单包或分包发送。
 */
public final class ConfigNetworkSender {
    private ConfigNetworkSender() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T extends BaseConversionConfig> void sendToServer(
            ConfigType configType, List<T> configs, ConfigJsonCodec<T> codec) {
        String jsonData = codec.serialize(configs);
        if (SaveConfigChunker.requiresChunking(jsonData)) {
            SaveConfigChunker.sendChunks(configType, jsonData);
        } else {
            SaveConfigChunker.sendSingle(configType, jsonData);
        }
    }
}
