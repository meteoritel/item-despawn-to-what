package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.client.register.ClientConversionTypeRegistry;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConfigTypeSelectionScreen;
import com.meteorite.itemdespawntowhat.network.ConfigEditSnapshotManager;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ConfigSnapshotPayload;
import net.minecraft.client.Minecraft;

/**
 * 跨平台客户端配置编辑 payload 响应逻辑。
 */
public final class ConfigEditClientPayloadHandler {

    private ConfigEditClientPayloadHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void handleOpenGui() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            ConfigEditSnapshotManager.clearAll();
            client.setScreen(new ConfigTypeSelectionScreen());
        });
    }

    public static void handleConfigSnapshot(ConfigSnapshotPayload payload) {
        if (payload.typeId() == null || !ClientConversionTypeRegistry.contains(payload.typeId())) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            ConfigEditSnapshotManager.putSnapshot(payload.typeId(), payload.configJson());
            client.setScreen(ClientConversionTypeRegistry.createScreen(payload.typeId()));
        });
    }

    public static void handleForceCloseEditor() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            ConfigEditSnapshotManager.clearAll();
            client.setScreen(new ConfigTypeSelectionScreen());
        });
    }
}
