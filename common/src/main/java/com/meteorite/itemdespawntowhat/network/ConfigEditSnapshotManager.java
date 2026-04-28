package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigEditSnapshotManager {
    private static final Map<ConfigType, String> SNAPSHOT_JSONS = new ConcurrentHashMap<>();

    private ConfigEditSnapshotManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void putSnapshot(ConfigType configType, String jsonData) {
        if (configType == null) {
            return;
        }
        if (jsonData == null) {
            SNAPSHOT_JSONS.remove(configType);
        } else {
            SNAPSHOT_JSONS.put(configType, jsonData);
        }
    }

    public static <T extends BaseConversionConfig> List<T> consumeSnapshot(
            ConfigType configType,
            BaseConfigHandler<T> handler
    ) {
        if (configType == null || handler == null) {
            return Collections.emptyList();
        }

        String jsonData = SNAPSHOT_JSONS.remove(configType);
        if (jsonData == null || jsonData.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> configs = handler.deserializeFromJson(jsonData);
        return configs != null ? configs : Collections.emptyList();
    }

    public static void clearAll() {
        SNAPSHOT_JSONS.clear();
    }
}
