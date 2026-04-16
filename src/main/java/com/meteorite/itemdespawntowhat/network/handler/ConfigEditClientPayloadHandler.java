package com.meteorite.itemdespawntowhat.network.handler;

import com.meteorite.itemdespawntowhat.network.ConfigEditSnapshotManager;
import com.meteorite.itemdespawntowhat.client.register.ConfigEditScreenRegistry;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConfigTypeSelectionScreen;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ConfigSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// 客户端 payload 处理器：负责打开界面并接收服务端下发的配置快照。
public final class ConfigEditClientPayloadHandler {
    private ConfigEditClientPayloadHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void handleOpenGui(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 选择界面只负责发起请求，不保留旧快照。
            ConfigEditSnapshotManager.clearAll();
            Minecraft.getInstance().setScreen(new ConfigTypeSelectionScreen());
        });
    }

    public static void handleConfigSnapshot(ConfigSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 快照到达后再构建具体编辑屏幕，确保屏幕初始数据来自服务端缓存。
            ConfigEditSnapshotManager.putSnapshot(payload.configType(), payload.configJson());
            Minecraft.getInstance().setScreen(ConfigEditScreenRegistry.create(payload.configType()));
        });
    }
}
