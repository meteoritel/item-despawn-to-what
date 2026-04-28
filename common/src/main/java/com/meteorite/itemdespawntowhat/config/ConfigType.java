package com.meteorite.itemdespawntowhat.config;

public enum ConfigType {
    ITEM_TO_ITEM("item_to_item.json"),
    ITEM_TO_MOB("item_to_mob.json"),
    ITEM_TO_BLOCK("item_to_block.json"),
    ITEM_TO_XP_ORB("item_to_xp_orb.json"),
    ITEM_TO_WORLD_EFFECT("item_to_world_effect.json");

    private final String fileName;

    ConfigType(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
