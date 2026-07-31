package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.client.register.ConfigEditScreenRegistry;
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
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            ConfigEditSnapshotManager.putSnapshot(payload.configType(), payload.configJson());
            client.setScreen(ConfigEditScreenRegistry.create(payload.configType()));
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
