package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.EditorShortcut;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/** 客户端按键入口：驱动会话心跳，并把快捷键交给共用的编辑器快捷键逻辑。 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class InputEvents {

    // 客户端 tick：驱动编辑工作区的会话心跳（契约 §3.1）
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        RuleEditClientWorkspace.instance().tick();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!ModKeyBindings.openGuiKey.consumeClick()) {
            return;
        }
        // 快捷键只重开已有会话的界面，无会话时提示用指令
        EditorShortcut.activate();
    }
}
