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

    /** 客户端 tick：驱动编辑工作区的会话心跳。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        RuleEditClientWorkspace.instance().tick();
    }
}
