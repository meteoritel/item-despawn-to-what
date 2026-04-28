package com.meteorite.itemdespawntowhat.network.handler;

import com.meteorite.itemdespawntowhat.client.register.ConfigEditScreenRegistry;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConfigTypeSelectionScreen;
import com.meteorite.itemdespawntowhat.network.ConfigEditSnapshotManager;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ConfigSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ConfigEditClientPayloadHandler {

    private ConfigEditClientPayloadHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void handleOpenGui(Object payload, ClientPlayNetworking.Context context) {
        Minecraft.getInstance().execute(() -> {
            ConfigEditSnapshotManager.clearAll();
            Minecraft.getInstance().setScreen(new ConfigTypeSelectionScreen());
        });
    }

    public static void handleConfigSnapshot(ConfigSnapshotPayload payload, ClientPlayNetworking.Context context) {
        Minecraft.getInstance().execute(() -> {
            ConfigEditSnapshotManager.putSnapshot(payload.configType(), payload.configJson());
            Minecraft.getInstance().setScreen(ConfigEditScreenRegistry.create(payload.configType()));
        });
    }

    public static void handleForceCloseEditor(Object payload, ClientPlayNetworking.Context context) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new ConfigTypeSelectionScreen());
        });
    }
}
