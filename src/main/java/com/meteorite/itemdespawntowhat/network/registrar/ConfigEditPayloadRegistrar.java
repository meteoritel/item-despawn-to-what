package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.network.handler.ConfigEditServerPayloadHandler;
import com.meteorite.itemdespawntowhat.network.payload.c2s.ReleaseEditSessionPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.RequestConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigChunkPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// 服务端侧网络注册入口：只负责注册 C2S 配置编辑相关 payload
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public class ConfigEditPayloadRegistrar {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // 客户端请求服务端下发当前配置快照，服务端按自己的缓存返回结果。
        registrar.playToServer(
                RequestConfigSnapshotPayload.TYPE,
                RequestConfigSnapshotPayload.STREAM_CODEC,
                (payload, context) ->
                        context.enqueueWork(() -> ConfigEditServerPayloadHandler.handleConfigSnapshotRequest(payload, context))
        );

        // 客户端关闭编辑会话时释放服务端锁。
        registrar.playToServer(
                ReleaseEditSessionPayload.TYPE,
                ReleaseEditSessionPayload.STREAM_CODEC,
                (payload, context) ->
                        context.enqueueWork(() -> ConfigEditServerPayloadHandler.handleReleaseEditSession(context))
        );

        // 客户端发包到服务端，服务端保存配置并刷新缓存。
        registrar.playToServer(
                SaveConfigPayload.TYPE,
                SaveConfigPayload.STREAM_CODEC,
                (payload, context) ->
                        context.enqueueWork(() -> ConfigEditServerPayloadHandler.handleSaveConfig(payload, context))
        );

        // 超长 JSON 走分包保存，服务端按 transferId 重组后复用同一保存流程。
        registrar.playToServer(
                SaveConfigChunkPayload.TYPE,
                SaveConfigChunkPayload.STREAM_CODEC,
                (payload, context) ->
                        context.enqueueWork(() -> ConfigEditServerPayloadHandler.handleSaveConfigChunk(payload, context))
        );
    }
}
