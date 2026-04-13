package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.ConfigHandlerManager;
import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.config.conversion.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.network.configedit.c2s.ReleaseEditSessionPayload;
import com.meteorite.itemdespawntowhat.network.configedit.c2s.RequestConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.network.configedit.c2s.SaveConfigPayload;
import com.meteorite.itemdespawntowhat.network.configedit.s2c.ConfigSnapshotPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public class NetworkEvent {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // 客户端请求服务端下发当前配置快照，服务端按自己的缓存返回结果。
        registrar.playToServer(
                RequestConfigSnapshotPayload.TYPE,
                RequestConfigSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleConfigSnapshotRequest(payload, context))
        );

        // 客户端关闭编辑会话时释放服务端锁。
        registrar.playToServer(
                ReleaseEditSessionPayload.TYPE,
                ReleaseEditSessionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleReleaseEditSession(context))
        );

        // 客户端发包到服务端，服务端保存配置并刷新缓存。
        registrar.playToServer(
                SaveConfigPayload.TYPE,
                SaveConfigPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSaveConfig(payload, context))
        );
    }

    // 处理客户端的快照请求：先确保服务端缓存可用，再把当前缓存序列化后回传。
    private static void handleConfigSnapshotRequest(RequestConfigSnapshotPayload payload, IPayloadContext context) {
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
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
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

    private static void handleReleaseEditSession(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            EditSessionLockManager.release(serverPlayer);
        }
    }

    // 处理客户端发起的配置保存请求：写盘后立即刷新服务端缓存，避免下一次打开看到旧数据。
    private static void handleSaveConfig(SaveConfigPayload payload, IPayloadContext context) {
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
