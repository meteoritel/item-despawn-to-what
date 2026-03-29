package com.meteorite.itemdespawntowhat.client.network;

import com.meteorite.itemdespawntowhat.network.OpenGuiPayload;
import com.meteorite.itemdespawntowhat.ui.screen.ConfigSelectionScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientNetworkHandler {

    public static void handleOpenGui(OpenGuiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ConfigSelectionScreen()));
    }
}
