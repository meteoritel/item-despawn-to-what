package com.meteorite.itemdespawntowhat.network.handler;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.io.JsonConfigRepository;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeDefinition;
import com.meteorite.itemdespawntowhat.config.type.ConversionTypeRegistry;
import com.meteorite.itemdespawntowhat.network.ConfigEditAccessControl;
import com.meteorite.itemdespawntowhat.network.ConfigEditLimits;
import com.meteorite.itemdespawntowhat.network.EditSessionLockManager;
import com.meteorite.itemdespawntowhat.network.payload.c2s.RequestConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigChunkPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigPayload;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * 跨平台服务端配置编辑请求处理器。
 */
public final class ConfigEditServerPayloadHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private ConfigEditServerPayloadHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void handleConfigSnapshotRequest(RequestConfigSnapshotPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !hasEditPermission(serverPlayer)) {
            return;
        }

        if (!ConfigExtractorManager.isInitialized()) {
            ConfigExtractorManager.initialize(Services.PLATFORM.getConfigDir());
        }

        try {
            ConversionTypeDefinition<?> definition = ConversionTypeRegistry.get(payload.configType());

            if (!EditSessionLockManager.tryAcquire(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.locked"));
                return;
            }

            List<? extends BaseConversionConfig> configs = ConfigExtractorManager.getConfigByType(payload.configType());
            String jsonData = definition.codec().serialize(configs);
            Services.PLATFORM.sendToPlayer(
                    serverPlayer,
                    new ConfigSnapshotPayload(payload.configType(), jsonData)
            );
        } catch (Exception e) {
            EditSessionLockManager.release(serverPlayer);
            LOGGER.error("Failed to handle config snapshot request for type {}", payload.configType(), e);
        }
    }

    public static void handleReleaseEditSession(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            EditSessionLockManager.release(serverPlayer);
            SaveConfigChunkAccumulator.clear(serverPlayer);
        }
    }

    public static void handleSaveConfig(SaveConfigPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !hasOwnedEditSession(serverPlayer)) {
            return;
        }
        EditSessionLockManager.touch(serverPlayer);

        try {
            saveConfigData(serverPlayer, payload.configType(), payload.configData());
        } finally {
            EditSessionLockManager.release(serverPlayer);
        }
    }

    public static void handleSaveConfigChunk(SaveConfigChunkPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !hasOwnedEditSession(serverPlayer)) {
            return;
        }
        EditSessionLockManager.touch(serverPlayer);

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
            if (!ConfigEditLimits.isConfigSizeValid(configData)) {
                serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.payload_too_large"));
                LOGGER.warn("Rejected oversized config payload from player {} ({})",
                        serverPlayer.getName().getString(), serverPlayer.getUUID());
                return;
            }

            ConversionTypeDefinition<?> definition = ConversionTypeRegistry.get(configType);
            JsonConfigRepository<?> repository = definition.repository(Services.PLATFORM.getConfigDir());

            List<? extends BaseConversionConfig> newConfigs = definition.codec().deserializeStrict(configData);
            if (newConfigs.stream().anyMatch(config -> config == null || !config.shouldProcess())) {
                serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.save_error"));
                LOGGER.warn("Rejected invalid config data from player {} ({})",
                        serverPlayer.getName().getString(), serverPlayer.getUUID());
                return;
            }

            byte[] previousConfig = repository.readBytes();
            repository.save(newConfigs);
            if (!ConfigExtractorManager.reloadConfigsForType(Services.PLATFORM.getConfigDir(), configType)) {
                repository.restoreBytes(previousConfig);
                serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.save_error"));
                LOGGER.error("Failed to reload type {}; previous config file and runtime cache were restored",
                        configType.getFileName());
                return;
            }

            LOGGER.info("Successfully saved {} configs of type {} from player {}",
                    newConfigs.size(), configType.getFileName(), serverPlayer.getName().getString());
        } catch (IOException e) {
            serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.save_error"));
            LOGGER.error("Failed to persist config save request for type {}", configType.getFileName(), e);
        } catch (Exception e) {
            serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.save_error"));
            LOGGER.error("Unexpected error while processing save config request for type {}",
                    configType.getFileName(), e);
        }
    }

    private static boolean hasEditPermission(ServerPlayer serverPlayer) {
        if (ConfigEditAccessControl.canEdit(serverPlayer)) {
            return true;
        }

        serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.permission_denied"));
        LOGGER.warn("Rejected unauthorized config edit request from player {} ({})",
                serverPlayer.getName().getString(), serverPlayer.getUUID());
        return false;
    }

    private static boolean hasOwnedEditSession(ServerPlayer serverPlayer) {
        if (!hasEditPermission(serverPlayer)) {
            return false;
        }
        if (EditSessionLockManager.isOwnedBy(serverPlayer)) {
            return true;
        }

        serverPlayer.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.edit.session_invalid"));
        LOGGER.warn("Rejected config save without an owned edit session from player {} ({})",
                serverPlayer.getName().getString(), serverPlayer.getUUID());
        return false;
    }
}
