package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.client.key.EditorShortcut;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric 客户端按键事件注册入口。
 */
public class InputEvents {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 驱动编辑工作区的会话心跳（契约 §3.1）
            RuleEditClientWorkspace.instance().tick();
            while (ModKeyBindings.openGuiKey.consumeClick()) {
                // 快捷键只重开已有会话的界面，无会话时提示用指令；默认按键未绑定，需在控制设置里手动绑定
                EditorShortcut.activate();
            }
        });
    }
}
