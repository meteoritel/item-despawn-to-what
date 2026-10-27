package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 客户端 tick 入口：驱动编辑工作区的会话心跳。 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class InputEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        RuleEditClientWorkspace.instance().tick();
    }
    /** 登录时创建该连接的编辑接收对象。 */
    @net.neoforged.bus.api.SubscribeEvent
    public static void connected(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        RuleEditClientWorkspace.instance().bindConnection(event.getConnection());
    }

    /** 断开时只清理对应连接。 */
    @net.neoforged.bus.api.SubscribeEvent
    public static void disconnected(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        RuleEditClientWorkspace.instance().unbindConnection(event.getConnection());
    }
}
