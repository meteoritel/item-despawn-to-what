package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.ui.screen.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

// 客户端注册事件类
@EventBusSubscriber(modid = ItemDespawnToWhat.MOD_ID, value = Dist.CLIENT)
public class RegisterEvent {

    // 注册GUI
    static {
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_ITEM, ItemToItemEditScreen::new);
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_MOB, ItemToMobEditScreen::new);
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_BLOCK, ItemToBlockEditScreen::new);
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_XP_ORB, ItemToExpOrbEditScreen::new);
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_WORLD_EFFECT, ItemToWorldEffectEditScreen::new);
    }

    // 注册按键
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.openGuiKey);
    }
}
