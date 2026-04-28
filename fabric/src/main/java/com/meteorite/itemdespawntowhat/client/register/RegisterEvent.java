package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToBlockEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToExpOrbEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToItemEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToMobEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToWorldEffectEditScreen;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public class RegisterEvent {

    static {
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_ITEM, ItemToItemEditScreen::new);
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_MOB, ItemToMobEditScreen::new);
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_BLOCK, ItemToBlockEditScreen::new);
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_XP_ORB, ItemToExpOrbEditScreen::new);
        ConfigEditScreenRegistry.register(ConfigType.ITEM_TO_WORLD_EFFECT, ItemToWorldEffectEditScreen::new);
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.openGuiKey);
    }
}
