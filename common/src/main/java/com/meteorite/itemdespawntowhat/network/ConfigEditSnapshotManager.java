package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.io.ConfigJsonCodec;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 暂存客户端尚未被编辑界面消费的服务端配置快照。
 */
public final class ConfigEditSnapshotManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<ResourceLocation, String> SNAPSHOT_JSONS = new ConcurrentHashMap<>();

    private ConfigEditSnapshotManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void putSnapshot(ResourceLocation typeId, String jsonData) {
        if (typeId == null) {
            return;
        }
        if (jsonData == null) {
            SNAPSHOT_JSONS.remove(typeId);
        } else {
            SNAPSHOT_JSONS.put(typeId, jsonData);
        }
    }

    public static <T extends BaseConversionConfig> List<T> consumeSnapshot(
            ResourceLocation typeId,
            ConfigJsonCodec<T> codec
    ) {
        if (typeId == null || codec == null) {
            return Collections.emptyList();
        }

        String jsonData = SNAPSHOT_JSONS.remove(typeId);
        if (jsonData == null || jsonData.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return codec.deserialize(jsonData);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to deserialize config snapshot for type {}", typeId, e);
            return Collections.emptyList();
        }
    }

    public static void clearAll() {
        SNAPSHOT_JSONS.clear();
    }
}
