package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.client.register.ConfigEditScreenRegistry;
import com.meteorite.itemdespawntowhat.network.configedit.s2c.ConfigSnapshotPayload;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConfigSelectionScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientNetworkHandler {

    public static void handleOpenGui(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 选择界面只负责发起请求，不保留旧快照。
            ClientConfigSnapshotManager.clearAll();
            Minecraft.getInstance().setScreen(new ConfigSelectionScreen());
        });
    }

    public static void handleConfigSnapshot(ConfigSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 快照到达后再构建具体编辑屏幕，确保屏幕初始数据来自服务端缓存。
            ClientConfigSnapshotManager.putSnapshot(payload.configType(), payload.configJson());
            Minecraft.getInstance().setScreen(ConfigEditScreenRegistry.create(payload.configType()));
        });
    }
}
