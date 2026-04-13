package com.meteorite.itemdespawntowhat.network.handler;

import com.meteorite.itemdespawntowhat.network.ConfigEditSnapshotManager;
import com.meteorite.itemdespawntowhat.client.register.ConfigEditScreenRegistry;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConfigTypeSelectionScreen;
import com.meteorite.itemdespawntowhat.network.payload.s2c.ConfigSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// 客户端接收到服务端发包后的处理
public class ConfigEditClientPayloadHandler {

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
