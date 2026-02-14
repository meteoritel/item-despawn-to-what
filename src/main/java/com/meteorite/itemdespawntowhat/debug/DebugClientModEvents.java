package com.meteorite.itemdespawntowhat.debug;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.ui.Screen.ConfigSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID)
public class DebugClientModEvents {
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            // 按钮位置：右上角，距右105px，距顶5px
            int x = event.getScreen().width - 105;
            int y = 5;
            Button button = Button.builder(
                    Component.translatable("gui.itemdespawntowhat.open_config"),
                    btn -> Minecraft.getInstance().setScreen(new ConfigSelectionScreen())
            ).bounds(x, y, 100, 20).build();
            event.addListener(button);
        }
    }
}
