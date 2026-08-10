package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.config.type.ConversionType;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToBlockEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToExpOrbEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToItemEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToMobEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.ItemToWorldEffectEditScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 配置类型到编辑界面的客户端工厂注册表。
 */
public final class ConfigEditScreenRegistry {
    private static final Map<ConversionType, Supplier<Screen>> SCREENS = new HashMap<>();

    static {
        register(BuiltinConversionTypes.ITEM_TO_ITEM, ItemToItemEditScreen::new);
        register(BuiltinConversionTypes.ITEM_TO_MOB, ItemToMobEditScreen::new);
        register(BuiltinConversionTypes.ITEM_TO_BLOCK, ItemToBlockEditScreen::new);
        register(BuiltinConversionTypes.ITEM_TO_XP_ORB, ItemToExpOrbEditScreen::new);
        register(BuiltinConversionTypes.ITEM_TO_WORLD_EFFECT, ItemToWorldEffectEditScreen::new);
    }

    private ConfigEditScreenRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void register(ConversionType type, Supplier<Screen> factory) {
        SCREENS.put(type, factory);
    }

    public static boolean contains(ConversionType type) {
        return SCREENS.containsKey(type);
    }

    public static Screen create(ConversionType type) {
        Supplier<Screen> factory = SCREENS.get(type);
        if (factory == null) {
            throw new IllegalStateException("No screen registered for conversion type: " + type);
        }
        return factory.get();
    }
}
