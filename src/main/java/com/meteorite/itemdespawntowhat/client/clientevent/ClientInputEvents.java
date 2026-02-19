package com.meteorite.itemdespawntowhat.client.clientevent;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.ui.Screen.ConfigSelectionScreen;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public class ClientInputEvents {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // 快捷键只在单人模式下起作用
        if (PlayerStateChecker.isSinglePlayerServerReady(Minecraft.getInstance())
                && ModKeyBindings.openGuiKey.consumeClick()) {
            Minecraft.getInstance().setScreen(new ConfigSelectionScreen());
        }
    }

    // 游戏开始页面入口的按钮
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            // 按钮位置：右上角，距右5px，距顶5px
            int x = event.getScreen().width - 25;
            int y = 5;
            Button button = Button.builder(
                    Component.empty(),
                    btn -> Minecraft.getInstance().setScreen(new ConfigSelectionScreen())
            ).bounds(x, y, 20, 20).build();
            event.addListener(button);
        }
    }

}
