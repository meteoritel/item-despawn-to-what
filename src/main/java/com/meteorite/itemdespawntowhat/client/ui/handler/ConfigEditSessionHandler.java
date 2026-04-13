package com.meteorite.itemdespawntowhat.client.ui.handler;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.ConfigHandlerManager;
import com.meteorite.itemdespawntowhat.client.ui.EditCallback;
import com.meteorite.itemdespawntowhat.network.ConfigEditSnapshotManager;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

// 客户端编辑会话控制器：管理草稿、待提交列表和配置提交流程。
public class ConfigEditSessionHandler<T extends BaseConversionConfig> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String LOCAL_MODE_KEY = "gui.itemdespawntowhat.edit.mode.local_mode";
    private static final String SERVER_MODE_KEY = "gui.itemdespawntowhat.edit.mode.server_mode";

    private final ConfigType configType;
    private final BaseConfigHandler<T> handler;
    // 当前会话开始时，从服务端快照拿到的原始配置
    private final List<T> originalConfigs;
    // 当前会话中新建但尚未提交到服务端的条目
    private final List<T> pendingConfigs = new ArrayList<>();
    private final Minecraft mc;

    public ConfigEditSessionHandler(ConfigType configType) {
        this.mc = Minecraft.getInstance();
        this.configType = configType;

        this.handler = ConfigHandlerManager.getInstance().getHandler(configType);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + configType);
        }

        this.originalConfigs = new ArrayList<>(loadOriginalConfigs());
    }

    private List<T> loadOriginalConfigs() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                List<T> configs = ConfigExtractorManager.getConfigByType(configType);
                LOGGER.debug("Loaded server cache for {}, count = {}", configType.name(), configs.size());
                return configs;
            } catch (IllegalStateException e) {
                LOGGER.warn("Server cache not ready for {}, falling back to snapshot", configType.name(), e);
            }
        }

        // 远程服务器场景仍然走快照，保持双端兼容。
        List<T> configs = ConfigEditSnapshotManager.consumeSnapshot(configType, handler);
        if (configs.isEmpty()) {
            LOGGER.warn("No client snapshot available for {}, using empty initial list", configType.name());
        } else {
            LOGGER.debug("Loaded client snapshot for {}, count = {}", configType.name(), configs.size());
        }
        return configs;
    }

    // ========== 配置操作 ========== //
    // 将当前表单内容写入缓存，对应"Save to Cache"按钮
    public void saveCurrentToCache(EditCallback<T> callback) {
        T draft = callback.buildConfigFromFields();
        if (draft == null || !draft.shouldProcess()) {
            callback.onSaveError();
            LOGGER.warn("Invalid config, this won't be saved");
            return;
        }

        prepareDraftForSession(draft);
        pendingConfigs.add(draft);
        callback.onClearFields();
        callback.onListChanged();
        LOGGER.debug("Saved to cache: {}", draft);
    }

    // 将当前会话内容统一提交给服务端，由服务端负责落盘和缓存刷新，对应 apply to file按钮
    public void applyToFile(EditCallback<T> callback) {
        // Apply 前先把表单里最后一次修改收进待提交列表，避免漏掉用户刚输入的内容。
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
            // 只把最终 JSON 发给服务端，由服务端负责落盘和缓存刷新。
            String jsonData = handler.serializeToJson(allConfigs);

            // 判断json是否过大，是否需要分包
            if (SaveConfigChunker.requiresChunking(jsonData)) {
                int chunkCount = SaveConfigChunker.sendChunks(configType, jsonData);
                LOGGER.info("Sent {} configs to server for type: {} in {} chunks",
                        allConfigs.size(), configType.getFileName(), chunkCount);
            } else {
                SaveConfigChunker.sendSingle(configType, jsonData);
                LOGGER.info("Sent {} configs to server for type: {}",
                        allConfigs.size(), configType.getFileName());
            }

            originalConfigs.clear();
            pendingConfigs.clear();
            callback.onClose();
        } catch (Exception e) {
            LOGGER.error("Failed to send config packet to server", e);
            callback.onSaveError();
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
