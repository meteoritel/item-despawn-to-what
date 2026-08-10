package com.meteorite.itemdespawntowhat.config;

import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.resources.ResourceLocation;

/**
 * 配置类型及其稳定标识和文件名。
 */
public enum ConfigType {
    ITEM_TO_ITEM("item_to_item", "item_to_item.json"),
    ITEM_TO_MOB("item_to_mob", "item_to_mob.json"),
    ITEM_TO_BLOCK("item_to_block", "item_to_block.json"),
    ITEM_TO_XP_ORB("item_to_xp_orb", "item_to_xp_orb.json"),
    ITEM_TO_WORLD_EFFECT("item_to_world_effect", "item_to_world_effect.json");

    private final ResourceLocation id;
    private final String fileName;

    ConfigType(String path, String fileName) {
        this.id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
        this.fileName = fileName;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getSerializedId() {
        return id.toString();
    }

    public String getFileName() {
        return fileName;
    }

    public static ConfigType fromSerializedId(String id) {
        for (ConfigType type : values()) {
            if (type.getSerializedId().equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown config type id: " + id);
    }
}
