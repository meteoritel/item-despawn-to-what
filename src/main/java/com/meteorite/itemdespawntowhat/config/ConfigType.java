package com.meteorite.itemdespawntowhat.config;

import com.meteorite.itemdespawntowhat.config.conversion.*;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum ConfigType {
    ITEM_TO_ITEM("item_to_item.json", ItemToItemConfig.class),
    ITEM_TO_MOB("item_to_mob.json", ItemToMobConfig.class),
    ITEM_TO_BLOCK("item_to_block.json", ItemToBlockConfig.class),
    ITEM_TO_XP_ORB("item_to_xp_orb.json", ItemToExpOrbConfig.class),
    ITEM_TO_WORLD_EFFECT("item_to_world_effect.json", ItemToWorldEffectConfig.class);

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

    public String getTooltipKey() {
        return "gui.itemdespawntowhat.config_type." + name().toLowerCase() + ".tooltip";
    }

    /**
     * 返回用于渲染悬停说明框的 Component 列表。
     * tooltip 内容可在语言文件中自由换行（通过多个 key 后缀区分行，或使用 \n 分隔）。
     */
    // 返回用于渲染悬停说明框的 Component 列表
    public List<Component> getTooltipLines() {
        return List.of(Component.translatable(getTooltipKey()));
    }


}
