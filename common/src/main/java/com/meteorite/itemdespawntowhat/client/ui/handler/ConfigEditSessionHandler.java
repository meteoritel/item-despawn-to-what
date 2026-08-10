package com.meteorite.itemdespawntowhat.client.ui.handler;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.client.ui.support.EditCallback;
import com.meteorite.itemdespawntowhat.network.ConfigEditSnapshotManager;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.io.ConfigJsonCodec;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonElement;

/**
 * 协调客户端编辑草稿、服务端快照与保存请求。
 */
public class ConfigEditSessionHandler<T extends BaseConversionConfig> {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ResourceLocation typeId;
    private final ConfigJsonCodec<T> codec;
    private final List<T> originalConfigs;
    private final List<T> pendingConfigs = new ArrayList<>();

    public ConfigEditSessionHandler(ResourceLocation typeId, ConfigJsonCodec<T> codec) {
        this.typeId = typeId;
        this.codec = codec;

        this.originalConfigs = new ArrayList<>(loadOriginalConfigs());
    }

    private List<T> loadOriginalConfigs() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            try {
                ConversionType configType = ConversionTypeRegistry.byId(typeId);
                if (configType != null) {
                    List<T> configs = ConfigExtractorManager.getConfigByType(configType);
                    LOGGER.debug("Loaded server cache for {}, count = {}", typeId, configs.size());
                    return configs;
                }
            } catch (IllegalStateException e) {
                LOGGER.warn("Server cache not ready for {}, falling back to snapshot", typeId, e);
            }
        }

        List<T> configs = ConfigEditSnapshotManager.consumeSnapshot(typeId, codec);
        if (configs.isEmpty()) {
            LOGGER.warn("No client snapshot available for {}, using empty initial list", typeId);
        } else {
            LOGGER.debug("Loaded client snapshot for {}, count = {}", typeId, configs.size());
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
        JsonElement draftTree = codec.toJsonTree(draft);
        return getAllConfigs().stream()
                .map(codec::toJsonTree)
                .anyMatch(draftTree::equals);
    }

    public void applyToFile(EditCallback<T> callback) {
        T draft = callback.buildConfigFromFields();
        if (draft != null && draft.shouldProcess()) {
            pendingConfigs.add(draft);
            LOGGER.debug("Added current form to pending list before applying");
        }

        applyToServer(callback);
    }

    private void applyToServer(EditCallback<T> callback) {
        try {
            List<T> allConfigs = getAllConfigs();
            ConfigNetworkSender.sendToServer(typeId, allConfigs, codec);
            LOGGER.info("Sent {} configs to server for type: {}",
                    allConfigs.size(), typeId);
            originalConfigs.clear();
            pendingConfigs.clear();
            callback.onClose();
        } catch (Exception e) {
            LOGGER.error("Failed to send config packet to server", e);
            callback.onSaveError();
        }
    }

    // ========== getters ========== //
    public ResourceLocation getTypeId() {
        return typeId;
    }

    public String getFileName() {
        return typeId.getNamespace() + "/" + typeId.getPath() + ".json";
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
