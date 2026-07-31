package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConfigTypeSelectionScreen;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

/**
 * Fabric 客户端按键事件注册入口。
 */
public class InputEvents {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeyBindings.openGuiKey.consumeClick()) {
                if (PlayerStateChecker.isSinglePlayerMode(client)) {
                    client.setScreen(new ConfigTypeSelectionScreen());
                } else if (PlayerStateChecker.isMultiPlayerMode(client) && client.player != null) {
                    client.player.sendSystemMessage(Component.translatable(
                            "gui.itemdespawntowhat.keybind.disabled.multiplayer"));
                }
            }
        });
    }
}
