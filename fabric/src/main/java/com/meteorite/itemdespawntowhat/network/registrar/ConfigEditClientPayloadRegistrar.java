package com.meteorite.itemdespawntowhat.network.registrar;

import com.meteorite.itemdespawntowhat.network.handler.ConfigEditClientPayloadHandler;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ForceCloseEditorPayload;
import com.meteorite.itemdespawntowhat.network.payload.s2c.OpenGuiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ConfigEditClientPayloadRegistrar {

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenGuiPayload.TYPE, OpenGuiPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSnapshotPayload.TYPE, ConfigSnapshotPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ForceCloseEditorPayload.TYPE, ForceCloseEditorPayload.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(OpenGuiPayload.TYPE,
                (payload, context) -> ConfigEditClientPayloadHandler.handleOpenGui());

        ClientPlayNetworking.registerGlobalReceiver(ConfigSnapshotPayload.TYPE,
                (payload, context) -> ConfigEditClientPayloadHandler.handleConfigSnapshot(payload));

        ClientPlayNetworking.registerGlobalReceiver(ForceCloseEditorPayload.TYPE,
                (payload, context) -> ConfigEditClientPayloadHandler.handleForceCloseEditor());
    }
}
