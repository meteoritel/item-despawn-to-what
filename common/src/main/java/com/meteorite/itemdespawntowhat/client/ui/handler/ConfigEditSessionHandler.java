package com.meteorite.itemdespawntowhat.client.ui.handler;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.ConfigHandlerManager;
import com.meteorite.itemdespawntowhat.client.ui.support.EditCallback;
import com.meteorite.itemdespawntowhat.network.ConfigEditSnapshotManager;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.meteorite.itemdespawntowhat.platform.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ConfigEditSessionHandler<T extends BaseConversionConfig> {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ConfigType configType;
    private final BaseConfigHandler<T> handler;
    private final List<T> originalConfigs;
    private final List<T> pendingConfigs = new ArrayList<>();

    public ConfigEditSessionHandler(ConfigType configType) {
        this.configType = configType;

        this.handler = ConfigHandlerManager.getInstance(Services.PLATFORM.getConfigDir()).getHandler(configType);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + configType);
        }

        this.originalConfigs = new ArrayList<>(loadOriginalConfigs());
    }

    private List<T> loadOriginalConfigs() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            try {
                List<T> configs = ConfigExtractorManager.getConfigByType(configType);
                LOGGER.debug("Loaded server cache for {}, count = {}", configType.name(), configs.size());
                return configs;
            } catch (IllegalStateException e) {
                LOGGER.warn("Server cache not ready for {}, falling back to snapshot", configType.name(), e);
            }
        }

        List<T> configs = ConfigEditSnapshotManager.consumeSnapshot(configType, handler);
        if (configs.isEmpty()) {
            LOGGER.warn("No client snapshot available for {}, using empty initial list", configType.name());
        } else {
            LOGGER.debug("Loaded client snapshot for {}, count = {}", configType.name(), configs.size());
        }
        return configs;
    }

    // ========== Config operations ========== //
    public void saveCurrentToCache(EditCallback<T> callback) {
        T draft = callback.buildConfigFromFields();
        if (draft == null || !draft.shouldProcess()) {
            callback.onSaveError();
            LOGGER.warn("Invalid config, this won't be saved");
            return;
        }

        prepareDraftForSession(draft);

        if (isDuplicate(draft)) {
            callback.onDisplayError(Component.translatable("gui.itemdespawntowhat.edit.duplicate"));
            LOGGER.warn("Duplicate config detected, not adding to cache: {}", draft);
            return;
        }

        pendingConfigs.add(draft);
        callback.onClearFields();
        callback.onListChanged();
        LOGGER.debug("Saved to cache: {}", draft);
    }

    private boolean isDuplicate(T draft) {
        List<T> all = getAllConfigs();
        for (T existing : all) {
            if (handler.getGson().toJson(existing).equals(handler.getGson().toJson(draft))) {
                return true;
            }
        }
        return false;
    }

    public void applyToFile(EditCallback<T> callback) {
        T draft = callback.buildConfigFromFields();
        if (draft != null && draft.shouldProcess()) {
            prepareDraftForSession(draft);
            pendingConfigs.add(draft);
            LOGGER.debug("Added current form to pending list before applying");
        }

        applyToServer(callback);
    }

    private void prepareDraftForSession(T draft) {
        draft.initCache();
        if (draft.isTagMode()) {
            draft.expandTagItems();
        }
    }

    private void applyToServer(EditCallback<T> callback) {
        try {
            List<T> allConfigs = getAllConfigs();
            ConfigNetworkSender.sendToServer(configType, allConfigs, handler);
            LOGGER.info("Sent {} configs to server for type: {}",
                    allConfigs.size(), configType.getFileName());
            originalConfigs.clear();
            pendingConfigs.clear();
            callback.onClose();
        } catch (Exception e) {
            LOGGER.error("Failed to send config packet to server", e);
            callback.onSaveError();
        }
    }

    // ========== getters ========== //
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

}
