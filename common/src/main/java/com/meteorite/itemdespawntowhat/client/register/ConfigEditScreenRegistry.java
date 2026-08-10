package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToBlockEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToExpOrbEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToItemEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToMobEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToWorldEffectEditScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 配置类型到编辑界面的客户端工厂注册表。
 */
public final class ConfigEditScreenRegistry {
    private static final Map<ConfigType, Supplier<Screen>> SCREENS = new EnumMap<>(ConfigType.class);

    static {
        register(ConfigType.ITEM_TO_ITEM, ItemToItemEditScreen::new);
        register(ConfigType.ITEM_TO_MOB, ItemToMobEditScreen::new);
        register(ConfigType.ITEM_TO_BLOCK, ItemToBlockEditScreen::new);
        register(ConfigType.ITEM_TO_XP_ORB, ItemToExpOrbEditScreen::new);
        register(ConfigType.ITEM_TO_WORLD_EFFECT, ItemToWorldEffectEditScreen::new);
    }

    private ConfigEditScreenRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void register(ConfigType type, Supplier<Screen> factory) {
        SCREENS.put(type, factory);
    }

    public static Screen create(ConfigType type) {
        Supplier<Screen> factory = SCREENS.get(type);
        if (factory == null) {
            throw new IllegalStateException("No screen registered for ConfigType: " + type);
        }
        return factory.get();
    }
}
