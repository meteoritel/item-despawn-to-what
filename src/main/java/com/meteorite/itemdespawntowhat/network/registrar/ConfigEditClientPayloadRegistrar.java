package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.network.handler.ConfigEditClientPayloadHandler;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.network.payload.s2c.OpenGuiPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// 注册客户端接收服务端的发包
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class ConfigEditClientPayloadRegistrar {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // 客户端只接收服务端下发的“打开界面”与“配置快照”，不参与本地生成配置。
        registrar.playToClient(
                OpenGuiPayload.TYPE,
                OpenGuiPayload.STREAM_CODEC,
                (payload, context) -> ConfigEditClientPayloadHandler.handleOpenGui(context)
        );
        registrar.playToClient(
                ConfigSnapshotPayload.TYPE,
                ConfigSnapshotPayload.STREAM_CODEC,
                ConfigEditClientPayloadHandler::handleConfigSnapshot
        );
    }
}
