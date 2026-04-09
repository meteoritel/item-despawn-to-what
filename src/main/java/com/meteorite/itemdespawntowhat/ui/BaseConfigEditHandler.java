package com.meteorite.itemdespawntowhat.ui;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.ConfigHandlerManager;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.network.SaveConfigPayload;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BaseConfigEditHandler<T extends BaseConversionConfig> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String LOCAL_MODE_KEY = "gui.itemdespawntowhat.edit.mode.local_mode";
    private static final String SERVER_MODE_KEY = "gui.itemdespawntowhat.edit.mode.server_mode";

    private final ConfigType configType;
    private final BaseConfigHandler<T> handler;
    private final List<T> originalConfigs;
    private final List<T> pendingConfigs = new ArrayList<>();
    private final Minecraft mc;

    public BaseConfigEditHandler(ConfigType configType) {
        this.mc = Minecraft.getInstance();
        this.configType = configType;

        this.handler = ConfigHandlerManager.getInstance().getHandler(configType);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + configType);
        }

        List<T> configs;

        if (PlayerStateChecker.isSinglePlayerServerReady(mc)
                || PlayerStateChecker.isMultiPlayerServerConnected(mc)) {
            // 服务端已经加载，直接读取缓存
            configs = ConfigExtractorManager.getConfigByType(configType);
            LOGGER.debug("Read cache from {}, count = {}", configType.name(), configs.size());
        } else {
            configs = List.of();
            LOGGER.warn("ConfigEditHandler initialized before server-ready state for {}", configType.name());
        }

        this.originalConfigs = new ArrayList<>(configs);
    }

    // ========== 配置操作 ========== //

    // 将当前表单内容写入缓存，对应"Save to Cache"按钮
    public void saveCurrentToCache(EditCallback<T> callback) {
        T config = callback.buildConfigFromFields();
        if (config != null && config.shouldProcess()) {
            pendingConfigs.add(config);
            callback.onClearFields();
            callback.onListChanged();
            LOGGER.debug("Saved to cache: {}", config);
        } else {
            callback.onSaveError();
            LOGGER.warn("Invalid config, this won't be saved, config is {}",config);
        }
    }

    // 将缓存写入文件或发包至服务端，对应"Apply to File"按钮
    public void applyToFile(EditCallback<T> callback) {
        // 先尝试把当前表单内容追加进缓存
        T currentConfig = callback.buildConfigFromFields();
        if (currentConfig != null && currentConfig.shouldProcess()) {
            pendingConfigs.add(currentConfig);
            LOGGER.debug("Added current form to cache before applying");
        }

        if (PlayerStateChecker.isSinglePlayerServerReady(mc)) {
            applyToLocalFile(callback);
        } else if (PlayerStateChecker.isMultiPlayerServerConnected(mc)) {
            applyToServer(callback);
        } else {
            LOGGER.warn("applyToFile ignored because server is not ready, type = {}", configType.name());
            callback.onSaveError();
        }
    }

    // ========== 内部保存逻辑 ========== //
    private void applyToLocalFile(EditCallback<T> callback) {
        try {
            List<T> allConfigs = getAllConfigs();
            handler.saveConfig(allConfigs);
            LOGGER.info("Applied {} configs to local file: {}",
                    allConfigs.size(), configType.getFileName());

            originalConfigs.clear();
            pendingConfigs.clear();
            callback.onClose();
        } catch (IOException e) {
            LOGGER.error("Failed to save config file: {}", configType.getFileName(), e);
        }
    }

    private void applyToServer(EditCallback<T> callback) {
        try {
            List<T> allConfigs = getAllConfigs();
            String jsonData = handler.serializeToJson(allConfigs);
            SaveConfigPayload payload = new SaveConfigPayload(configType, jsonData);
            PacketDistributor.sendToServer(payload);

            LOGGER.info("Sent {} configs to server for type: {}",
                    allConfigs.size(), configType.getFileName());

            originalConfigs.clear();
            pendingConfigs.clear();
            callback.onClose();
        } catch (Exception e) {
            LOGGER.error("Failed to send config packet to server", e);
        }
    }

    // ========== getter ========== //
    public ConfigType getConfigType() {
        return configType;
    }

    public List<T> getPendingConfigs() {
        return pendingConfigs;
    }

    public List<T> getOriginalConfigs() {
        return originalConfigs;
    }

    public List<T> getAllConfigs() {
        List<T> all = new ArrayList<>(originalConfigs);
        all.addAll(pendingConfigs);
        return all;
    }

    // 判断当前运行模式文本（供 UI 渲染使用）
    public Component getModeLabelText() {
        if (PlayerStateChecker.isSinglePlayerServerReady(mc)) {
            return Component.translatable(LOCAL_MODE_KEY);
        } else if (PlayerStateChecker.isMultiPlayerServerConnected(mc)) {
            return Component.translatable(SERVER_MODE_KEY);
        }
        return Component.empty();
    }
}
