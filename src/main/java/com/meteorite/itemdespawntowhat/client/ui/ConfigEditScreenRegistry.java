package com.meteorite.itemdespawntowhat.client.ui;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import net.minecraft.client.gui.screens.Screen;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class ConfigEditScreenRegistry {
    private static final Map<ConfigType, Supplier<Screen>> SCREENS = new EnumMap<>(ConfigType.class);

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
