package com.meteorite.itemdespawntowhat.network.configedit;

import com.meteorite.itemdespawntowhat.server.configedit.EditSessionLockManager;

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
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public class NetworkEvent {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // 客户端请求服务端下发当前配置快照，服务端按自己的缓存返回结果。
        registrar.playToServer(
                RequestConfigSnapshotPayload.TYPE,
                RequestConfigSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.meteorite.itemdespawntowhat.server.configedit.ConfigEditServerHandler.handleConfigSnapshotRequest(payload, context))
        );

        // 客户端关闭编辑会话时释放服务端锁。
        registrar.playToServer(
                ReleaseEditSessionPayload.TYPE,
                ReleaseEditSessionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.meteorite.itemdespawntowhat.server.configedit.ConfigEditServerHandler.handleReleaseEditSession(context))
        );

        // 客户端发包到服务端，服务端保存配置并刷新缓存。
        registrar.playToServer(
                SaveConfigPayload.TYPE,
                SaveConfigPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.meteorite.itemdespawntowhat.server.configedit.ConfigEditServerHandler.handleSaveConfig(payload, context))
        );
    }
}
