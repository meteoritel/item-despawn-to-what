package com.meteorite.itemdespawntowhat.client.event;

import com.meteorite.itemdespawntowhat.ItemDespawnToWhat;
import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.ui.ConfigScreenRegistry;
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
        ConfigScreenRegistry.register(ConfigType.ITEM_TO_ITEM, ItemToItemEditScreen::new);
        ConfigScreenRegistry.register(ConfigType.ITEM_TO_MOB, ItemToMobEditScreen::new);
        ConfigScreenRegistry.register(ConfigType.ITEM_TO_BLOCK, ItemToBlockEditScreen::new);
        ConfigScreenRegistry.register(ConfigType.ITEM_TO_XP_ORB, ItemToExpOrbEditScreen::new);
        ConfigScreenRegistry.register(ConfigType.ITEM_TO_WORLD_EFFECT, ItemToWorldEffectEditScreen::new);
    }

    // 注册按键
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.openGuiKey);
    }
}
