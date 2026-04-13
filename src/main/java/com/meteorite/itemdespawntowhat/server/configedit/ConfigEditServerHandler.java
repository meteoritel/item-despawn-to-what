package com.meteorite.itemdespawntowhat.server.configedit;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.ConfigHandlerManager;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.network.configedit.c2s.ReleaseEditSessionPayload;
import com.meteorite.itemdespawntowhat.network.configedit.c2s.RequestConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.network.configedit.c2s.SaveConfigPayload;
import com.meteorite.itemdespawntowhat.network.configedit.s2c.ConfigSnapshotPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public final class ConfigEditServerHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private ConfigEditServerHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 处理客户端的快照请求：先确保服务端缓存可用，再把当前缓存序列化后回传。
    public static void handleConfigSnapshotRequest(RequestConfigSnapshotPayload payload, IPayloadContext context) {
        if (!ConfigExtractorManager.isInitialized()) {
            ConfigExtractorManager.initialize();
        }

        try {
            BaseConfigHandler<?> handler = ConfigHandlerManager.getInstance().getHandler(payload.configType());
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
            String jsonData = handler.serializeToJsonUnchecked(configs);
            PacketDistributor.sendToPlayer(
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
        }
    }

    // 处理客户端发起的配置保存请求：写盘后立即刷新服务端缓存，避免下一次打开看到旧数据。
    public static void handleSaveConfig(SaveConfigPayload payload, IPayloadContext context) {
        ServerPlayer serverPlayer = context.player() instanceof ServerPlayer player ? player : null;
        try {
            BaseConfigHandler<?> handler = ConfigHandlerManager.getInstance().getHandler(payload.configType());

            if (handler == null) {
                LOGGER.error("[SaveConfigPayload] No handler found for config type: {}", payload.configType());
                return;
            }

            List<? extends BaseConversionConfig> newConfigs = handler.deserializeFromJson(payload.configData());
            if (newConfigs == null || newConfigs.isEmpty()) {
                LOGGER.warn("Received empty or invalid config data from client");
                return;
            }

            handler.saveConfigUnchecked(newConfigs);
            ConfigExtractorManager.reloadAllConfigs();

            if (serverPlayer != null) {
                LOGGER.info("Successfully saved {} configs of type {} from player {}",
                        newConfigs.size(),
                        payload.configType().getFileName(),
                        serverPlayer.getName().getString()
                );
            } else {
                LOGGER.info("Successfully saved {} configs of type {}",
                        newConfigs.size(),
                        payload.configType().getFileName()
                );
            }
        } catch (IOException e) {
            LOGGER.error("Failed to persist config save request for type {}", payload.configType().getFileName(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error while processing save config request for type {}",
                    payload.configType().getFileName(), e);
        } finally {
            if (serverPlayer != null) {
                EditSessionLockManager.release(serverPlayer);
            }
        }
    }
}
