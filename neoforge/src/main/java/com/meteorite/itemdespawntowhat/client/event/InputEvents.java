package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorPlaceholderScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** 客户端按键入口，打开规则编辑占位屏幕。 */
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class InputEvents {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!ModKeyBindings.openGuiKey.consumeClick()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        // 占位入口只显示本地说明，不发起编辑会话。
        if (minecraft.level != null) {
            minecraft.setScreen(new RuleEditorPlaceholderScreen());
            return;
        }

    }
}
