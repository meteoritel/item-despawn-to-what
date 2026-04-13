package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// 客户端临时快照缓存：保存服务端下发的配置 JSON，供编辑界面一次性消费。
public final class ConfigEditSnapshotManager {
    private static final Map<ConfigType, String> SNAPSHOT_JSONS = new EnumMap<>(ConfigType.class);

    private ConfigEditSnapshotManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 记录服务端发来的快照 JSON，供随后的编辑屏幕消费一次。
    public static synchronized void putSnapshot(ConfigType configType, String jsonData) {
        if (configType == null) {
            return;
        }
        if (jsonData == null) {
            SNAPSHOT_JSONS.remove(configType);
        } else {
            SNAPSHOT_JSONS.put(configType, jsonData);
        }
    }

    public static synchronized <T extends BaseConversionConfig> List<T> consumeSnapshot(
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

    // 打开新的选择界面前清空旧快照，避免上一次会话残留数据污染当前会话。
    public static synchronized void clearAll() {
        SNAPSHOT_JSONS.clear();
    }
}
