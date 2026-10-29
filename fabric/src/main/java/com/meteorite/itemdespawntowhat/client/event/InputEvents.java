package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewResources;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric 客户端 tick 事件注册入口。
 */
public class InputEvents {

    /** 注册客户端 tick 钩子：驱动编辑工作区的会话心跳。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            RuleEditClientWorkspace.instance().tick();
            PreviewResources.observe();
        });
    }
}
