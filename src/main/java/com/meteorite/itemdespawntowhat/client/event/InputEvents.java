package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.ui.screen.ConfigTypeSelectionScreen;
import com.meteorite.itemdespawntowhat.util.PlayerStateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

// 客户端输入事件类：按键注册，按钮注册
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class InputEvents {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!ModKeyBindings.openGuiKey.consumeClick()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        // 单人模式继续打开编辑入口，联机环境只给本地提示，不再发起编辑请求。
        if (PlayerStateChecker.isSinglePlayerServerReady(minecraft)) {
            minecraft.setScreen(new ConfigTypeSelectionScreen());
            return;
        }

        if (PlayerStateChecker.isMultiPlayerServerConnected(minecraft) && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("gui.itemdespawntowhat.keybind.disabled.multiplayer"));
        }
    }
}
