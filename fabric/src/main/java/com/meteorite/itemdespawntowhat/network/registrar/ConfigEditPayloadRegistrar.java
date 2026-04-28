package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.network.handler.ConfigEditServerPayloadHandler;
import com.meteorite.itemdespawntowhat.network.payload.c2s.ReleaseEditSessionPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.RequestConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigChunkPayload;
import com.meteorite.itemdespawntowhat.network.payload.c2s.SaveConfigPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ConfigEditPayloadRegistrar {

    public static void register() {
        PayloadTypeRegistry.playC2S().register(RequestConfigSnapshotPayload.TYPE, RequestConfigSnapshotPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ReleaseEditSessionPayload.TYPE, ReleaseEditSessionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SaveConfigPayload.TYPE, SaveConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SaveConfigChunkPayload.TYPE, SaveConfigChunkPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestConfigSnapshotPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ConfigEditServerPayloadHandler.handleConfigSnapshotRequest(payload, context)));

        ServerPlayNetworking.registerGlobalReceiver(ReleaseEditSessionPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ConfigEditServerPayloadHandler.handleReleaseEditSession(payload, context)));

        ServerPlayNetworking.registerGlobalReceiver(SaveConfigPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ConfigEditServerPayloadHandler.handleSaveConfig(payload, context)));

        ServerPlayNetworking.registerGlobalReceiver(SaveConfigChunkPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ConfigEditServerPayloadHandler.handleSaveConfigChunk(payload, context)));
    }
}
