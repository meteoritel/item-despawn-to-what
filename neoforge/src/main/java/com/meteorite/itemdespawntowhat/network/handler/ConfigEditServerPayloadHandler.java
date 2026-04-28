package com.meteorite.itemdespawntowhat.network.handler;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.ConfigHandlerManager;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.network.EditSessionLockManager;
import com.meteorite.itemdespawntowhat.network.payload.c2s.RequestConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigChunkPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigPayload;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ConfigSnapshotPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

// 服务端接收客户端打开GUI 的发包后续处理
// 服务端 payload 处理器：负责配置快照请求、保存配置和会话释放。
public final class ConfigEditServerPayloadHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private ConfigEditServerPayloadHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 处理客户端的快照请求：先确保服务端缓存可用，再把当前缓存序列化后回传。
    public static void handleConfigSnapshotRequest(RequestConfigSnapshotPayload payload, IPayloadContext context) {
        EditSessionLockManager.touch();
        if (!ConfigExtractorManager.isInitialized()) {
            ConfigExtractorManager.initialize(Services.PLATFORM.getConfigDir());
        }

        try {
            BaseConfigHandler<?> handler = ConfigHandlerManager.getInstance(Services.PLATFORM.getConfigDir()).getHandler(payload.configType());
            if (handler == null) {
                LOGGER.error("[RequestConfigSnapshotPayload] No handler found for config type: {}", payload.configType());
                return;
            }

            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            if (!EditSessionLockManager.tryAcquire(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.locked"));
                return;
            }

            List<? extends BaseConversionConfig> configs = ConfigExtractorManager.getConfigByType(payload.configType());
            String jsonData = handler.serializeToJson(configs);
            Services.PLATFORM.sendToPlayer(
                    serverPlayer,
                    new ConfigSnapshotPayload(payload.configType(), jsonData)
            );
        } catch (Exception e) {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                EditSessionLockManager.release(serverPlayer);
            }
            LOGGER.error("Failed to handle config snapshot request for type {}", payload.configType(), e);
        }
    }

    public static void handleReleaseEditSession(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            EditSessionLockManager.release(serverPlayer);
            SaveConfigChunkAccumulator.clear(serverPlayer);
        }
    }

    // 处理客户端发起的配置保存请求：写盘后立即刷新服务端缓存，避免下一次打开看到旧数据。
    public static void handleSaveConfig(SaveConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        EditSessionLockManager.touch();

        try {
            saveConfigData(serverPlayer, payload.configType(), payload.configData());
        } finally {
            EditSessionLockManager.release(serverPlayer);
        }
    }

    // 处理客户端发起的分包保存请求：收齐后复用同一条保存逻辑。
    public static void handleSaveConfigChunk(SaveConfigChunkPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        EditSessionLockManager.touch();

        String jsonData = SaveConfigChunkAccumulator.acceptChunk(serverPlayer, payload);
        if (jsonData == null) {
            return;
        }

        try {
            saveConfigData(serverPlayer, payload.configType(), jsonData);
        } finally {
            EditSessionLockManager.release(serverPlayer);
        }
    }

    private static void saveConfigData(ServerPlayer serverPlayer, ConfigType configType, String configData) {
        try {
            BaseConfigHandler<?> handler = ConfigHandlerManager.getInstance(Services.PLATFORM.getConfigDir()).getHandler(configType);

            if (handler == null) {
                LOGGER.error("[SaveConfigPayload] No handler found for config type: {}", configType);
                return;
            }

            List<? extends BaseConversionConfig> newConfigs = handler.deserializeFromJson(configData);
            if (newConfigs == null || newConfigs.isEmpty()) {
                LOGGER.warn("Received empty or invalid config data from client");
                return;
            }

            handler.saveConfig(newConfigs);
            ConfigExtractorManager.reloadConfigsForType(Services.PLATFORM.getConfigDir(), configType);

            LOGGER.info("Successfully saved {} configs of type {} from player {}",
                    newConfigs.size(),
                    configType.getFileName(),
                    serverPlayer.getName().getString()
            );
        } catch (IOException e) {
            LOGGER.error("Failed to persist config save request for type {}", configType.getFileName(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error while processing save config request for type {}",
                    configType.getFileName(), e);
        }
    }
}
