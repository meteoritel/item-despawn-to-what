package com.meteorite.itemdespawntowhat.network;

import com.meteorite.itemdespawntowhat.ConfigManager;
import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.config.BaseConversionConfig;
import com.meteorite.itemdespawntowhat.config.handler.BaseConfigHandler;
import com.meteorite.itemdespawntowhat.ui.Screen.ConfigSelectionScreen;
import net.minecraft.client.Minecraft;
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

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                OpenGuiPayload.TYPE,
                OpenGuiPayload.STREAM_CODEC,
                (payload, context) -> {
                    // 安排到客户端线程
                    context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ConfigSelectionScreen()));
                }
        );

        // 客户端发包到服务端，服务端保存配置
        registrar.playToServer(
                SaveConfigPayload.TYPE,
                SaveConfigPayload.STREAM_CODEC,
                (payload, context) -> {
                    // 在服务端线程处理
                    context.enqueueWork(() -> handleSaveConfig(payload, context));
                }
        );

    }

    // 处理客户端发起的配置保存请求
    private static void handleSaveConfig(SaveConfigPayload payload, IPayloadContext context) {

        try {
            BaseConfigHandler<?> handler = ConfigManager.getInstance().getHandler(payload.configType());

            if (handler == null) {
                LOGGER.error("No handler found for config type: {}", payload.configType());
                return;
            }

            List<? extends BaseConversionConfig> newConfigs = handler.deserializeFromJson(payload.configData());
            if (newConfigs == null || newConfigs.isEmpty()) {
                LOGGER.warn("Received empty or invalid config data from client");
                return;
            }

            handler.saveConfigUnchecked(newConfigs);

            LOGGER.info("Successfully saved {} configs of type {} from player {}",
                    newConfigs.size(),
                    payload.configType().getFileName(),
                    context.player().getName().getString()
            );
        } catch (IOException e) {
            LOGGER.error("Failed to save config file on server: {}", payload.configType().getFileName(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error while handling save config packet", e);
        }
    }

}
