package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConfigTypeSelectionScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class InputEvents {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeyBindings.openGuiKey.consumeClick()) {
                Minecraft.getInstance().setScreen(new ConfigTypeSelectionScreen());
            }
        });
    }
}
