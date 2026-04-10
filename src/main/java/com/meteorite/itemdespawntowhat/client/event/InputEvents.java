package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.ui.screen.ConfigSelectionScreen;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

// 客户端输入事件类：按键注册，按钮注册
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class InputEvents {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // 快捷键只在单人模式下起作用
        if (PlayerStateChecker.isSinglePlayerServerReady(Minecraft.getInstance())
                && ModKeyBindings.openGuiKey.consumeClick()) {
            Minecraft.getInstance().setScreen(new ConfigSelectionScreen());
        }
    }
}
