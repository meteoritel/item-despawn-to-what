package com.meteorite.itemdespawntowhat.config;

import com.meteorite.itemdespawntowhat.config.conversion.*;

import java.util.HashMap;
import java.util.Map;

public enum ConfigType {
    ITEM_TO_ITEM("item_to_item.json", ItemToItemConfig.class),
    ITEM_TO_MOB("item_to_mob.json", ItemToMobConfig.class),
    ITEM_TO_BLOCK("item_to_block.json", ItemToBlockConfig.class),
    ITEM_TO_XP_ORB("item_to_xp_orb.json", ItemToExpOrbConfig.class),
    ITEM_TO_SIDE_EFFECT("item_to_side_effect.json", ItemToSideEffectConfig.class);

    private final String fileName;
    private final Class<? extends BaseConversionConfig> configClass;
    private static final Map<Class<? extends BaseConversionConfig>, ConfigType> CLASS_MAP;

    static {
        CLASS_MAP = new HashMap<>();
        for (ConfigType type : values()) {
            CLASS_MAP.put(type.configClass, type);
        }
    }

    ConfigType(String fileName, Class<? extends BaseConversionConfig> configClass) {
        this.fileName = fileName;
        this.configClass = configClass;
    }

    public String getFileName() {
        return fileName;
    }

    public Class<? extends BaseConversionConfig> getConfigClass() {
        return configClass;
    }

    public static ConfigType fromClass(Class<? extends BaseConversionConfig> clazz) {
        ConfigType type = CLASS_MAP.get(clazz);

        if (type == null) {
            throw new IllegalStateException("No ConfigType found for class: " + clazz.getName());
        }
        return type;
    }
}
