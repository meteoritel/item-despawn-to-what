package com.meteorite.itemdespawntowhat.client.ui.handler;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.io.ConfigJsonCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 将配置 JSON 按数据量选择单包或分包发送。
 */
public final class ConfigNetworkSender {
    private ConfigNetworkSender() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T extends BaseConversionConfig> void sendToServer(
            ResourceLocation typeId, List<T> configs, ConfigJsonCodec<T> codec) {
        String jsonData = codec.serialize(configs);
        if (SaveConfigChunker.requiresChunking(jsonData)) {
            SaveConfigChunker.sendChunks(typeId, jsonData);
        } else {
            SaveConfigChunker.sendSingle(typeId, jsonData);
        }
    }
}
