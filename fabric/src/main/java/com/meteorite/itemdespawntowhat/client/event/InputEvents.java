package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorPlaceholderScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric 客户端按键事件注册入口。
 */
public class InputEvents {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeyBindings.openGuiKey.consumeClick()) {
                if (client.level != null) {
                    client.setScreen(new RuleEditorPlaceholderScreen());
                }
            }
        });
    }
}
